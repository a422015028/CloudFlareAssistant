package com.muort.upworker.core.webdav

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavClient @Inject constructor() {
    
    companion object {
        private const val TAG = "WebDavClient"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private fun createAuthHeader(username: String, password: String): String {
        return Credentials.basic(username, password)
    }
    
    /**
     * 测试WebDAV连接
     */
    suspend fun testConnection(url: String, username: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .method("PROPFIND", null)
                    .header("Authorization", createAuthHeader(username, password))
                    .header("Depth", "0")
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("连接失败: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 上传文件到WebDAV
     */
    suspend fun uploadFile(
        url: String,
        username: String,
        password: String,
        filePath: String,
        content: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim().trimEnd('/')
                val cleanPath = filePath.trim().trimStart('/')
                val fullUrl = "$cleanUrl/$cleanPath"
                
                // 创建父目录（如果需要）
                val parentUrl = fullUrl.substringBeforeLast("/")
                createDirectoryIfNeeded(parentUrl, username, password)
                
                // 上传文件
                val requestBody = content.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(fullUrl)
                    .put(requestBody)
                    .header("Authorization", createAuthHeader(username, password))
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code == 201 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("上传失败: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 从WebDAV下载文件
     */
    suspend fun downloadFile(
        url: String,
        username: String,
        password: String,
        filePath: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim().trimEnd('/')
                val cleanPath = filePath.trim().trimStart('/')
                val fullUrl = "$cleanUrl/$cleanPath"
                
                val request = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .header("Authorization", createAuthHeader(username, password))
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val content = response.body?.string() ?: ""
                    Result.success(content)
                } else {
                    Result.failure(Exception("下载失败: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 列出WebDAV目录中的文件
     */
    suspend fun listFiles(
        url: String,
        username: String,
        password: String,
        path: String = ""
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                // 构建完整URL，确保路径正确
                val cleanPath = path.trim().trim('/')
                val cleanUrl = url.trim().trimEnd('/')
                val fullUrl = if (cleanPath.isEmpty()) {
                    cleanUrl
                } else {
                    "$cleanUrl/$cleanPath"
                }
                
                // 确保URL以/结尾（对于目录PROPFIND很重要）
                val finalUrl = if (!fullUrl.endsWith("/")) "$fullUrl/" else fullUrl
                
                val propfindBody = """
                    <?xml version="1.0" encoding="utf-8" ?>
                    <D:propfind xmlns:D="DAV:">
                        <D:prop>
                            <D:displayname/>
                            <D:getcontenttype/>
                        </D:prop>
                    </D:propfind>
                """.trimIndent()
                
                val request = Request.Builder()
                    .url(finalUrl)
                    .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                    .header("Authorization", createAuthHeader(username, password))
                    .header("Depth", "1")
                    .build()
                
                val response = client.newCall(request).execute()
                Log.d(TAG, "listFiles response: ${response.code} ${response.message}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.d(TAG, "listFiles response body length: ${responseBody.length}")
                    Log.d(TAG, "listFiles response body preview: ${responseBody.take(500)}")
                    
                    val fileNames = parseFileNames(responseBody)
                    Log.d(TAG, "listFiles parsed ${fileNames.size} files: $fileNames")
                    
                    Result.success(fileNames)
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "listFiles failed: HTTP ${response.code} - ${response.message}, body: $errorBody")
                    Result.failure(Exception("列表获取失败: HTTP ${response.code} - ${response.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "listFiles exception", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * 删除WebDAV文件
     */
    suspend fun deleteFile(
        url: String,
        username: String,
        password: String,
        filePath: String
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim().trimEnd('/')
                val cleanPath = filePath.trim().trimStart('/')
                val fullUrl = "$cleanUrl/$cleanPath"
                
                val request = Request.Builder()
                    .url(fullUrl)
                    .delete()
                    .header("Authorization", createAuthHeader(username, password))
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("删除失败: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 创建目录（如果不存在）
     */
    private suspend fun createDirectoryIfNeeded(url: String, username: String, password: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .header("Authorization", createAuthHeader(username, password))
                .build()
            
            client.newCall(request).execute()
        } catch (e: Exception) {
            // 忽略错误，可能目录已存在
        }
    }
    
    /**
     * 解析PROPFIND响应中的文件名
     */
    private fun parseFileNames(xml: String): List<String> {
        val fileNames = mutableListOf<String>()
        try {
            // 支持多种XML命名空间格式
            val hrefPatterns = listOf(
                "<D:href>([^<]+)</D:href>".toRegex(),
                "<d:href>([^<]+)</d:href>".toRegex(),
                "<href>([^<]+)</href>".toRegex()
            )
            
            for (pattern in hrefPatterns) {
                val matches = pattern.findAll(xml)
                Log.d(TAG, "parseFileNames pattern: $pattern, matches count: ${matches.count()}")
                
                for (match in matches) {
                    var href = match.groupValues[1]
                    Log.d(TAG, "parseFileNames found href: $href")
                    
                    // URL解码
                    href = java.net.URLDecoder.decode(href, "UTF-8")
                    Log.d(TAG, "parseFileNames decoded href: $href")
                    
                    // 提取文件名（最后一个/后的内容）
                    val fileName = href.trimEnd('/').substringAfterLast('/')
                    Log.d(TAG, "parseFileNames extracted fileName: $fileName")
                    
                    // 检查是否是备份文件
                    if (fileName.isNotEmpty() && 
                        fileName.startsWith("cloudflare_backup_") && 
                        fileName.endsWith(".json")) {
                        Log.d(TAG, "parseFileNames matched backup file: $fileName")
                        fileNames.add(fileName)
                    }
                }
                
                // 如果找到了文件，就不再尝试其他模式
                if (fileNames.isNotEmpty()) {
                    break
                }
            }
        } catch (e: Exception) {
            // 解析失败，记录但返回空列表
            Log.e(TAG, "parseFileNames exception", e)
            e.printStackTrace()
        }
        Log.d(TAG, "parseFileNames final result: $fileNames")
        return fileNames.distinct()
    }
}
