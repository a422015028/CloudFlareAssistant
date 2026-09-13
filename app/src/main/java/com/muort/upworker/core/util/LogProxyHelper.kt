package com.muort.upworker.core.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 实时日志代理域名工具类
 *
 * 用于解决国内直连 wss://tail.developers.workers.dev 不稳定的问题。
 * 用户可配置一个 Cloudflare Worker 反向代理的自定义域名，
 * Worker/Pages 实时日志连接时自动将 host 替换为该代理域名。
 *
 * 代理 Worker 部署方式参见研究文档：
 * 只需将请求转发到 https://tail.developers.workers.dev 并透传
 * Sec-WebSocket-Protocol: trace-v1 即可。
 */
object LogProxyHelper {

    private const val PREFS_NAME = "log_proxy_prefs"
    private const val KEY_PROXY_DOMAIN = "proxy_domain"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取配置的代理域名，未配置时返回空字符串
     */
    fun getProxyDomain(context: Context): String {
        return getPrefs(context).getString(KEY_PROXY_DOMAIN, "") ?: ""
    }

    /**
     * 保存代理域名，自动去除首尾空白
     */
    fun setProxyDomain(context: Context, domain: String) {
        getPrefs(context).edit().putString(KEY_PROXY_DOMAIN, domain.trim()).apply()
    }

    /**
     * 如果配置了代理域名，将 wss URL 的 host 替换为代理域名。
     *
     * 例如：
     *   wss://tail.developers.workers.dev/abc123
     *   → wss://tail.example.com/abc123
     *
     * 未配置代理域名、URL 格式异常时原样返回。
     */
    fun applyProxy(context: Context, wssUrl: String): String {
        val proxyDomain = getProxyDomain(context).trim()
        if (proxyDomain.isEmpty()) return wssUrl

        val prefix = "wss://"
        if (!wssUrl.startsWith(prefix)) return wssUrl

        val afterPrefix = wssUrl.substring(prefix.length)
        val slashIndex = afterPrefix.indexOf('/')
        val pathAndQuery = if (slashIndex >= 0) afterPrefix.substring(slashIndex) else ""
        return "$prefix$proxyDomain$pathAndQuery"
    }
}
