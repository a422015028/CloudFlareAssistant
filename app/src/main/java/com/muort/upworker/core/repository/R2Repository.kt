package com.muort.upworker.core.repository

import com.amazonaws.services.s3.AmazonS3Client
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.network.R2S3Client
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class R2Repository @Inject constructor(
    private val api: CloudFlareApi,
    private val r2S3Client: R2S3Client
) {
    
    // Cache S3 clients to avoid recreating them
    private val s3ClientCache = mutableMapOf<String, AmazonS3Client>()
    
    suspend fun listBuckets(account: Account): Resource<List<R2Bucket>> = 
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listR2Buckets(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val buckets = response.body()?.result?.buckets ?: emptyList()
                    Resource.Success(buckets)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list buckets: $errorMsg")
                }
            }
        }
    
    suspend fun createBucket(
        account: Account,
        name: String,
        location: String? = null
    ): Resource<R2Bucket> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createR2Bucket(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                bucket = R2BucketRequest(
                    name = name,
                    location = location
                )
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Bucket created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create bucket: $errorMsg")
            }
        }
    }
    
    suspend fun deleteBucket(
        account: Account,
        bucketName: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteR2Bucket(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                bucketName = bucketName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete bucket: $errorMsg")
            }
        }
    }
    
    /**
     * Get or create S3 client for account
     * Requires R2 access key ID and secret access key to be set in account
     */
    private fun getS3Client(account: Account): AmazonS3Client {
        val cacheKey = "${account.accountId}:${account.r2AccessKeyId}"
        return s3ClientCache.getOrPut(cacheKey) {
            if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                throw IllegalArgumentException("R2 access credentials not configured")
            }
            r2S3Client.createS3Client(
                accountId = account.accountId,
                accessKeyId = account.r2AccessKeyId,
                secretAccessKey = account.r2SecretAccessKey
            )
        }
    }
    
    suspend fun listObjects(
        account: Account,
        bucketName: String,
        prefix: String? = null
    ): Resource<R2ObjectList> = withContext(Dispatchers.IO) {
        safeApiCall {
            try {
                val s3Client = getS3Client(account)
                val result = r2S3Client.listObjects(s3Client, bucketName, prefix)
                Resource.Success(result)
            } catch (e: IllegalArgumentException) {
                Resource.Error("请先配置R2访问凭证（Access Key ID和Secret Access Key）")
            } catch (e: Exception) {
                Resource.Error("加载对象列表失败: ${e.message}")
            }
        }
    }
    
    suspend fun uploadObject(
        account: Account,
        bucketName: String,
        objectKey: String,
        file: File
    ): Resource<R2ObjectUpload> = withContext(Dispatchers.IO) {
        safeApiCall {
            try {
                val s3Client = getS3Client(account)
                r2S3Client.uploadObject(s3Client, bucketName, objectKey, file)
                Resource.Success(R2ObjectUpload(objectKey, file.length(), null))
            } catch (e: IllegalArgumentException) {
                Resource.Error("请先配置R2访问凭证（Access Key ID和Secret Access Key）")
            } catch (e: Exception) {
                Resource.Error("上传失败: ${e.message}")
            }
        }
    }
    
    suspend fun downloadObject(
        account: Account,
        bucketName: String,
        objectKey: String
    ): Resource<ByteArray> = withContext(Dispatchers.IO) {
        safeApiCall {
            try {
                val s3Client = getS3Client(account)
                val data = r2S3Client.downloadObject(s3Client, bucketName, objectKey)
                Resource.Success(data)
            } catch (e: IllegalArgumentException) {
                Resource.Error("请先配置R2访问凭证（Access Key ID和Secret Access Key）")
            } catch (e: Exception) {
                Resource.Error("下载失败: ${e.message}")
            }
        }
    }
    
    suspend fun deleteObject(
        account: Account,
        bucketName: String,
        objectKey: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            try {
                val s3Client = getS3Client(account)
                r2S3Client.deleteObject(s3Client, bucketName, objectKey)
                Resource.Success(Unit)
            } catch (e: IllegalArgumentException) {
                Resource.Error("请先配置R2访问凭证（Access Key ID和Secret Access Key）")
            } catch (e: Exception) {
                Resource.Error("删除失败: ${e.message}")
            }
        }
    }
    
    // ==================== Custom Domains ====================
    
    suspend fun listCustomDomains(
        account: Account,
        bucketName: String
    ): Resource<List<R2CustomDomain>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.listR2CustomDomains(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                bucketName = bucketName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val domains = response.body()?.result?.domains ?: emptyList()
                Resource.Success(domains)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to list custom domains: $errorMsg")
            }
        }
    }
    
    suspend fun createCustomDomain(
        account: Account,
        bucketName: String,
        domain: String
    ): Resource<R2CustomDomain> = withContext(Dispatchers.IO) {
        safeApiCall {
            val zoneId = account.zoneId 
                ?: return@safeApiCall Resource.Error("账号未配置 Zone ID")
            
            val request = R2CustomDomainRequest(
                domain = domain,
                zoneId = zoneId,
                enabled = true
            )
            
            val response = api.createR2CustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                bucketName = bucketName,
                request = request
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val customDomain = response.body()?.result
                if (customDomain != null) {
                    Resource.Success(customDomain)
                } else {
                    Resource.Error("创建成功但无返回数据")
                }
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create custom domain: $errorMsg")
            }
        }
    }
    
    suspend fun deleteCustomDomain(
        account: Account,
        bucketName: String,
        domain: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteR2CustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                bucketName = bucketName,
                domain = domain
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete custom domain: $errorMsg")
            }
        }
    }
}
