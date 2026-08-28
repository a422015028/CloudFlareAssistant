package com.muort.upworker.feature.zone

import android.content.Context
import com.muort.upworker.R

/**
 * Snippet 规则表达式目录与构建/解析。
 * 字段与运算符清单基于 Cloudflare Rules 语言，并在 http_request_snippets 阶段（Pro 套餐）实测验证；
 * cf.bot_management.* / cf.waf.score / matches 运算符因套餐限制不可用，未收录。
 */
object SnippetRuleExpression {

    enum class ValueType { STRING, IP, NUMBER, BOOLEAN }

    data class Field(
        val expr: String,
        val labelResId: Int,
        val type: ValueType,
        val needsHeaderName: Boolean = false,
    ) {
        fun label(ctx: Context): String = ctx.getString(labelResId)
    }

    data class Op(
        val expr: String,
        val labelResId: Int,
        val types: List<ValueType>,
        val noValue: Boolean = false,
    ) {
        fun label(ctx: Context): String = ctx.getString(labelResId)
    }

    data class Condition(
        var fieldIndex: Int = 0,
        var opIndex: Int = 0,
        var value: String = "",
        var headerName: String = "",
    )

    data class Parsed(
        val conditions: List<Condition>,
        val useAnd: Boolean,
    )

    val FIELDS = listOf(
        Field("http.request.uri.path", R.string.snippet_field_uri_path, ValueType.STRING),
        Field("http.request.uri.query", R.string.snippet_field_uri_query, ValueType.STRING),
        Field("http.request.full_uri", R.string.snippet_field_full_uri, ValueType.STRING),
        Field("http.host", R.string.snippet_field_hostname, ValueType.STRING),
        Field("http.request.method", R.string.snippet_field_request_method, ValueType.STRING),
        Field("http.request.version", R.string.snippet_field_http_version, ValueType.STRING),
        Field("http.user_agent", R.string.snippet_field_user_agent, ValueType.STRING),
        Field("http.cookie", R.string.snippet_field_cookie, ValueType.STRING),
        Field("http.referer", R.string.snippet_field_referer, ValueType.STRING),
        Field("http.x_forwarded_for", R.string.snippet_field_x_forwarded_for, ValueType.STRING),
        Field("http.request.headers", R.string.snippet_field_request_headers, ValueType.STRING, needsHeaderName = true),
        Field("ip.src", R.string.snippet_field_client_ip, ValueType.IP),
        Field("ip.src.country", R.string.snippet_field_country_code, ValueType.STRING),
        Field("ip.src.continent", R.string.snippet_field_continent_code, ValueType.STRING),
        Field("ip.src.city", R.string.snippet_field_city, ValueType.STRING),
        Field("ip.src.region", R.string.snippet_field_region, ValueType.STRING),
        Field("ip.src.asnum", R.string.snippet_field_asnum, ValueType.NUMBER),
        Field("ssl", R.string.snippet_field_ssl, ValueType.BOOLEAN),
        Field("cf.tls_version", R.string.snippet_field_tls_version, ValueType.STRING),
        Field("cf.zone.name", R.string.snippet_field_zone_name, ValueType.STRING),
    )

    val OPS = listOf(
        Op("eq", R.string.snippet_op_eq, ValueType.entries.toList()),
        Op("ne", R.string.snippet_op_ne, ValueType.entries.toList()),
        Op("contains", R.string.snippet_op_contains, listOf(ValueType.STRING)),
        Op("wildcard", R.string.snippet_op_wildcard, listOf(ValueType.STRING)),
        Op("in", R.string.snippet_op_in, listOf(ValueType.STRING, ValueType.IP, ValueType.NUMBER)),
        Op("lt", R.string.snippet_op_lt, listOf(ValueType.NUMBER)),
        Op("le", R.string.snippet_op_le, listOf(ValueType.NUMBER)),
        Op("gt", R.string.snippet_op_gt, listOf(ValueType.NUMBER)),
        Op("ge", R.string.snippet_op_ge, listOf(ValueType.NUMBER)),
        Op("starts_with", R.string.snippet_op_starts_with, listOf(ValueType.STRING)),
        Op("ends_with", R.string.snippet_op_ends_with, listOf(ValueType.STRING)),
        Op("is_true", R.string.snippet_op_is_true, listOf(ValueType.BOOLEAN), noValue = true),
        Op("is_false", R.string.snippet_op_is_false, listOf(ValueType.BOOLEAN), noValue = true),
    )

    fun opsFor(type: ValueType): List<Op> = OPS.filter { type in it.types }

    fun fieldExpr(field: Field, condition: Condition): String? =
        if (field.needsHeaderName) {
            val name = condition.headerName.trim()
            if (name.isEmpty()) null
            else "http.request.headers[\"${name.escapeStringLiteral()}\"][0]"
        } else field.expr

