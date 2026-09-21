package com.muort.upworker.core.log

import android.util.Log
import timber.log.Timber

/**
 * 应用日志树：将 Timber 日志转发到 [LogRepository]，
 * 与 HTTP 日志一起显示在应用内日志界面，便于用户反馈问题。
 *
 * 仅当日志开关开启（[LogRepository.isEnabled]）时才捕获，
 * 避免正常使用时产生不必要的字符串拼接与存储开销。
 */
class InAppLogTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        LogRepository.isEnabled

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
        val time = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val timeStr = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy HH:mm:ss z"))
        val log = buildString {
            append("[$level] $timeStr ${tag ?: "App"}: $message\n")
            t?.let { append("${it.stackTraceToString()}\n") }
        }
        LogRepository.appendLog(log)
    }
}
