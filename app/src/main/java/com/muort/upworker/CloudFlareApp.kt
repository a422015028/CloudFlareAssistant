package com.muort.upworker

import android.app.Application
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudFlareApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 应用 Material You 动态配色（仅 Android 12+ 生效，低版本自动降级）
        DynamicColors.applyToActivitiesIfAvailable(this)
        
        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
