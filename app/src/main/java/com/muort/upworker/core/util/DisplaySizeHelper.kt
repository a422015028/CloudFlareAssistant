package com.muort.upworker.core.util

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics

/**
 * 全局显示大小（字体缩放）偏好管理
 */
object DisplaySizeHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_FONT_SCALE = "font_scale"

    /** 可选项：标签 -> 缩放比例 */
    val OPTIONS = listOf(
        "极小" to 0.7f,
        "较小" to 0.78f,
        "小" to 0.85f,
        "默认" to 1.0f,
        "大" to 1.15f,
        "超大" to 1.3f
    )

    fun getFontScale(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f)
    }

    fun setFontScale(context: Context, scale: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, scale)
            .apply()
    }

    /** 当前选中项的索引 */
    fun getSelectedIndex(context: Context): Int {
        val current = getFontScale(context)
        return OPTIONS.indexOfFirst { it.second == current }.let { if (it < 0) 1 else it }
    }

    /**
     * 包装 Context：将密度归一化到设备原生密度再缩小 1.2 倍，避免系统"显示大小"设置导致布局溢出；
     * 同时应用应用内字体缩放偏好。
     */
    fun wrap(context: Context): Context {
        val dm = context.resources.displayMetrics
        val stableDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
        val targetDpi = if (stableDpi > 0) (stableDpi / 1.2f).toInt() else dm.densityDpi
        val fontScale = getFontScale(context)
        val needFix = dm.densityDpi != targetDpi || fontScale != 1.0f

        return if (needFix) {
            val config = Configuration(context.resources.configuration)
            config.densityDpi = targetDpi
            config.fontScale = fontScale
            context.createConfigurationContext(config)
        } else {
            context
        }
    }
}

