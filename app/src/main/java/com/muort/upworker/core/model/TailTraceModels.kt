package com.muort.upworker.core.model

data class TailTraceItem(
    val outcome: String? = null,
    val scriptName: String? = null,
    val eventTimestamp: Long? = null,
    val event: TailEventInfo? = null,
    val logs: List<TailLog>? = null,
    val exceptions: List<TailException>? = null,
)

data class TailEventInfo(
    val request: TailRequestInfo? = null,
    val cron: String? = null,
)

data class TailRequestInfo(
    val url: String? = null,
    val method: String? = null,
)

data class TailLog(
    val level: String = "log",
    val timestamp: Long? = null,
    val message: List<Any?>? = null,
)

data class TailException(
    val name: String? = null,
    val message: String? = null,
    val timestamp: Long? = null,
)