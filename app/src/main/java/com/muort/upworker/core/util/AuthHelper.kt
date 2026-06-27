package com.muort.upworker.core.util

import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AuthType

/**
 * Cloudflare API 认证辅助类
 * 支持两种认证方式：
 * 1. API Token (Bearer Token) - Authorization: Bearer xxx
 * 2. Global API Key - X-Auth-Email: xxx, X-Auth-Key: xxx
 */
object AuthHelper {
    
    /**
     * 获取 Bearer Token（用于 Authorization header）
     * 当使用 Global API Key 时返回 null（不使用 Bearer token）
     */
    fun getBearerToken(account: Account): String? {
        return if (account.useGlobalApiKey()) {
            null // Global API Key 不使用 Bearer token
        } else {
            "Bearer ${account.token}"
        }
    }
    
    /**
     * 获取邮箱（用于 X-Auth-Email header）
     * 当使用 API Token 时返回 null
     */
    fun getEmail(account: Account): String? {
        return if (account.useGlobalApiKey()) {
            account.email
        } else {
            null // API Token 不需要邮箱
        }
    }
    
    /**
     * 获取 Global API Key（用于 X-Auth-Key header）
     * 当使用 API Token 时返回 null
     */
    fun getGlobalApiKey(account: Account): String? {
        return if (account.useGlobalApiKey()) {
            account.globalApiKey
        } else {
            null // API Token 不需要 Global API Key
        }
    }
    
    /**
     * 获取认证 headers Map
     * 可用于 OkHttp Interceptor 或 Retrofit @HeaderMap
     */
    fun getAuthHeaders(account: Account): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (account.useGlobalApiKey()) {
            account.email?.let { headers["X-Auth-Email"] = it }
            account.globalApiKey?.let { headers["X-Auth-Key"] = it }
        } else {
            headers["Authorization"] = "Bearer ${account.token}"
        }
        return headers
    }
    
    /**
     * 获取认证类型的显示名称（用于 UI）
     */
    fun getAuthTypeDisplayName(account: Account): String {
        return when (account.getAuthTypeEnum()) {
            AuthType.TOKEN -> "API Token"
            AuthType.GLOBAL_API_KEY -> "Global API Key"
        }
    }
    
    /**
     * 检查账号认证配置是否完整
     */
    fun validateAuthConfig(account: Account): ValidationResult {
        return when (account.getAuthTypeEnum()) {
            AuthType.TOKEN -> {
                if (account.token.isBlank()) {
                    ValidationResult.Error("API Token 不能为空")
                } else {
                    ValidationResult.Success
                }
            }
            AuthType.GLOBAL_API_KEY -> {
                val errors = mutableListOf<String>()
                if (account.email?.isBlank() != false) {
                    errors.add("邮箱不能为空")
                }
                if (account.globalApiKey?.isBlank() != false) {
                    errors.add("Global API Key 不能为空")
                }
                if (errors.isEmpty()) {
                    ValidationResult.Success
                } else {
                    ValidationResult.Error(errors.joinToString("\n"))
                }
            }
        }
    }
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}