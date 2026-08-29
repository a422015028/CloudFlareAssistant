package com.muort.upworker.core.util

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/** Pages/Worker 统一文件大小上限：25MB。 */
const val REMOTE_MAX_FILE_SIZE_BYTES: Long = 25L * 1024L * 1024L

/** 进度回调：(已读字节, 总字节(若服务端返回 Content-Length 则有, 否则 null), 当前 URL) */
typealias RemoteProgressListener = (bytesRead: Long, totalBytes: Long?, url: String) -> Unit

/**
 * @return 是否是 http/https URL（只看前缀，不做合法性校验）。
 */
fun isRemoteUrl(pathOrUrl: String): Boolean {
    val t = pathOrUrl.trim()
    return t.startsWith("http://", ignoreCase = true) ||
            t.startsWith("https://", ignoreCase = true)
}

/**
 * 校验路径或 URL 的扩展名是否在允许范围内（用于本地/远程共享的前置校验）。
 * 允许：.js / .zip / .html / .htm（忽略大小写）。
 */
fun hasSupportedExtension(nameOrUrl: String): Boolean {
    val clean = nameOrUrl.trim()
    val pathPart = when {
        isRemoteUrl(clean) -> (clean.toUri().path ?: clean.substringAfter("://"))
        else -> clean.substringAfterLast(File.separatorChar, clean)
    }
    val ext = pathPart.substringAfterLast('.', "").lowercase()
    return ext in RemoteFileResolverInternals.ALLOWED_EXTENSIONS
}

/**
 * 远程文件下载助手：用于 Worker / Pages 部署卡片的"文件路径输入框"支持 http(s):// URL。
 *
 * 智能判断：
 *   - 字符串以 http:// 或 https:// 开头 → 判定为远程 URL，走下载逻辑。
 *   - 否则 → 视为本地路径（调用方直接 File(path) 处理）。
 *
 * 下载要求：
 *   - URL 最后一段路径名必须带受支持扩展名（.js / .zip / .html / .htm，忽略大小写）。
 *   - 单文件不超过 25MB（与 Pages 部署卡片本地文件大小上限对齐，方便 Worker 也复用同一上限）。
 */
object RemoteFileResolver {

    private const val TAG = "RemoteFileResolver"

    /**
     * 从 http(s) URL 下载文件到 Context 私有缓存目录。
     *
     * @param maxSizeBytes 最大允许大小（字节）。默认 [REMOTE_MAX_FILE_SIZE_BYTES]（25MB），
     *                     传 [Long.MAX_VALUE] 表示完全不做大小限制（例如 Worker 脚本的场景）。
     * @return 成功 → Result.success(File) 临时文件；失败 → Result.failure(Throwable)。
     *         临时文件命名：`remote_<时间戳>_<URL中的文件名>`，保证扩展名正确，便于后续上传逻辑识别。
     */
    suspend fun resolve(
        context: Context,
        url: String,
        maxSizeBytes: Long = REMOTE_MAX_FILE_SIZE_BYTES,
        onProgress: RemoteProgressListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!isRemoteUrl(cleanUrl)) {
            return@withContext Result.failure<File>(
                IllegalArgumentException("Not a remote URL: $cleanUrl")
            )
        }
        // 扩展名校验：从 URL 的 path 最后一段取文件名。无扩展名或不在白名单 → 提前失败
        val fileName = RemoteFileResolverInternals.extractFileName(cleanUrl)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in RemoteFileResolverInternals.ALLOWED_EXTENSIONS) {
            return@withContext Result.failure<File>(
                IllegalArgumentException(
                    "Unsupported remote extension '$ext' from '$fileName'"
                )
            )
        }

        val tmpDir = File(context.cacheDir, "remote_inputs").apply { mkdirs() }
        val target = File(tmpDir, "remote_${System.currentTimeMillis()}_$fileName")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = try {
            Request.Builder().url(cleanUrl).build()
        } catch (t: Throwable) {
            return@withContext Result.failure<File>(t)
        }

        val enforceSizeLimit = maxSizeBytes != Long.MAX_VALUE

        val responseResult: Result<File> = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use Result.failure<File>(
                        IllegalStateException("HTTP ${response.code} ${response.message}".trim())
                    )
                }
                val body = response.body
                    ?: return@use Result.failure<File>(IllegalStateException("Empty response body"))
                val totalBytes = body.contentLength().takeIf { it > 0 }
                if (enforceSizeLimit && totalBytes != null && totalBytes > maxSizeBytes) {
                    return@use Result.failure<File>(
                        IllegalStateException("Remote file too large: $totalBytes bytes")
                    )
                }

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead = 0L
                var emittedProgress = false
                var streamingExceeded = false
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            bytesRead += n
                            if (enforceSizeLimit && bytesRead > maxSizeBytes) {
                                // 流下载过程超上限：截断并抛错
                                streamingExceeded = true
                                break
                            }
                            // 避免 UI 回调过频：>=64KB 或 完成 或 第一次
                            if (!emittedProgress ||
                                (totalBytes != null && bytesRead >= totalBytes) ||
                                bytesRead % (64 * 1024) < n
                            ) {
                                emittedProgress = true
                                onProgress?.invoke(bytesRead, totalBytes, cleanUrl)
                            }
                        }
                    }
                }
                if (streamingExceeded) {
                    target.delete()
                    return@use Result.failure<File>(
                        IllegalStateException("Remote file exceeds ${maxSizeBytes / (1024 * 1024)}MB while streaming")
                    )
                }

                onProgress?.invoke(bytesRead, totalBytes, cleanUrl)
                Timber.i(
                    "$TAG: 下载完成 url=$cleanUrl file=${target.absolutePath}" +
                            " size=$bytesRead expected=$totalBytes"
                )
                Result.success(target)
            }
        } catch (t: Throwable) {
            if (target.exists()) target.delete()
            Timber.w(t, "$TAG: 下载失败 url=$cleanUrl")
            Result.failure<File>(t)
        }
        responseResult
    }
}

/**
 * 把 RemoteFileResolver 需要的内部 helper（扩展名集合、文件名提取）集中到一起，
 * 避免污染顶层命名空间。
 */
internal object RemoteFileResolverInternals {
    /** 允许的远程文件扩展名（小写）。 */
    val ALLOWED_EXTENSIONS = setOf("js", "zip", "html", "htm")

    fun extractFileName(url: String): String {
        val uri = runCatching { url.toUri() }.getOrNull()
        val path = uri?.path?.takeIf { it.isNotBlank() } ?: url.substringAfter("://")
        val last = path.trimEnd('/').substringAfterLast('/')
        if (last.isBlank() || '.' !in last) {
            // 退化：看 URL query 里有没有 filename=xxx；都没有就 fallback download.js
            val query = uri?.query ?: ""
            if (query.contains("filename=", ignoreCase = true)) {
                val m = Regex("""[Ff]ilename=([^&]+)""").find(query)
                val v = m?.groupValues?.getOrNull(1)?.trim('"', '\'') ?: ""
                if (v.isNotBlank() && '.' in v) return v
            }
            return "download.js"
        }
        return last
    }
}
