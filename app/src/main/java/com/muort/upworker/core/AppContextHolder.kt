package com.muort.upworker.core

import android.annotation.SuppressLint
import android.content.Context

/**
 * 全局 Application Context 持有者，供无法通过依赖注入获取 Context 的工具函数使用。
 * 在 [com.muort.upworker.CloudFlareApp.onCreate] 中初始化。
 */
@SuppressLint("StaticFieldLeak") // 持有 applicationContext，不会造成内存泄漏
object AppContextHolder {
    @Volatile
    @SuppressLint("StaticFieldLeak") // 持有 applicationContext，不会造成内存泄漏
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context =
        context ?: error("AppContextHolder not initialized. Call init() in Application.onCreate().")
}
