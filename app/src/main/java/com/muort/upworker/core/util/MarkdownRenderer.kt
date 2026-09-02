package com.muort.upworker.core.util

import android.webkit.WebView
import java.util.regex.Pattern

/**
 * 极简 Markdown 渲染工具
 * 将 Markdown 文本转换为 HTML，用于 WebView 展示
 *
 * 支持的语法：
 * - 标题 (#, ##, ###, ####, #####, ######)
 * - 粗体 (**text** / __text__)
 * - 斜体 (*text* / _text_)
 * - 删除线 (~~text~~)
 * - 行内代码 (`code`)
 * - 代码块 (```lang ... ```)
 * - 无序列表 (- item / * item)
 * - 有序列表 (1. item)
 * - 任务列表 (- [ ] task / - [x] task)
 * - 引用块 (> quote)
 * - 表格 (| col1 | col2 |)
 * - 链接 [text](url)
 * - 图片 ![alt](url)
 * - 水平线 (--- / *** / ___)
 * - 段落（空行分隔）
 * - 行内 HTML 标签（<br>, <img>, <a>, <strong>, <em>, <code> 等安全标签）
 * - 自动链接（URL 自动识别为链接）
 * - 明/暗主题自适应
 */
object MarkdownRenderer {

    /** 允许保留的 HTML 标签（白名单） */
    private val ALLOWED_HTML_TAGS = setOf(
        "br", "wbr", "img", "a", "strong", "b", "em", "i", "s", "del",
        "code", "kbd", "mark", "small", "sub", "sup", "span", "div", "p",
        "ul", "ol", "li", "blockquote", "hr", "br/", "br /",
        "h1", "h2", "h3", "h4", "h5", "h6",
        "table", "thead", "tbody", "tr", "th", "td",
        "pre", "details", "summary"
    )

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

        // 统一换行符
        result = result.replace("\r\n", "\n").replace("\r", "\n")

        // ========== 第一步：保护代码块和行内代码 ==========
        val codeBlocks = mutableMapOf<String, String>()
        var codeBlockIndex = 0

        // 代码块 ```lang ... ```
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

        // 行内代码 `code`
        val inlineCodes = mutableMapOf<String, String>()
        var inlineCodeIndex = 0
        val inlineCodePattern = Pattern.compile("`([^`]+)`")
        val inlineCodeMatcher = inlineCodePattern.matcher(result)
        val sb2 = StringBuffer()
        while (inlineCodeMatcher.find()) {
            val code = inlineCodeMatcher.group(1) ?: ""
            val placeholder = "%%%INLINECODE_${inlineCodeIndex}%%%"
            inlineCodes[placeholder] = "<code>${escapeHtml(code)}</code>"
            inlineCodeMatcher.appendReplacement(sb2, placeholder)
            inlineCodeIndex++
        }
        inlineCodeMatcher.appendTail(sb2)
        result = sb2.toString()

        // ========== 第二步：保护安全的 HTML 标签 ==========
        // 匹配所有 HTML 标签：开始标签 <tag ...>、结束标签 </tag>、自闭合标签 <tag .../>
        // 只要标签名在白名单中，就保护起来不被 Markdown 解析和 escapeHtml 转义
        val htmlTags = mutableMapOf<String, String>()
        var htmlTagIndex = 0

        val htmlTagPattern = Pattern.compile(
            "<\\s*/?\\s*([a-zA-Z][a-zA-Z0-9]*)\\b([^>]*)\\s*/?>",
            Pattern.CASE_INSENSITIVE
        )
        val htmlMatcher = htmlTagPattern.matcher(result)
        val sb3 = StringBuffer()
        while (htmlMatcher.find()) {
            val tagName = htmlMatcher.group(1)?.lowercase() ?: ""
            if (tagName in ALLOWED_HTML_TAGS) {
                val fullMatch = htmlMatcher.group()
                val placeholder = "%%%HTMLTAG_${htmlTagIndex}%%%"
                htmlTags[placeholder] = fullMatch
                htmlMatcher.appendReplacement(sb3, placeholder)
                htmlTagIndex++
            }
        }
        htmlMatcher.appendTail(sb3)
        result = sb3.toString()

