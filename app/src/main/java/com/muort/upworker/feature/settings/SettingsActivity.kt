package com.muort.upworker.feature.settings

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.muort.upworker.R
import com.muort.upworker.core.util.DisplaySizeHelper
import com.muort.upworker.core.util.LocaleHelper
import com.muort.upworker.core.util.LogProxyHelper
import com.muort.upworker.core.util.ThemeHelper
import com.muort.upworker.databinding.ActivitySettingsBinding

/**
 * 设置页面（Activity 形式）
 * 外观 / 显示 / 语言 / 网络 四个分组卡片
 */
class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(LocaleHelper.applyLocale(newBase)))
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        applyStatusBarStyle()
        setupThemeMode()
        setupDynamicColor()
        setupDisplaySize()
        setupLanguage()
        setupLogProxyDomain()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    private fun applyStatusBarStyle() {
        val isDarkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.black, theme)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                @Suppress("DEPRECATION")
                ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
                    controller.isAppearanceLightStatusBars = false
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.white, theme)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
                    controller.isAppearanceLightStatusBars = true
                }
            }
        }
    }

    // ==================== 主题模式 ====================

    private fun setupThemeMode() {
        val themeMode = ThemeHelper.getThemeMode(this)
        val themeButtonId = when (themeMode) {
            ThemeHelper.THEME_LIGHT -> R.id.themeLightBtn
            ThemeHelper.THEME_DARK -> R.id.themeDarkBtn
            else -> R.id.themeFollowSystemBtn
        }
        binding.themeModeToggleGroup.check(themeButtonId)

        binding.themeModeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.themeLightBtn -> ThemeHelper.THEME_LIGHT
                    R.id.themeDarkBtn -> ThemeHelper.THEME_DARK
                    else -> ThemeHelper.THEME_FOLLOW_SYSTEM
                }
                ThemeHelper.setThemeMode(this, mode)
                recreate()
            }
        }
    }

    // ==================== 动态配色 ====================

    private fun setupDynamicColor() {
        val dynamicAvailable = ThemeHelper.isDynamicColorAvailable()
        if (dynamicAvailable) {
            binding.dynamicColorSwitch.isChecked = ThemeHelper.isDynamicColorEnabled(this)
            binding.dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
                ThemeHelper.setDynamicColorEnabled(this, isChecked)
                recreate()
            }
        } else {
            binding.dynamicColorLayout.visibility = View.GONE
        }
    }

    // ==================== 显示大小 ====================

    private fun setupDisplaySize() {
        val sizeOptions = DisplaySizeHelper.getOptions(this)
        val selectedIdx = DisplaySizeHelper.getSelectedIndex(this)

        val idToIndex = mapOf(
            R.id.displaySizeExtraSmallBtn to 0,
            R.id.displaySizeSmallerBtn to 1,
            R.id.displaySizeSmallBtn to 2,
            R.id.displaySizeDefaultBtn to 3,
            R.id.displaySizeLargeBtn to 4,
            R.id.displaySizeExtraLargeBtn to 5,
        )
        val indexToId = idToIndex.entries.associate { (k, v) -> v to k }

        indexToId[selectedIdx]?.let { id ->
            if (idToIndex[id]!! < 3) binding.displaySizeRow1.check(id)
            else binding.displaySizeRow2.check(id)
        }

        fun onSizeChecked(checkedId: Int, whichRow: Int) {
            if (checkedId == View.NO_ID) return
            val idx = idToIndex[checkedId] ?: return
            if (whichRow == 1) binding.displaySizeRow2.clearChecked()
            else binding.displaySizeRow1.clearChecked()
            val scale = sizeOptions[idx].second
            if (scale != DisplaySizeHelper.getFontScale(this)) {
                DisplaySizeHelper.setFontScale(this, scale)
                recreate()
            }
        }

        binding.displaySizeRow1.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onSizeChecked(checkedId, 1)
        }
        binding.displaySizeRow2.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) onSizeChecked(checkedId, 2)
        }
    }

    // ==================== 语言 ====================

    private fun setupLanguage() {
        val language = LocaleHelper.getLanguage(this)
        val langButtonId = when (language) {
            LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE -> R.id.langChineseBtn
            LocaleHelper.LANGUAGE_ENGLISH -> R.id.langEnglishBtn
            else -> R.id.langFollowSystemBtn
        }
        binding.languageToggleGroup.check(langButtonId)

        binding.languageToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val lang = when (checkedId) {
                    R.id.langChineseBtn -> LocaleHelper.LANGUAGE_SIMPLIFIED_CHINESE
                    R.id.langEnglishBtn -> LocaleHelper.LANGUAGE_ENGLISH
                    else -> LocaleHelper.LANGUAGE_FOLLOW_SYSTEM
                }
                LocaleHelper.setLanguage(this, lang)
                recreate()
            }
        }
    }

    // ==================== 实时日志代理域名 ====================

    private fun setupLogProxyDomain() {
        binding.logProxyDomainInput.setText(LogProxyHelper.getProxyDomain(this))
    }

    override fun onPause() {
        super.onPause()
        // 离开页面时保存代理域名
        LogProxyHelper.setProxyDomain(this, binding.logProxyDomainInput.text?.toString() ?: "")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
