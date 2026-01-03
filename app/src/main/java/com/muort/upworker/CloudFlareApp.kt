package com.muort.upworker

import android.app.Application
import android.content.res.Resources
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudFlareApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // 固定显示密度，但保留字体缩放
        adaptDisplayDensity()
    }
    
    /**
     * 固定应用显示大小为默认值，不跟随系统显示大小设置变化
     * 但保留系统字体大小设置
     */
    @Suppress("DEPRECATION")
    private fun adaptDisplayDensity() {
        val appDisplayMetrics = resources.displayMetrics
        val targetDensity = 3.5f // 固定为默认密度（介于xxhdpi和xxxhdpi之间）
        val targetDensityDpi = (160 * targetDensity).toInt()
        
        // 获取系统的字体缩放比例
        val systemFontScale = Resources.getSystem().configuration.fontScale
        
        // 设置固定的显示密度
        appDisplayMetrics.density = targetDensity
        appDisplayMetrics.densityDpi = targetDensityDpi
        // scaledDensity 需要根据字体缩放比例计算，这样字体会跟随系统设置
        appDisplayMetrics.scaledDensity = targetDensity * systemFontScale
        
        // 同时更新 Configuration
        val appConfig = resources.configuration
        appConfig.densityDpi = targetDensityDpi
        appConfig.fontScale = systemFontScale
    }
}
