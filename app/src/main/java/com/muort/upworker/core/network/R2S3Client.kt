

package com.muort.upworker.core.network

import com.muort.upworker.core.model.R2Object
import com.muort.upworker.core.model.R2ObjectList
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.LinkedHashMap

/**
 * R2 S3 API Client (OkHttp + AWS v4 签名实现)
 * 兼容 Cloudflare R2，支持列举、上传、下载、删除对象
 */

@Singleton
class R2S3Client @Inject constructor() {

    data class S3Config(
        val accountId: String,
        val accessKeyId: String,
        val secretAccessKey: String
    ) {
        val endpoint: String get() = "https://$accountId.r2.cloudflarestorage.com"
    }

    private val httpClient = OkHttpClient()

    // ================== S3 API ==================

    fun listObjects(
        config: S3Config,
        bucketName: String,
        prefix: String? = null,
        maxKeys: Int = 1000
    ): R2ObjectList {
        val query = LinkedHashMap<String, String>()
        query["list-type"] = "2"
        query["max-keys"] = maxKeys.toString()
        if (!prefix.isNullOrEmpty()) query["prefix"] = prefix
        val url = buildUrl(config.endpoint, bucketName, query)
        val now = Date()
        val request = Request.Builder()
            .url(url)
            .get()
            .applyAwsV4Signature(config, "GET", "/$bucketName", query, null, now)
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("List failed: ${resp.code} ${resp.message} $bodyStr")
            }
            return parseListObjectsXml(bodyStr)
        }
    }

    fun uploadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        file: File,
        contentType: String = "application/octet-stream"
    ) {
        FileInputStream(file).use { inputStream ->
            uploadObject(config, bucketName, objectKey, inputStream, file.length(), contentType)
        }
    }

    fun uploadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String = "application/octet-stream"
    ) {
        val url = buildUrl(config.endpoint, bucketName, null, objectKey)
        val now = Date()
        val body = object : RequestBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength() = contentLength
            override fun writeTo(sink: okio.BufferedSink) {
                inputStream.use { it.copyTo(sink.outputStream()) }
            }
        }
        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Content-Type", contentType)
            .applyAwsV4Signature(config, "PUT", "/$bucketName/$objectKey", null, null, now)
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Upload failed: ${resp.code} ${resp.message} $bodyStr")
            }
        }
    }

    fun downloadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String
    ): ByteArray {
        val url = buildUrl(config.endpoint, bucketName, null, objectKey)
        val now = Date()
        val request = Request.Builder()
            .url(url)
            .get()
            .applyAwsV4Signature(config, "GET", "/$bucketName/$objectKey", null, null, now)
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                val bodyStr = String(bodyBytes)
                throw RuntimeException("Download failed: ${resp.code} ${resp.message} $bodyStr")
            }
            return bodyBytes
        }
    }

    fun downloadObjectToFile(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        destinationFile: File
    ) {
        val data = downloadObject(config, bucketName, objectKey)
        destinationFile.outputStream().use { it.write(data) }
    }

    fun deleteObject(
        config: S3Config,
        bucketName: String,
        objectKey: String
    ) {
        val url = buildUrl(config.endpoint, bucketName, null, objectKey)
        val now = Date()
        val request = Request.Builder()
            .url(url)
            .delete()
            .applyAwsV4Signature(config, "DELETE", "/$bucketName/$objectKey", null, null, now)
            .build()
        httpClient.newCall(request).execute().use { resp ->
            val bodyStr = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                throw RuntimeException("Delete failed: ${resp.code} ${resp.message} $bodyStr")
            }
        }
    }

    // ================== 工具方法 ==================

    private fun buildUrl(endpoint: String, bucket: String, query: Map<String, String>? = null, objectKey: String? = null): String {
        val sb = StringBuilder()
        sb.append(endpoint)
        sb.append("/")
        sb.append(bucket)
        if (!objectKey.isNullOrEmpty()) {
            sb.append("/")
            sb.append(URLEncoder.encode(objectKey, "UTF-8").replace("+", "%20"))
        }
        if (!query.isNullOrEmpty()) {
            sb.append("?")
            sb.append(query.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" })
        }
        return sb.toString()
    }

    // AWS v4 签名实现（简化版，支持 R2 S3 基本操作）
    private fun Request.Builder.applyAwsV4Signature(
        config: S3Config,
        method: String,
        canonicalUri: String,
        query: Map<String, String>?,
        body: ByteArray?,
        now: Date
    ): Request.Builder {
        val region = "auto" // R2 只支持 auto
        val service = "s3"
        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dateStampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val amzDate = dateFormat.format(now)
        val dateStamp = dateStampFormat.format(now)
        val host = config.endpoint.toHttpUrl().host
        val payloadHash = body?.let { sha256Hex(it) } ?: if (method == "GET" || method == "DELETE") "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" else "UNSIGNED-PAYLOAD"
        val canonicalQuery = query?.entries
            ?.sortedBy { it.key }
            ?.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
            ?: ""
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = listOf(
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash
        ).joinToString("\n")
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray())
        ).joinToString("\n")
        val signingKey = getSignatureKey(config.secretAccessKey, dateStamp, region, service)
        val signature = hmacSha256Hex(signingKey, stringToSign)
        val authorization = "AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
        return this
            .addHeader("x-amz-date", amzDate)
            .addHeader("x-amz-content-sha256", payloadHash)
            .addHeader("Authorization", authorization)
            .addHeader("host", host)
    }

    private fun sha256Hex(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        var kSecret = ("AWS4" + key).toByteArray()
        var kDate = hmacSha256(kSecret, dateStamp)
        var kRegion = hmacSha256(kDate, regionName)
        var kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    // 解析 S3 ListObjectsV2 XML 响应（只提取常用字段）
    private fun parseListObjectsXml(xml: String): R2ObjectList {
        val objects = mutableListOf<R2Object>()
        val regex = Regex("<Contents>(.*?)</Contents>", RegexOption.DOT_MATCHES_ALL)
        val keyRegex = Regex("<Key>(.*?)</Key>")
        val sizeRegex = Regex("<Size>(\\d+)</Size>")
        val etagRegex = Regex("<ETag>\\\"(.*?)\\\"</ETag>")
        val lastModRegex = Regex("<LastModified>(.*?)</LastModified>")
        for (match in regex.findAll(xml)) {
            val content = match.groupValues[1]
            val key = keyRegex.find(content)?.groupValues?.get(1) ?: continue
            val size = sizeRegex.find(content)?.groupValues?.get(1)?.toLongOrNull()
            val etag = etagRegex.find(content)?.groupValues?.get(1)
            val lastMod = lastModRegex.find(content)?.groupValues?.get(1)
            objects.add(
                R2Object(
                    key = key,
                    size = size,
                    etag = etag,
                    uploaded = lastMod,
                    httpMetadata = null
                )
            )
        }
        return R2ObjectList(
            objects = objects,
            truncated = false,
            cursor = null,
            delimitedPrefixes = emptyList()
        )
    }
}
