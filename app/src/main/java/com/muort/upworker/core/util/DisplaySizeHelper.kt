package com.muort.upworker.core.util

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import com.muort.upworker.R

/**
 * 全局显示大小（字体缩放）偏好管理
 */
object DisplaySizeHelper {

    private const val PREFS_NAME = "app_settings"
    private const val KEY_FONT_SCALE = "font_scale"

    /** 可选项：标签 -> 缩放比例。需要 Context 以获取本地化字符串。 */
    fun getOptions(context: Context): List<Pair<String, Float>> = listOf(
        context.getString(R.string.helper_size_extra_small) to 0.7f,
        context.getString(R.string.helper_size_smaller) to 0.78f,
        context.getString(R.string.helper_size_small) to 0.85f,
        context.getString(R.string.helper_size_default) to 1.0f,
        context.getString(R.string.helper_size_large) to 1.15f,
        context.getString(R.string.helper_size_extra_large) to 1.3f
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
        return getOptions(context).indexOfFirst { it.second == current }.let { if (it < 0) 1 else it }
    }

    /**
     * 包装 Context：将密度归一化到设备原生密度再缩小 1.2 倍，避免系统"显示大小"设置导致布局溢出；
     * 同时应用应用内字体缩放偏好。
     *
     * 注意：此函数通常在 LocaleHelper.applyLocale 之后链式调用，必须**保留前一步已设置的 Locale**
     * （locale / locales / layoutDirection），否则会把语言退回系统默认值。
     *
     * 另外：构建 clone Configuration 时会将 uiMode 的 NIGHT_MASK 清为 UNDEFINED，
     * 防止 density/fontScale 变更把前一步清掉的夜间快照又带回来，破坏 MODE_NIGHT_FOLLOW_SYSTEM。
     */
    fun wrap(context: Context): Context {
        val dm = context.resources.displayMetrics
        val stableDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
        val targetDpi = if (stableDpi > 0) (stableDpi / 1.2f).toInt() else dm.densityDpi
        val fontScale = getFontScale(context)
        val needFix = dm.densityDpi != targetDpi || fontScale != 1.0f

        return if (needFix) {
            val config = Configuration(context.resources.configuration)
            // minSdk = 26 (O) > N (24) → 直接取 LocaleList[0]，不再做 SDK 判断
            val locale = config.locales[0]
            // 2) 再写入密度 / 字体缩放（这两项是本 helper 唯一需要修改的）
            config.densityDpi = targetDpi
            config.fontScale = fontScale
            // 3) 重新 setLocale / setLocales + setLayoutDirection 确保回写生效，
            //    防止某些厂商 ROM 在 densityDpi 变更时把 Locale 状态洗掉
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
            config.setLayoutDirection(locale)
            // 清除 night 快照，保证 AppCompat 根据 defaultNightMode 重新推导
            config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        Configuration.UI_MODE_NIGHT_UNDEFINED
            context.createConfigurationContext(config)
        } else {
            context
        }
    }
}

