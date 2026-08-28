package com.muort.upworker

import android.app.Application
import com.muort.upworker.core.util.ThemeHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudFlareApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 应用主题模式（跟随系统/浅色/深色）
        ThemeHelper.applySavedTheme(this)
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
