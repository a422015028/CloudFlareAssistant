package com.muort.upworker.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// ==================== WebDAV Configuration ====================

@Entity(tableName = "webdav_config")
data class WebDavConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val username: String,
    val password: String,
    val backupPath: String = "/CloudFlareAssistant/",
    val autoBackup: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ==================== Backup Data ====================

data class AccountBackup(
    @SerializedName("version") val version: String = "1.1",
    @SerializedName("backupDate") val backupDate: Long = System.currentTimeMillis(),
    @SerializedName("accounts") val accounts: List<AccountData>
)

data class AccountData(
    @SerializedName("name") val name: String,
    @SerializedName("accountId") val accountId: String,
    @SerializedName("token") val token: String,
    @SerializedName("zoneId") val zoneId: String? = null,
    @SerializedName("isDefault") val isDefault: Boolean = false,
    @SerializedName("r2AccessKeyId") val r2AccessKeyId: String? = null,
    @SerializedName("r2SecretAccessKey") val r2SecretAccessKey: String? = null,
    @SerializedName("createdAt") val createdAt: Long,
    @SerializedName("updatedAt") val updatedAt: Long,
    @SerializedName("zones") val zones: List<Zone>? = null
)

// Convert between Account and AccountData
fun Account.toAccountData() = AccountData(
    name = name,
    accountId = accountId,
    token = token,
    zoneId = zoneId,
    isDefault = isDefault,
    r2AccessKeyId = r2AccessKeyId,
    r2SecretAccessKey = r2SecretAccessKey,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AccountData.toAccount() = Account(
    id = 0, // Auto-generate new ID
    name = name,
    accountId = accountId,
    token = token,
    zoneId = zoneId,
    isDefault = isDefault,
    r2AccessKeyId = r2AccessKeyId,
    r2SecretAccessKey = r2SecretAccessKey,
    createdAt = createdAt,
    updatedAt = updatedAt
)
