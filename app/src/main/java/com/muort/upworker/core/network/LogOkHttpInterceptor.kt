package com.muort.upworker.core.network

import com.muort.upworker.core.log.LogRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class LogOkHttpInterceptor @Inject constructor() : Interceptor {
    companion object {
        private const val MAX_BODY_LOG_BYTES = 1024 * 1024L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val time = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val timeStr = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy HH:mm:ss z"))
        val requestLog = buildString {
            append("\n--- 请求 ---    $timeStr\n")
            append("URL: ${request.url}\n")
            append("Method: ${request.method}\n")
            request.headers.forEach { append("Header: ${it.first}: ${it.second}\n") }
            request.body?.let { body ->
                val contentLength = body.contentLength()
                when {
                    contentLength in 0..MAX_BODY_LOG_BYTES -> {
                        val buffer = okio.Buffer()
                        body.writeTo(buffer)
                        val bodyStr = buffer.readUtf8()
                        if (bodyStr.isNotEmpty()) {
                            append("Body: $bodyStr\n")
                        }
                    }
                    contentLength > MAX_BODY_LOG_BYTES -> {
                        append("Body: [skipped ${contentLength} bytes, too large]\n")
                    }
                    else -> {
                        append("Body: [size unknown, skipped]\n")
                    }
                }
            }
        }
        LogRepository.appendLog(requestLog)
        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            LogRepository.appendLog("--- 响应异常 ---\n${e}\n")
            throw e
        }
        val time2 = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val timeStr2 = time2.format(java.time.format.DateTimeFormatter.ofPattern("yyyy HH:mm:ss z"))
        val responseLog = buildString {
            append("--- 响应 ---    $timeStr2\n")
            append("URL: ${response.request.url}\n")
            append("Code: ${response.code}\n")
            response.headers.forEach { append("Header: ${it.first}: ${it.second}\n") }
            val peeked = response.peekBody(MAX_BODY_LOG_BYTES)
            append("Body: ${peeked.string()}\n")
        }
        LogRepository.appendLog(responseLog)
        return response
    }
}
