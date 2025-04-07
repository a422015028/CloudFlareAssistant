package com.muort.upworker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AccountStorage {
    private const val PREF_NAME = "cloudflare_accounts"
    private const val KEY_ACCOUNTS = "accounts"
    private val gson = Gson()

    fun loadAccounts(context: Context): List<Account> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Account>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAccounts(context: Context, accounts: List<Account>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(accounts)
        prefs.edit().putString(KEY_ACCOUNTS, json).apply()
    }
}