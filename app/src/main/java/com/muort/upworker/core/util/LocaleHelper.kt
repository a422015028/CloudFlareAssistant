package com.muort.upworker.core.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
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
     * 在 Application.attachBaseContext 和 Activity.attachBaseContext 中调用。
     * 三种模式都显式调用 updateResources：避免用户"英文→跟随系统"切换时，
     * Application 进程的 Resources Locale 仍被上一次 setLocale 缓存为 English，
     * 造成回到中文手机系统却仍然显示英文的问题。
     */
    fun applyLocale(context: Context): Context {
        val desiredLocale = resolveDesiredLocale(context)
        return updateResources(context, desiredLocale)
    }

    /**
     * 根据用户偏好解析最终希望生效的 Locale（本项目仅支持简体中文 + English，
     * 其他系统语言统一走默认 values = 简体中文，避免展示翻译缺失的混合界面）。
     */
    private fun resolveDesiredLocale(context: Context): Locale {
        return when (getLanguage(context)) {
            LANGUAGE_SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_ENGLISH -> Locale.ENGLISH
            else -> pickFromSystem(context.resources.configuration)
        }
    }

    /**
     * 从系统配置中挑选一个我们支持的 Locale：
     * - 优先使用配置本身的 Locale（API 24+ 为 LocaleList[0]）
     * - 语言前缀为 zh / en → 分别规范化为 SIMPLIFIED_CHINESE / ENGLISH
     * - 其他语言 → 默认走 SIMPLIFIED_CHINESE（values 默认即为中文）
     */
    private fun pickFromSystem(config: Configuration): Locale {
        val systemLocale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
        return when (systemLocale.language) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.SIMPLIFIED_CHINESE
        }
    }

    /**
     * 更新资源配置的语言。
     *
     * minSdk = 26 (O) > N (24) → 直接使用 LocaleList 设置完整回退链，
     * 并调用 createConfigurationContext 让 Context 真正拿到新资源。
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        config.setLocales(LocaleList(locale))
        return context.createConfigurationContext(config)
    }

    /**
     * 获取当前生效的 Locale（用于 UI 显示 / 日志）
     */
    fun getCurrentLocale(context: Context): Locale {
        return resolveDesiredLocale(context)
    }
}

