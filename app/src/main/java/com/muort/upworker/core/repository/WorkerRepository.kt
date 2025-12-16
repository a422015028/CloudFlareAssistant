package com.muort.upworker.core.repository

import com.google.gson.Gson
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkerRepository @Inject constructor(
    private val api: CloudFlareApi,
    private val gson: Gson
) {
    
    /**
     * Upload Worker Script using multipart/form-data (Recommended method)
     * Supports full metadata configuration including bindings, compatibility settings, etc.
     */
    suspend fun uploadWorkerScriptMultipart(
        account: Account,
        scriptName: String,
        scriptFile: File,
        metadata: WorkerMetadata? = null
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            // Create metadata or use default
            val finalMetadata = metadata ?: WorkerMetadata(
                mainModule = scriptFile.name,
                compatibilityDate = "2024-12-01" // Use recent stable date
            )
            
            // Convert metadata to JSON RequestBody
            val metadataJson = gson.toJson(finalMetadata)
            Timber.d("Upload metadata JSON: $metadataJson")
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaType())
            
            // Determine content type based on file extension
            val contentType = when (scriptFile.extension.lowercase()) {
                "js" -> "application/javascript+module"
                "mjs" -> "application/javascript+module"
                "py" -> "text/x-python"
                "wasm" -> "application/wasm"
                else -> "application/javascript"
            }.toMediaType()
            
            // Create multipart body for script
            val scriptPart = MultipartBody.Part.createFormData(
                name = finalMetadata.mainModule ?: scriptFile.name,
                filename = scriptFile.name,
                body = scriptFile.asRequestBody(contentType)
            )
            
            val response = api.uploadWorkerScriptMultipart(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                metadata = metadataBody,
                script = scriptPart
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Timber.d("Upload successful: ${it.id}")
                    Resource.Success(it)
                } ?: Resource.Error("Upload successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message() 
                    ?: "Unknown error"
                Timber.e("Upload failed: $errorMsg, Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Upload failed: $errorMsg")
            }
        }
    }
    
    /**
     * Upload Worker Script with KV Namespace bindings
     * Convenience method that creates metadata with KV bindings
     */
    suspend fun uploadWorkerScriptWithKvBindings(
        account: Account,
        scriptName: String,
        scriptFile: File,
        kvBindings: List<Pair<String, String>> // List of (binding_name, namespace_id) pairs
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        Timber.d("Uploading worker with ${kvBindings.size} KV bindings")
        
        // Convert KV bindings to WorkerBinding objects
        val bindings = kvBindings.map { (name, namespaceId) ->
            Timber.d("Adding KV binding: $name -> $namespaceId")
            WorkerBinding(
                type = "kv_namespace",
                name = name,
                namespaceId = namespaceId
            )
        }
        
        // Create metadata with KV bindings
        val metadata = WorkerMetadata(
            mainModule = scriptFile.name,
            compatibilityDate = "2024-12-01",
            bindings = bindings
        )
        
        // Log metadata for debugging
        Timber.d("Metadata: ${gson.toJson(metadata)}")
        
        // Use the multipart upload method with metadata
        uploadWorkerScriptMultipart(account, scriptName, scriptFile, metadata)
    }
    
    /**
     * Upload Worker Script content only (without metadata)
     * Faster method when you only need to update the script code
     */
    suspend fun uploadWorkerScriptContent(
        account: Account,
        scriptName: String,
        scriptFile: File
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            val contentType = when (scriptFile.extension.lowercase()) {
                "js", "mjs" -> "application/javascript"
                "py" -> "text/x-python"
                else -> "application/javascript"
            }.toMediaType()
            
            val requestBody = scriptFile.asRequestBody(contentType)
            val response = api.uploadWorkerScriptContent(
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
    
    /**
     * Upload Worker Script (Legacy/Simple method)
     * Kept for backward compatibility - tries multiple upload methods
     */
    suspend fun uploadWorkerScript(
        account: Account,
        scriptName: String,
        scriptFile: File
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        // Try multipart upload first (recommended)
        Timber.d("Attempting multipart upload for $scriptName")
        val multipartResult = uploadWorkerScriptMultipart(account, scriptName, scriptFile)
        
        if (multipartResult is Resource.Success) {
            return@withContext multipartResult
        }
        
        // Fallback to content-only upload
        Timber.d("Multipart upload failed, trying content-only upload")
        val contentResult = uploadWorkerScriptContent(account, scriptName, scriptFile)
        
        if (contentResult is Resource.Success) {
            return@withContext contentResult
        }
        
        // Final fallback to simple upload
        Timber.d("Content upload failed, trying simple upload")
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
                Resource.Error("All upload methods failed. Last error: $errorMsg")
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
    
    /**
     * Update only the KV bindings for an existing Worker Script
     * Does NOT re-upload the script code, only updates the configuration
     * 
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param kvBindings List of (variable name, namespace ID) pairs
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerKvBindings(
        account: Account,
        scriptName: String,
        kvBindings: List<Pair<String, String>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating KV bindings for script '$scriptName' with ${kvBindings.size} bindings")
            
            // Convert pairs to WorkerBinding objects
            val bindings = kvBindings.map { (name, namespaceId) ->
                Timber.d("Adding KV binding: $name -> $namespaceId")
                WorkerBinding(
                    type = "kv_namespace",
                    name = name,
                    namespaceId = namespaceId
                )
            }
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = bindings,
                compatibilityDate = "2024-12-01"
            )
            
            val settingsJson = gson.toJson(settingsRequest)
            Timber.d("Settings request: $settingsJson")
            
            // Convert to RequestBody with multipart content type
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated KV bindings for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Update successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to update bindings: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Failed to update bindings: $errorMsg")
            }
        }
    }
    
    /**
     * Get Worker Script settings (includes bindings)
     * @param account The Cloudflare account
     * @param scriptName Name of the script
     * @return Resource with WorkerScript including bindings
     */
    suspend fun getWorkerSettings(
        account: Account,
        scriptName: String
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully fetched settings for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("No settings returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to fetch settings: $errorMsg")
                Resource.Error("Failed to fetch settings: $errorMsg")
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
