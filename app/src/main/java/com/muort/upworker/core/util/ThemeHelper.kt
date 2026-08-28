package com.muort.upworker.core.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
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
        applyThemeMode(mode)
    }

    private fun applyThemeMode(mode: Int) {
        when (mode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /**
     * 应用当前保存的主题模式（在 Application 启动时调用）
     */
    fun applySavedTheme(context: Context) {
        applyThemeMode(getThemeMode(context))
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
