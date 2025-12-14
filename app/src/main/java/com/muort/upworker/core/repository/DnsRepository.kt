package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    suspend fun listDnsRecords(
        account: Account,
        type: String? = null,
        name: String? = null
    ): Resource<List<DnsRecord>> = withContext(Dispatchers.IO) {
        Timber.d("listDnsRecords called for account: ${account.name}, zoneId: ${account.zoneId}")
        
        if (account.zoneId.isNullOrBlank()) {
            Timber.e("Zone ID is missing for account: ${account.name}")
            return@withContext Resource.Error("Zone ID is required for DNS operations")
        }
        
        safeApiCall {
            Timber.d("Calling DNS API with zoneId: ${account.zoneId}, type: $type, name: $name")
            val response = api.listDnsRecords(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                type = type,
                name = name
            )
            
            Timber.d("DNS API response - success: ${response.isSuccessful}, code: ${response.code()}")
            
            if (response.isSuccessful && response.body()?.success == true) {
                val records = response.body()?.result ?: emptyList()
                Timber.d("Successfully loaded ${records.size} DNS records")
                Resource.Success(records)
            } else {
                val body = response.body()
                val errors = body?.errors
                val errorMsg = errors?.firstOrNull()?.message ?: response.message()
                
                // Log detailed error information
                Timber.e("DNS API error - code: ${response.code()}")
                Timber.e("Response success flag: ${body?.success}")
                Timber.e("Error count: ${errors?.size ?: 0}")
                errors?.forEach { error ->
                    Timber.e("Error code: ${error.code}, message: ${error.message}")
                }
                
                // Provide helpful error message based on HTTP status code
                val friendlyMsg = when (response.code()) {
                    403 -> "权限不足：API Token 没有访问此 Zone DNS 记录的权限。请检查 Token 权限设置。"
                    401 -> "认证失败：API Token 无效或已过期。"
                    404 -> "Zone ID 不存在或无法访问。"
                    else -> errorMsg
                }
                
                Resource.Error("Failed to list DNS records: $friendlyMsg")
            }
        }
    }
    
    suspend fun createDnsRecord(
        account: Account,
        record: DnsRecordRequest
    ): Resource<DnsRecord> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for DNS operations")
        }
        
        safeApiCall {
            val response = api.createDnsRecord(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                record = record
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("DNS record created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create DNS record: $errorMsg")
            }
        }
    }
    
    suspend fun updateDnsRecord(
        account: Account,
        recordId: String,
        record: DnsRecordRequest
    ): Resource<DnsRecord> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for DNS operations")
        }
        
        safeApiCall {
            val response = api.updateDnsRecord(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                recordId = recordId,
                record = record
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("DNS record updated but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to update DNS record: $errorMsg")
            }
        }
    }
    
    suspend fun deleteDnsRecord(
        account: Account,
        recordId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for DNS operations")
        }
        
        safeApiCall {
            val response = api.deleteDnsRecord(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                recordId = recordId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete DNS record: $errorMsg")
            }
        }
    }
}
