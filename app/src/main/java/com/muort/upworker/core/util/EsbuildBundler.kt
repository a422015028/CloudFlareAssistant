package com.muort.upworker.core.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * esbuild 打包输入
 * @param id 文件唯一标识（使用 relativePath）
 * @param content 文件原始内容
 * @param filePath 源文件名（如 "hello.tsx"）
 * @param loader esbuild loader 类型（"js", "jsx", "ts", "tsx"）
 */
data class EsbuildInput(
    val id: String,
    val content: String,
    val filePath: String,
    val loader: String
)

/**
 * esbuild 打包结果
 */
data class EsbuildResult(
    @SerializedName("id") val id: String,
    @SerializedName("success") val success: Boolean,
    @SerializedName("code") val code: String?,
    @SerializedName("error") val error: String?
)

/**
 * 使用 WebView 加载 esbuild-wasm，将含有 NPM 包导入的文件打包为自包含 ES 模块。
 *
 * esbuild-wasm 是 esbuild 的 WebAssembly 版本，支持：
 * - NPM 包解析（通过 esm.sh CDN）
 * - TypeScript → JavaScript
 * - JSX → JavaScript
 * - 模块打包（内联所有依赖）
 *
 * WASM 文件按需下载，不打包进 APK。
 * 仅用于含有 bare imports（NPM 包导入）的文件，其他文件仍使用 Sucrase。
 *
 * 相对导入（./ ../）标记为 external，保留给现有的 import 重写器处理。
 */
