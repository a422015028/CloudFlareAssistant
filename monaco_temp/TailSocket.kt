package jiamin.chen.orangecloud.core.network

import jiamin.chen.orangecloud.data.model.TailTraceItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.time.Duration

/**
 * Workers tail 的 WebSocket 传输层（对应 iOS Core/Network/TailSocket.swift）。
 * - URL 是创建 tail session 时返回的预签名 wss://，连接无需 Bearer
 * - 子协议 trace-v1 走 Sec-WebSocket-Protocol header；开连后先声明过滤器
 * - 事件 JSON 是 camelCase；单条解码失败跳过不中断流
 * - 30s 自动 ping 防 session 因 inactivity 过期；一次连接即弃，重连由 ViewModel 新建实例
 */
class TailSocket(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val url: String,
) {
    private val wsClient = okHttpClient.newBuilder()
        .pingInterval(Duration.ofSeconds(30))
        .build()

    fun events(): Flow<TailTraceItem> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "trace-v1")
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // trace-v1 要求连接后先声明过滤器
                webSocket.send("""{"filters":[],"debug":false}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                emit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                emit(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            private fun emit(payload: String) {
                runCatching { json.decodeFromString(TailTraceItem.serializer(), payload) }
                    .getOrNull()
                    ?.let { trySend(it) }
            }
        }

        val webSocket = wsClient.newWebSocket(request, listener)
        awaitClose { webSocket.cancel() }
    }
}
