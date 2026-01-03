package com.muort.upworker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudFlareApp : Application() {
    
    companion object {
        private const val TARGET_DENSITY = 3.5f
        private const val TARGET_DENSITY_DPI = (160 * TARGET_DENSITY).toInt()
    }
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(createConfigurationContext(base))
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
    
    override fun getResources(): Resources {
        val res = super.getResources()
        adaptDisplayDensity(res)
        return res
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adaptDisplayDensity(resources)
    }
    
    /**
     * 创建固定配置的Context
     */
    private fun createConfigurationContext(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.fontScale = 1.0f
        config.densityDpi = TARGET_DENSITY_DPI
        return context.createConfigurationContext(config)
    }
    
    /**
     * 固定应用显示大小和字体大小，不跟随系统设置变化
     */
    @Suppress("DEPRECATION")
    private fun adaptDisplayDensity(res: Resources) {
        val config = res.configuration
        val dm = res.displayMetrics
        
        // 固定 fontScale 为 1.0
        if (config.fontScale != 1.0f) {
            config.fontScale = 1.0f
        }
        if (config.densityDpi != TARGET_DENSITY_DPI) {
            config.densityDpi = TARGET_DENSITY_DPI
        }
        
        // 设置固定的显示密度和字体密度
        dm.density = TARGET_DENSITY
        dm.densityDpi = TARGET_DENSITY_DPI
        dm.scaledDensity = TARGET_DENSITY
    }
}