        // ========== 第三步：Markdown 解析 ==========

        // 分割为段落
        val paragraphs = result.split(Regex("\\n\\s*\\n"))
        val htmlParts = mutableListOf<String>()

        for (para in paragraphs) {
            val trimmed = para.trim()
            if (trimmed.isBlank()) continue

            when {
                // 水平线
                trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> {
                    htmlParts.add("<hr/>")
                }
                // 标题
                trimmed.startsWith("#") -> {
                    htmlParts.add(renderHeading(trimmed))
                }
                // 引用块
                trimmed.startsWith(">") -> {
                    htmlParts.add(renderBlockquote(trimmed))
                }
                // 表格
                trimmed.contains("|") && isTable(trimmed) -> {
                    htmlParts.add(renderTable(trimmed))
                }
                // 无序列表 / 任务列表
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
                // HTML 块级元素占位符（整段都是 HTML）
                trimmed.startsWith("%%%HTMLTAG_") && trimmed.endsWith("%%%") -> {
                    htmlParts.add(htmlTags[trimmed] ?: "")
                }
                // 普通段落
                else -> {
                    htmlParts.add(renderParagraph(trimmed))
                }
            }
        }

        result = htmlParts.joinToString("\n")

        // ========== 第四步：恢复保护的内容 ==========

        // 恢复 HTML 标签
        for ((placeholder, tag) in htmlTags) {
            result = result.replace(placeholder, tag)
        }

        // 恢复行内代码
        for ((placeholder, code) in inlineCodes) {
            result = result.replace(placeholder, code)
        }

        // 恢复代码块
        for ((placeholder, block) in codeBlocks) {
            result = result.replace(placeholder, block)
        }

