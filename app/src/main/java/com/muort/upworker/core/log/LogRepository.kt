package com.muort.upworker.core.log

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LogRepository {
    private const val PREF_NAME = "log_prefs"
    private const val KEY_LOG = "log_content"
    private const val KEY_ENABLE = "log_enable"
    private lateinit var prefs: SharedPreferences
    private val _logFlow = MutableStateFlow("")
    private val _enableFlow = MutableStateFlow(true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _logFlow.value = prefs.getString(KEY_LOG, "") ?: ""
        _enableFlow.value = prefs.getBoolean(KEY_ENABLE, true)
    }

    fun appendLog(log: String) {
        if (!_enableFlow.value) return
        val newLog = (_logFlow.value + log).takeLast(100_000) // 限制最大长度
        _logFlow.value = newLog
        prefs.edit().putString(KEY_LOG, newLog).apply()
    }

    fun clearLog() {
        _logFlow.value = ""
        prefs.edit().putString(KEY_LOG, "").apply()
    }

    fun setEnable(enable: Boolean) {
        _enableFlow.value = enable
        prefs.edit().putBoolean(KEY_ENABLE, enable).apply()
    }

    fun getLogFlow(): StateFlow<String> = _logFlow
    fun getEnableFlow(): StateFlow<Boolean> = _enableFlow
}
