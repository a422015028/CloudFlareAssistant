package com.muort.upworker.core.repository

import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountInfo
import com.muort.upworker.core.model.ApiToken
import com.muort.upworker.core.model.CloudflareUser
import com.muort.upworker.core.model.CreateTokenRequest
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.PermissionGroupRef
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.ScopeCategory
import com.muort.upworker.core.model.TokenPolicy
import com.muort.upworker.core.model.ZoneInfo
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API Token 权限管理仓库
 * 负责权限组获取、当前 Token 校验/详情、以及创建自定义权限 Token
 */
@Singleton
class ApiTokenRepository @Inject constructor(
    private val api: CloudFlareApi
) {

    private fun auth(account: Account) = Triple(
        AuthHelper.getBearerToken(account),
        AuthHelper.getEmail(account),
        AuthHelper.getGlobalApiKey(account)
    )

    /** 获取所有可用权限组 */
    suspend fun getPermissionGroups(account: Account): Resource<List<PermissionGroup>> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.listPermissionGroups(token, email, key)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "获取权限组失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Timber.e(e, "getPermissionGroups error")
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /** 校验当前 Token（返回 id/status） */
    suspend fun verifyToken(account: Account): Resource<ApiToken> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.verifyToken(token, email, key)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "Token 校验失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Timber.e(e, "verifyToken error")
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /** 获取指定 Token 详情（含策略） */
    suspend fun getTokenDetail(account: Account, tokenId: String): Resource<ApiToken> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.getTokenDetail(token, email, key, tokenId)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "获取 Token 详情失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Timber.e(e, "getTokenDetail error")
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /** 获取账号列表（用于资源范围选择） */
    suspend fun listAccounts(account: Account): Resource<List<AccountInfo>> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.listAccounts(token, email, key)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "获取账号列表失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /** 获取域名列表（用于资源范围选择） */
    suspend fun listZones(account: Account): Resource<List<ZoneInfo>> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.listZones(token, email, key)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "获取域名列表失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /** 获取当前用户（用于 user 作用域策略） */
    suspend fun getCurrentUser(account: Account): Resource<CloudflareUser> {
        return try {
            val (token, email, key) = auth(account)
            val response = api.getCurrentUser(token, email, key)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "获取用户信息失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), account))
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "网络错误")
        }
    }

    /**
     * 创建自定义权限 API Token
     * @param creator 用于创建新 Token 的凭据账号（需具有 "API Tokens Write" 权限）
     * @param name 新 Token 名称
     * @param selectedGroups 已选权限组（按作用域分类）
     * @param accountScope 账号资源范围选择
     * @param zoneScope 域名资源范围选择
     * @param expiresOn 可选过期时间（ISO8601 UTC，如 "2026-08-01T00:00:00Z"）；null 表示永不过期
     */
    suspend fun createApiToken(
        creator: Account,
        name: String,
        selectedGroups: Map<ScopeCategory, List<PermissionGroup>>,
        accountScope: AccountResourceScope,
        zoneScope: ZoneResourceScope,
        expiresOn: String?
    ): Resource<ApiToken> {
        return try {
            // 需要 user tag 才能构建 user 作用域策略
            var userTag: String? = null
            if (selectedGroups.containsKey(ScopeCategory.USER) &&
                selectedGroups[ScopeCategory.USER].orEmpty().isNotEmpty()
            ) {
                when (val u = getCurrentUser(creator)) {
                    is Resource.Success -> userTag = u.data.id
                    is Resource.Error -> {
                        return Resource.Error("获取用户信息失败（user 作用域策略所需）: ${u.message}")
                    }
                    is Resource.Loading -> {}
                }
                if (userTag.isNullOrBlank()) {
                    return Resource.Error("无法获取用户标识，无法创建 user 作用域策略")
                }
            }

            val policies = buildPolicies(selectedGroups, accountScope, zoneScope, userTag)
            if (policies.isEmpty()) {
                return Resource.Error("请至少选择一个权限")
            }

            val request = CreateTokenRequest(
                name = name,
                policies = policies,
                notBefore = null,
                expiresOn = expiresOn
            )

            val (token, email, key) = auth(creator)
            val response = api.createApiToken(token, email, key, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    Resource.Error(body?.errors?.firstOrNull()?.message ?: "创建 Token 失败")
                }
            } else {
                Resource.Error(errorBody(response.code(), response.message(), creator))
            }
        } catch (e: Exception) {
            Timber.e(e, "createApiToken error")
            Resource.Error(e.message ?: "创建 Token 失败")
        }
    }

    /**
     * 根据所选权限组与资源范围构建策略列表（每种作用域一个策略）
     */
    private fun buildPolicies(
        selected: Map<ScopeCategory, List<PermissionGroup>>,
        accountScope: AccountResourceScope,
        zoneScope: ZoneResourceScope,
        userTag: String?
    ): List<TokenPolicy> {
        val policies = mutableListOf<TokenPolicy>()

        // Zone 作用域策略
        val zoneGroups = selected[ScopeCategory.ZONE].orEmpty()
        if (zoneGroups.isNotEmpty()) {
            policies.add(
                TokenPolicy(
                    effect = "allow",
                    resources = zoneScope.toResources(),
                    permissionGroups = zoneGroups.map { PermissionGroupRef(it.id) }
                )
            )
        }

        // Account 作用域策略
        val accountGroups = selected[ScopeCategory.ACCOUNT].orEmpty()
        if (accountGroups.isNotEmpty()) {
            policies.add(
                TokenPolicy(
                    effect = "allow",
                    resources = accountScope.toResources(),
                    permissionGroups = accountGroups.map { PermissionGroupRef(it.id) }
                )
            )
        }

        // User 作用域策略
        val userGroups = selected[ScopeCategory.USER].orEmpty()
        if (userGroups.isNotEmpty() && !userTag.isNullOrBlank()) {
            policies.add(
                TokenPolicy(
                    effect = "allow",
                    resources = mapOf("com.cloudflare.api.user.$userTag" to "*"),
                    permissionGroups = userGroups.map { PermissionGroupRef(it.id) }
                )
            )
        }

        return policies
    }

    /** 构造友好的错误信息（针对常见鉴权/权限错误给出提示） */
    private fun errorBody(code: Int, message: String, account: Account): String {
        return when (code) {
            401, 403 -> "无权限或凭据无效($code)。此操作需要当前 Token 具有 \"API Tokens Read/Write\" 权限。" +
                    if (account.getAuthTypeEnum() == com.muort.upworker.core.model.AuthType.GLOBAL_API_KEY)
                        "（当前使用 Global API Key）" else ""
            else -> "HTTP $code: $message"
        }
    }

    // ==================== 资源范围 ====================

    /** 账号资源范围 */
    sealed class AccountResourceScope {
        abstract fun toResources(): Map<String, Any>
        /** 所有账号 */
        data object AllAccounts : AccountResourceScope() {
            override fun toResources() = mapOf<String, Any>("com.cloudflare.api.account.*" to "*")
        }
        /** 指定账号 */
        data class SpecificAccount(val accountId: String) : AccountResourceScope() {
            override fun toResources() = mapOf<String, Any>("com.cloudflare.api.account.$accountId" to "*")
        }
    }

    /** 域名资源范围 */
    sealed class ZoneResourceScope {
        abstract fun toResources(): Map<String, Any>
        /** 所有账号的所有 Zone */
        data object AllZones : ZoneResourceScope() {
            override fun toResources() = mapOf<String, Any>("com.cloudflare.api.account.zone.*" to "*")
        }
        /** 指定 Zone */
        data class SpecificZone(val zoneId: String) : ZoneResourceScope() {
            override fun toResources() = mapOf<String, Any>("com.cloudflare.api.account.zone.$zoneId" to "*")
        }
        /** 指定账号下的所有 Zone（嵌套资源） */
        data class AllZonesInAccount(val accountId: String) : ZoneResourceScope() {
            override fun toResources(): Map<String, Any> = mapOf(
                "com.cloudflare.api.account.$accountId" to mapOf("com.cloudflare.api.account.zone.*" to "*")
            )
        }
    }

    companion object {
        /**
         * 根据权限组的 scopes 字段判断其作用域分类
         * - 含 "user" → User
         * - 含 "zone" → Zone
         * - 含 "account" → Account
         */
        fun categorize(scopes: List<String>?): ScopeCategory {
            val joined = (scopes ?: emptyList()).joinToString(",").lowercase()
            return when {
                joined.contains("user") -> ScopeCategory.USER
                joined.contains("zone") -> ScopeCategory.ZONE
                joined.contains("account") -> ScopeCategory.ACCOUNT
                else -> ScopeCategory.ACCOUNT
            }
        }
    }
}
