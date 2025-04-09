package com.muort.upworker

import android.content.Context
import android.content.SharedPreferences

object WebDavConfig {

    private const val PREFS_NAME = "webdav_prefs"
    private const val KEY_URL = "url"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 保存 WebDAV 配置
    fun save(context: Context, url: String, username: String, password: String) {
        val prefs = getSharedPreferences(context)
        val editor = prefs.edit()
        editor.putString(KEY_URL, url)
        editor.putString(KEY_USERNAME, username)
        editor.putString(KEY_PASSWORD, password)
        editor.apply()
    }

    // 加载 WebDAV 配置
    fun load(context: Context): Triple<String?, String?, String?> {
        val prefs = getSharedPreferences(context)
        val url = prefs.getString(KEY_URL, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        return Triple(url, username, password)
    }
}