    /** 将条件列表拼成表达式；空条件（缺字段名/值）自动跳过，全部为空时返回空串。 */
    fun build(conditions: List<Condition>, useAnd: Boolean): String {
        val parts = conditions.mapNotNull { c -> buildPart(c) }
        if (parts.isEmpty()) return ""
        if (parts.size == 1) return parts.first()
        val joiner = if (useAnd) " and " else " or "
        return parts.joinToString(joiner) { "($it)" }
    }

    private fun buildPart(c: Condition): String? {
        val field = FIELDS.getOrNull(c.fieldIndex) ?: return null
        val op = OPS.getOrNull(c.opIndex) ?: return null
        if (field.type !in op.types) return null
        val fExpr = fieldExpr(field, c) ?: return null
        val raw = c.value.trim()

        if (op.noValue) return if (op.expr == "is_true") fExpr else "not $fExpr"

        if (raw.isEmpty()) return null
        return when (op.expr) {
            "starts_with" -> "starts_with($fExpr, \"${raw.escapeStringLiteral()}\")"
            "ends_with" -> "ends_with($fExpr, \"${raw.escapeStringLiteral()}\")"
            "in" -> {
                val items = raw.split(',', '，', ' ').map { it.trim() }.filter { it.isNotEmpty() }
                if (items.isEmpty()) return null
                if (field.type == ValueType.STRING) {
                    "$fExpr in {${items.joinToString(" ") { "\"${it.escapeStringLiteral()}\"" }}}"
                } else {
                    "$fExpr in {${items.joinToString(" ")}}"
                }
            }
            else -> when (field.type) {
                ValueType.STRING -> "$fExpr ${op.expr} \"${raw.escapeStringLiteral()}\""
                ValueType.NUMBER -> {
                    val n = raw.filter { it.isDigit() }
                    if (n.isEmpty()) return null
                    "$fExpr ${op.expr} $n"
                }
                ValueType.IP -> "$fExpr ${op.expr} $raw"
                ValueType.BOOLEAN -> null
            }
        }
    }

    private fun String.escapeStringLiteral(): String = replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * 将已有表达式解析回构建器条件。
     * 仅支持平铺的同级 and/or（构建器能力范围）；嵌套括号、not 复合、函数（starts_with/ends_with 除外）、
     * 未收录字段等高级特性返回 null，由调用方回退到表达式编辑器。
     */
    fun parse(expression: String): Parsed? {
        val expr = expression.trim()
        if (expr.isEmpty()) return null

        // 顶层按 and/or 切分（忽略字符串字面量与括号内的分隔符），连接符必须一致
        val segments = splitTopLevel(expr) ?: return null

        val conditions = segments.parts.mapNotNull { part -> parsePart(part.trim()) }
        if (conditions.size != segments.parts.size || conditions.isEmpty()) return null
        return Parsed(conditions, segments.useAnd)
    }

    private data class Segments(val parts: List<String>, val useAnd: Boolean)

    /** 顶层切分；混合 and/or 或无法切分时返回 null。 */
    private fun splitTopLevel(expr: String): Segments? {
        val parts = mutableListOf<String>()
        val connectors = mutableSetOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var inString = false
        var i = 0
        while (i < expr.length) {
            val ch = expr[i]
            when {
                inString -> {
                    sb.append(ch)
                    if (ch == '\\') {
                        if (i + 1 < expr.length) { sb.append(expr[i + 1]); i++ }
                    } else if (ch == '"') inString = false
                }
                ch == '"' -> { inString = true; sb.append(ch) }
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; if (depth < 0) return null; sb.append(ch) }
                depth == 0 && (expr.startsWith(" and ", i) || expr.startsWith(" or ", i)) -> {
                    connectors.add(if (expr[i + 1] == 'a') "and" else "or")
                    parts.add(sb.toString().trim())
                    sb.clear()
                    i += if (expr[i + 1] == 'a') 5 else 4
                    continue
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (inString || depth != 0) return null
        parts.add(sb.toString().trim())

        if (connectors.size > 1) return null
        // 单段：剥掉整体包裹的括号
        if (parts.size == 1) {
            var p = parts[0]
            while (p.startsWith("(") && p.endsWith(")") && isBalancedOuter(p)) {
                p = p.substring(1, p.length - 1).trim()
            }
            return Segments(listOf(p), useAnd = true)
        }
        return Segments(parts, connectors.first() == "and")
    }

    /** 判断首尾括号是否为同一对（而非 (a) and (b) 剥壳后语义改变）。 */
    private fun isBalancedOuter(s: String): Boolean {
        var depth = 0
        var inString = false
        for (i in s.indices) {
            val ch = s[i]
            if (inString) {
                if (ch == '\\') continue
                if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0 && i != s.length - 1) return false
                }
            }
        }
        return true
    }

    private fun parsePart(part: String): Condition? {
        // 布尔字段单独使用 / not 前缀
        FIELDS.forEachIndexed { idx, f ->
            if (f.type == ValueType.BOOLEAN) {
                if (part == f.expr) return Condition(fieldIndex = idx, opIndex = OPS.indexOfFirst { it.expr == "is_true" })
                if (part == "not ${f.expr}") return Condition(fieldIndex = idx, opIndex = OPS.indexOfFirst { it.expr == "is_false" })
            }
        }

        // 函数形式 starts_with(field, "v") / ends_with(field, "v")
        Regex("^(starts_with|ends_with)\\((.+),\\s*\"(.*)\"\\)$").matchEntire(part)?.let { m ->
            val opExpr = m.groupValues[1]
            val fieldExpr = m.groupValues[2].trim()
            val value = m.groupValues[3].unescapeStringLiteral()
            val (fieldIdx, headerName) = matchField(fieldExpr) ?: return null
            val opIdx = OPS.indexOfFirst { it.expr == opExpr }
            if (opIdx < 0) return null
            return Condition(fieldIdx, opIdx, value, headerName)
        }

        // field op rhs
        Regex("^([\\w.\\[\\]\"$-]+)\\s+(eq|ne|contains|wildcard|strict wildcard|lt|le|gt|ge|in)\\s+(.+)$")
            .matchEntire(part)?.let { m ->
                val fieldExpr = m.groupValues[1].trim()
                val opExpr = m.groupValues[2]
                val rhs = m.groupValues[3].trim()
                val (fieldIdx, headerName) = matchField(fieldExpr) ?: return null
                val field = FIELDS[fieldIdx]
                val opIdx = OPS.indexOfFirst { it.expr == opExpr }
                if (opIdx < 0) return null

                if (opExpr == "in") {
                    val inner = rhs.removeSurrounding("{", "}")
                    val items = splitItems(inner) ?: return null
                    val value = if (field.type == ValueType.STRING) {
                        items.joinToString(", ") { it.removeSurrounding("\"").unescapeStringLiteral() }
                    } else items.joinToString(", ")
                    return Condition(fieldIdx, opIdx, value, headerName)
                }
                return when (field.type) {
                    ValueType.STRING -> {
                        val v = rhs.removeSurrounding("\"")
                        if (!rhs.startsWith("\"") || !rhs.endsWith("\"")) return null
                        Condition(fieldIdx, opIdx, v.unescapeStringLiteral(), headerName)
                    }
                    ValueType.IP -> {
                        if (!Regex("^[0-9a-fA-F:.]+(/\\d+)?$").matches(rhs)) return null
                        Condition(fieldIdx, opIdx, rhs, headerName)
                    }
                    ValueType.NUMBER -> {
                        if (!rhs.all { it.isDigit() }) return null
                        Condition(fieldIdx, opIdx, rhs, headerName)
                    }
                    ValueType.BOOLEAN -> null
                }
            }
        return null
    }

    /** 拆分 {a b "c d"} 集合内容；支持带引号与裸值。 */
    private fun splitItems(inner: String): List<String>? {
        val items = mutableListOf<String>()
        val sb = StringBuilder()
        var inString = false
        var i = 0
        while (i < inner.length) {
            val ch = inner[i]
            when {
                inString -> {
                    if (ch == '\\') {
                        if (i + 1 < inner.length) { sb.append(inner[i + 1]); i++ }
                    } else if (ch == '"') inString = false
                    else sb.append(ch)
                }
                ch == '"' -> inString = true
                ch == ' ' || ch == '\t' -> {
                    if (sb.isNotEmpty()) { items.add(sb.toString()); sb.clear() }
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (inString) return null
        if (sb.isNotEmpty()) items.add(sb.toString())
        return items.ifEmpty { null }
    }

    /** 字段名匹配（含 http.request.headers["X"][0] 形式），返回字段索引与头名称。 */
    private fun matchField(fieldExpr: String): Pair<Int, String>? {
        Regex("^http\\.request\\.headers\\[\"(.*)\"\\]\\[0\\]$").matchEntire(fieldExpr)?.let { m ->
            return Pair(
                FIELDS.indexOfFirst { it.needsHeaderName },
                m.groupValues[1].unescapeStringLiteral(),
            )
        }
        val idx = FIELDS.indexOfFirst { !it.needsHeaderName && it.expr == fieldExpr }
        if (idx < 0) return null
        return Pair(idx, "")
    }

    private fun String.unescapeStringLiteral(): String =
        replace("\\\"", "\"").replace("\\\\", "\\")
}
