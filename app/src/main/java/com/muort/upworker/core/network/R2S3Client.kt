package com.muort.upworker.core.network

import com.amazonaws.ClientConfiguration
import com.amazonaws.HttpMethod
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.muort.upworker.core.model.R2Object
import com.muort.upworker.core.model.R2ObjectList
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class R2S3Client @Inject constructor(
    private val logInterceptor: LogOkHttpInterceptor
) {
    data class S3Config(
        val accountId: String,
        val accessKeyId: String,
        val secretAccessKey: String
    ) {
        val endpoint: String get() = "https://$accountId.r2.cloudflarestorage.com"
        
        fun cacheKey(): String = "$accountId:$accessKeyId"
    }

    private val clientCache = ConcurrentHashMap<String, AmazonS3Client>()

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun getS3Client(config: S3Config): AmazonS3Client {
        return clientCache.getOrPut(config.cacheKey()) {
            createS3ClientInternal(config)
        }
    }

    private fun createS3ClientInternal(config: S3Config): AmazonS3Client {
        val credentials = BasicAWSCredentials(config.accessKeyId, config.secretAccessKey)
        
        // 兼容性修复：手动实现 Provider 接口
        val credentialsProvider = object : AWSCredentialsProvider {
            override fun getCredentials(): AWSCredentials = credentials
            override fun refresh() {}
        }

        val clientConfiguration = ClientConfiguration().apply {
            connectionTimeout = 30 * 1000
            socketTimeout = 30 * 1000
            signerOverride = "AWSS3V4SignerType"
        }

        val region = Region.getRegion(Regions.US_EAST_1)
        val client = AmazonS3Client(credentialsProvider, region, clientConfiguration)
        
        client.endpoint = config.endpoint
        client.setS3ClientOptions(
            S3ClientOptions.builder()
                .setPathStyleAccess(true)
                .disableChunkedEncoding()
                .build()
        )
        return client
    }

    // ================== S3 API Methods ==================

    fun listObjects(
        config: S3Config,
        bucketName: String,
        prefix: String? = null,
        maxKeys: Int = 1000
    ): R2ObjectList {
        val s3Client = getS3Client(config)
        
        val request = ListObjectsV2Request().apply {
            this.bucketName = bucketName
            this.maxKeys = maxKeys
            if (!prefix.isNullOrEmpty()) {
                this.prefix = prefix
            }
        }

        val result = s3Client.listObjectsV2(request)

        val objects = result.objectSummaries.map { summary ->
            R2Object(
                key = summary.key,
                size = summary.size,
                etag = summary.eTag?.trim('"'),
                uploaded = summary.lastModified?.toInstant()?.toString(),
                httpMetadata = null
            )
        }

        return R2ObjectList(
            objects = objects,
            truncated = result.isTruncated,
            cursor = result.nextContinuationToken,
            delimitedPrefixes = result.commonPrefixes ?: emptyList()
        )
    }

    fun uploadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        file: File,
        contentType: String = "application/octet-stream"
    ) {
        val s3Client = getS3Client(config)
        val expiration = Date(System.currentTimeMillis() + 60 * 60 * 1000)

        // 修复：签名需包含 Content-Type
        val generatePresignedUrlRequest = GeneratePresignedUrlRequest(bucketName, objectKey)
            .withMethod(HttpMethod.PUT)
            .withContentType(contentType) 
            .withExpiration(expiration)

        val presignedUrl = s3Client.generatePresignedUrl(generatePresignedUrlRequest)

        val requestBody = file.asRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(presignedUrl.toString())
            .put(requestBody)
            .addHeader("Content-Type", contentType)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("Upload failed: code=${response.code}, msg=${response.message}, body=$errorBody")
            }
        }
    }

    /**
     * 从 InputStream 上传（需要先写入临时文件）
     * @param tempDir 临时文件存储目录，建议使用 context.cacheDir
     */
    fun uploadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        inputStream: InputStream,
        tempDir: File,
        contentType: String = "application/octet-stream"
    ) {
        val tempFile = File.createTempFile("r2_upload_", ".tmp", tempDir)
        try {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            uploadObject(config, bucketName, objectKey, tempFile, contentType)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * ✅ 推荐的大文件下载方式
     * 直接流式写入目标文件，内存占用极低（通常 < 8KB），不会导致 OOM。
     *
     * @param destinationFile 下载内容的保存路径
     */
    fun downloadObjectToFile(
        config: S3Config,
        bucketName: String,
        objectKey: String,
        destinationFile: File
    ) {
        val s3Client = getS3Client(config)
        // 获取 S3 对象（此时还没完全下载数据，只是建立了连接）
        val s3Object = s3Client.getObject(bucketName, objectKey)

        // 使用 .use 确保流在使用后自动关闭，防止内存泄漏
        s3Object.objectContent.use { input ->
            destinationFile.outputStream().use { output ->
                // copyTo 会自动处理缓冲，从网络流读一部分，写一部分到文件
                input.copyTo(output)
            }
        }
    }

    /**
     * ⚠️ 警告：仅适用于小文件（如 < 5MB 的配置文件）
     * 如果文件过大，会导致 OutOfMemoryError。
     */
    fun downloadObject(
        config: S3Config,
        bucketName: String,
        objectKey: String
    ): ByteArray {
        val s3Client = getS3Client(config)
        val s3Object = s3Client.getObject(bucketName, objectKey)
        return s3Object.objectContent.use { it.readBytes() }
    }

    fun deleteObject(
        config: S3Config,
        bucketName: String,
        objectKey: String
    ) {
        val s3Client = getS3Client(config)
        s3Client.deleteObject(bucketName, objectKey)
    }
}
