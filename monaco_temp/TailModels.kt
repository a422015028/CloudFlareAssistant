package jiamin.chen.orangecloud.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Workers 实时日志（tail）模型（对应 iOS WorkerTailModels.swift）。
 * - TailSession 来自 REST（snake_case）
 * - TailTraceItem 来自 trace-v1 WebSocket 帧（**camelCase**，与 REST 不同，勿加 SerialName）
 */
@Serializable
data class TailSession(
    val id: String,
    val url: String,                                  // 预签名 wss://，连接无需 Bearer
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class TailTraceItem(
    val outcome: String? = null,                      // "ok" | "exception" | "exceededCpu" ...
    val scriptName: String? = null,
    val eventTimestamp: Long? = null,                 // 毫秒
    val event: TailEventInfo? = null,
    val logs: List<TailLog>? = null,
    val exceptions: List<TailException>? = null,
)

@Serializable
data class TailEventInfo(
    val request: TailRequestInfo? = null,
    val cron: String? = null,
)

@Serializable
data class TailRequestInfo(
    val url: String? = null,
    val method: String? = null,
)

@Serializable
data class TailLog(
    val level: String = "log",                        // log | warn | error | debug | info
    val timestamp: Long? = null,
    val message: List<JsonElement>? = null,           // console.log 参数：任意 JSON 数组
)

@Serializable
data class TailException(
    val name: String? = null,
    val message: String? = null,
    val timestamp: Long? = null,
)

/** console.* 参数（任意 JSON）的展示文本。 */
fun JsonElement.tailDisplayText(): String = when (this) {
    is JsonPrimitive -> content
    is JsonArray -> "[" + joinToString(", ") { it.tailDisplayText() } + "]"
    is JsonObject -> "{" + entries.sortedBy { it.key }.joinToString(", ") { "${it.key}: ${it.value.tailDisplayText()}" } + "}"
}
