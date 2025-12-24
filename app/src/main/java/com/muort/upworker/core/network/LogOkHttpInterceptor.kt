package com.muort.upworker.core.network

import com.muort.upworker.core.log.LogRepository
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class LogOkHttpInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val time = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
        val timeStr = time.format(java.time.format.DateTimeFormatter.ofPattern("yyyy HH:mm:ss z"))
        val requestLog = buildString {
            append("\n--- 请求 ---    $timeStr\n")
            append("URL: ${request.url}\n")
            append("Method: ${request.method}\n")
            request.headers.forEach { append("Header: ${it.first}: ${it.second}\n") }
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
            val peeked = response.peekBody(1024 * 1024)
            append("Body: ${peeked.string()}\n")
        }
        LogRepository.appendLog(responseLog)
        return response
    }
}
