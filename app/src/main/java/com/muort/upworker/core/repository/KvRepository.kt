package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KvRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    suspend fun listNamespaces(account: Account): Resource<List<KvNamespace>> = 
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listKvNamespaces(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list namespaces: $errorMsg")
                }
            }
        }
    
    suspend fun createNamespace(
        account: Account,
        title: String
    ): Resource<KvNamespace> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createKvNamespace(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespace = KvNamespaceRequest(title = title)
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Namespace created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create namespace: $errorMsg")
            }
        }
    }
    
    suspend fun deleteNamespace(
        account: Account,
        namespaceId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteKvNamespace(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespaceId = namespaceId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete namespace: $errorMsg")
            }
        }
    }
    
    suspend fun listKeys(
        account: Account,
        namespaceId: String
    ): Resource<List<KvKey>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.listKvKeys(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespaceId = namespaceId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(response.body()?.result ?: emptyList())
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to list keys: $errorMsg")
            }
        }
    }
    
    suspend fun getValue(
        account: Account,
        namespaceId: String,
        keyName: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getKvValue(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespaceId = namespaceId,
                keyName = keyName
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    Resource.Success(body.string())
                } else {
                    Resource.Success("")
                }
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Resource.Error("Failed to get value: $errorMsg")
            }
        }
    }
    
    suspend fun putValue(
        account: Account,
        namespaceId: String,
        keyName: String,
        value: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val requestBody = value.toRequestBody("text/plain".toMediaType())
            val response = api.putKvValue(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespaceId = namespaceId,
                keyName = keyName,
                value = requestBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to put value: $errorMsg")
            }
        }
    }
    
    suspend fun deleteValue(
        account: Account,
        namespaceId: String,
        keyName: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteKvValue(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                namespaceId = namespaceId,
                keyName = keyName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete value: $errorMsg")
            }
        }
    }
}
