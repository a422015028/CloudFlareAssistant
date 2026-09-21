package com.muort.upworker.core.repository

import com.muort.upworker.R
import com.muort.upworker.core.AppContextHolder
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.resolveApiError
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
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                        ?: response.message()
                    Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_list_namespaces_failed_format, errorMsg ?: ""))
                }
            }
        }
    
    suspend fun createNamespace(
        account: Account,
        title: String
    ): Resource<KvNamespace> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createKvNamespace(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                namespace = KvNamespaceRequest(title = title)
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_create_namespace_no_result))
            } else {
                val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                    ?: response.message()
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_create_namespace_failed_format, errorMsg ?: ""))
            }
        }
    }
    
    suspend fun deleteNamespace(
        account: Account,
        namespaceId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteKvNamespace(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                namespaceId = namespaceId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                    ?: response.message()
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_delete_namespace_failed_format, errorMsg ?: ""))
            }
        }
    }
    
    suspend fun listKeys(
        account: Account,
        namespaceId: String
    ): Resource<List<KvKey>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.listKvKeys(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                namespaceId = namespaceId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(response.body()?.result ?: emptyList())
            } else {
                val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                    ?: response.message()
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_list_keys_failed_format, errorMsg ?: ""))
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_get_value_failed_format, errorMsg ?: ""))
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                namespaceId = namespaceId,
                keyName = keyName,
                value = requestBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                    ?: response.message()
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_put_value_failed_format, errorMsg ?: ""))
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                namespaceId = namespaceId,
                keyName = keyName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response) 
                    ?: response.message()
                Resource.Error(AppContextHolder.get().getString(R.string.repo_kv_delete_value_failed_format, errorMsg ?: ""))
            }
        }
    }
}
