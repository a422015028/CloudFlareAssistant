package com.muort.upworker.core.network

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.*
import com.muort.upworker.core.model.R2Object
import com.muort.upworker.core.model.R2ObjectList
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat
import java.util.*

/**
 * R2 S3 API Client
 * Handles R2 object operations through S3-compatible API
 */
@Singleton
class R2S3Client @Inject constructor() {
    
    /**
     * Create S3 client for R2
     * Requires R2 access key ID and secret access key (not the API token)
     * Users need to create R2 API tokens from Cloudflare dashboard
     */
    fun createS3Client(accountId: String, accessKeyId: String, secretAccessKey: String): AmazonS3Client {
        val credentials: AWSCredentials = BasicAWSCredentials(accessKeyId, secretAccessKey)
        val endpoint = "https://$accountId.r2.cloudflarestorage.com"
        
        // Critical: Disable chunked encoding globally to avoid STREAMING-AWS4-HMAC-SHA256-PAYLOAD
        System.setProperty("com.amazonaws.services.s3.disableChunkedEncoding", "true")
        
        val clientConfiguration = ClientConfiguration().apply {
            // Disable chunked encoding to avoid STREAMING-AWS4-HMAC-SHA256-PAYLOAD error
            // R2 doesn't support streaming signature, use standard AWS4-HMAC-SHA256 instead
            signerOverride = "AWSS3V4SignerType"
            maxConnections = 50
            connectionTimeout = 50000
            socketTimeout = 50000
        }
        val client = AmazonS3Client(credentials, Region.getRegion("us-east-1"), clientConfiguration)
        client.setEndpoint(endpoint)
        
        return client
    }
    
    /**
     * List objects in a bucket
     */
    fun listObjects(
        s3Client: AmazonS3Client,
        bucketName: String,
        prefix: String? = null,
        maxKeys: Int = 1000
    ): R2ObjectList {
        try {
            val request = ListObjectsV2Request()
                .withBucketName(bucketName)
                .withMaxKeys(maxKeys)
            
            if (!prefix.isNullOrEmpty()) {
                request.withPrefix(prefix)
            }
            
            val result = s3Client.listObjectsV2(request)
            
            val objects = result.objectSummaries.map { summary ->
                R2Object(
                    key = summary.key,
                    size = summary.size,
                    etag = summary.eTag,
                    uploaded = summary.lastModified?.toString(),
                    httpMetadata = null
                )
            }
            
            return R2ObjectList(
                objects = objects,
                truncated = result.isTruncated,
                cursor = result.nextContinuationToken,
                delimitedPrefixes = result.commonPrefixes
            )
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Upload object to bucket using presigned URL
     * This bypasses AWS SDK's chunked encoding which R2 doesn't support
     */
    fun uploadObject(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String,
        file: File,
        contentType: String = "application/octet-stream"
    ) {
        try {
            // Generate presigned URL for PUT operation (valid for 1 hour)
            val expiration = Date(System.currentTimeMillis() + 3600 * 1000)
            val generatePresignedUrlRequest = GeneratePresignedUrlRequest(bucketName, objectKey)
                .withMethod(com.amazonaws.HttpMethod.PUT)
                .withExpiration(expiration)
                .withContentType(contentType)
            
            val presignedUrl = s3Client.generatePresignedUrl(generatePresignedUrlRequest)
            
            // Use OkHttp to upload directly via HTTP PUT (no chunked encoding)
            val client = OkHttpClient()
            val mediaType = contentType.toMediaType()
            val requestBody = file.asRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(presignedUrl)
                .put(requestBody)
                .addHeader("Content-Type", contentType)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("Upload failed: ${response.code} ${response.message} - $errorBody")
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Upload object from input stream
     */
    fun uploadObject(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String,
        inputStream: InputStream,
        contentLength: Long,
        contentType: String = "application/octet-stream"
    ) {
        try {
            val metadata = ObjectMetadata().apply {
                this.contentType = contentType
                this.contentLength = contentLength
            }
            
            val request = PutObjectRequest(bucketName, objectKey, inputStream, metadata)
            s3Client.putObject(request)
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Download object from bucket
     */
    fun downloadObject(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String
    ): ByteArray {
        try {
            val s3Object = s3Client.getObject(bucketName, objectKey)
            return s3Object.objectContent.use { it.readBytes() }
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Download object to file
     */
    fun downloadObjectToFile(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String,
        destinationFile: File
    ) {
        try {
            val s3Object = s3Client.getObject(bucketName, objectKey)
            s3Object.objectContent.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Delete object from bucket
     */
    fun deleteObject(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String
    ) {
        try {
            s3Client.deleteObject(bucketName, objectKey)
        } catch (e: Exception) {
            throw e
        }
    }
    
    /**
     * Get object metadata
     */
    fun getObjectMetadata(
        s3Client: AmazonS3Client,
        bucketName: String,
        objectKey: String
    ): ObjectMetadata {
        try {
            return s3Client.getObjectMetadata(bucketName, objectKey)
        } catch (e: Exception) {
            throw e
        }
    }
}
