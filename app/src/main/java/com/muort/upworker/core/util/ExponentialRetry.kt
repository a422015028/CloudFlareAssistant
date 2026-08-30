package com.muort.upworker.core.util

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.pow

/**
 * 通用指数退避重试（cf-manager zr 模式：attempts=3, base=1000ms, multiplier=2.0）。
 *
 * 异常语义：
 *   - block 内任何抛异常 → 进入重试（sleep baseDelayMs * multiplier^attemptIndex）。
 *   - 所有 attempts 都失败 → 抛出最后一次异常（由上层 decide 是否映射到用户可见 R.string）。
 *
 * onLog 回调保证**不**在 .kt 里硬编码用户可见文本；调用方必须通过 context.getString 注入。
 */
object ExponentialRetry {

    const val DEFAULT_ATTEMPTS = 3
    const val DEFAULT_BASE_DELAY_MS = 1000L
    const val DEFAULT_MULTIPLIER = 2.0

    suspend inline fun <T> withRetry(
        attempts: Int = DEFAULT_ATTEMPTS,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        multiplier: Double = DEFAULT_MULTIPLIER,
        tag: String = "ExponentialRetry",
        /**
         * 每次重试前的 log 回调；参数 (attemptIndex, delayMs)，attemptIndex 从 0 开始到 attempts-2。
         * 调用方必须 context.getString(R.string.repo_pages_retry_backoff_log_format, tag, attemptIndex+1, delayMs)。
         */
        noinline onBeforeRetry: ((attemptIndex: Int, delayMs: Long) -> Unit)? = null,
        block: () -> T,
    ): T {
        require(attempts >= 1) { "attempts must be >= 1, got $attempts" }
        var last: Throwable? = null
        repeat(attempts) { i ->
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                val remaining = attempts - 1 - i
                if (remaining <= 0) throw t
                val delayMs = (baseDelayMs * multiplier.pow(i)).toLong().coerceAtLeast(1L)
                Timber.d("$tag: attempt ${i + 1} failed (${t.javaClass.simpleName}), " +
                        "retry in ${delayMs}ms; $remaining remaining")
                try {
                    onBeforeRetry?.invoke(i, delayMs)
                } catch (logT: Throwable) {
                    Timber.w(logT, "$tag: onBeforeRetry threw, ignoring")
                }
                delay(delayMs)
            }
        }
        // Kotlin 编译器无法识别 repeat 100% 会 return 或 throw；这里兜底
        throw last ?: IllegalStateException("ExponentialRetry: no attempt executed (attempts=$attempts)")
    }
}
