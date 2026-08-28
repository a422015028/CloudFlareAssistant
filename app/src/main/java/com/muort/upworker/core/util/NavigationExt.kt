package com.muort.upworker.core.util

import androidx.annotation.IdRes
import androidx.navigation.NavController

/**
 * 安全导航，避免在当前目的地不匹配时崩溃。
 * 常见场景：postDelayed 延迟导航期间用户已切换到其他页面。
 */
fun NavController.safeNavigate(@IdRes resId: Int) {
    try {
        navigate(resId)
    } catch (_: IllegalArgumentException) {
        // 导航目标在当前 destination 上不存在，忽略即可
    }
}