        return result
    }

    /**
     * 判断是否为表格
     */
    private fun isTable(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return false
        if (!lines[0].contains("|") || !lines[1].contains("|")) return false
        val secondLine = lines[1].trim()
        return secondLine.matches(Regex("^[|\\s\\-:]+$")) && secondLine.contains("-")
    }

    /**
     * 渲染表格
     */
    private fun renderTable(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return renderParagraph(text)

        val headers = parseTableRow(lines[0])
        val rows = lines.drop(2).map { parseTableRow(it) }

        val sb = StringBuilder()
        sb.append("<table>\n")
        sb.append("  <thead>\n    <tr>\n")
        for (header in headers) {
            sb.append("      <th>${renderInline(header)}</th>\n")
        }
        sb.append("    </tr>\n  </thead>\n")
        sb.append("  <tbody>\n")
        for (row in rows) {
            sb.append("    <tr>\n")
            for (cell in row) {
                sb.append("      <td>${renderInline(cell)}</td>\n")
            }
            sb.append("    </tr>\n")
        }
        sb.append("  </tbody>\n")
        sb.append("</table>")
        return sb.toString()
    }

    /**
     * 解析表格行
     */
    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim()
        val content = trimmed.trimStart('|').trimEnd('|')
        return content.split("|").map { it.trim() }
    }

    /**
     * 渲染引用块
     */
    private fun renderBlockquote(text: String): String {
        val lines = text.lines()
        val content = lines.joinToString("\n") { line ->
            line.trimStart().removePrefix(">").trim()
        }
        val innerHtml = toHtml(content)
        return "<blockquote>$innerHtml</blockquote>"
    }

    /**
     * 包装为完整的 HTML 文档（支持明/暗主题自适应）
     */
    private fun wrapHtml(content: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    * { box-sizing: border-box; }
                    :root {
                        --text-color: #1f2937;
                        --text-secondary: #6b7280;
                        --bg-color: transparent;
                        --link-color: #2563eb;
                        --code-color: #dc2626;
                        --code-bg: rgba(127, 127, 127, 0.12);
                        --pre-bg: rgba(127, 127, 127, 0.08);
                        --pre-border: rgba(127, 127, 127, 0.1);
                        --blockquote-border: #3b82f6;
                        --blockquote-bg: rgba(59, 130, 246, 0.06);
                        --blockquote-text: #475569;
                        --table-border: rgba(127, 127, 127, 0.15);
                        --table-head-bg: rgba(127, 127, 127, 0.08);
                        --table-row-bg: rgba(127, 127, 127, 0.03);
                        --hr-color: rgba(127, 127, 127, 0.2);
                        --scrollbar-thumb: rgba(127, 127, 127, 0.3);
                        --scrollbar-thumb-hover: rgba(127, 127, 127, 0.5);
                    }
                    @media (prefers-color-scheme: dark) {
                        :root {
                            --text-color: #e5e7eb;
                            --text-secondary: #9ca3af;
                            --bg-color: transparent;
                            --link-color: #60a5fa;
                            --code-color: #f87171;
                            --code-bg: rgba(255, 255, 255, 0.1);
                            --pre-bg: rgba(255, 255, 255, 0.06);
                            --pre-border: rgba(255, 255, 255, 0.1);
                            --blockquote-border: #60a5fa;
                            --blockquote-bg: rgba(96, 165, 250, 0.1);
                            --blockquote-text: #d1d5db;
                            --table-border: rgba(255, 255, 255, 0.12);
                            --table-head-bg: rgba(255, 255, 255, 0.06);
                            --table-row-bg: rgba(255, 255, 255, 0.02);
                            --hr-color: rgba(255, 255, 255, 0.15);
                            --scrollbar-thumb: rgba(255, 255, 255, 0.2);
                            --scrollbar-thumb-hover: rgba(255, 255, 255, 0.35);
                        }
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
                        font-size: 14px;
                        line-height: 1.7;
                        color: var(--text-color);
                        background-color: var(--bg-color);
                        margin: 0;
                        padding: 12px 16px;
                        word-wrap: break-word;
                        -webkit-text-size-adjust: 100%;
                    }
                    h1 { font-size: 22px; font-weight: 600; margin: 18px 0 12px 0; line-height: 1.3; }
                    h2 { font-size: 18px; font-weight: 600; margin: 16px 0 10px 0; line-height: 1.35; }
                    h3 { font-size: 16px; font-weight: 600; margin: 14px 0 8px 0; line-height: 1.4; }
                    h4 { font-size: 15px; font-weight: 600; margin: 12px 0 6px 0; line-height: 1.45; }
                    h5, h6 { font-size: 14px; font-weight: 600; margin: 10px 0 4px 0; }
                    p { margin: 8px 0; }
                    ul, ol { margin: 8px 0; padding-left: 24px; }
                    li { margin: 4px 0; }
                    li > ul, li > ol { margin: 4px 0; }
                    /* 任务列表 */
                    .task-list-item {
                        list-style: none;
                        margin-left: -20px;
                    }
                    .task-list-item-checkbox {
                        margin-right: 6px;
                        vertical-align: middle;
                        accent-color: var(--link-color);
                    }
                    /* 行内代码 */
                    code {
                        background-color: var(--code-bg);
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, 'Courier New', monospace;
                        font-size: 13px;
                        color: var(--code-color);
                    }
                    /* 代码块 */
                    pre {
                        background-color: var(--pre-bg);
                        padding: 12px 14px;
                        border-radius: 8px;
                        overflow-x: auto;
                        margin: 10px 0;
                        border: 1px solid var(--pre-border);
                    }
                    pre code {
                        background: none;
                        padding: 0;
                        font-size: 12.5px;
                        line-height: 1.6;
                        color: var(--text-color);
                        display: block;
                        white-space: pre;
                    }
                    /* 链接 */
                    a {
                        color: var(--link-color);
                        text-decoration: none;
                    }
                    a:hover { text-decoration: underline; }
                    /* 水平线 */
                    hr {
                        border: none;
                        border-top: 1px solid var(--hr-color);
                        margin: 18px 0;
                    }
                    strong { font-weight: 600; }
                    em { font-style: italic; }
                    /* 删除线 */
                    del, s { text-decoration: line-through; opacity: 0.7; }
                    /* 图片 */
                    img { max-width: 100%; height: auto; border-radius: 6px; }
                    /* 引用块 */
                    blockquote {
                        margin: 10px 0;
                        padding: 8px 14px;
                        border-left: 4px solid var(--blockquote-border);
                        background-color: var(--blockquote-bg);
                        color: var(--blockquote-text);
                        border-radius: 0 6px 6px 0;
                    }
                    blockquote p { margin: 4px 0; }
                    blockquote code {
                        background-color: var(--code-bg);
                    }
                    /* 表格 */
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 10px 0;
                        font-size: 13.5px;
                        display: block;
                        overflow-x: auto;
                    }
                    th, td {
                        padding: 8px 12px;
                        text-align: left;
                        border: 1px solid var(--table-border);
                    }
                    th {
                        background-color: var(--table-head-bg);
                        font-weight: 600;
                    }
                    tr:nth-child(even) td {
                        background-color: var(--table-row-bg);
                    }
                    /* 滚动条美化 */
                    ::-webkit-scrollbar {
                        width: 6px;
                        height: 6px;
                    }
                    ::-webkit-scrollbar-track {
                        background: transparent;
                    }
                    ::-webkit-scrollbar-thumb {
                        background: var(--scrollbar-thumb);
                        border-radius: 3px;
                    }
                    ::-webkit-scrollbar-thumb:hover {
                        background: var(--scrollbar-thumb-hover);
                    }
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
            "<ul>\n" + items.joinToString("\n") { item ->
                if (item.startsWith("[ ] ") || item.startsWith("[x] ") || item.startsWith("[X] ")) {
                    val isChecked = item.startsWith("[x] ", ignoreCase = true)
                    val taskText = item.removePrefix("[ ] ").removePrefix("[x] ").removePrefix("[X] ").trim()
                    "  <li class=\"task-list-item\">" +
                            "<input type=\"checkbox\" class=\"task-list-item-checkbox\" ${if (isChecked) "checked" else ""} disabled/>" +
                            "${renderInline(taskText)}</li>"
                } else {
                    "  <li>${renderInline(item)}</li>"
                }
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
        val processed = text.replace(Regex("\\n"), "<br/>")
        return "<p>${renderInline(processed)}</p>"
    }

    private fun renderCodeBlock(code: String, lang: String): String {
        val escapedCode = escapeHtml(code.trimEnd())
        val langClass = if (lang.isNotBlank()) " class=\"language-$lang\"" else ""
        return "<pre><code$langClass>$escapedCode</code></pre>"
    }

    /**
     * 渲染行内格式：图片、链接、自动链接、删除线、粗体、斜体
     * 注意：HTML 标签和行内代码已在此之前被保护，不会被 escapeHtml 影响
     */
    private fun renderInline(text: String): String {
        var result = escapeHtml(text)

        // 1. 图片 ![alt](url) （先处理图片，避免被链接规则匹配）
        result = result.replace(Regex("!\\[([^\\]]*)\\]\\(([^)]+)\\)")) { match ->
            val alt = match.groupValues[1]
            val url = match.groupValues[2]
            "<img src=\"$url\" alt=\"$alt\" />"
        }

        // 2. 链接 [text](url)
        result = result.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { match ->
            val linkText = match.groupValues[1]
            val url = match.groupValues[2]
            "<a href=\"$url\" target=\"_blank\" rel=\"noopener noreferrer\">$linkText</a>"
        }

        // 3. 自动链接：识别裸 URL 并转为链接
        //    排除已经是链接的（前面有 " 或 >）
        result = result.replace(
            Regex("(?<!href=\")(?<!'>)(?<!\")https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        ) { match ->
            val url = match.value
            "<a href=\"$url\" target=\"_blank\" rel=\"noopener noreferrer\">$url</a>"
        }

        // 4. 删除线 ~~text~~
        result = result.replace(Regex("~~(.+?)~~"), "<del>$1</del>")

        // 5. 粗体 **text**
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("__(.+?)__"), "<strong>$1</strong>")

        // 6. 斜体 *text*
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        result = result.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "<em>$1</em>")

        return result
    }

    /**
     * HTML 转义（只转义非保护的普通文本）
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