@Singleton
class EsbuildBundler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EsbuildBundler"
        private const val ESBUILD_DIR = "esbuild"
        private const val NPM_REGISTRY = "https://registry.npmjs.org/esbuild-wasm/latest"
    }

    // 动态版本号，首次下载时从 npm registry 获取
    @Volatile
    private var esbuildVersion: String? = null

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

        @JavascriptInterface
        fun onReady() {
            Timber.d("$TAG: esbuild initialized successfully")
            initLatch.countDown()
        }

        @JavascriptInterface
        fun log(message: String) {
            Timber.i("$TAG JS: $message")
        }
    }

    /**
     * 检查 esbuild-wasm 文件是否已下载
     */
    fun isAvailable(): Boolean {
        val dir = File(context.filesDir, ESBUILD_DIR)
        return File(dir, "esbuild-browser.js").exists() && File(dir, "esbuild.wasm").exists()
    }

    /**
     * 读取本地缓存的版本号
     */
    private fun getLocalVersion(): String? {
        val versionFile = File(context.filesDir, "$ESBUILD_DIR/version.txt")
        return if (versionFile.exists()) versionFile.readText().trim() else null
    }

    /**
     * 保存版本号到本地
     */
    private fun saveLocalVersion(version: String) {
        val versionFile = File(context.filesDir, "$ESBUILD_DIR/version.txt")
        versionFile.writeText(version)
    }

    /**
     * 确保 esbuild-wasm 文件可用且为最新版本
     * 检查本地版本与 npm registry 最新版本，不一致时重新下载
     * @return true 如果文件可用（已是最新或下载成功）
     */
    suspend fun ensureAvailable(): Boolean {
        // 获取最新版本号（每次都查，确保及时更新）
        val latestVersion = esbuildVersion ?: fetchLatestVersion()
        if (latestVersion != null) {
            esbuildVersion = latestVersion
        }

        // 检查本地版本
        val localVersion = getLocalVersion()

        if (localVersion == latestVersion && isAvailable()) {
            // 本地已是最新版本，无需下载
            return true
        }

        // 版本不一致或文件不存在，需要下载
        if (localVersion != null && isAvailable()) {
            Timber.i("$TAG: 本地版本 $localVersion 不是最新 $latestVersion，重新下载")
        }
        return downloadEsbuild()
    }

    /**
     * 下载 esbuild-browser.js 和 esbuild.wasm 到应用私有存储
     * 自动获取最新版本号，使用多 CDN 备选
     */
    private suspend fun downloadEsbuild(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 获取最新版本号
                val version = esbuildVersion ?: fetchLatestVersion()
                if (version == null) {
                    Timber.e("$TAG: 无法获取 esbuild-wasm 最新版本号")
                    return@withContext false
                }
                esbuildVersion = version
                Timber.i("$TAG: esbuild-wasm 最新版本: $version")

                val dir = File(context.filesDir, ESBUILD_DIR)
                if (!dir.exists()) dir.mkdirs()

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

                val jsUrls = listOf(
                    "https://cdn.jsdelivr.net/npm/esbuild-wasm@$version/esm/browser.min.js",
                    "https://unpkg.com/esbuild-wasm@$version/esm/browser.min.js"
                )
                val wasmUrls = listOf(
                    "https://cdn.jsdelivr.net/npm/esbuild-wasm@$version/esbuild.wasm",
                    "https://unpkg.com/esbuild-wasm@$version/esbuild.wasm"
                )

                // 下载 esbuild-browser.js (ESM 版本)
                val jsFile = File(dir, "esbuild-browser.js")
                val jsSuccess = downloadFile(client, jsUrls, jsFile, "esbuild-browser.js")
                if (!jsSuccess) {
                    Timber.e("$TAG: 所有 CDN 下载 esbuild-browser.js 均失败")
                    return@withContext false
                }

                // 下载 esbuild.wasm
                val wasmFile = File(dir, "esbuild.wasm")
                val wasmSuccess = downloadFile(client, wasmUrls, wasmFile, "esbuild.wasm")
                if (!wasmSuccess) {
                    Timber.e("$TAG: 所有 CDN 下载 esbuild.wasm 均失败")
                    return@withContext false
                }

                Timber.i("$TAG: esbuild-wasm 下载完成, js=${jsFile.length()} 字节, wasm=${wasmFile.length()} 字节")
                saveLocalVersion(version)
                true
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 下载 esbuild-wasm 异常")
                false
            }
        }
    }

    /**
     * 从 npm registry 获取 esbuild-wasm 最新版本号
     */
    private fun fetchLatestVersion(): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(NPM_REGISTRY).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: 获取版本号失败: HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                json.get("version")?.asString
            }
        } catch (e: Exception) {
            Timber.w("$TAG: 获取版本号异常: ${e.message}")
            null
        }
    }

    /**
     * 从多个 URL 尝试下载文件，成功一个即返回
     */
    private fun downloadFile(
        client: OkHttpClient,
        urls: List<String>,
        targetFile: File,
        label: String
    ): Boolean {
        for (url in urls) {
            try {
                Timber.i("$TAG: 正在下载 $label 从 $url ...")
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("$TAG: 下载 $label 失败 ($url): HTTP ${response.code}")
                        return@use
                    }
                    targetFile.outputStream().use { output ->
                        response.body?.byteStream()?.copyTo(output)
                    }
                    Timber.i("$TAG: $label 下载成功 (${targetFile.length()} 字节) 从 $url")
                    return true
                }
            } catch (e: Exception) {
                Timber.w("$TAG: 下载 $label 异常 ($url): ${e.message}")
            }
        }
        return false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return
        synchronized(this) {
            if (webView != null) return
            val esbuildDir = File(context.filesDir, ESBUILD_DIR)
            val jsFile = File(esbuildDir, "esbuild-browser.js")
            val wasmFile = File(esbuildDir, "esbuild.wasm")

            if (!jsFile.exists() || !wasmFile.exists()) {
                Timber.e("$TAG: esbuild 文件不存在")
                initLatch.countDown()
                return
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = true
                        addJavascriptInterface(bridge, "esbuildBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString() ?: return null

                                // 拦截 esbuild-browser.js (ESM 模块)
                                if (url.contains("esbuild-browser.js")) {
                                    if (jsFile.exists()) {
                                        return WebResourceResponse(
                                            "application/javascript",
                                            "utf-8",
                                            jsFile.inputStream()
                                        )
                                    }
                                }

                                // 拦截 esbuild.wasm
                                if (url.contains("esbuild.wasm")) {
                                    if (wasmFile.exists()) {
                                        return WebResourceResponse(
                                            "application/wasm",
                                            null,
                                            wasmFile.inputStream()
                                        )
                                    }
                                }

                                // 其他请求（CDN fetch）放行
                                return null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                val url = request?.url?.toString() ?: ""
                                // 忽略 CDN fetch 的错误（由 JS 端处理）
                                if (!url.contains("esbuild")) {
                                    return
                                }
                                Timber.e("$TAG: WebView 资源加载错误: $url - ${error?.description}")
                            }
                        }
                    }

                    val html = buildHtml()
                    webView?.loadDataWithBaseURL(
                        "https://esbuild.local/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    Timber.d("$TAG: WebView 初始化中...")
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: WebView 初始化失败")
                    initLatch.countDown()
                }
            }
        }
    }

    /**
     * 构建 WebView HTML 页面，包含 esbuild-wasm 和 CDN 插件
     */
    private fun buildHtml(): String {
        return """
<html>
<head>
<script type="module">
import { initialize, build } from './esbuild-browser.js';

// CDN 插件：将 bare imports 重定向到 esm.sh
const cdnPlugin = {
  name: 'cdn',
  setup(build) {
    // 跟踪 URL 重定向：原始 URL → 最终 URL
    const urlRedirects = new Map();

    // 1. 相对路径（./ ../）— 先注册，优先匹配
    build.onResolve({ filter: /^\.\.?\// }, (args) => {
      esbuildBridge.log('onResolve relative: path=' + args.path + ' importer=' + (args.importer || 'null') + ' namespace=' + (args.namespace || 'null'));
      
      // 判断是否来自 CDN 模块：检查 namespace、importer URL、或重定向映射
      var isCdn = false;
      var actualUrl = args.importer || '';
      
      if (args.namespace === 'cdn') {
        isCdn = true;
      } else if (actualUrl.indexOf('://') !== -1) {
        isCdn = true;
      } else if (urlRedirects.has(actualUrl)) {
        isCdn = true;
        actualUrl = urlRedirects.get(actualUrl);
      }
      
      if (isCdn) {
        // 使用重定向后的最终 URL 作为 base
        if (urlRedirects.has(actualUrl)) {
          actualUrl = urlRedirects.get(actualUrl);
        }
        var baseUrl = actualUrl.substring(0, actualUrl.lastIndexOf('/') + 1);
        try {
          var url = new URL(args.path, baseUrl).href;
          esbuildBridge.log('onResolve relative → CDN: ' + url);
          return { path: url, namespace: 'cdn' };
        } catch (e) {
          return { errors: [{ text: 'URL resolve error: ' + e.message }] };
        }
      }
      
      // 相对路径来自原始文件 → external（保留给 import 重写器处理）
      esbuildBridge.log('onResolve relative → external');
      return { path: args.path, external: true };
    });

    // 2. 其他所有路径（bare imports, URL, 绝对路径）→ CDN
    build.onResolve({ filter: /./ }, (args) => {
      var url;
      if (args.path.startsWith('https://') || args.path.startsWith('http://')) {
        url = args.path;
      } else if (args.path.startsWith('/')) {
        url = 'https://esm.sh' + args.path;
      } else {
        url = 'https://esm.sh/' + args.path + '?bundle';
      }
      esbuildBridge.log('onResolve bare → CDN: ' + url);
      return { path: url, namespace: 'cdn' };
    });

    // 通过 fetch 从 CDN 加载模块
    build.onLoad({ filter: /./, namespace: 'cdn' }, async (args) => {
      try {
        esbuildBridge.log('onLoad CDN: ' + args.path);
        var response = await fetch(args.path);
        if (!response.ok) {
          return { errors: [{ text: 'HTTP ' + response.status + ': ' + args.path }] };
        }
        // 记录重定向后的最终 URL
        if (response.url && response.url !== args.path) {
          urlRedirects.set(args.path, response.url);
          esbuildBridge.log('onLoad redirect: ' + args.path + ' → ' + response.url);
        }
        var text = await response.text();
        esbuildBridge.log('onLoad CDN success: ' + args.path + ' (' + text.length + ' chars)');
        return { contents: text, loader: 'js' };
      } catch (e) {
        return { errors: [{ text: 'Fetch error: ' + (e.message || String(e)) + ' (' + args.path + ')' }] };
      }
    });
  }
};

// 初始化 esbuild
async function init() {
  try {
    await initialize({
      wasmURL: './esbuild.wasm',
      worker: false
    });
    esbuildBridge.onReady();
  } catch (e) {
    esbuildBridge.log('Init error: ' + (e.message || String(e)));
  }
}

// 批量打包（挂载到 window 供 evaluateJavascript 调用）
window.bundleAll = async function() {
  try {
    const input = JSON.parse(esbuildBridge.getInput());
    const results = [];
    for (let i = 0; i < input.length; i++) {
      const req = input[i];
      try {
        // 检测 @jsx pragma
        const jsxMatch = req.content.match(/@jsx\s+(\S+)/);
        const fragMatch = req.content.match(/@jsxFrag\s+(\S+)/);

        const buildOptions = {
          stdin: {
            contents: req.content,
            sourcefile: req.filePath,
            loader: req.loader
          },
          bundle: true,
          format: 'esm',
          target: 'es2022',
          plugins: [cdnPlugin],
          write: false
        };

        if (jsxMatch) {
          buildOptions.jsxFactory = jsxMatch[1];
        }
        if (fragMatch) {
          buildOptions.jsxFragment = fragMatch[1];
        }

        const result = await build(buildOptions);
        results.push({
          id: req.id,
          success: true,
          code: result.outputFiles[0].text
        });
      } catch (e) {
        results.push({
          id: req.id,
          success: false,
          error: e.message || String(e)
        });
      }
    }
    esbuildBridge.onComplete(JSON.stringify(results));
  } catch (e) {
    esbuildBridge.onComplete(JSON.stringify([{
      id: '__error__',
      success: false,
      error: 'Outer: ' + (e.message || String(e))
    }]));
  }
};

// 启动初始化
init();
</script>
</head>
<body>
</body>
</html>
        """.trimIndent()
    }

    /**
     * 批量打包文件
     *
     * @param files 需要打包的文件列表
     * @return 打包结果列表，顺序与输入一致
     */
    suspend fun bundleBatch(files: List<EsbuildInput>): List<EsbuildResult> {
        if (files.isEmpty()) return emptyList()

        try {
            ensureWebView()

            if (!initLatch.await(60, TimeUnit.SECONDS)) {
                Timber.e("$TAG: esbuild 初始化超时")
                return files.map { EsbuildResult(it.id, false, null, "esbuild init timeout") }
            }

            if (webView == null) {
                return files.map { EsbuildResult(it.id, false, null, "WebView not available") }
            }

            // 准备输入 JSON
            currentInput = gson.toJson(files.map {
                mapOf(
                    "id" to it.id,
                    "content" to it.content,
                    "filePath" to it.filePath,
                    "loader" to it.loader
                )
            })
            Timber.d("$TAG: bundleBatch 输入 ${files.size} 个文件, 总大小 ${currentInput.length} 字符")

            val deferred = CompletableDeferred<String>()
            resultCompleter = deferred

            withContext(Dispatchers.Main) {
                webView?.evaluateJavascript("bundleAll();", null)
            }

            // 每个文件最多 120 秒，总超时 = 文件数 * 120 秒
            val totalTimeout = (files.size * 120_000L).coerceAtLeast(120_000L)
            val resultJson = withTimeoutOrNull(totalTimeout) { deferred.await() }
            resultCompleter = null

            if (resultJson == null) {
                Timber.e("$TAG: 打包超时")
                return files.map { EsbuildResult(it.id, false, null, "Bundle timeout") }
            }

            val results = gson.fromJson(resultJson, Array<EsbuildResult>::class.java)?.toList()
                ?: emptyList()

            val successCount = results.count { it.success }
            Timber.d("$TAG: bundleBatch 完成: ${files.size} 个文件, $successCount 成功")
            return results
        } catch (e: Exception) {
            Timber.e(e, "$TAG: bundleBatch 失败")
            return files.map { EsbuildResult(it.id, false, null, e.message ?: "Unknown error") }
        }
    }
}
