package com.muort.upworker.core.repository

import timber.log.Timber
import java.util.Base64 as JvmBase64

/**
 * Cloudflare Pages 上传 JWT 的**无签名过期检查工具**（对应 cf-manager Mo/Vn 算法）。
 *
 * ⚠ 这里**不做 JWT 签名校验**：因为 JWT 由 Cloudflare 官方签发，签名校验只在服务器端执行。
 * 客户端只解析 `exp` (Unix epoch second) 用于"在过期前 30s 自动刷新"，避免 401 重试浪费带宽。
 *
 * 解析失败（格式非法 / exp 不是 Long 等）→ 返回 null 或 false，并输出一条
 * `R.string.repo_pages_jwt_invalid_format_warn` 调用方日志；不会中断主流程（在首次 401 时会自动 force 刷新）。
 */
object PagesJwtRefresher {

    private const val NEAR_EXPIRY_WINDOW_SECONDS = 30L

    /** 解析 JWT payload 中的 exp (unix epoch second)；非法返回 null，纯函数可单元测试。 */
    fun parseExp(jwt: String): Long? {
        val parts = jwt.split('.')
        if (parts.size != 3) {
            Timber.w("PagesJwtRefresher: JWT 段数=${parts.size} 非法")
            return null
        }
        val payload = try {
            val p = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { padded(it) }
            // Android unit test (JVM) 用 java.util.Base64；Android runtime 也是同 API 26+ 可用
            // 由于 minSdk=26 无需 fallback android.util.Base64。
            JvmBase64.getDecoder().decode(p)
        } catch (t: Throwable) {
            Timber.w(t, "PagesJwtRefresher: payload base64 解码失败")
            return null
        }
        // 用简单字符串搜索 "exp":<digits>，避免 Gson 依赖（com.google.gson 是项目已有依赖，这里直接用）。
        val json = try {
            String(payload, charset = Charsets.UTF_8)
        } catch (t: Throwable) {
            Timber.w(t, "PagesJwtRefresher: payload bytes -> UTF-8 失败")
            return null
        }
        // 用正则 "exp"\s*:\s*(\d+)，对 JWT payload 有符号整数/十进制足够，免 Gson 反射开销。
        val m = EXP_REGEX.find(json) ?: run {
            Timber.w("PagesJwtRefresher: payload 无 exp 字段")
            return null
        }
        return try {
            m.groupValues[1].toLong()
        } catch (nfe: NumberFormatException) {
            Timber.w(nfe, "PagesJwtRefresher: exp 非十进制 Long")
            null
        }
    }

    /** JWT 是否在 <30s 内过期；解析失败返回 false（交由首次 401 触发刷新）。 */
    fun isNearExpiry(jwt: String, nowSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        val exp = parseExp(jwt) ?: return false
        return (exp - nowSec) <= NEAR_EXPIRY_WINDOW_SECONDS
    }

    private fun padded(s: String): String {
        val rem = s.length % 4
        if (rem == 0) return s
        return s + "=".repeat(4 - rem)
    }

    private val EXP_REGEX = Regex(""""exp"\s*:\s*(\d+)""")
}
