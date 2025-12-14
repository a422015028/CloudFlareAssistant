package com.muort.upworker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CloudFlareApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
