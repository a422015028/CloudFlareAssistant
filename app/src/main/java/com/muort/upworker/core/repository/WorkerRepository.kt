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
    suspend fun updateCustomDomain(
        account: Account,
        domainId: String,
        request: CustomDomainRequest
    ): Resource<CustomDomain> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.updateCustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                domainId = domainId,
                request = request
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("域名更新成功但无返回结果")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Resource.Error("更新自定义域名失败: $errorMsg")
            }
        }
    }
    
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
                compatibilityDate = "2022-01-01" // Use stable compatibility date
            )
            
            // Convert metadata to JSON RequestBody
            val metadataJson = gson.toJson(finalMetadata)
            Timber.d("Upload metadata JSON: $metadataJson")
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaType())
            
            // 读取文件内容以检测脚本类型
            val scriptContent = scriptFile.readText()
            val isESModule = scriptContent.contains("export default") || scriptContent.contains("export {")
            val isServiceWorker = !isESModule && scriptContent.contains("addEventListener")
            
            // 定义可能的 content type 列表（按优先级排序）
            val contentTypesToTry = mutableListOf<String>()
            
            // 根据文件扩展名和内容确定优先尝试的类型
            when {
                scriptFile.extension.lowercase() == "py" -> {
                    contentTypesToTry.add("text/x-python")
                }
                scriptFile.extension.lowercase() == "wasm" -> {
                    contentTypesToTry.add("application/wasm")
                }
                isESModule -> {
                    contentTypesToTry.add("application/javascript+module")
                    contentTypesToTry.add("application/javascript")
                    contentTypesToTry.add("text/javascript")
                }
                isServiceWorker -> {
                    contentTypesToTry.add("application/javascript")
                    contentTypesToTry.add("application/javascript+module")
                    contentTypesToTry.add("text/javascript")
                }
                else -> {
                    // 加密/混淆或未识别的脚本，尝试所有 JavaScript 类型
                    contentTypesToTry.add("application/javascript+module")
                    contentTypesToTry.add("application/javascript")
                    contentTypesToTry.add("text/javascript")
                }
            }
            
            Timber.d("Uploading script: ${scriptFile.name}, will try content types: $contentTypesToTry")
            
            var lastError: String? = null
            var lastErrorBody: String? = null
            
            // 尝试每种 content type
            for ((index, contentTypeStr) in contentTypesToTry.withIndex()) {
                val contentType = contentTypeStr.toMediaType()
                Timber.d("Attempt ${index + 1}/${contentTypesToTry.size}: Using content type: $contentType")
                
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
                        Timber.d("Upload successful with content type: $contentType")
                        return@safeApiCall Resource.Success(it)
                    } ?: return@safeApiCall Resource.Error("Upload successful but no result returned")
                } else {
                    lastErrorBody = response.errorBody()?.string()
                    lastError = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message() 
                        ?: "Unknown error"
                    Timber.w("Upload failed with $contentType: $lastError (code: ${response.code()})")
                    
                    // 如果不是最后一次尝试，继续下一个类型
                    if (index < contentTypesToTry.size - 1) {
                        Timber.d("Retrying with next content type...")
                        continue
                    }
                }
            }
            
            // 所有尝试都失败
            Timber.e("All upload attempts failed. Last error: $lastError, Error body: $lastErrorBody")
            Resource.Error("Upload failed (tried ${contentTypesToTry.size} content types): $lastError")
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
            compatibilityDate = "2022-01-01",
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
                val body = response.body()?.string() ?: ""
                // 检查是否为 multipart 格式
                val boundaryRegex = Regex("--([a-zA-Z0-9]+)")
                val boundaryMatch = boundaryRegex.find(body)
                if (boundaryMatch != null) {
                    val boundary = boundaryMatch.value
                    // 提取 name="xxx.js" 部分
                    val partRegex = Regex("""Content-Disposition: form-data; name=".*?\.js"\r?\n\r?\n([\s\S]*?)\r?\n$boundary""", RegexOption.MULTILINE)
                    val extracted = partRegex.find(body)?.groups?.get(1)?.value ?: body
                    Resource.Success(extracted.trim())
                } else {
                    Resource.Success(body.trim())
                }
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
            
            // First, get existing settings to preserve other bindings
            val existingBindings = mutableListOf<WorkerBinding>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-KV bindings
                    if (binding.type != "kv_namespace") {
                        existingBindings.add(binding)
                    }
                }
            }
            
            // Convert pairs to WorkerBinding objects
            val kvBindingsList = kvBindings.map { (name, namespaceId) ->
                Timber.d("Adding KV binding: $name -> $namespaceId")
                WorkerBinding(
                    type = "kv_namespace",
                    name = name,
                    namespaceId = namespaceId
                )
            }
            
            // Combine existing bindings with new KV bindings
            val allBindings = existingBindings + kvBindingsList
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${kvBindingsList.size} KV)")
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = "2022-01-01"
            )
            
            val settingsJson = gson.toJson(settingsRequest)
            Timber.d("KV Settings request: $settingsRequest")
            
            // Convert to RequestBody for multipart
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
     * Update R2 bindings for an existing Worker Script (without re-uploading script code)
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param r2Bindings List of (variable name, bucket name) pairs
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerR2Bindings(
        account: Account,
        scriptName: String,
        r2Bindings: List<Pair<String, String>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating R2 bindings for script '$scriptName' with ${r2Bindings.size} bindings")
            
            // First, get existing settings to preserve other bindings
            val existingBindings = mutableListOf<WorkerBinding>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-R2 bindings
                    if (binding.type != "r2_bucket") {
                        existingBindings.add(binding)
                    }
                }
            }
            
            // Convert pairs to WorkerBinding objects
            val r2BindingsList = r2Bindings.map { (name, bucketName) ->
                Timber.d("Adding R2 binding: $name -> $bucketName")
                WorkerBinding(
                    type = "r2_bucket",
                    name = name,
                    bucketName = bucketName
                )
            }
            
            // Combine existing bindings with new R2 bindings
            val allBindings = existingBindings + r2BindingsList
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${r2BindingsList.size} R2)")
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = "2022-01-01"
            )
            
            val settingsJson = gson.toJson(settingsRequest)
            Timber.d("R2 Settings request: $settingsRequest")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated R2 bindings for '$scriptName'")
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
     * Update D1 database bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param d1Bindings List of (variable name, database id) pairs
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerD1Bindings(
        account: Account,
        scriptName: String,
        d1Bindings: List<Pair<String, String>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating D1 bindings for script '$scriptName' with ${d1Bindings.size} bindings")
            
            // First, get existing settings to preserve other bindings
            val existingBindings = mutableListOf<WorkerBinding>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-D1 bindings
                    if (binding.type != "d1") {
                        existingBindings.add(binding)
                    }
                }
            }
            
            // Convert pairs to WorkerBinding objects
            val d1BindingsList = d1Bindings.map { (name, databaseId) ->
                Timber.d("Adding D1 binding: $name -> $databaseId")
                WorkerBinding(
                    type = "d1",
                    name = name,
                    databaseId = databaseId
                )
            }
            
            // Combine existing bindings with new D1 bindings
            val allBindings = existingBindings + d1BindingsList
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${d1BindingsList.size} D1)")
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = "2022-01-01"
            )
            
            Timber.d("D1 Settings request: $settingsRequest")
            
            val settingsJson = gson.toJson(settingsRequest)
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated D1 bindings for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Update successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to update D1 bindings: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Failed to update D1 bindings: $errorMsg")
            }
        }
    }
    
    /**
     * Update environment variables for an existing Worker Script
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param variables List of (variable name, variable value, variable type) triples
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerVariables(
        account: Account,
        scriptName: String,
        variables: List<Triple<String, String, String>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating variables for script '$scriptName' with ${variables.size} variables")
            
            // First, get existing settings to preserve other bindings
            val existingBindings = mutableListOf<WorkerBinding>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-variable bindings (KV, R2, Secrets, etc.)
                    if (binding.type != "plain_text" && binding.type != "json") {
                        existingBindings.add(binding)
                    }
                }
            }
            
            // Convert triples to WorkerBinding objects
            val variableBindings = variables.map { (name, value, type) ->
                Timber.d("Adding variable: name='$name', type='$type', value='$value'")
                if (type == "json") {
                    // For JSON type, parse the value and put it in json field
                    val jsonObject = try {
                        com.google.gson.JsonParser.parseString(value)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse JSON value for variable $name")
                        null
                    }
                    WorkerBinding(
                        type = type,
                        name = name,
                        json = jsonObject
                    ).also {
                        Timber.d("Created WorkerBinding: type=${it.type}, name=${it.name}, json=${it.json}")
                    }
                } else {
                    // For plain_text type, use text field
                    WorkerBinding(
                        type = type,
                        name = name,
                        text = value
                    ).also {
                        Timber.d("Created WorkerBinding: type=${it.type}, name=${it.name}, text=${it.text}")
                    }
                }
            }
            
            // Combine existing bindings with new variables
            val allBindings = existingBindings + variableBindings
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${variableBindings.size} variables)")
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = "2022-01-01"
            )
            
            val settingsJson = gson.toJson(settingsRequest)
            Timber.d("Settings request: $settingsJson")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated variables for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Update successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to update variables: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Failed to update variables: $errorMsg")
            }
        }
    }
    
    /**
     * Update secrets for an existing Worker Script
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param secrets List of (secret name, secret value) pairs
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerSecrets(
        account: Account,
        scriptName: String,
        secrets: List<Pair<String, String>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating secrets for script '$scriptName' with ${secrets.size} secrets")
            
            // First, get existing settings to preserve other bindings
            val existingBindings = mutableListOf<WorkerBinding>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-secret bindings
                    if (binding.type != "secret_text") {
                        existingBindings.add(binding)
                    }
                }
            }
            
            // Convert pairs to WorkerBinding objects
            val secretBindings = secrets.map { (name, value) ->
                Timber.d("Adding secret: $name")
                WorkerBinding(
                    type = "secret_text",
                    name = name,
                    text = value
                )
            }
            
            // Combine existing bindings with new secrets
            val allBindings = existingBindings + secretBindings
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${secretBindings.size} secrets)")
            
            // Create settings request
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = "2022-01-01"
            )
            
            val settingsJson = gson.toJson(settingsRequest)
            Timber.d("Settings request (secrets hidden): bindings count = ${allBindings.size}")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated secrets for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Update successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to update secrets: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Failed to update secrets: $errorMsg")
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
        try {
            val response = api.deleteCustomDomain(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                domainId = domainId
            )
            
            Timber.d("Delete custom domain response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
            
            if (response.isSuccessful) {
                Timber.d("Delete custom domain successful")
                Resource.Success(Unit)
            } else {
                val errorMsg = response.message() ?: "HTTP ${response.code()}"
                Timber.e("Delete custom domain failed: $errorMsg")
                Resource.Error("Failed to delete custom domain: $errorMsg")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in deleteCustomDomain")
            Resource.Error("Failed to delete custom domain: ${e.message}")
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
