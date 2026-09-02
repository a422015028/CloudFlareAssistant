package com.muort.upworker.core.util

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import timber.log.Timber

/**
 * Markdown 渲染工具
 * 使用 marked.js + highlight.js 在 WebView 中客户端渲染
 * 完整支持 GitHub Flavored Markdown (GFM)
 *
 * - 标题、粗体、斜体、删除线、行内代码、代码块
 * - 无序列表、有序列表、任务列表
 * - 引用块、表格、链接、图片、水平线
 * - HTML 标签、details/summary 折叠块
 * - 代码语法高亮（16种常用语言）
 * - 明/暗主题自适应
 */
object MarkdownRenderer {

    private const val ASSET_URL = "file:///android_asset/markdown_viewer.html"

    /**
     * 将 Markdown 文本渲染到 WebView
     *
     * @param webView 目标 WebView
     * @param markdown Markdown 文本内容
     * @param baseUrl 基础 URL，用于解析相对路径的图片/链接（暂未使用，保留接口兼容）
     * @param onComplete 渲染完成回调（可选）
     */
    @Suppress("UNUSED_PARAMETER")
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    fun renderToWebView(
        webView: WebView,
        markdown: String,
        baseUrl: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        // 配置 WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.loadWithOverviewMode = false
        webView.settings.useWideViewPort = false
        webView.settings.setSupportZoom(false)

        // JS Bridge：接收渲染完成通知
        val bridgeName = "AndroidBridge"
        try {
            webView.removeJavascriptInterface(bridgeName)
        } catch (_: Exception) {}
        webView.addJavascriptInterface(
            object {
                @android.webkit.JavascriptInterface
                fun onRenderComplete() {
                    onComplete?.let { callback ->
                        webView.post { callback() }
                    }
                }
            },
            bridgeName
        )

        // 包装 WebViewClient：在模板加载完成后注入 Markdown
        val outerClient = webView.webViewClient
        webView.webViewClient = object : WebViewClient() {
            private var injected = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // 模板页面加载完成，注入 Markdown
                if (!injected && isTemplateUrl(url)) {
                    injected = true
                    view?.post {
                        injectMarkdown(view, markdown)
                    }
                }

                // 转发给原始 client（如果有）
                try {
                    outerClient.onPageFinished(view, url)
                } catch (_: Exception) {}
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // 非 file:// 链接用外部浏览器打开
                val url = request?.url
                if (url != null && url.scheme != "file") {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW, url
                        )
                        view?.context?.startActivity(intent)
                    } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }

        // 加载 HTML 模板
        webView.loadUrl(ASSET_URL)
    }

    private fun isTemplateUrl(url: String?): Boolean {
        if (url == null) return false
        return url == ASSET_URL || url.endsWith("markdown_viewer.html")
    }

    /**
     * 将 Markdown 内容注入到 WebView 中进行渲染
     */
    private fun injectMarkdown(webView: WebView?, markdown: String) {
        if (webView == null) return
        try {
            // 将 Markdown 转义为 JS 字符串字面量
            val escaped = buildString {
                for (ch in markdown) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '\'' -> append("\\'")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        '\u2028' -> append("\\u2028")
                        '\u2029' -> append("\\u2029")
                        else -> append(ch)
                    }
                }
            }
            webView.evaluateJavascript("renderMarkdown('$escaped')", null)
        } catch (e: Exception) {
            Timber.e(e, "[MarkdownRenderer] 注入 Markdown 失败")
            // 降级：直接显示纯文本
            webView.loadData(markdown, "text/plain; charset=utf-8", "UTF-8")
        }
    }
}
