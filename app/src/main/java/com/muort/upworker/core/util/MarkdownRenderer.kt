package com.muort.upworker.core.util

import android.webkit.WebView
import java.util.regex.Pattern

/**
 * 极简 Markdown 渲染工具
 * 将 Markdown 文本转换为 HTML，用于 WebView 展示
 *
 * 支持的语法：
 * - 标题 (#, ##, ###, ####)
 * - 粗体 (**text**)
 * - 斜体 (*text*)
 * - 行内代码 (`code`)
 * - 代码块 (```lang ... ```)
 * - 无序列表 (- item)
 * - 有序列表 (1. item)
 * - 链接 [text](url)
 * - 水平线 (---)
 * - 段落（空行分隔）
 * - 转义字符
 */
object MarkdownRenderer {

    /**
     * 将 Markdown 文本渲染到 WebView
     */
    fun renderToWebView(webView: WebView, markdown: String, baseUrl: String? = null) {
        val html = toHtml(markdown)
        val fullHtml = wrapHtml(html)
        webView.loadDataWithBaseURL(baseUrl, fullHtml, "text/html", "UTF-8", null)
    }

    /**
     * 将 Markdown 转换为 HTML 片段
     */
    fun toHtml(markdown: String): String {
        if (markdown.isBlank()) return ""

        var result = markdown

        // 先处理代码块（避免内部内容被其他规则干扰）
        val codeBlocks = mutableMapOf<String, String>()
        var codeBlockIndex = 0
        val codeBlockPattern = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE)
        val codeBlockMatcher = codeBlockPattern.matcher(result)
        val sb = StringBuffer()
        while (codeBlockMatcher.find()) {
            val lang = codeBlockMatcher.group(1) ?: ""
            val code = codeBlockMatcher.group(2) ?: ""
            val placeholder = "%%%CODEBLOCK_${codeBlockIndex}%%%"
            codeBlocks[placeholder] = renderCodeBlock(code, lang)
            codeBlockMatcher.appendReplacement(sb, placeholder)
            codeBlockIndex++
        }
        codeBlockMatcher.appendTail(sb)
        result = sb.toString()

        // 处理行内代码
        result = result.replace(Regex("`([^`]+)`")) { match ->
            "<code>${escapeHtml(match.groupValues[1])}</code>"
        }

        // 分割为段落
        val paragraphs = result.split(Regex("\\n\\s*\\n"))
        val htmlParts = mutableListOf<String>()

        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isBlank()) continue

            when {
                // 水平线
                trimmed.matches(Regex("^-{3,}$")) -> {
                    htmlParts.add("<hr/>")
                }
                // 标题
                trimmed.startsWith("#") -> {
                    htmlParts.add(renderHeading(trimmed))
                }
                // 无序列表
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    htmlParts.add(renderUnorderedList(trimmed))
                }
                // 有序列表
                trimmed.matches(Regex("^\\d+\\.\\s.*", RegexOption.DOT_MATCHES_ALL)) -> {
                    htmlParts.add(renderOrderedList(trimmed))
                }
                // 代码块占位符
                trimmed.startsWith("%%%CODEBLOCK_") -> {
                    htmlParts.add(codeBlocks[trimmed] ?: "")
                }
                // 普通段落
                else -> {
                    htmlParts.add(renderParagraph(trimmed))
                }
            }
        }

        return htmlParts.joinToString("\n")
    }

    /**
     * 包装为完整的 HTML 文档
     */
    private fun wrapHtml(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        font-size: 14px;
                        line-height: 1.6;
                        color: var(--text-color, #333);
                        background-color: transparent;
                        margin: 0;
                        padding: 0;
                        word-wrap: break-word;
                    }
                    h1 { font-size: 22px; font-weight: 600; margin: 16px 0 10px 0; }
                    h2 { font-size: 18px; font-weight: 600; margin: 14px 0 8px 0; }
                    h3 { font-size: 16px; font-weight: 600; margin: 12px 0 6px 0; }
                    h4 { font-size: 15px; font-weight: 600; margin: 10px 0 4px 0; }
                    p { margin: 8px 0; }
                    ul, ol { margin: 8px 0; padding-left: 24px; }
                    li { margin: 4px 0; }
                    code {
                        background-color: rgba(127, 127, 127, 0.15);
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
                        font-size: 13px;
                    }
                    pre {
                        background-color: rgba(127, 127, 127, 0.1);
                        padding: 12px;
                        border-radius: 8px;
                        overflow-x: auto;
                        margin: 10px 0;
                    }
                    pre code {
                        background: none;
                        padding: 0;
                        font-size: 12px;
                        line-height: 1.5;
                    }
                    a {
                        color: var(--link-color, #1976d2);
                        text-decoration: none;
                    }
                    a:hover { text-decoration: underline; }
                    hr {
                        border: none;
                        border-top: 1px solid rgba(127, 127, 127, 0.2);
                        margin: 16px 0;
                    }
                    strong { font-weight: 600; }
                    em { font-style: italic; }
                    img { max-width: 100%; height: auto; }
                </style>
            </head>
            <body>
                $content
            </body>
            </html>
        """.trimIndent()
    }

    // ========== 渲染辅助方法 ==========

    private fun renderHeading(line: String): String {
        var level = 0
        while (level < line.length && line[level] == '#') {
            level++
        }
        val text = line.substring(level).trim()
        val safeLevel = level.coerceIn(1, 6)
        return "<h$safeLevel>${renderInline(text)}</h$safeLevel>"
    }

    private fun renderUnorderedList(text: String): String {
        val items = text.lines()
            .filter { it.trim().startsWith("- ") || it.trim().startsWith("* ") }
            .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }

        return if (items.isEmpty()) {
            renderParagraph(text)
        } else {
            "<ul>\n" + items.joinToString("\n") {
                "  <li>${renderInline(it)}</li>"
            } + "\n</ul>"
        }
    }

    private fun renderOrderedList(text: String): String {
        val items = text.lines()
            .filter { it.trim().matches(Regex("^\\d+\\.\\s.*")) }
            .map { it.trim().replaceFirst(Regex("^\\d+\\.\\s"), "").trim() }

        return if (items.isEmpty()) {
            renderParagraph(text)
        } else {
            "<ol>\n" + items.joinToString("\n") {
                "  <li>${renderInline(it)}</li>"
            } + "\n</ol>"
        }
    }

    private fun renderParagraph(text: String): String {
        return "<p>${renderInline(text)}</p>"
    }

    private fun renderCodeBlock(code: String, lang: String): String {
        val escapedCode = escapeHtml(code.trimEnd())
        val langClass = if (lang.isNotBlank()) " class=\"language-$lang\"" else ""
        return "<pre><code$langClass>$escapedCode</code></pre>"
    }

    /**
     * 渲染行内格式：粗体、斜体、链接、行内代码
     */
    private fun renderInline(text: String): String {
        var result = escapeHtml(text)

        // 粗体 **text**
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("__(.+?)__"), "<strong>$1</strong>")

        // 斜体 *text*
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        result = result.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<em>$1</em>")

        // 链接 [text](url)
        result = result.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { match ->
            val linkText = match.groupValues[1]
            val url = match.groupValues[2]
            "<a href=\"$url\" target=\"_blank\" rel=\"noopener noreferrer\">$linkText</a>"
        }

        // 图片 ![alt](url)
        result = result.replace(Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")) { match ->
            val alt = match.groupValues[1]
            val url = match.groupValues[2]
            "<img src=\"$url\" alt=\"$alt\" />"
        }

        return result
    }

    /**
     * HTML 转义
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
