package com.muort.upworker.core.util

/**
 * Cron 表达式时区转换器。
 *
 * Cloudflare Workers Cron Triggers 始终使用 **UTC** 执行，且只支持 **5 字段**
 * （分 时 日 月 周），不支持秒级 6 字段。
 * 本应用 UI 提示用户按 **UTC+8（中国时间）** 填写 Cron 表达式，因此：
 * - 提交给 Cloudflare 前需把 UTC+8 转为 UTC
 * - 展示时再把 UTC 转回 UTC+8
 *
 * 此外，周字段编号约定不同：
 * - 标准 cron（用户输入/展示）：0-6，周日=0
 * - Cloudflare：1-7，周日=1
 * 本转换器在时区转换的同时处理该编号差异。
 *
 * - [toUtc]：UTC+8 → UTC（提交前调用）
 * - [toLocal]：UTC → UTC+8（展示前调用）
 *
 * 对于无法可靠转换的复杂表达式（如跨午夜的多小时列表同时限定了具体日期），
 * 小时字段仍会转换，但日期字段保持原样以避免错误偏移。
 */
object CronTimezoneConverter {

    /** 中国标准时间相对 UTC 的小时偏移。 */
    private const val OFFSET_HOURS = 8

    /** UTC+8 → UTC。 */
    fun toUtc(cronLocal: String): String = shiftHours(cronLocal, -OFFSET_HOURS)

    /** UTC → UTC+8。 */
    fun toLocal(cronUtc: String): String = shiftHours(cronUtc, OFFSET_HOURS)

    /**
     * 对 Cron 表达式的小时字段执行 [delta] 小时偏移。
     * @param delta 正数=向后（加），负数=向前（减）
     */
    private fun shiftHours(cron: String, delta: Int): String {
        val parts = cron.trim().split("\\s+".toRegex()).toMutableList()
        val hourIndex = when (parts.size) {
            5 -> 1   // 分 时 日 月 周
            6 -> 2   // 秒 分 时 日 月 周
            else -> return cron
        }

        val hour = parts[hourIndex]
        // 小时为 * 或 */N：每小时/每隔 N 小时触发，时区偏移不影响触发频率，原样返回
        if (hour == "*" || hour.startsWith("*/")) return cron

        val newHour = shiftHourField(hour, delta) ?: return cron

        val domIndex = hourIndex + 1
        val dowIndex = hourIndex + 3
        val dom = parts[domIndex]
        val dow = parts[dowIndex]

        val (anyWrap, allWrap) = hourWrapInfo(hour, delta)

        // 只有跨午夜且日期字段被限定时才需要调整日期；若日/周均为 * 则无需调整
        val dayRestricted = dom != "*" && dom != "?" || dow != "*" && dow != "?"
        if (anyWrap && dayRestricted && allWrap) {
            parts[hourIndex] = newHour
            parts[domIndex] = shiftDayOfMonth(dom, delta)
            parts[dowIndex] = shiftDayOfWeek(dow, delta)
        } else {
            parts[hourIndex] = newHour
        }
        return parts.joinToString(" ")
    }

    /** 转换小时字段，返回新的小时字段字符串；无法解析返回 null。 */
    private fun shiftHourField(hour: String, delta: Int): String? = try {
        when {
            hour.contains(",") -> hour.split(",")
                .map { it.trim() }
                .mapNotNull { shiftSingleHour(it, delta) }
                .distinct()
                .sorted()
                .joinToString(",")
            hour.contains("-") -> {
                val slashParts = hour.split("/")
                val step = slashParts.getOrNull(1)
                val (start, end) = slashParts[0].split("-")
                    .let { it[0].trim().toInt() to it[1].trim().toInt() }
                val newStart = wrapHour(start + delta)
                val newEnd = wrapHour(end + delta)
                if (newStart <= newEnd) {
                    if (step != null) "$newStart-$newEnd/$step" else "$newStart-$newEnd"
                } else {
                    // 范围跨越午夜，展开为列表
                    val expanded = mutableListOf<Int>()
                    var cur = start
                    while (true) {
                        expanded.add(wrapHour(cur + delta))
                        if (cur == end) break
                        cur = (cur + 1) % 24
                    }
                    expanded.distinct().sorted().joinToString(",")
                }
            }
            else -> shiftSingleHour(hour, delta)
        }
    } catch (e: Exception) {
        null
    }

    private fun shiftSingleHour(token: String, delta: Int): String? = try {
        wrapHour(token.trim().toInt() + delta).toString()
    } catch (e: Exception) {
        null
    }

    private fun wrapHour(value: Int): Int = ((value % 24) + 24) % 24

    /**
     * 返回小时字段在偏移 [delta] 后的跨天信息：
     * - first: 是否有任意小时跨午夜
     * - second: 是否所有具体小时都跨午夜（用于决定是否整体偏移日期）
     */
    private fun hourWrapInfo(hour: String, delta: Int): Pair<Boolean, Boolean> {
        val values = extractHourValues(hour) ?: return false to false
        if (values.isEmpty()) return false to false
        val wraps = values.map { h ->
            if (delta < 0) h < -delta else h + delta >= 24
        }
        return wraps.any { it } to wraps.all { it }
    }

    private fun extractHourValues(hour: String): List<Int>? = try {
        when {
            hour.contains(",") -> hour.split(",").map { it.trim().toInt() }
            hour.contains("-") -> hour.split("/")[0].split("-")
                .let { (it[0].trim().toInt()..it[1].trim().toInt()).toList() }
            else -> listOf(hour.trim().toInt())
        }
    } catch (e: Exception) {
        null
    }

    /**
     * 偏移"日"字段。delta<0 往前一天，delta>0 往后一天。
     * 支持：* 、? 、单个数字、L（月末）。
     */
    private fun shiftDayOfMonth(dom: String, delta: Int): String {
        if (dom == "*" || dom == "?") return dom
        return try {
            when {
                dom.equals("L", ignoreCase = true) -> if (delta > 0) "1" else "L"
                else -> {
                    val d = dom.trim().toInt()
                    if (delta < 0) {
                        if (d == 1) "L" else (d - 1).toString()
                    } else {
                        if (d >= 31) "1" else (d + 1).toString()
                    }
                }
            }
        } catch (e: Exception) {
            dom
        }
    }

    /**
     * 偏移"周"字段并转换编号约定。
     *
     * 标准 cron（用户输入/展示）：0-6，周日=0，周六=6；部分系统 7 也表示周日。
     * Cloudflare：1-7，周日=1，周六=7。
     *
     * delta<0（toUtc）：输入为用户约定，输出为 Cloudflare 约定，同时往前偏移一天。
     * delta>0（toLocal）：输入为 Cloudflare 约定，输出为用户约定，同时往后偏移一天。
     */
    private fun shiftDayOfWeek(dow: String, delta: Int): String {
        if (dow == "*" || dow == "?") return dow
        return try {
            val d = dow.trim().toInt()
            // 统一转为内部 0-6 表示（0=周日，6=周六）
            val internal = if (delta < 0) {
                // 用户约定：0-6；7 视为 0（周日）
                if (d == 7) 0 else d
            } else {
                // Cloudflare 约定：1-7 → 0-6
                ((d - 1) % 7 + 7) % 7
            }
            // 应用日期偏移
            val shifted = if (delta < 0) (internal - 1 + 7) % 7 else (internal + 1) % 7
            // 转为目标约定
            if (delta < 0) {
                // Cloudflare 约定：0-6 → 1-7
                (shifted + 1).toString()
            } else {
                // 用户约定：0-6
                shifted.toString()
            }
        } catch (e: Exception) {
            dow
        }
    }
}
