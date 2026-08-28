package com.muort.upworker.core.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言切换工具类
 *
 * 支持三种模式：
 * - 跟随系统（默认）
 * - 简体中文
 * - English
 *
 * 使用 AndroidX 的 Appcompat 原生 API 实现，兼容 Android 7.0+
 */
object LocaleHelper {

    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LANGUAGE = "language"

    const val LANGUAGE_FOLLOW_SYSTEM = "follow_system"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh-CN"
    const val LANGUAGE_ENGLISH = "en"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取当前保存的语言设置
     */
    fun getLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_LANGUAGE, LANGUAGE_FOLLOW_SYSTEM) ?: LANGUAGE_FOLLOW_SYSTEM
    }

    /**
     * 设置应用语言并保存
     *
     * 注意：调用后需要 recreate Activity 才能生效
     */
    fun setLanguage(context: Context, language: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * 应用保存的语言设置到 Context
     *
     * 在 Application.attachBaseContext 和 Activity.attachBaseContext 中调用
     */
    fun applyLocale(context: Context): Context {
        val language = getLanguage(context)
        val locale = when (language) {
            LANGUAGE_SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            else -> null // 跟随系统
        }

        return if (locale != null) {
            updateResources(context, locale)
        } else {
            context
        }
    }

    /**
     * 更新资源配置的语言
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        val config = context.resources.configuration
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * 获取当前生效的 Locale
     */
    fun getCurrentLocale(context: Context): Locale {
        val language = getLanguage(context)
        return when (language) {
            LANGUAGE_SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    LocaleList.getDefault()[0]
                } else {
                    @Suppress("DEPRECATION")
                    Locale.getDefault()
                }
            }
        }
    }
}
