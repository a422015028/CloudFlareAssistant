package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.MultipartReader
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloudflare Snippets 仓库（zone 级边缘 JS）：列表 / 正文 / 创建更新（multipart）/ 删除。
 * 对应 orange-cloud SnippetRepository。
 */
@Singleton
class SnippetRepository @Inject constructor(
    private val api: CloudFlareApi,
    private val dnsRepository: DnsRepository,
) {
    suspend fun listSnippets(account: Account, zoneId: String): Resource<List<Snippet>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listSnippets(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result ?: emptyList())
                } else {
                    val errors = resp.body()?.errors ?: parseErrors(resp)
                    Resource.Error(friendlyError(errors.firstOrNull()?.message)
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun getSnippetContent(account: Account, zoneId: String, name: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getSnippetContent(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, name,
                )
                if (resp.isSuccessful && resp.body() != null) {
                    val body = resp.body()!!
                    val contentType = body.contentType()
                    if (contentType != null && contentType.type == "multipart") {
                        val boundary = contentType.parameter("boundary")
                        if (boundary != null) {
                            val reader = MultipartReader(body.source(), boundary)
                            var mainModuleContent: String? = null
                            var firstJsContent: String? = null
                            while (true) {
                                val part = reader.nextPart() ?: break
                                val disposition = part.headers["Content-Disposition"]
                                val fileName = disposition?.let { extractFilename(it) }
                                val partContent = part.body.readUtf8()
                                if (fileName != null && fileName.endsWith(".js")) {
                                    if (firstJsContent == null) firstJsContent = partContent
                                    if (fileName == "snippet.js" || fileName == "index.js") {
                                        mainModuleContent = partContent
                                    }
                                }
                            }
                            val content = mainModuleContent ?: firstJsContent ?: ""
                            Resource.Success(content)
                        } else {
                            Resource.Success(body.string())
                        }
                    } else {
                        Resource.Success(body.string())
                    }
                } else {
                    Resource.Error("HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    private fun extractFilename(contentDisposition: String): String? {
        val patterns = listOf(
            """filename="([^"]+)"""",
            """filename=([^;]+)""",
        )
        for (pattern in patterns) {
            val regex = Regex(pattern)
            val match = regex.find(contentDisposition)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    /** 创建或更新（multipart：metadata + JS 模块）。 */
    suspend fun putSnippet(
        account: Account, zoneId: String, name: String, code: String,
        mainModule: String = "snippet.js",
    ): Resource<Snippet> = withContext(Dispatchers.IO) {
        safeApiCall {
            val metadataJson = """{"main_module":"$mainModule"}"""
                .toRequestBody("application/json".toMediaType())
            val scriptBody = code.toRequestBody("application/javascript+module".toMediaType())
            val scriptPart = MultipartBody.Part.createFormData(mainModule, mainModule, scriptBody)

            val resp = api.putSnippet(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, name, metadataJson, scriptPart,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("保存失败：无返回数据")
            } else {
                val errors = resp.body()?.errors ?: parseErrors(resp)
                Resource.Error(friendlyError(errors.firstOrNull()?.message)
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun deleteSnippet(account: Account, zoneId: String, name: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteSnippet(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, name,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    val errors = resp.body()?.errors ?: parseErrors(resp)
                    Resource.Error(friendlyError(errors.firstOrNull()?.message)
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    // ==================== Snippet Rules ====================

    /**
     * 彻底下线片段：先删触发规则（Cloudflare 会拒绝删除仍被规则引用的片段），连带清理
     * 规则表达式指向的主机名 DNS 记录，最后删片段。
     */
    suspend fun teardownSnippet(account: Account, zoneId: String, name: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            // 先读一次规则：提取主机名供 DNS 清理；读到的列表复用给 deleteSnippetRule 省一次请求。
            // 读取失败时不传 existing，由 deleteSnippetRule 内部重试——绝不能把空列表当“无规则”去全量清空
            val existing = (listSnippetRules(account, zoneId) as? Resource.Success)?.data
            val hosts = existing.orEmpty().filter { it.snippetName == name }
                .flatMap { HOST_EQ_PATTERN.findAll(it.expression).map { m -> m.groupValues[1] } }
            when (val r = deleteSnippetRule(account, zoneId, name, existing)) {
                is Resource.Error -> return@withContext Resource.Error("删除触发规则失败（片段仍在使用中）：${r.message}")
                else -> {}
            }
            // DNS 记录尽力清理：仅精确 eq 的主机名才安全可删，失败不阻塞片段删除
            hosts.forEach { host ->
                val records = (dnsRepository.listDnsRecords(account, zoneId, name = host) as? Resource.Success)?.data
                    ?: return@forEach
                records.forEach { dnsRepository.deleteDnsRecord(account, zoneId, it.id) }
            }
            deleteSnippet(account, zoneId, name)
        }

    companion object {
        /** 官网 UI 口径的表达式长度上限（API 硬上限为 4096）。 */
        const val MAX_EXPRESSION_LENGTH = 4000

        /** 从规则表达式中提取 http.host eq "xxx" 的主机名（contains 等模糊条件不做 DNS 清理）。 */
        private val HOST_EQ_PATTERN = Regex("""http\.host\s+eq\s+"([^"]+)"""")
    }

    /**
     * 列出 zone 的代码片段规则。
     * 错误码 10003 = http_request_snippets 入口规则集尚不存在（从未创建过规则），按空列表处理。
     */
    suspend fun listSnippetRules(account: Account, zoneId: String): Resource<List<SnippetRule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listSnippetRules(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result ?: emptyList())
                } else {
                    val errors = resp.body()?.errors ?: parseErrors(resp)
                    if (errors.any { it.code == 10003 }) {
                        Resource.Success(emptyList())
                    } else {
                        Resource.Error(friendlyError(errors.firstOrNull()?.message)
                            ?: "HTTP ${resp.code()}: ${resp.message()}")
                    }
                }
            }
        }

    /**
     * 保存指定代码片段的规则。
     * PUT 为全量替换语义：先取现有规则，替换该片段的规则后整体回传，其余片段的规则保持不变。
     */
    suspend fun saveSnippetRule(
        account: Account,
        zoneId: String,
        rule: SnippetRule,
    ): Resource<SnippetRule> = withContext(Dispatchers.IO) {
        safeApiCall {
            val expr = rule.expression.trim()
            when {
                expr.isEmpty() -> return@safeApiCall Resource.Error("表达式不能为空")
                expr.length > MAX_EXPRESSION_LENGTH ->
                    return@safeApiCall Resource.Error("表达式长度 ${expr.length} 超过上限 $MAX_EXPRESSION_LENGTH 字符")
            }
            val existing = when (val r = listSnippetRules(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> return@safeApiCall Resource.Error("读取现有规则失败：${r.message}")
                is Resource.Loading -> return@safeApiCall Resource.Error("读取现有规则失败")
            }
            val merged = existing.filterNot { it.snippetName == rule.snippetName } + rule
            val resp = api.putSnippetRules(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId,
                SnippetRulesRequest(merged),
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                val saved = resp.body()?.result?.firstOrNull { it.snippetName == rule.snippetName }
                Resource.Success(saved ?: rule)
            } else {
                val errors = resp.body()?.errors ?: parseErrors(resp)
                Resource.Error(friendlyError(errors.firstOrNull()?.message)
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    /** 删除指定代码片段的规则（保留其它片段的规则）。 */
    suspend fun deleteSnippetRule(
        account: Account,
        zoneId: String,
        snippetName: String,
        existing: List<SnippetRule>? = null,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            // 调用方已读过规则列表时直接复用，省一次网络请求；为 null 才自行读取
            val current = existing ?: when (val r = listSnippetRules(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> return@safeApiCall Resource.Error("读取现有规则失败：${r.message}")
                is Resource.Loading -> return@safeApiCall Resource.Error("读取现有规则失败")
            }
            val remaining = current.filterNot { it.snippetName == snippetName }
            val resp = if (remaining.isEmpty()) {
                api.deleteSnippetRules(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
            } else {
                api.putSnippetRules(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                    SnippetRulesRequest(remaining),
                )
            }
            if (resp.isSuccessful && resp.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errors = resp.body()?.errors ?: parseErrors(resp)
                Resource.Error(friendlyError(errors.firstOrNull()?.message)
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    /** 已知 Snippets API 英文报错 → 中文提示；未知报错原样返回。 */
    private fun friendlyError(message: String?): String? = when {
        message == null -> null
        message.contains("snippets are not allowed", ignoreCase = true) ->
            "该域名未开通 Snippets 权限（免费计划仅部分域名可用，需 Pro 及以上计划）"
        message.contains("can only contain the characters", ignoreCase = true) ->
            "片段名称仅支持小写字母、数字和下划线"
        else -> message
    }

    /** 从 errorBody 解析错误（非 2xx 时 Retrofit body() 为 null）。 */
    private fun parseErrors(resp: retrofit2.Response<*>): List<CloudFlareError> =
        try {
            val body = resp.errorBody()?.string()
            if (body.isNullOrBlank()) emptyList()
            else com.google.gson.Gson().fromJson(body, CloudFlareResponse::class.java).errors ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
}
