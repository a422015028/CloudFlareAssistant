package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    suspend fun uploadWorkerScript(
        account: Account,
        scriptName: String,
        scriptFile: File
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            val requestBody = scriptFile.asRequestBody("application/javascript".toMediaType())
            val response = api.uploadWorkerScript(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                script = requestBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Upload successful but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message() 
                    ?: "Unknown error"
                Resource.Error("Upload failed: $errorMsg")
            }
        }
    }
    
    suspend fun listWorkerScripts(account: Account): Resource<List<WorkerScript>> = 
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listWorkerScripts(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list scripts: $errorMsg")
                }
            }
        }
    
    suspend fun getWorkerScript(
        account: Account,
        scriptName: String
    ): Resource<String> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getWorkerScript(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName
            )
            
            if (response.isSuccessful) {
                val scriptContent = response.body()?.string() ?: ""
                Resource.Success(scriptContent)
            } else {
                Resource.Error("Failed to get script: ${response.message()}")
            }
        }
    }
    
    suspend fun deleteWorkerScript(
        account: Account,
        scriptName: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteWorkerScript(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete script: $errorMsg")
            }
        }
    }
    
    // Routes
    suspend fun listRoutes(account: Account): Resource<List<Route>> = 
        withContext(Dispatchers.IO) {
            if (account.zoneId.isNullOrBlank()) {
                return@withContext Resource.Error("Zone ID is required for route operations")
            }
            
            safeApiCall {
                val response = api.listRoutes(
                    token = "Bearer ${account.token}",
                    zoneId = account.zoneId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list routes: $errorMsg")
                }
            }
        }
    
    suspend fun createRoute(
        account: Account,
        pattern: String,
        scriptName: String
    ): Resource<Route> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.createRoute(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                route = RouteRequest(pattern = pattern, script = scriptName)
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Route created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create route: $errorMsg")
            }
        }
    }
    
    suspend fun updateRoute(
        account: Account,
        routeId: String,
        pattern: String,
        scriptName: String
    ): Resource<Route> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.updateRoute(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                routeId = routeId,
                route = RouteRequest(pattern = pattern, script = scriptName)
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Route updated but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to update route: $errorMsg")
            }
        }
    }
    
    suspend fun listCustomDomains(account: Account): Resource<List<CustomDomain>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listCustomDomains(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list custom domains: $errorMsg")
                }
            }
        }
    
    suspend fun addCustomDomain(
        account: Account,
        hostname: String,
        scriptName: String
    ): Resource<CustomDomain> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.addCustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                request = CustomDomainRequest(
                    hostname = hostname,
                    service = scriptName
                )
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Domain added but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to add custom domain: $errorMsg")
            }
        }
    }
    
    suspend fun deleteCustomDomain(
        account: Account,
        domainId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteCustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                domainId = domainId
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
    
    suspend fun deleteRoute(
        account: Account,
        routeId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        if (account.zoneId.isNullOrBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.deleteRoute(
                token = "Bearer ${account.token}",
                zoneId = account.zoneId,
                routeId = routeId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete route: $errorMsg")
            }
        }
    }
}
