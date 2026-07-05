package jiamin.chen.orangecloud.data.repository

import jiamin.chen.orangecloud.core.network.CfApiClient
import jiamin.chen.orangecloud.core.network.TailSocket
import jiamin.chen.orangecloud.data.model.TailSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Workers tail session 的 REST 生命周期 + WebSocket 工厂（对应 iOS WorkerTailService）。
 */
@Singleton
class WorkerTailRepository @Inject constructor(
    private val api: CfApiClient,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /** 创建 tail session，返回预签名 wss:// URL。 */
    suspend fun createTail(accountId: String, scriptName: String): TailSession =
        api.post(
            "accounts/$accountId/workers/scripts/$scriptName/tails",
            JsonObject(emptyMap()),
        )

    /** 销毁 tail session（退出日志页时调用，失败不阻塞）。 */
    suspend fun deleteTail(accountId: String, scriptName: String, tailId: String) {
        api.delete("accounts/$accountId/workers/scripts/$scriptName/tails/$tailId")
    }

    /** 由 session 构造 WebSocket。 */
    fun makeSocket(session: TailSession): TailSocket = TailSocket(okHttpClient, json, session.url)
}
