package com.muort.upworker.core.util

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import java.security.MessageDigest
import kotlin.math.min

/**
 * Extension functions for showing Toast messages
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Fragment.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().showToast(message, duration)
}

fun Context.showLongToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
}

fun Fragment.showLongToast(message: String) {
    showToast(message, Toast.LENGTH_LONG)
}

/**
 * 智能列表变更通知：根据新旧大小自动选择 changed / inserted / removed，
 * 避免 notifyDataSetChanged() 的性能浪费，也避免缩容时悬空 ViewHolder 崩溃。
 */
fun <VH : RecyclerView.ViewHolder> RecyclerView.Adapter<VH>.notifyListChanged(oldSize: Int, newSize: Int) {
    val common = min(oldSize, newSize)
    if (common > 0) notifyItemRangeChanged(0, common)
    when {
        newSize > oldSize -> notifyItemRangeInserted(oldSize, newSize - oldSize)
        newSize < oldSize -> notifyItemRangeRemoved(newSize, oldSize - newSize)
    }
}

/**
 * 计算字符串的 SHA-256 摘要，返回小写十六进制字符串（64 字符）。
 * 用于从 API 令牌 value 派生 R2 S3 客户端的 Secret Access Key。
 */
fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
