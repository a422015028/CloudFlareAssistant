package com.muort.upworker.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sucrase 转换输入
 * @param id 文件唯一标识（使用 relativePath）
 * @param content 文件原始内容
 * @param isTS 是否需要 TypeScript 转换
 * @param isJSX 是否需要 JSX 转换
 */
data class SucraseInput(
    val id: String,
    val content: String,
    val isTS: Boolean,
    val isJSX: Boolean
)

/**
 * Sucrase 转换结果
 */
data class SucraseResult(
    @SerializedName("id") val id: String,
    @SerializedName("success") val success: Boolean,
    @SerializedName("code") val code: String?,
    @SerializedName("error") val error: String?
)

/**
 * 使用 WebView 加载 Sucrase 库，批量将 TypeScript/JSX 文件转换为纯 JavaScript。
 *
 * Sucrase 是纯 JS 的 TS/JSX 转换器（193KB），无需原生依赖，
 * 通过 WebView 的 JS 引擎执行 transform() 完成类型剥离和 JSX 转换。
 */
@Singleton
class SucraseTransformer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var webView: WebView? = null

    private val initLatch = CountDownLatch(1)

    @Volatile
    private var currentInput: String = "[]"

    @Volatile
    private var resultCompleter: CompletableDeferred<String>? = null

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    private val bridge = object {
        @JavascriptInterface
        fun getInput(): String = currentInput

        @JavascriptInterface
        fun onComplete(result: String) {
            resultCompleter?.complete(result)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        synchronized(this) {
            if (webView != null) return
            Handler(Looper.getMainLooper()).post {
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        addJavascriptInterface(bridge, "sucraseBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                Timber.d("Sucrase WebView page loaded")
                                initLatch.countDown()
                            }
                        }
                    }
                    val sucraseJs = context.assets
                        .open("sucrase.min.js")
                        .bufferedReader()
                        .use { it.readText() }
                    val html = "<html><head><script>$sucraseJs</script></head><body></body></html>"
                    webView?.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)
                    Timber.d("Sucrase WebView initializing...")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to initialize Sucrase WebView")
                    initLatch.countDown()
                }
            }
        }
    }

    /**
     * 批量转换文件
     * 所有文件在一次 evaluateJavascript 调用中完成转换，减少 WebView 交互开销。
     *
     * @param files 需要转换的文件列表
     * @return 转换结果列表，顺序与输入一致
     */
    suspend fun transformBatch(files: List<SucraseInput>): List<SucraseResult> {
        if (files.isEmpty()) return emptyList()

        try {
            ensureWebView()

            if (!initLatch.await(30, TimeUnit.SECONDS)) {
                Timber.e("Sucrase WebView initialization timed out")
                return files.map { SucraseResult(it.id, false, null, "WebView init timeout") }
            }

            if (webView == null) {
                return files.map { SucraseResult(it.id, false, null, "WebView not available") }
            }

            // 准备输入 JSON
            currentInput = gson.toJson(files.map {
                mapOf(
                    "id" to it.id,
                    "content" to it.content,
                    "isTS" to it.isTS,
                    "isJSX" to it.isJSX
                )
            })
            Timber.d("transformBatch: input size = ${currentInput.length} chars, ${files.size} files")

            val deferred = CompletableDeferred<String>()
            resultCompleter = deferred

            withContext(Dispatchers.Main) {
                val js = """
                    (function() {
                        try {
                            var input = sucraseBridge.getInput();
                            var files = JSON.parse(input);
                            var results = [];
                            for (var i = 0; i < files.length; i++) {
                                var f = files[i];
                                try {
                                    var transforms = [];
                                    if (f.isTS) transforms.push("typescript");
                                    if (f.isJSX) transforms.push("jsx");
                                    var options = {transforms: transforms};
                                    // 检测 @jsx pragma 注释
                                    var jsxMatch = f.content.match(/@jsx\s+(\S+)/);
                                    if (jsxMatch) {
                                        options.jsxPragma = jsxMatch[1];
                                    }
                                    // 检测 @jsxFrag pragma 注释
                                    var fragMatch = f.content.match(/@jsxFrag\s+(\S+)/);
                                    if (fragMatch) {
                                        options.jsxFragmentPragma = fragMatch[1];
                                    }
                                    var result = Sucrase.transform(f.content, options);
                                    results.push({id: f.id, success: true, code: result.code});
                                } catch(e) {
                                    results.push({id: f.id, success: false, error: e.message});
                                }
                            }
                            sucraseBridge.onComplete(JSON.stringify(results));
                        } catch(e) {
                            sucraseBridge.onComplete(JSON.stringify([{id: "__error__", success: false, error: "Outer: " + e.message}]));
                        }
                    })();
                """.trimIndent()

                webView?.evaluateJavascript(js, null)
            }

            val resultJson = withTimeoutOrNull(60_000L) { deferred.await() }
            resultCompleter = null

            if (resultJson == null) {
                Timber.e("Sucrase transform timed out")
                return files.map { SucraseResult(it.id, false, null, "Transform timeout") }
            }

            val results = gson.fromJson(resultJson, Array<SucraseResult>::class.java)?.toList()
                ?: emptyList()

            val successCount = results.count { it.success }
            Timber.d("Sucrase transformBatch: ${files.size} files, $successCount succeeded")
            return results
        } catch (e: Exception) {
            Timber.e(e, "Sucrase transformBatch failed")
            return files.map { SucraseResult(it.id, false, null, e.message ?: "Unknown error") }
        }
    }
}
