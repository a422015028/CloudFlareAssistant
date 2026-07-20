package com.muort.upworker.core.model

import com.google.gson.annotations.SerializedName

/**
 * Cloudflare API Token 相关数据模型
 * 文档: https://developers.cloudflare.com/fundamentals/api/how-to/create-via-api/
 */

// ==================== Permission Groups ====================

/**
 * 权限组（来自 GET /user/tokens/permission_groups）
 * name 仅为展示用（易变），创建 Token 时使用 id。
 */
data class PermissionGroup(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("scopes") val scopes: List<String>? = null
)

/**
 * 策略中对权限组的引用（创建/更新 Token 时只需 id）
 */
data class PermissionGroupRef(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null
)

/**
 * 权限作用域分类（用于 UI 分组展示与构建策略 resources）
 */
enum class ScopeCategory { USER, ACCOUNT, ZONE }

// ==================== Token Policy & Request ====================

/**
 * Token 访问策略
 * - resources: 资源键值映射。值通常为 "*"（String），
 *   或 "账号内全部 Zone" 时为嵌套 Map: {"com.cloudflare.api.account.zone.*":"*"}
 * - 使用 Map<String, Any> 以兼容 Gson 的混合类型序列化/反序列化
 */
data class TokenPolicy(
    @SerializedName("effect") val effect: String = "allow",
    @SerializedName("resources") val resources: Map<String, Any>,
    @SerializedName("permission_groups") val permissionGroups: List<PermissionGroupRef>
)

/**
 * 创建 API Token 请求体（POST /user/tokens）
 */
data class CreateTokenRequest(
    @SerializedName("name") val name: String,
    @SerializedName("policies") val policies: List<TokenPolicy>,
    @SerializedName("not_before") val notBefore: String? = null,
    @SerializedName("expires_on") val expiresOn: String? = null
)

// ==================== Token Object (response) ====================

/**
 * API Token 对象（创建/列表/详情/verify 响应）
 * value: Token 密钥，仅在创建时返回一次
 */
data class ApiToken(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("policies") val policies: List<TokenPolicy>? = null,
    @SerializedName("value") val value: String? = null,
    @SerializedName("not_before") val notBefore: String? = null,
    @SerializedName("expires_on") val expiresOn: String? = null,
    @SerializedName("issued_on") val issuedOn: String? = null,
    @SerializedName("modified_on") val modifiedOn: String? = null
)

/**
 * 当前用户信息（GET /user，用于获取 USER_TAG 构建 user 作用域策略）
 */
data class CloudflareUser(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null
)
