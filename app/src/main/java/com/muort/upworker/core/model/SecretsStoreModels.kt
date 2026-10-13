package com.muort.upworker.core.model

import com.google.gson.annotations.SerializedName

// ==================== Secrets Store ====================
// 文档: https://developers.cloudflare.com/api/resources/secrets_store/

/**
 * 密钥库（Secrets Store）
 * GET /accounts/{account_id}/secrets_store/stores
 */
data class SecretStore(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("created") val created: String? = null,
    @SerializedName("modified") val modified: String? = null,
    @SerializedName("account_id") val accountId: String? = null
)

/** 创建密钥库请求体 */
data class SecretStoreCreateRequest(
    @SerializedName("name") val name: String
)

/**
 * 库中的密钥。value 为只写字段，任何读接口都不会返回。
 * status: pending / active / deleted
 * scopes: workers / ai_gateway / dex / access / containers / websearch
 */
data class StoredSecret(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String? = null,
    @SerializedName("store_id") val storeId: String? = null,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("scopes") val scopes: List<String>? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("modified") val modified: String? = null
)

/**
 * 创建密钥请求体元素。接口接收 JSON 数组，支持一次创建多个。
 * value 最大 64 KiB（65,536 字节）。
 */
data class StoredSecretCreateBody(
    @SerializedName("name") val name: String,
    @SerializedName("scopes") val scopes: List<String>,
    @SerializedName("value") val value: String,
    @SerializedName("comment") val comment: String? = null
)

/**
 * 编辑密钥（PATCH）。name 不可修改；value 留空传输时表示不覆盖。
 */
data class StoredSecretEditBody(
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("scopes") val scopes: List<String>? = null,
    @SerializedName("value") val value: String? = null
)

/**
 * 复制密钥（POST .../duplicate）：服务端保留原 value，
 * 必须提供新的 name 与 scopes。
 */
data class StoredSecretDuplicateBody(
    @SerializedName("name") val name: String,
    @SerializedName("scopes") val scopes: List<String>,
    @SerializedName("comment") val comment: String? = null
)

/** 账户密钥用量配额 GET .../secrets_store/quota */
data class SecretsStoreQuotaResult(
    @SerializedName("secrets") val secrets: SecretQuota? = null
)

data class SecretQuota(
    @SerializedName("quota") val quota: Int = 0,
    @SerializedName("usage") val usage: Int = 0
)

/** Secrets Store 支持的服务范围（与 API 枚举保持一致） */
object SecretScope {
    const val WORKERS = "workers"
    const val AI_GATEWAY = "ai_gateway"
    const val DEX = "dex"
    const val ACCESS = "access"
    const val CONTAINERS = "containers"
    const val WEBSEARCH = "websearch"

    val ALL: List<String> = listOf(WORKERS, AI_GATEWAY, DEX, ACCESS, CONTAINERS, WEBSEARCH)

    /** 密钥值大小上限：64 KiB */
    const val MAX_VALUE_BYTES = 65_536
}
