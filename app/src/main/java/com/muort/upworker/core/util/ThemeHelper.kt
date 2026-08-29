package com.muort.upworker.core.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.muort.upworker.R

/**
 * 主题管理：浅色 / 深色 / 跟随系统 / 动态配色开关
 */
object ThemeHelper {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    const val THEME_FOLLOW_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, THEME_FOLLOW_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
        applyThemeMode(context, mode)
    }

    private fun applyThemeMode(context: Context, mode: Int) {
        when (mode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        // 同步 Application Resources 的 uiMode night 位到"实际希望的状态"，
        // 防止后续 LocaleHelper / DisplaySizeHelper wrapper clone 时把旧快照又带出来，
        // 使 MODE_NIGHT_FOLLOW_SYSTEM 粘在之前的浅色/深色状态上。
        val desiredNightBits = when (mode) {
            THEME_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            THEME_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        syncAppResourcesNightBits(context.applicationContext, desiredNightBits)
    }

    /**
     * 把 app 自身 Resources Configuration 的 NIGHT 位强制同步为给定值。
     * 后续 LocaleHelper / DisplaySizeHelper wrapper 里会把 NIGHT_MASK 再清成 UNDEFINED，
     * 让 AppCompat 根据 setDefaultNightMode 重新正确推导。
     */
    private fun syncAppResourcesNightBits(appContext: Context, desiredNightBits: Int) {
        val res = appContext.resources
        val config = Configuration(res.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or desiredNightBits
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    /**
     * 应用当前保存的主题模式（在 Application 启动时调用）
     */
    fun applySavedTheme(context: Context) {
        applyThemeMode(context, getThemeMode(context))
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        // 默认开启（如果设备支持）
        return getPrefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /**
     * 设备是否支持动态配色
     */
    fun isDynamicColorAvailable(): Boolean {
        return DynamicColors.isDynamicColorAvailable()
    }

    /**
     * 对单个 Activity 应用动态配色（根据设置决定是否应用）
     * 在 Activity 的 setContentView 之前调用
     */
    fun applyDynamicColorIfEnabled(activity: Activity) {
        if (isDynamicColorAvailable() && isDynamicColorEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun getThemeModeLabels(ctx: Context): List<String> = listOf(
        ctx.getString(R.string.theme_follow_system),
        ctx.getString(R.string.theme_light),
        ctx.getString(R.string.theme_dark)
    )
}
