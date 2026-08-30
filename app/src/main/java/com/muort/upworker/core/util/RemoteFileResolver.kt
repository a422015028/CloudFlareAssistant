package com.muort.upworker.core.util

import android.content.Context
import androidx.core.net.toUri
import com.muort.upworker.R
import com.muort.upworker.core.net.security.RemoteFileDownloadSecurityException
import com.muort.upworker.core.net.security.RemoteSecurityCode
import com.muort.upworker.core.net.security.RemoteUrlSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/** Pages/Worker 单文件最大默认：25MB Pages zip 上限（CT 使用 SCRIPT_MAX 5MB 单独约束）。 */
const val REMOTE_MAX_FILE_SIZE_BYTES: Long = RemoteUrlSecurity.ZIP_MAX_BYTES

/** Worker 脚本场景大小上限：5 MiB（对齐 cf-manager Wn）。 */
const val REMOTE_SCRIPT_MAX_FILE_SIZE_BYTES: Long = RemoteUrlSecurity.SCRIPT_MAX_BYTES

/** 进度回调：(已读字节, 总字节(若服务端返回 Content-Length 则有, 否则 null), 当前 URL) */
typealias RemoteProgressListener = (bytesRead: Long, totalBytes: Long?, url: String) -> Unit

/**
 * @return 是否是 http(s) URL（只看前缀，不做合法性校验）。
 * 注：为了兼容历史调用，本函数仍然返回 http + https；真正在 resolve 里会强制拒绝 http。
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
 * 远程文件下载助手。
 *
 * 安全规则（对齐 cf-manager Wn 管线）：
 *   1. 仅 HTTPS；
 *   2. 域名 allowlist（CSV，null=允许所有）；
 *   3. 所有 A/AAAA 解析结果不得落在私网/保留网段（SSRF 防护）；
 *   4. 手动 follow 重定向 ≤5，每跳重新执行 1-3，且重定向目标必须也是 HTTPS；
 *   5. 2xx 响应 Content-Type 必须在脚本/zip 白名单内；
 *   6. Worker 脚本硬上限 5 MiB；Pages zip 硬上限 25 MiB；
 *   7. zip 下载后首 2 字节必须是 0x50 0x4B（"PK"）。
 *
 * 所有对外异常均为 [RemoteFileDownloadSecurityException]，且带 [RemoteSecurityCode]；
 * UI 层根据 code 映射到 R.string.remote_url_security_*（已提供中/英双语），严禁代码内再次硬编码用户可见文案。
 */
object RemoteFileResolver {

    private const val TAG = "RemoteFileResolver"

    /**
     * 测试专用 hook：注入自定义 message resolver。production 保持 null。
     * 这允许 unit test 在 Robolectric 无法加载 resources 时，退化成占位字符串，
     * 同时不影响 production 路径 `wrapUserVisible` 严格走 `context.getString(R.string.*)`。
     */
    @Volatile
    internal var messageResolverForTests: ((
        context: Context,
        code: RemoteSecurityCode,
        arg1: String?,
        arg2: String?,
    ) -> String)? = null

    /**
     * 测试专用 hook：绕过 SSRF DNS 私网拦截的 host 集合。production 永远 null。
     * 原因：MockWebServer 只能监听 127.0.0.1/localhost；如果严格启用 SSRF，
     * 所有连向 localhost 的请求都会在安全层被拒绝；此 hook 仅对 unit test 暴露，
     * 使用 `Dns.SYSTEM.lookup(localhost) → 127.0.0.1` 自然路径，避免 SSL/TLS Socket 重写。
     */
    @Volatile
    internal var ssrfBypassHostsForTests: Set<String>? = null

    /**
     * 测试专用 hook：允许 HTTP（明文）协议。production 永远 false。
     * 原因：MockWebServer 明文 HTTP 最稳定，避免自签 TLS + keyManager 复杂配置；
     * 此 flag 仅放行 parseUrlOrThrow 的 protocol check，其它 security 流程保持一致。
     */
    @Volatile
    internal var allowInsecureProtocolForTests: Boolean = false

