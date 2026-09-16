package com.muort.upworker

import android.app.Application
import android.content.Context
import android.util.Log
import com.muort.upworker.core.AppContextHolder
import com.muort.upworker.core.log.LogRepository
import com.muort.upworker.core.util.LocaleHelper
import com.muort.upworker.core.util.ThemeHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudFlareApp : Application() {

    override fun attachBaseContext(base: Context) {
        // 应用保存的语言设置
        super.attachBaseContext(LocaleHelper.applyLocale(base))
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化全局 Context 持有者（供 ApiUtils 等无注入的工具函数使用）
        AppContextHolder.init(this)

        // 初始化 HTTP 日志仓库（加载应用内日志开关状态）
        LogRepository.init(this)

        // 应用主题模式（跟随系统/浅色/深色）
        ThemeHelper.applySavedTheme(this)

        // 初始化 Timber 日志
        if (BuildConfig.DEBUG) {
            // Debug 版本输出所有级别日志
            Timber.plant(Timber.DebugTree())
        } else {
            // Release 版本仅输出 ERROR 及以上级别日志
            Timber.plant(ReleaseTree())
        }
    }

    /**
     * Release 版本日志树：仅输出 ERROR（及 ASSERT）级别日志，
     * 屏蔽 Debug/Info/Warn/Verbose 级别以减少发布包日志噪音。
     */
    private class ReleaseTree : Timber.Tree() {

        override fun isLoggable(tag: String?, priority: Int): Boolean =
            priority >= Log.ERROR

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            Log.println(priority, tag, message)
        }
    }
}
