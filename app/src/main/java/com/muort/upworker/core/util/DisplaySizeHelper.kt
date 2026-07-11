package com.muort.upworker.core.util

import android.content.Context

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
}
