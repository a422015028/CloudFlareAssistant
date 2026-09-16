package com.muort.upworker.core.util

import com.muort.upworker.R
import com.muort.upworker.core.AppContextHolder
import com.muort.upworker.core.model.Resource
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
