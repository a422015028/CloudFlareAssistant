package com.muort.upworker.core.util

import com.muort.upworker.R
import com.muort.upworker.core.AppContextHolder
import com.muort.upworker.core.model.Resource
import retrofit2.Response
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Safe API call wrapper with error handling
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> Resource<T>): Resource<T> {
    return try {
        apiCall()
    } catch (e: SocketTimeoutException) {
        Timber.e(e, "Network timeout")
        Resource.Error(AppContextHolder.get().getString(R.string.repo_generic_timeout), e)
    } catch (e: IOException) {
        Timber.e(e, "Network error")
        Resource.Error(AppContextHolder.get().getString(R.string.repo_generic_network_error), e)
    } catch (e: Exception) {
        Timber.e(e, "Unexpected error")
        Resource.Error(
            AppContextHolder.get().getString(
                R.string.repo_generic_unexpected_error_format,
                e.message ?: ""
            ),
            e
        )
    }
}

/**
 * 从 API 响应中提取有意义的错误信息。
 * 优先级：Cloudflare errors 数组 > HTTP reason phrase > HTTP 状态码
 * 注意：HTTP/2 没有 reason phrase，response.message() 可能返回空字符串，
 * 所以需要用 isNullOrBlank 判断而非仅依赖 ?: 回退。
 */
fun resolveApiError(bodyErrorMessage: String?, response: Response<*>): String {
    if (!bodyErrorMessage.isNullOrBlank()) return bodyErrorMessage
    val reason = response.message()
    if (!reason.isNullOrBlank()) return reason
    return "HTTP ${response.code()}"
}

/**
 * Execute with loading state
 */
suspend fun <T> executeWithLoading(
    onLoading: () -> Unit,
    onSuccess: (T) -> Unit,
    onError: (String) -> Unit,
    apiCall: suspend () -> Resource<T>
) {
    onLoading()
    when (val result = apiCall()) {
        is Resource.Success -> onSuccess(result.data)
        is Resource.Error -> onError(result.message)
        is Resource.Loading -> onLoading()
    }
}
