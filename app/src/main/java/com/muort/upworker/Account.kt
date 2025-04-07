package com.muort.upworker

import com.google.gson.annotations.SerializedName

data class Account(
    @SerializedName("a") var name: String,        // 账号显示名称
    @SerializedName("b") var accountId: String,   // Cloudflare 帐号 ID
    @SerializedName("c") var token: String,       // API Token
    @SerializedName("d") var zoneId: String? = null       // Zone ID
) {
    override fun toString(): String = name
}