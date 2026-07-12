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
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
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
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
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
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }
}