    /**
     * 从远程 URL 下载文件到 Context 私有缓存目录。
     *
     * 默认 strict 模式：强制 HTTPS + DNS SSRF 私网拦截（对齐 P0-2 Wn 生产安全等级）。
     * 需要本地/局域网连接时（自建 NAS、本地调试服务 192.168.x.x / 10.x.x.x / 127.0.0.1）：
     *  ```
     *  resolve(ctx, url, allowInsecureProtocol = true, allowPrivateIp = true)
     *  ```
     *  注意：即使两参数都为 true，**扩展名 / Content-Type 白名单 / 大小 5MiB/25MiB / ZIP 魔数 / 重定向上限 5 / HTTP 状态码 2xx 这 6 条安全约束仍强制执行**，不会裸奔。
     *
     * @param maxSizeBytes 最大字节。**不再允许 Long.MAX_VALUE（会被强制到脚本 5MiB）**。
     *                     建议显式传 [REMOTE_MAX_FILE_SIZE_BYTES] 或 [REMOTE_SCRIPT_MAX_FILE_SIZE_BYTES]。
     * @param hostAllowlistCsv 逗号分隔允许的域名集合；null/空=允许所有（对齐 cf-manager WORKER_DEPLOY_URL_ALLOWLIST）。
     * @param dns 用于 SSRF 校验的 DNS 解析器；默认 [Dns.SYSTEM]，测试可注入自定义。
     * @param okHttpClientSupplier 可选 OkHttpClient 工厂（测试用 MockWebServer 可注入）。
     * @param allowInsecureProtocol 默认 `false`（强制 HTTPS）；传 `true` 允许 `http://` 明文协议。
     * @param allowPrivateIp         默认 `false`（SSRF 私网 IP 拦截）；传 `true` 允许解析结果为私网段。
     */
    suspend fun resolve(
        context: Context,
        url: String,
        maxSizeBytes: Long = REMOTE_MAX_FILE_SIZE_BYTES,
        hostAllowlistCsv: String? = null,
        dns: Dns = Dns.SYSTEM,
        okHttpClientSupplier: (() -> OkHttpClient)? = null,
        onProgress: RemoteProgressListener? = null,
        allowInsecureProtocol: Boolean = false,
        allowPrivateIp: Boolean = false,
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (!isRemoteUrl(cleanUrl)) {
            return@withContext Result.failure(
                wrapUserVisible(
                    context, RemoteSecurityCode.INVALID_URL_FORMAT, cause = null, formatArg1 = null
                )
            )
        }

        // 扩展名校验：决定后续按脚本/zip 走的安全分类
        val fileName = RemoteFileResolverInternals.extractFileName(cleanUrl)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in RemoteFileResolverInternals.ALLOWED_EXTENSIONS) {
            return@withContext Result.failure(
                wrapUserVisible(
                    context, RemoteSecurityCode.INVALID_URL_FORMAT, cause = null, formatArg1 = null
                )
            )
        }
        val isZip = ext == "zip"
        val effectiveMax = when {
            isZip -> minOf(maxSizeBytes, RemoteUrlSecurity.ZIP_MAX_BYTES)
            else  -> minOf(
                if (maxSizeBytes == Long.MAX_VALUE) RemoteUrlSecurity.SCRIPT_MAX_BYTES else maxSizeBytes,
                RemoteUrlSecurity.SCRIPT_MAX_BYTES
            )
        }
        val allowlist = RemoteUrlSecurity.parseAllowlist(hostAllowlistCsv)

        val tmpDir = File(context.cacheDir, "remote_inputs").apply { mkdirs() }
        val target = File(tmpDir, "remote_${System.currentTimeMillis()}_$fileName")

        val client = okHttpClientSupplier?.invoke() ?: defaultClient()

