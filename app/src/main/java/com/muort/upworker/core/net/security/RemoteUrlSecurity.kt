package com.muort.upworker.core.net.security

import okhttp3.Dns
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException

/**
 * 远程文件下载 Wn 管线的安全校验集合（纯函数，便于 JVM 单元测试）。
 *
 * 覆盖场景：
 *   1. 协议：仅 HTTPS
 *   2. 域名 allowlist（可为 null 代表允许所有，对齐 cf-manager WORKER_DEPLOY_URL_ALLOWLIST）
 *   3. SSRF 私网拦截：V4 + V6 + V4-mapped-V6
 *   4. Content-Type 白名单（脚本 / zip 两类）
 *   5. 大小（脚本 5 MiB / zip 25 MiB）
 *   6. ZIP 魔数校验（首 2 字节 == 0x50 0x4B）
 */
object RemoteUrlSecurity {

    const val MAX_REDIRECTS = 5
    const val SCRIPT_MAX_BYTES = 5L * 1024 * 1024       // 5 MiB
    const val ZIP_MAX_BYTES = 25L * 1024 * 1024         // 25 MiB

    // ----------------------------------------------------------------------
    // 1. 协议 & 格式
    // ----------------------------------------------------------------------

    @JvmStatic
    fun parseUrlOrThrow(urlString: String): URL {
        val url = try {
            URL(urlString)
        } catch (t: Throwable) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.INVALID_URL_FORMAT,
                // 具体 message 由上层用 context.getString(R.string.remote_url_security_invalid_url_format) 注入；
                // 这里为了"异常被打印到 logcat 时也可读"，只使用英文中性描述（非用户 UI 上可见），
                // UI 层统一通过 code 映射到 R.string.* 展示给用户。
                "invalid URL",
                t
            )
        }
        if (url.protocol?.equals("https", ignoreCase = true) != true) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.NOT_HTTPS,
                "only HTTPS allowed"
            )
        }
        return url
    }

    // ----------------------------------------------------------------------
    // 2. Allowlist
    // ----------------------------------------------------------------------

    /** 若 allowlistSet 为 null/空 则"允许所有"；否则 host 必须在集合内（忽略大小写）。 */
    @JvmStatic
    fun checkHostAllowlist(host: String, allowlistSet: Set<String>?) {
        if (allowlistSet.isNullOrEmpty()) return
        if (host.lowercase() !in allowlistSet.map { it.lowercase() }) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.HOST_NOT_IN_ALLOWLIST,
                "host $host not in allowlist"
            )
        }
    }

    @JvmStatic
    fun parseAllowlist(csv: String?): Set<String>? =
        csv?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            ?.takeUnless { it.isEmpty() }

    // ----------------------------------------------------------------------
    // 3. SSRF 私网拦截
    // ----------------------------------------------------------------------

    /**
     * 解析 host，逐条 IP 检查私网段；任何一条命中 → 抛 SSRF_BLOCKED。
     *
     * @throws RemoteFileDownloadSecurityException SSRF_BLOCKED / INVALID_URL_FORMAT (no host)
     * @throws UnknownHostException 当 DNS 解析失败（保留未分类，由上层统一包装为 FETCH_IO_ERROR）
     */
    @JvmStatic
    @Throws(UnknownHostException::class)
    fun dnsResolveAndCheckPrivate(host: String?, dns: Dns = Dns.SYSTEM): List<InetAddress> {
        if (host.isNullOrBlank()) throw RemoteFileDownloadSecurityException(
            RemoteSecurityCode.INVALID_URL_FORMAT,
            "missing host"
        )
        val addrs: List<InetAddress> = try {
            dns.lookup(host)
        } catch (uh: UnknownHostException) {
            throw uh
        } catch (t: Throwable) {
            throw UnknownHostException(host).apply { initCause(t) }
        }
        if (addrs.isEmpty()) throw UnknownHostException(host)
        for (a in addrs) {
            if (isPrivateAddress(a)) {
                Timber.w("RemoteUrlSecurity: SSRF blocked host=$host addr=$a")
                throw RemoteFileDownloadSecurityException(
                    RemoteSecurityCode.SSRF_BLOCKED,
                    "SSRF blocked $host → $a"
                )
            }
        }
        return addrs
    }

    @JvmStatic
    fun isPrivateAddress(addr: InetAddress): Boolean = when (addr) {
        is Inet4Address -> isPrivateIpV4(addr.address)
        is Inet6Address -> {
            if (addr.isIPv4CompatibleAddress ||
                // JDK: Inet6Address.getByAddress 会将 ::ffff:a.b.c.d 标记为 IPv4-Mapped
                addr.address.copyOfRange(0, 10).contentEquals(ByteArray(10)) &&
                addr.address[10] == (-1).toByte() && addr.address[11] == (-1).toByte()
            ) {
                // 映射到 IPv4，用 IPv4 规则重查
                val v4 = addr.address.copyOfRange(12, 16)
                isPrivateIpV4(v4)
            } else {
                isPrivateIpV6(addr.address)
            }
        }
        else -> false
    }

    @JvmStatic
    fun isPrivateIpV4(b: ByteArray): Boolean {
        require(b.size == 4)
        val x = b[0].toInt() and 0xFF
        val y = b[1].toInt() and 0xFF
        return when {
            // 0.0.0.0/8 "this" network
            x == 0 -> true
            // 10.0.0.0/8
            x == 10 -> true
            // 127.0.0.0/8 loopback
            x == 127 -> true
            // 169.254.0.0/16 link-local
            x == 169 && y == 254 -> true
            // 172.16.0.0/12
            x == 172 && y in 16..31 -> true
            // 192.168.0.0/16
            x == 192 && y == 168 -> true
            else -> false
        }
    }

    @JvmStatic
    fun isPrivateIpV6(b: ByteArray): Boolean {
        require(b.size == 16)
        // :: (unspecified)
        if (b.all { it == 0.toByte() }) return true
        // ::1 (loopback)
        if (b.copyOfRange(0, 15).all { it == 0.toByte() } && b[15] == 1.toByte()) return true
        // fc00::/7 unique local (fc + fd prefix high 7 bits = 0xfc)
        val hi = b[0].toInt() and 0xFE
        if (hi == 0xFC) return true
        // fe80::/10 link-local (high 10 bits = 0xFE80)
        if ((b[0].toInt() and 0xFF) == 0xFE && (b[1].toInt() and 0xC0) == 0x80) return true
        return false
    }

    // ----------------------------------------------------------------------
    // 4. Content-Type
    // ----------------------------------------------------------------------

    /**
     * 脚本 Content-Type 白名单：
     *   - text/ 前缀（text/plain、text/html 等）
     *   - application/javascript, application/ecmascript, application/x-javascript, application/json
     *   - application/octet-stream（兼容 CDN 错误配置）
     */
    @JvmStatic
    fun checkContentTypeScript(contentTypeRaw: String?) {
        val ct = (contentTypeRaw ?: "").trim().lowercase()
        if (ct.isEmpty()) return   // 缺失时放行（由 5MiB + 扩展名双重兜底）
        if (ct.startsWith("text/")) return
        val primary = ct.substringBefore(';').trim()
        val allowed = setOf(
            "application/javascript",
            "application/ecmascript",
            "application/x-javascript",
            "application/json",
            "application/octet-stream",
            "binary/octet-stream"    // 非标准遗留；cf.muort.com 等站点实际下发
        )
        if (primary !in allowed) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.CONTENT_TYPE_DISALLOWED,
                "disallowed Content-Type=$ct"
            )
        }
    }

    /** ZIP 响应 Content-Type 白名单：application/zip / x-zip-compressed / octet-stream (+legacy binary/) */
    @JvmStatic
    fun checkContentTypeZip(contentTypeRaw: String?) {
        val ct = (contentTypeRaw ?: "").trim().lowercase()
        if (ct.isEmpty()) return
        val primary = ct.substringBefore(';').trim()
        val allowed = setOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
            "application/x-zip",
            "binary/octet-stream"   // 非标准遗留；cf.muort.com 等站点实际下发
        )
        if (primary !in allowed && !primary.startsWith("multipart/")) {
            // 不直接允许 text/html，防止把 429 HTML 页面当 zip 下载
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.CONTENT_TYPE_DISALLOWED,
                "disallowed Content-Type for ZIP=$ct"
            )
        }
    }

    // ----------------------------------------------------------------------
    // 5. 大小
    // ----------------------------------------------------------------------

    @JvmStatic
    fun checkScriptSize(bytesRead: Long) {
        if (bytesRead > SCRIPT_MAX_BYTES) throw RemoteFileDownloadSecurityException(
            RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB,
            "script size $bytesRead > $SCRIPT_MAX_BYTES"
        )
    }

    @JvmStatic
    fun checkZipSize(bytesRead: Long) {
        if (bytesRead > ZIP_MAX_BYTES) throw RemoteFileDownloadSecurityException(
            RemoteSecurityCode.ZIP_SIZE_EXCEEDED_25MIB,
            "zip size $bytesRead > $ZIP_MAX_BYTES"
        )
    }

    // ----------------------------------------------------------------------
    // 6. ZIP 魔数
    // ----------------------------------------------------------------------

    @JvmStatic
    fun checkZipMagic(first2Bytes: ByteArray?) {
        if (first2Bytes == null || first2Bytes.size < 2) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.ZIP_MAGIC_MISMATCH,
                "zip payload too short"
            )
        }
        if (first2Bytes[0] != 0x50.toByte() || first2Bytes[1] != 0x4B.toByte()) {
            throw RemoteFileDownloadSecurityException(
                RemoteSecurityCode.ZIP_MAGIC_MISMATCH,
                "missing ZIP magic (PK)"
            )
        }
    }
}