        var remaining = RemoteUrlSecurity.MAX_REDIRECTS
        var currentUrl = cleanUrl
        val seenHosts = linkedSetOf<String>()
        val result: Result<File> = try {
            while (true) {
                // ------- 每跳安全校验：协议 + allowlist + DNS SSRF -------
                var host: String? = null
                val parsed: URL = try {
                    val raw = RemoteUrlSecurity.parseUrlOrThrow(currentUrl)
                    raw
                } catch (se: RemoteFileDownloadSecurityException) {
                    // parseUrlOrThrow 会抛出 NOT_HTTPS / INVALID_URL_FORMAT 两种。
                    // INVALID_URL_FORMAT 肯定要继续抛；NOT_HTTPS 在用户显式 allowInsecureProtocol=true
                    // 或 test hook allowInsecureProtocolForTests=true 时降级到 java.net.URL 解析。
                    if (se.code == RemoteSecurityCode.NOT_HTTPS && (allowInsecureProtocol || allowInsecureProtocolForTests)) {
                        try {
                            URL(currentUrl).also { host = it.host }
                        } catch (t: Throwable) {
                            throw wrapUserVisible(
                                context, RemoteSecurityCode.INVALID_URL_FORMAT, t,
                                formatArg1 = currentUrl
                            )
                        }
                    } else {
                        throw wrapUserVisible(
                            context, se.code, se, formatArg1 = host ?: currentUrl
                        )
                    }
                }.also { host = it.host }
                RemoteUrlSecurity.checkHostAllowlist(parsed.host, allowlist)
                val bypass = ssrfBypassHostsForTests
                try {
                    when {
                        // 用户显式允许私网 IP（本地/局域网连接）：跳过 SSRF 拦截但仍做 DNS 解析
                        allowPrivateIp -> dns.lookup(parsed.host)
                        // test hook 集合匹配：同上跳过
                        bypass != null && parsed.host in bypass -> dns.lookup(parsed.host)
                        // strict 默认：走 SSRF + DNS 校验
                        else -> RemoteUrlSecurity.dnsResolveAndCheckPrivate(parsed.host, dns)
                    }
                } catch (ssrf: RemoteFileDownloadSecurityException) {
                    throw wrapUserVisible(context, ssrf.code, ssrf, parsed.host)
                } catch (t: Throwable) {
                    target.deleteQuietly()
                    val c = wrapUserVisible(
                        context, RemoteSecurityCode.FETCH_IO_ERROR, t,
                        formatArg1 = (t.message ?: t.javaClass.simpleName)
                    )
                    return@withContext Result.failure(c)
                }
                seenHosts.add(parsed.host)

                val httpUrl = currentUrl.toHttpUrlOrNull()
                    ?: throw wrapUserVisible(context, RemoteSecurityCode.INVALID_URL_FORMAT, null, null)
                val request = Request.Builder().url(httpUrl).build()

                val response: Response = try {
                    client.newCall(request).execute()
                } catch (t: Throwable) {
                    target.deleteQuietly()
                    val c = wrapUserVisible(
                        context, RemoteSecurityCode.FETCH_IO_ERROR, t,
                        formatArg1 = (t.message ?: t.javaClass.simpleName)
                    )
                    return@withContext Result.failure(c)
                }

                var redirected = false
                response.use { resp ->
                    val code = resp.code
                    if (code in 300..399 && code != 304) {
                        remaining -= 1
                        if (remaining < 0) {
                            throw wrapUserVisible(
                                context, RemoteSecurityCode.TOO_MANY_REDIRECTS, cause = null, formatArg1 = null
                            )
                        }
                        val location = resp.header("Location")?.trim().orEmpty()
                        val next = resolveLocation(currentUrl, location)
                            ?: throw wrapUserVisible(
                                context, RemoteSecurityCode.INVALID_URL_FORMAT, cause = null, formatArg1 = null
                            )
                        // 非 HTTPS Location：allowInsecureProtocol=true（用户本地连接）或 test hook 才放行
                        if (next.protocol?.equals("https", ignoreCase = true) != true && !(allowInsecureProtocol || allowInsecureProtocolForTests)) {
                            throw wrapUserVisible(
                                context, RemoteSecurityCode.REDIRECT_TO_NON_HTTPS, cause = null, formatArg1 = null
                            )
                        }
                        currentUrl = next.toString()
                        redirected = true
                        return@use
                    }

                    if (!resp.isSuccessful) {
                        throw wrapUserVisible(
                            context, RemoteSecurityCode.HTTP_STATUS_ERROR, cause = null,
                            formatArg1 = code.toString(),
                            formatArg2 = resp.message.ifBlank { "HTTP $code" }
                        )
                    }

                    val body = resp.body
                        ?: throw wrapUserVisible(
                            context, RemoteSecurityCode.FETCH_IO_ERROR, cause = null,
                            formatArg1 = "empty response body"
                        )

                    val ct = body.contentType()?.toString()
                    if (isZip) RemoteUrlSecurity.checkContentTypeZip(ct)
                    else RemoteUrlSecurity.checkContentTypeScript(ct)

                    val totalBytes = body.contentLength().takeIf { it > 0 }
                    if (totalBytes != null && totalBytes > effectiveMax) {
                        val szCode = if (isZip) RemoteSecurityCode.ZIP_SIZE_EXCEEDED_25MIB
                                     else RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB
                        throw wrapUserVisible(context, szCode, cause = null, formatArg1 = null)
                    }

                    var streamingExceeded = false
                    var bytesRead = 0L
                    // 使用局部 holder，避免在 lambda 内读写外部 var 触发 kotlin smart cast 限制。
                    val first2Holder = object { var bytes: ByteArray? = null }
                    target.outputStream().use { fos ->
                        body.byteStream().use { ins ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var emitted = false
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                val prev = first2Holder.bytes
                                if (prev == null) {
                                    first2Holder.bytes = when {
                                        n >= 2 -> buf.copyOfRange(0, 2)
                                        else   -> buf.copyOf(n)
                                    }
                                } else if (prev.size < 2) {
                                    val need = 2 - prev.size
                                    val take = minOf(need, n)
                                    val merged = prev + buf.copyOfRange(0, take)
                                    first2Holder.bytes = merged
                                }
                                fos.write(buf, 0, n)
                                bytesRead += n
                                if (bytesRead > effectiveMax) {
                                    streamingExceeded = true
                                    break
                                }
                                if (!emitted ||
                                    (totalBytes != null && bytesRead >= totalBytes) ||
                                    bytesRead % (64 * 1024) < n
                                ) {
                                    emitted = true
                                    onProgress?.invoke(bytesRead, totalBytes, currentUrl)
                                }
                            }
                        }
                    }
                    if (streamingExceeded) {
                        target.deleteQuietly()
                        val szCode = if (isZip) RemoteSecurityCode.ZIP_SIZE_EXCEEDED_25MIB
                                     else RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB
                        throw wrapUserVisible(context, szCode, cause = null, formatArg1 = null)
                    }
                    if (isZip) {
                        val head2 = first2Holder.bytes.let { h ->
                            if (h != null && h.size >= 2) h
                            else target.let { f -> if (f.length() >= 2) f.readBytes().copyOf(2) else null }
                        }
                        RemoteUrlSecurity.checkZipMagic(head2)
                    }
                    onProgress?.invoke(bytesRead, totalBytes, currentUrl)
                    Timber.i(
                        "$TAG: 下载完成 url=$currentUrl file=${target.absolutePath} size=$bytesRead expected=$totalBytes hosts=$seenHosts"
                    )
                    return@withContext Result.success(target)
                } // end use(response)
                if (!redirected) {
                    // use block 正常返回且未被重定向：理论上 while(true) 内所有非 redirect 路径
                    // 都通过 throw / return@withContext 终止；这里只是 unreachable 兜底。
                    error("RemoteFileResolver: unreachable non-redirect, non-terminal branch")
                }
            } // end while
            @Suppress("UNREACHABLE_CODE")
            error("RemoteFileResolver: while(true) escaped")
        } catch (se: RemoteFileDownloadSecurityException) {
            target.deleteQuietly()
            Timber.w(se, "$TAG: 安全拦截 code=${se.code} url=$currentUrl")
            Result.failure(se)
        } catch (t: Throwable) {
            target.deleteQuietly()
            Timber.w(t, "$TAG: IO 失败 url=$currentUrl")
            Result.failure(
                wrapUserVisible(
                    context, RemoteSecurityCode.FETCH_IO_ERROR, t,
                    formatArg1 = (t.message ?: t.javaClass.simpleName)
                )
            )
        }
        result
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        // 所有重定向必须手动 follow，防止 OkHttp 跳过后我们不再做 DNS SSRF 校验。
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun resolveLocation(current: String, location: String): URL? {
        if (location.isBlank()) return null
        return runCatching {
            val base = URL(current)
            URL(base, location)
        }.getOrNull()
    }

    /**
     * 把分类异常包装成"用户 UI 上可见"的异常：
     *   - message 直接由 [Context.getString] 注入，保证无硬编码文本、lint HardcodedText=error 通过；
     *   - cause 保留给 Timber 调试。
     *
     * 资源映射严格对应 values/strings.xml & values-en/strings.xml 中声明的
     * `remote_url_security_<snake_case(code)>` 名称。
     */
    private fun wrapUserVisible(
        context: Context,
        code: RemoteSecurityCode,
        cause: Throwable?,
        formatArg1: String?,
        formatArg2: String? = null,
    ): RemoteFileDownloadSecurityException {
        val hook = messageResolverForTests
        val msg = if (hook != null) {
            hook(context, code, formatArg1, formatArg2)
        } else {
            val resId = when (code) {
                RemoteSecurityCode.INVALID_URL_FORMAT -> R.string.remote_url_security_invalid_url_format
                RemoteSecurityCode.NOT_HTTPS -> R.string.remote_url_security_not_https
                RemoteSecurityCode.SSRF_BLOCKED -> R.string.remote_url_security_ssrf_blocked_format
                RemoteSecurityCode.HOST_NOT_IN_ALLOWLIST -> R.string.remote_url_security_host_not_allowed_format
                RemoteSecurityCode.TOO_MANY_REDIRECTS -> R.string.remote_url_security_too_many_redirects
                RemoteSecurityCode.REDIRECT_TO_NON_HTTPS -> R.string.remote_url_security_redirect_to_non_https
                RemoteSecurityCode.CONTENT_TYPE_DISALLOWED -> R.string.remote_url_security_content_type_disallowed_format
                RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB -> R.string.remote_url_security_script_size_exceeded_5mib
                RemoteSecurityCode.ZIP_SIZE_EXCEEDED_25MIB -> R.string.remote_url_security_zip_size_exceeded_25mib
                RemoteSecurityCode.ZIP_MAGIC_MISMATCH -> R.string.remote_url_security_zip_magic_mismatch
                RemoteSecurityCode.HTTP_STATUS_ERROR -> R.string.remote_url_security_http_status_error_format
                RemoteSecurityCode.FETCH_IO_ERROR -> R.string.remote_url_security_fetch_io_error_format
            }
            when {
                formatArg1 != null && formatArg2 != null -> context.getString(resId, formatArg1, formatArg2)
                formatArg1 != null -> context.getString(resId, formatArg1)
                else -> context.getString(resId)
            }
        }
        return RemoteFileDownloadSecurityException(code, msg, cause)
    }

    /** helper：如果异常里没有 host 信息，退回到当前 URL 的 host（便于 SSRF/allowlist 日志）。 */
    private fun Throwable.hostOrNullIfKnown(currentUrl: String): String? =
        runCatching { URL(currentUrl).host }.getOrNull()

    private fun File.deleteQuietly() = runCatching { delete() }
}

/**
 * RemoteFileResolver 内部 helper 集合。
 */
internal object RemoteFileResolverInternals {
    /** 允许的远程文件扩展名（小写）。 */
    val ALLOWED_EXTENSIONS = setOf("js", "zip", "html", "htm")

    fun extractFileName(url: String): String {
        val uri = runCatching { url.toUri() }.getOrNull()
        val path = uri?.path?.takeIf { it.isNotBlank() } ?: url.substringAfter("://")
        val last = path.trimEnd('/').substringAfterLast('/')
        if (last.isBlank() || '.' !in last) {
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
