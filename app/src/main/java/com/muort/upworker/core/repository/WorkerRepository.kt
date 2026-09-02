package com.muort.upworker.core.repository

import android.content.Context
import com.google.gson.Gson
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val api: CloudFlareApi,
    private val gson: Gson
) {
    /**
     * 对 D1 binding 字段执行"统一只保留 `database_id`"的规范化：
     *   - 若对象里只有旧字段 `"id"`（遗留 @SerializedName 或旧响应）：复制值到 `"database_id"` 并移除 `"id"`
     *   - 若对象里同时有两个字段：保留 `"database_id"`（Cloudflare 规范要求），移除已弃用的 `"id"` 以免 API 校验冲突
     *   - 若对象里只有 `"database_id"`：已是最佳状态，保持不变
     *
     * 参考 Cloudflare OpenAPI spec /workers/scripts/{script_name}/settings patch:
     *   D1 binding required = [name, type, database_id]，字段 id 已 deprecated。
     */
    private fun fixD1BindingFields(settingsJson: String): String {
        try {
            val tree = com.google.gson.JsonParser.parseString(settingsJson).asJsonObject
            val bindings = tree.getAsJsonArray("bindings") ?: return settingsJson
            bindings.forEach { el ->
                val obj = el.asJsonObject
                if (obj.get("type")?.asString == "d1") {
                    val idEl = obj.get("id")
                    val dbIdEl = obj.get("database_id")
                    when {
                        // 只有旧字段 id → 迁移到 database_id，移除 id
                        idEl != null && dbIdEl == null -> {
                            obj.add("database_id", idEl)
                            obj.remove("id")
                        }
                        // 两个都有 → 保留规范的 database_id，移除已弃用的 id
                        idEl != null && dbIdEl != null -> {
                            obj.remove("id")
                        }
                        // 只有 database_id → 符合规范，无需处理
                    }
                }
            }
            return gson.toJson(tree)
        } catch (e: Exception) {
            Timber.w(e, "Failed to normalize D1 `database_id` fields, using original JSON")
            return settingsJson
        }
    }

    /**
     * 构造 Worker versioned settings PATCH 请求体 JSON 的统一入口：
     *   1) 将 typed request 序列化为 JSON
     *   2) 若提供了 existingSettings（来自 getWorkerSettings），将 request 未显式设置的
     *      VERSIONED 保留字段（exports、exports_reconciliation、migrations、limits、
     *      cache_options、usage_model、compatibility_flags、placement）从现有设置回填，
     *      避免 Cloudflare PATCH "omit = clear" 语义意外清空关键元数据（ES Module
     *      exports 被清空 → SyntaxError 10021；单 binding 便捷操作意外清兼容性标志
     *      或 placement 放置模式 → Bug W2）。
     *   3) 从最终 JSON 中 **无条件剥离** 4 个 SCRIPT-LEVEL 字段（logpush、
     *      tail_consumers、observability、tags）。这些字段属于 Cloudflare Worker
     *      Versions 模型的脚本级共享配置，**只能通过单独的 PATCH
     *      /workers/scripts/{name}/script-settings (application/json) endpoint 修改**，
     *      绝对不能出现在 versioned /settings multipart body 中。在启用 Versions
     *      的 Worker 上（latest version 未部署时），只要 versioned body 包含任何
     *      字段就会返回 10214，而脚本级 4 字段在错误信息中被显式点名。
     *   4) 最后执行 fixD1BindingFields 双字段兼容。
     */
    private fun buildPatchSettingsJson(
        request: WorkerSettingsRequest,
        existingSettings: WorkerScript? = null
    ): String {
        val reqTree = gson.toJsonTree(request).asJsonObject
        if (existingSettings != null) {
            val existing = gson.toJsonTree(existingSettings).asJsonObject
            // settings 字段在 data class 中默认 null 时，gson.toJsonTree 会产生 JsonNull，
            // 所以这里判断 isJsonNull 而非单纯 has(key) 来识别"调用方未显式提供"。
            // 仅覆盖 PURE VERSIONED-FIELDS（8 项），4 项 script-level 字段绝对不能在
            // versioned body 中出现（见下方 STRIP step）。
            listOf(
                "exports", "exports_reconciliation", "migrations", "limits",
                "cache_options", "usage_model", "compatibility_flags", "placement"
            ).forEach { key ->
                val reqVal = reqTree.get(key)
                if ((reqVal == null || reqVal.isJsonNull) && existing.has(key) && !existing.get(key).isJsonNull) {
                    reqTree.add(key, existing.get(key))
                }
            }
        }
        // ===== CRITICAL: Strip all script-level fields from versioned settings body =====
        // These 4 keys belong to the /script-settings (PATCH JSON) endpoint ONLY.
        // Even if the caller explicitly set them in WorkerSettingsRequest, or the merge
        // step added them (shouldn't after the 8-key list above), we remove them here.
        setOf("logpush", "tail_consumers", "observability", "tags").forEach(reqTree::remove)

        val mergedJson = gson.toJson(reqTree)
        return fixD1BindingFields(mergedJson)
    }

    suspend fun updateCustomDomain(
        account: Account,
        domainId: String,
        request: CustomDomainRequest
    ): Resource<CustomDomain> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.updateCustomDomain(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                domainId = domainId,
                request = request
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_worker_custom_domain_update_no_result))
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Resource.Error(appContext.getString(R.string.repo_worker_custom_domain_update_failed_format, errorMsg))
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
            val scriptContent = scriptFile.readText()
            val isESModule = scriptContent.contains("export default") || scriptContent.contains("export {")
            val isServiceWorker = !isESModule && scriptContent.contains("addEventListener")
            
            val finalMetadata = WorkerMetadata(
                // 优先使用调用方指定的 mainModule（如模板配置的），未指定则自动检测
                mainModule = metadata?.mainModule ?: if (isESModule) scriptFile.name else null,
                bodyPart = if (isServiceWorker || !isESModule) scriptFile.name else null,
                compatibilityDate = metadata?.compatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                bindings = metadata?.bindings,
                usageModel = metadata?.usageModel,
                compatibilityFlags = metadata?.compatibilityFlags,
                vars = metadata?.vars,
                logpush = metadata?.logpush,
                tailConsumers = metadata?.tailConsumers,
                // 保留字段从调用方 metadata 原样透传，避免后续 PATCH omit=clear 被清空
                exports = metadata?.exports,
                exportsReconciliation = metadata?.exportsReconciliation,
                migrations = metadata?.migrations,
                limits = metadata?.limits,
                tags = metadata?.tags,
                cacheOptions = metadata?.cacheOptions,
                observability = metadata?.observability
            )
            
            val metadataJson = gson.toJson(finalMetadata)
            Timber.d("Upload metadata JSON: $metadataJson")
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaType())
            
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
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
            
            // First, get existing settings to preserve other bindings and compatibilityDate
            val existingBindings = mutableListOf<WorkerBinding>()
            var existingCompatibilityDate: String? = null
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-KV bindings
                    if (binding.type != "kv_namespace") {
                        existingBindings.add(binding)
                    }
                }
                existingCompatibilityDate = settingsResult.data.compatibilityDate
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
            
            // Create settings request with preserved compatibilityDate + compatibilityFlags + placement.
            // All three fields must be present in the PATCH body; any omission would be
            // interpreted as "clear this field" by the Worker settings PATCH semantics.
            val existingSettings = (settingsResult as? Resource.Success)?.data
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = existingCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                compatibilityFlags = existingSettings?.compatibilityFlags,
                placement = existingSettings?.placement
            )

            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)
            Timber.d("KV Settings request: $settingsRequest")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
            
            // First, get existing settings to preserve other bindings and compatibilityDate
            val existingBindings = mutableListOf<WorkerBinding>()
            var existingCompatibilityDate: String? = null
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-R2 bindings
                    if (binding.type != "r2_bucket") {
                        existingBindings.add(binding)
                    }
                }
                existingCompatibilityDate = settingsResult.data.compatibilityDate
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
            
            // Create settings request with preserved compatibilityDate + compatibilityFlags + placement.
            // All three fields must be present in the PATCH body; any omission would be
            // interpreted as "clear this field" by the Worker settings PATCH semantics.
            val existingSettings = (settingsResult as? Resource.Success)?.data
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = existingCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                compatibilityFlags = existingSettings?.compatibilityFlags,
                placement = existingSettings?.placement
            )

            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)
            Timber.d("R2 Settings request: $settingsRequest")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
            
            // First, get existing settings to preserve other bindings and compatibilityDate
            val existingBindings = mutableListOf<WorkerBinding>()
            var existingCompatibilityDate: String? = null
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-D1 bindings
                    if (binding.type != "d1") {
                        existingBindings.add(binding)
                    }
                }
                existingCompatibilityDate = settingsResult.data.compatibilityDate
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
            
            // Create settings request with preserved compatibilityDate + compatibilityFlags + placement.
            // All three fields must be present in the PATCH body; any omission would be
            // interpreted as "clear this field" by the Worker settings PATCH semantics.
            val existingSettings = (settingsResult as? Resource.Success)?.data
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = existingCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                compatibilityFlags = existingSettings?.compatibilityFlags,
                placement = existingSettings?.placement
            )

            Timber.d("D1 Settings request: $settingsRequest")

            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
     * Update service bindings for an existing Worker Script (without re-uploading script code)
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param serviceBindings List of (variable name, target worker name, target environment or null) triples
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerServiceBindings(
        account: Account,
        scriptName: String,
        serviceBindings: List<Triple<String, String, String?>>
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating service bindings for script '$scriptName' with ${serviceBindings.size} bindings")

            // First, get existing settings to preserve other bindings and compatibilityDate
            val existingBindings = mutableListOf<WorkerBinding>()
            var existingCompatibilityDate: String? = null
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-service bindings
                    if (binding.type != "service") {
                        existingBindings.add(binding)
                    }
                }
                existingCompatibilityDate = settingsResult.data.compatibilityDate
            }

            // Convert triples to WorkerBinding objects
            val svcBindingsList = serviceBindings.map { (name, serviceName, environment) ->
                Timber.d("Adding service binding: $name -> $serviceName ($environment)")
                WorkerBinding(
                    type = "service",
                    name = name,
                    service = serviceName,
                    environment = environment ?: "production"
                )
            }

            // Combine existing bindings with new service bindings
            val allBindings = existingBindings + svcBindingsList
            Timber.d("Total bindings: ${allBindings.size} (${existingBindings.size} preserved + ${svcBindingsList.size} service)")

            // Create settings request with preserved compatibilityDate + compatibilityFlags + placement.
            // All three fields must be present in the PATCH body; any omission would be
            // interpreted as "clear this field" by the Worker settings PATCH semantics.
            val existingSettings = (settingsResult as? Resource.Success)?.data
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = existingCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                compatibilityFlags = existingSettings?.compatibilityFlags,
                placement = existingSettings?.placement
            )

            Timber.d("Service Settings request: $settingsRequest")

            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)

            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())

            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                settings = settingsBody
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated service bindings for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Update successful but no result returned")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to update service bindings: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error("Failed to update service bindings: $errorMsg")
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
            
            // First, get existing settings to preserve other bindings and compatibilityDate
            val existingBindings = mutableListOf<WorkerBinding>()
            var existingCompatibilityDate: String? = null
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    // Keep all non-variable bindings (KV, R2, Secrets, etc.)
                    if (binding.type != "plain_text" && binding.type != "json") {
                        existingBindings.add(binding)
                    }
                }
                existingCompatibilityDate = settingsResult.data.compatibilityDate
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
            
            // Create settings request with preserved compatibilityDate + compatibilityFlags + placement.
            // All three fields must be present in the PATCH body; any omission would be
            // interpreted as "clear this field" by the Worker settings PATCH semantics.
            val existingSettings = (settingsResult as? Resource.Success)?.data
            val settingsRequest = WorkerSettingsRequest(
                bindings = allBindings,
                compatibilityDate = existingCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE,
                compatibilityFlags = existingSettings?.compatibilityFlags,
                placement = existingSettings?.placement
            )

            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)
            Timber.d("Settings request: $settingsJson")
            
            // Convert to RequestBody for multipart
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())
            
            // Call API to update settings
            val response = api.updateWorkerSettings(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
     * Update secrets for an existing Worker Script using bulk secrets API
     * Uses PATCH /secrets-bulk endpoint which supports:
     * - Create/update: set secret object {type, name, text}
     * - Delete: set to null
     * - Unchanged: omit from request
     *
     * @param account The Cloudflare account
     * @param scriptName Name of the existing script
     * @param secrets List of (secret name, secret value) pairs. Empty value means unchanged existing secret.
     * @return Resource indicating success or error
     */
    suspend fun updateWorkerSecrets(
        account: Account,
        scriptName: String,
        secrets: List<Pair<String, String>>
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Updating secrets for script '$scriptName' with ${secrets.size} secrets")

            // 1. Get existing secret_text binding names
            val existingSecretNames = mutableSetOf<String>()
            val settingsResult = getWorkerSettings(account, scriptName)
            if (settingsResult is Resource.Success) {
                settingsResult.data.bindings?.forEach { binding ->
                    if (binding.type == "secret_text") {
                        existingSecretNames.add(binding.name)
                    }
                }
            }

            // 2. Build secrets-bulk request map
            val newSecretNames = secrets.map { it.first }.toSet()
            val secretsMap = mutableMapOf<String, Any?>()

            // Deleted secrets: existed before but not in new list -> set to null
            for (name in existingSecretNames) {
                if (name !in newSecretNames) {
                    secretsMap[name] = null
                    Timber.d("Marking secret for deletion: $name")
                }
            }

            // Created/updated secrets: have non-empty value -> set secret object
            for ((name, value) in secrets) {
                if (value.isNotEmpty()) {
                    secretsMap[name] = mapOf(
                        "type" to "secret_text",
                        "name" to name,
                        "text" to value
                    )
                    Timber.d("Adding/updating secret: $name")
                }
                // Empty value = unchanged existing secret, omit from request
            }

            // No changes needed
            if (secretsMap.isEmpty()) {
                Timber.d("No secret changes for '$scriptName'")
                return@safeApiCall Resource.Success(Unit)
            }

            Timber.d("Secrets bulk update: ${secretsMap.size} operations for '$scriptName'")

            // 3. Build JSON request body
            // Need serializeNulls() so deleted secrets (set to null) are included in JSON
            val bulkGson = com.google.gson.GsonBuilder().serializeNulls().create()
            val requestBody = bulkGson.toJson(mapOf("secrets" to secretsMap))
            val body = requestBody.toRequestBody("application/json".toMediaType())

            // 4. Call bulk secrets API
            val response = api.updateSecretsBulk(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                body = body
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated secrets for '$scriptName'")
                Resource.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to update secrets: Response code: ${response.code()}, Error body: $errorBody")
                Resource.Error(appContext.getString(R.string.repo_worker_update_secrets_failed_format, errorMsg))
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
    
    /**
     * 更新 Worker 运行时设置（兼容日期、兼容性标志、放置），不重新上传脚本代码。
     *
     * @param existingSettings 可选：来自 getWorkerSettings 的现有设置。当提供时，会自动保留
     * 调用方未显式设置的字段（exports、migrations、limits、tags、usage_model 等），
     * 避免 ES Module 脚本因 exports 被清空导致的 SyntaxError 10021。
     */
    suspend fun updateWorkerSettings(
        account: Account,
        scriptName: String,
        settingsRequest: WorkerSettingsRequest,
        existingSettings: WorkerScript? = null
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            val settingsJson = buildPatchSettingsJson(settingsRequest, existingSettings)
            val settingsBody = settingsJson.toRequestBody("application/json".toMediaType())

            val response = api.updateWorkerSettings(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                account.accountId,
                scriptName,
                settingsBody
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_worker_update_env_no_result))
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to update runtime settings: $errorMsg")
                Resource.Error(appContext.getString(R.string.repo_worker_update_env_failed_format, errorMsg))
            }
        }
    }

    /**
     * 更新 Worker SCRIPT-LEVEL 共享配置（跨所有 version，不随版本变更）。
     * 唯一正确的 endpoint：PATCH /workers/scripts/{name}/script-settings (application/json)
     * 可修改的字段 = 4 个：observability（启用/配置可观测性）、logpush（启用 Logpush）、
     * tail_consumers（配置 Tail Consumer）、tags（脚本标签）。
     *
     * PATCH 语义：省略=不动；传值=覆盖；传 null=清除。versioned 字段（bindings、
     * compatibility 系列、exports 等）完全不受此 API 影响。
     *
     * 这 4 个字段 **绝对不能通过 versioned PATCH /settings endpoint 写入**：
     * 在 Worker Versions 模式下（latest version 未部署），versioned PATCH 会直接返回 10214
     * 并提示"使用 script-settings API 修改 logpush/tail_consumers"。所有其他路径
     * （applyObservability / updateWorkerRuntimeSettings / 上传脚本后阶段）必须经过此函数。
     *
     * @param bodyJson 已序列化的 JSON 字符串，只包含 caller 想改动的 script-level 字段。
     */
    suspend fun updateWorkerScriptSettings(
        account: Account,
        scriptName: String,
        bodyJson: String
    ): Resource<WorkerScript> = withContext(Dispatchers.IO) {
        safeApiCall {
            val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
            val response = api.updateWorkerScriptSettings(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                body = requestBody
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Timber.d("Successfully updated script-level settings for '$scriptName'")
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_worker_update_env_no_result))
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to update script-level settings: $errorMsg")
                Resource.Error(appContext.getString(R.string.repo_worker_update_env_failed_format, errorMsg))
            }
        }
    }

    suspend fun deleteWorkerScript(
        account: Account,
        scriptName: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteWorkerScript(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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

    suspend fun listWorkerVersions(
        account: Account,
        scriptName: String
    ): Resource<List<WorkerVersion>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Fetching worker versions for account: ${account.accountId}, script: $scriptName")
            Timber.d("Auth - Token: ${AuthHelper.getBearerToken(account) != null}, Email: ${AuthHelper.getEmail(account) != null}, API Key: ${AuthHelper.getGlobalApiKey(account) != null}")
            
            val response = api.listWorkerVersions(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val versions = response.body()?.result?.items ?: emptyList()
                Timber.d("Successfully fetched ${versions.size} versions")
                Resource.Success(versions)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Timber.e("Failed to list versions: $errorMsg, code: ${response.code()}")
                Resource.Error(appContext.getString(R.string.repo_worker_versions_failed_format, errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception when fetching worker versions for script: $scriptName")
            Resource.Error(appContext.getString(R.string.repo_worker_versions_exception_format, e.javaClass.simpleName, e.message))
        }
    }

    suspend fun getWorkerVersion(
        account: Account,
        scriptName: String,
        versionId: String
    ): Resource<WorkerVersion> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getWorkerVersion(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                versionId = versionId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("No version returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to get version: $errorMsg")
            }
        }
    }

    suspend fun deployWorkerVersion(
        account: Account,
        scriptName: String,
        versionId: String
    ): Resource<WorkerVersion> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deployWorkerVersion(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                versionId = versionId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("No version returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to deploy version: $errorMsg")
            }
        }
    }

    suspend fun deleteWorkerVersion(
        account: Account,
        scriptName: String,
        versionId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteWorkerVersion(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                workerId = scriptName,
                versionId = versionId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Resource.Error(appContext.getString(R.string.repo_worker_version_delete_failed_format, errorMsg))
            }
        }
    }

    /**
     * 列出 Worker 脚本的部署记录
     * GET /accounts/{account_id}/workers/scripts/{script_name}/deployments
     */
    suspend fun listWorkerDeployments(
        account: Account,
        scriptName: String
    ): Resource<List<WorkerDeployment>> = withContext(Dispatchers.IO) {
        try {
            val response = api.listWorkerDeployments(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val deployments = response.body()?.result ?: emptyList()
                Timber.d("Successfully fetched ${deployments.size} deployments for script: $scriptName")
                Resource.Success(deployments)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to list deployments: $errorMsg, code: ${response.code()}")
                Resource.Error(appContext.getString(R.string.repo_worker_deployments_failed_format, errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception when fetching worker deployments for script: $scriptName")
            Resource.Error(appContext.getString(R.string.repo_worker_deployments_exception_format, e.javaClass.simpleName, e.message))
        }
    }

    /**
     * 获取 Worker 脚本的特定部署详情
     * GET /accounts/{account_id}/workers/scripts/{script_name}/deployments/{deployment_id}
     */
    suspend fun getWorkerDeployment(
        account: Account,
        scriptName: String,
        deploymentId: String
    ): Resource<WorkerDeployment> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWorkerDeployment(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                deploymentId = deploymentId
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_worker_deployment_detail_no_result))
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Timber.e("Failed to get deployment: $errorMsg, code: ${response.code()}")
                Resource.Error(appContext.getString(R.string.repo_worker_deployment_detail_failed_format, errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception when fetching worker deployment: $scriptName/$deploymentId")
            Resource.Error(appContext.getString(R.string.repo_worker_deployment_detail_exception_format, e.javaClass.simpleName, e.message))
        }
    }

    // Routes
    suspend fun listRoutes(account: Account, zoneId: String): Resource<List<Route>> = 
        withContext(Dispatchers.IO) {
            if (zoneId.isBlank()) {
                return@withContext Resource.Error("Zone ID is required for route operations")
            }
            
            safeApiCall {
                val response = api.listRoutes(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    zoneId = zoneId
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
        zoneId: String,
        pattern: String,
        scriptName: String
    ): Resource<Route> = withContext(Dispatchers.IO) {
        if (zoneId.isBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.createRoute(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                zoneId = zoneId,
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
        zoneId: String,
        routeId: String,
        pattern: String,
        scriptName: String
    ): Resource<Route> = withContext(Dispatchers.IO) {
        if (zoneId.isBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.updateRoute(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                zoneId = zoneId,
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
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
        zoneId: String,
        routeId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        if (zoneId.isBlank()) {
            return@withContext Resource.Error("Zone ID is required for route operations")
        }
        
        safeApiCall {
            val response = api.deleteRoute(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                zoneId = zoneId,
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

    // ================= Worker Script-Level Feature Toggles =================
    // 对应 Cloudflare Worker Settings 页面的三个开关：
    //   1. 启用自定义子域名（workers.dev 子域名）→ subdomain.enabled
    //   2. 可观测性（Observability）          → script-settings.observability.enabled
    //   3. Logs 持久化（Logs Persist）         → script-settings.observability.logs.persist

    data class WorkerScriptSettings(
        val observabilityEnabled: Boolean = false,
        val logsPersist: Boolean = false,
        val logpushLegacy: Boolean = false,
        val raw: Map<String, Any> = emptyMap()
    )

    data class WorkerSubdomainStatus(
        val enabled: Boolean = false,
        val previewsEnabled: Boolean = false,
        val subdomain: String? = null,
        val raw: Map<String, Any> = emptyMap()
    )

    /**
     * GET /accounts/{id}/workers/scripts/{name}/script-settings
     * 读取 observability + logs.persist 等脚本级开关。
     */
    suspend fun getScriptSettings(account: Account, scriptName: String): Resource<WorkerScriptSettings> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getWorkerScriptSettings(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName
                )
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.result != null) {
                    val result = body.result
                    val observabilityMap = (result["observability"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }.orEmpty()
                    val observabilityEnabled = observabilityMap["enabled"] as? Boolean ?: false
                    val logsMap = (observabilityMap["logs"] as? Map<*, *>)
                        ?.mapKeys { it.key.toString() }.orEmpty()
                    val logsPersist = logsMap["persist"] as? Boolean
                        ?: (result["logpush"] as? Boolean)
                        ?: false
                    val logpushLegacy = result["logpush"] as? Boolean ?: false
                    Resource.Success(
                        WorkerScriptSettings(
                            observabilityEnabled = observabilityEnabled,
                            logsPersist = logsPersist,
                            logpushLegacy = logpushLegacy,
                            raw = result.mapKeys { it.key.toString() }
                        )
                    )
                } else {
                    val errorMsg = body?.errors?.firstOrNull()?.message ?: response.message()
                    Resource.Error(appContext.getString(R.string.worker_settings_fetch_failed_format, errorMsg))
                }
            }
        }

    /**
     * PATCH /accounts/{id}/workers/scripts/{name}/script-settings
     * 写入可观测性开关 + Logs 持久化开关。
     * observability 对象必须完整给出（observability.logs.enabled / invocation_logs 必填），
     * 所以我们先 GET 回来的 raw 作为基线，再覆盖 observability.enabled / logs.persist。
     */
    suspend fun patchScriptSettings(
        account: Account,
        scriptName: String,
        observabilityEnabled: Boolean,
        logsPersist: Boolean,
        baselineRaw: Map<String, Any> = emptyMap()
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val baseObservability = ((baselineRaw["observability"] as? Map<*, *>)
                ?.mapKeys { it.key.toString() }.orEmpty()).toMutableMap()
            // observability.enabled 必填
            baseObservability["enabled"] = observabilityEnabled
            // logs 子对象必填：enabled + invocation_logs，persist 是我们要写的持久化开关
            val baseLogs = ((baseObservability["logs"] as? Map<*, *>)
                ?.mapKeys { it.key.toString() }.orEmpty()).toMutableMap()
            baseLogs["enabled"] = baseLogs["enabled"] as? Boolean ?: true
            baseLogs["invocation_logs"] = baseLogs["invocation_logs"] as? Boolean ?: true
            baseLogs["persist"] = logsPersist
            baseObservability["logs"] = baseLogs
            // traces 对象不是必填，没有就不写

            // 用 Gson 构造 JsonObject / JsonArray（项目统一使用 Gson，不引入 kotlinx.serialization JSON DSL）
            val gson = Gson()
            val json = com.google.gson.JsonObject()

            val obs = com.google.gson.JsonObject()
            obs.addProperty("enabled", observabilityEnabled)
            (baseObservability["head_sampling_rate"] as? Number)?.let {
                obs.addProperty("head_sampling_rate", it.toDouble())
            }
            (baseObservability["redact_query_string"] as? Boolean)?.let {
                obs.addProperty("redact_query_string", it)
            }

            val logsJson = com.google.gson.JsonObject()
            logsJson.addProperty("enabled", baseLogs["enabled"] as Boolean)
            logsJson.addProperty("invocation_logs", baseLogs["invocation_logs"] as Boolean)
            logsJson.addProperty("persist", logsPersist)
            (baseLogs["head_sampling_rate"] as? Number)?.let {
                logsJson.addProperty("head_sampling_rate", it.toDouble())
            }
            (baseLogs["destinations"] as? List<*>)?.let { dests ->
                val arr = com.google.gson.JsonArray()
                dests.mapNotNull { d -> d as? String }.forEach { arr.add(it) }
                logsJson.add("destinations", arr)
            }
            obs.add("logs", logsJson)

            (baseObservability["traces"] as? Map<*, *>)?.let { traces ->
                val t = traces.mapKeys { it.key.toString() }
                val tracesJson = com.google.gson.JsonObject()
                (t["enabled"] as? Boolean)?.let { tracesJson.addProperty("enabled", it) }
                (t["persist"] as? Boolean)?.let { tracesJson.addProperty("persist", it) }
                (t["head_sampling_rate"] as? Number)?.let { tracesJson.addProperty("head_sampling_rate", it.toDouble()) }
                (t["propagation_policy"] as? String)?.let { tracesJson.addProperty("propagation_policy", it) }
                (t["destinations"] as? List<*>)?.let { dests ->
                    val arr = com.google.gson.JsonArray()
                    dests.mapNotNull { d -> d as? String }.forEach { arr.add(it) }
                    tracesJson.add("destinations", arr)
                }
                obs.add("traces", tracesJson)
            }

            json.add("observability", obs)
            // logpush 是老版本保留字段（Legacy Workers，非 Versions），同步写一下
            (baselineRaw["logpush"] as? Boolean)?.let {
                json.addProperty("logpush", logsPersist || it)
            }
            // tags（如有保留，一般是字符串数组）
            (baselineRaw["tags"] as? List<*>)?.let { tags ->
                val arr = com.google.gson.JsonArray()
                tags.mapNotNull { t -> t as? String }.forEach { arr.add(it) }
                json.add("tags", arr)
            }
            // tail_consumers（如有保留，是任意元素数组，直接序列化成 JsonElement）
            (baselineRaw["tail_consumers"] as? List<*>)?.let { tcs ->
                json.add("tail_consumers", gson.toJsonTree(tcs))
            }

            val body = gson.toJson(json).toRequestBody("application/json".toMediaType())
            val response = api.updateWorkerScriptSettings(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                body = body
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error(appContext.getString(R.string.worker_settings_update_failed_format, errorMsg))
            }
        }
    }

    /**
     * GET /accounts/{id}/workers/scripts/{name}/subdomain
     * 读取脚本的 workers.dev 自定义子域名是否已启用。
     */
    suspend fun getSubdomainStatus(account: Account, scriptName: String): Resource<WorkerSubdomainStatus> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getWorkerSubdomainStatus(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName
                )
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.result != null) {
                    val result = body.result.mapKeys { it.key.toString() }
                    Resource.Success(
                        WorkerSubdomainStatus(
                            enabled = result["enabled"] as? Boolean ?: false,
                            previewsEnabled = result["previews_enabled"] as? Boolean ?: false,
                            subdomain = result["subdomain"] as? String,
                            raw = result
                        )
                    )
                } else {
                    val errorMsg = body?.errors?.firstOrNull()?.message ?: response.message()
                    Resource.Error(appContext.getString(R.string.worker_subdomain_status_fetch_failed_format, errorMsg))
                }
            }
        }

    /**
     * POST /accounts/{id}/workers/scripts/{name}/subdomain
     * 写入 workers.dev 自定义子域名开关（Cloudflare 文档 endpoint 必填 enabled）。
     */
    suspend fun updateSubdomainStatus(
        account: Account,
        scriptName: String,
        enabled: Boolean,
        previewsEnabled: Boolean? = null
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val request = if (previewsEnabled == null) {
                com.muort.upworker.core.model.WorkerSubdomainEnableRequest(enabled = enabled)
            } else {
                com.muort.upworker.core.model.WorkerSubdomainEnableRequest(
                    enabled = enabled,
                    previewsEnabled = previewsEnabled
                )
            }
            val response = api.enableWorkerSubdomain(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                request = request
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error(appContext.getString(R.string.worker_subdomain_update_failed_format, errorMsg))
            }
        }
    }

    suspend fun listTails(account: Account, scriptName: String): Resource<List<TailResult>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listTails(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error(appContext.getString(R.string.repo_worker_log_channel_failed_format, errorMsg))
                }
            }
        }

    suspend fun createTail(account: Account, scriptName: String): Resource<TailResult> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                listTails(account, scriptName).let { listResult ->
                    if (listResult is Resource.Success && listResult.data.isNotEmpty()) {
                        listResult.data.first().id.let { tailId ->
                            api.deleteTail(
                                token = AuthHelper.getBearerToken(account),
                                email = AuthHelper.getEmail(account),
                                apiKey = AuthHelper.getGlobalApiKey(account),
                                accountId = account.accountId,
                                scriptName = scriptName,
                                id = tailId
                            )
                        }
                    }
                }
                
                val response = api.createTail(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName,
                    body = CreateTailRequest()
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_worker_log_channel_create_no_result))
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error(appContext.getString(R.string.repo_worker_log_channel_create_failed_format, errorMsg))
                }
            }
        }

    suspend fun deleteTail(account: Account, scriptName: String, tailId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteTail(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName,
                    id = tailId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error(appContext.getString(R.string.repo_worker_log_channel_delete_failed_format, errorMsg))
                }
            }
        }

    suspend fun listSchedules(account: Account, scriptName: String): Resource<List<Schedule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listSchedules(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result?.schedules ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error(appContext.getString(R.string.repo_worker_trigger_list_failed_format, errorMsg))
                }
            }
        }

    suspend fun updateSchedules(account: Account, scriptName: String, schedules: List<String>): Resource<List<Schedule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val request = schedules.map { ScheduleRequest(it) }
                val response = api.updateSchedules(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName,
                    schedules = request
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result?.schedules ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error(appContext.getString(R.string.repo_worker_trigger_update_failed_format, errorMsg))
                }
            }
        }

    // ==================== P1-1A Post-Upload pipeline (3 stages) ====================

    /**
     * Public entry: runs the 3 post-upload stages in order after a successful script upload.
     * Policy (user-chosen 2026-08-31):
     *  - Individual stage failures DO NOT abort later stages; upload overall result is preserved
     *  - Deployment only hits network if versionId is non-null (Versions API path).
     *    Otherwise PUT /scripts already deployed at 100% implicitly, so stage 3 is a
     *    Success no-op.
     */
    suspend fun afterUpload(
        account: Account,
        uploadResult: Resource<WorkerScript>,
        scriptName: String,
        versionId: String? = null,
        percentage: Int = 100,
        enabledStages: Set<WorkerPostStageKind> = WorkerPostStageKind.values().toSet()
    ): WorkerAfterUploadResult = withContext(Dispatchers.IO) {
        val stages = mutableListOf<WorkerPostActionStage>()
        if (uploadResult !is Resource.Success) {
            return@withContext WorkerAfterUploadResult(uploadResult, stages)
        }
        if (WorkerPostStageKind.Observability in enabledStages) {
            runCatching {
                stages.add(applyObservability(account, scriptName))
            }.getOrElse { t: Throwable ->
                stages.add(
                    WorkerPostActionStage.Failure(
                        kind = WorkerPostStageKind.Observability,
                        messageResId = R.string.worker_post_observability_fail_format,
                        formatArgs = arrayOf(t.message ?: "Unknown error")
                    )
                )
            }
        }
        if (WorkerPostStageKind.Subdomain in enabledStages) {
            runCatching {
                stages.add(enableSubdomain(account, scriptName))
            }.getOrElse { t: Throwable ->
                stages.add(
                    WorkerPostActionStage.Failure(
                        kind = WorkerPostStageKind.Subdomain,
                        messageResId = R.string.worker_post_subdomain_fail_format,
                        formatArgs = arrayOf(t.message ?: "Unknown error")
                    )
                )
            }
        }
        if (WorkerPostStageKind.Deployment in enabledStages) {
            runCatching {
                stages.add(promotePercentageDeployment(account, scriptName, versionId, percentage))
            }.getOrElse { t: Throwable ->
                stages.add(
                    WorkerPostActionStage.Failure(
                        kind = WorkerPostStageKind.Deployment,
                        messageResId = R.string.worker_post_deploy_fail_format,
                        formatArgs = arrayOf(t.message ?: "Unknown error")
                    )
                )
            }
        }
        WorkerAfterUploadResult(uploadResult, stages.toList())
    }

    /** Stage 1: Enable Observability — PATCH SCRIPT-LEVEL shared settings with
     *  traces+logs enabled.
     *
     *  Observability is a script-level field (cross-version, shared by all versions),
     *  so it MUST be modified through the dedicated PATCH /script-settings JSON endpoint
     *  — never through the versioned multipart PATCH /settings endpoint. On Workers with
     *  Versions enabled, the versioned endpoint returns error 10214 whenever latest
     *  version is not currently deployed (and the endpoint has no effect on script-level
     *  fields anyway). The /script-settings PATCH semantics are: omitted fields = keep,
     *  supplied fields = overwrite. So by sending only the "observability" key we
     *  preserve the user's existing logpush/tail_consumers/tags untouched AND we
     *  never touch any versioned settings (bindings, exports, compatibility flags,
     *  placement, ...) — this completely avoids both 10214 and any omit=clear risk
     *  on the versioned settings side.
     */
    suspend fun applyObservability(account: Account, scriptName: String): WorkerPostActionStage =
        withContext(Dispatchers.IO) {
            try {
                // Only set the observability sub-tree (all defaults = enabled head/traces/logs)
                val bodyObj = mapOf<String, Any?>(
                    "observability" to WorkerObservability()
                )
                val bodyJson = gson.toJson(bodyObj)
                val result = updateWorkerScriptSettings(account, scriptName, bodyJson)
                if (result is Resource.Success) {
                    WorkerPostActionStage.Success(
                        kind = WorkerPostStageKind.Observability,
                        messageResId = R.string.worker_post_observability_ok
                    )
                } else {
                    val err = (result as? Resource.Error)?.message
                        ?: "Unknown observability PATCH error"
                    WorkerPostActionStage.Failure(
                        kind = WorkerPostStageKind.Observability,
                        messageResId = R.string.worker_post_observability_fail_format,
                        formatArgs = arrayOf(err)
                    )
                }
            } catch (t: Throwable) {
                WorkerPostActionStage.Failure(
                    kind = WorkerPostStageKind.Observability,
                    messageResId = R.string.worker_post_observability_fail_format,
                    formatArgs = arrayOf(t.message ?: "Unknown error")
                )
            }
        }

    /**
     * Stage 2: Enable workers.dev subdomain for this script.
     * Special-case: HTTP 403 usually means the account-level workers.dev subdomain has not
     * been registered yet via the Cloudflare dashboard, so we append a guidance sentence.
     */
    suspend fun enableSubdomain(account: Account, scriptName: String): WorkerPostActionStage =
        withContext(Dispatchers.IO) {
            try {
                val response = api.enableWorkerSubdomain(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    scriptName = scriptName,
                    request = WorkerSubdomainEnableRequest()
                )
                when {
                    response.isSuccessful && response.body()?.success == true -> {
                        WorkerPostActionStage.Success(
                            kind = WorkerPostStageKind.Subdomain,
                            messageResId = R.string.worker_post_subdomain_ok_format,
                            formatArgs = arrayOf(scriptName)
                        )
                    }
                    response.code() == 403 -> {
                        val rawErr = response.body()?.errors?.firstOrNull()?.message
                            ?: response.errorBody()?.string()?.take(200)
                            ?: "HTTP 403 Forbidden"
                        val dashboardHint = appContext.getString(
                            R.string.worker_post_subdomain_403_dashboard_hint
                        )
                        val combined = "$rawErr\n$dashboardHint"
                        WorkerPostActionStage.Failure(
                            kind = WorkerPostStageKind.Subdomain,
                            messageResId = R.string.worker_post_subdomain_fail_format,
                            formatArgs = arrayOf(combined)
                        )
                    }
                    else -> {
                        val err = response.body()?.errors?.firstOrNull()?.message
                            ?: response.errorBody()?.string()?.take(200)
                            ?: response.message()
                        WorkerPostActionStage.Failure(
                            kind = WorkerPostStageKind.Subdomain,
                            messageResId = R.string.worker_post_subdomain_fail_format,
                            formatArgs = arrayOf(err)
                        )
                    }
                }
            } catch (t: Throwable) {
                WorkerPostActionStage.Failure(
                    kind = WorkerPostStageKind.Subdomain,
                    messageResId = R.string.worker_post_subdomain_fail_format,
                    formatArgs = arrayOf(t.message ?: "Unknown error")
                )
            }
        }

    /**
     * Stage 3: Promote a Worker version to a traffic percentage using POST /deployments.
     *
     * Decision (user-chosen 2026-08-31: "两种并存，自动选择"):
     *  - versionId == null  → Legacy PUT /scripts path, which already auto-deploys at 100%.
     *                         Return a descriptive Success (no-op) so the 3-stage UI still shows
     *                         "Deployment" as complete.
     *  - versionId != null  → Versions API active → actually call POST /deployments with the
     *                         strategy=percentage payload.
     */
    suspend fun promotePercentageDeployment(
        account: Account,
        scriptName: String,
        versionId: String?,
        percentage: Int
    ): WorkerPostActionStage = withContext(Dispatchers.IO) {
        val safePercentage = percentage.coerceIn(0, 100)
        if (versionId.isNullOrBlank()) {
            return@withContext WorkerPostActionStage.Success(
                kind = WorkerPostStageKind.Deployment,
                messageResId = R.string.worker_post_deploy_ok_format,
                formatArgs = arrayOf(0, safePercentage)
            )
        }
        try {
            val req = WorkerDeploymentCreateRequest(
                strategy = "percentage",
                versions = listOf(
                    WorkerDeploymentVersionRequest(
                        versionId = versionId,
                        percentage = safePercentage
                    )
                ),
                message = "CloudFlareAssistant promote $safePercentage%"
            )
            val response = api.createWorkerDeployment(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                scriptName = scriptName,
                request = req
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val realVersion = versionId.filter { it.isDigit() }.toIntOrNull() ?: 0
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_ok_format,
                    formatArgs = arrayOf(realVersion, safePercentage)
                )
            } else {
                val err = response.body()?.errors?.firstOrNull()?.message
                    ?: response.errorBody()?.string()?.take(200)
                    ?: response.message()
                WorkerPostActionStage.Failure(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_warn_format,
                    formatArgs = arrayOf(err)
                )
            }
        } catch (t: Throwable) {
            WorkerPostActionStage.Failure(
                kind = WorkerPostStageKind.Deployment,
                messageResId = R.string.worker_post_deploy_warn_format,
                formatArgs = arrayOf(t.message ?: "Unknown error")
            )
        }
    }

    // ========================================================================
    // P1-3: detectAndAppendNodejsCompat — auto Node.js compat flag detection
    // ========================================================================

    /**
     * Scans the Worker script source for 7+1 well-known Node.js compatibility signals
     * (aligned with P1-3 DETECT spec) and decides whether to append the
     * `"nodejs_compat"` compatibility_flag to [existingFlags].
     *
     * Pure string function — **no I/O, no HTTP, no suspend**. Safe to call from
     * ViewModel UI / unit tests / Repository HTTP pipeline without dispatcher hops.
     *
     * @param scriptContent  Full Worker script as UTF-8 text. Will be scanned as-is
     *                       (multiline OK; comments/strings NOT stripped — false positives
     *                       accepted because flag is additive-only and flag-dup is handled).
     * @param existingFlags  Caller-supplied compatibility_flags list (or null if empty).
     *                       Entries preserved in original order; new `"nodejs_compat"` appends
     *                       at END when first pattern hit.
     */
    fun detectAndAppendNodejsCompat(
        scriptContent: String,
        existingFlags: List<String>?
    ): WorkerNodejsDetectResult {
        // ---- P1-3 DETECT: 7+1 patterns ordered per spec (friendlyName, regex) ----
        // Distinct hits are preserved in this order (no scrambling), so users see a
        // stable left-to-right display of what their script is actually using.
        val patterns: List<Pair<String, Regex>> = listOf(
            "__commonJS" to Regex("""__commonJS\b"""),
            """require(" (CJS)""" to Regex("""require\s*\(\s*["']"""),
            """require("node:" (内置模块)""" to Regex("""require\s*\(\s*["']node:"""),
            "process.*" to Regex("""(?<![.\w])process\s*\.\s*\w+"""),
            "globalThis.process" to Regex("""globalThis\s*\.\s*process\b"""),
            // Negative lookahead (?!This) ensures we never match the "global" prefix
            // inside "globalThis.process" — that's pattern #5 territory.
            "global.process" to Regex("""(?<!\w)global\s*\.\s*process\b(?!This)"""),
            "Buffer.*" to Regex("""(?<!\w)Buffer\s*\.\s*\w+"""),
            "node:async_hooks" to Regex("""node:async_hooks\b""")
        )

        val hitPatterns = patterns
            .filter { (_, regex) -> regex.containsMatchIn(scriptContent) }
            .map { (name, _) -> name }
            .distinct()

        val baseFlags = existingFlags.orEmpty()
        val hasHit = hitPatterns.isNotEmpty()
        val nodejsFlag = "nodejs_compat"
        val alreadyPresent = nodejsFlag in baseFlags

        return when {
            !hasHit -> WorkerNodejsDetectResult(
                finalFlags = baseFlags,
                hitPatterns = emptyList(),
                logResId = R.string.worker_nodejs_detect_no_hit,
                logFormatArgs = emptyArray()
            )
            alreadyPresent -> WorkerNodejsDetectResult(
                finalFlags = baseFlags,
                hitPatterns = hitPatterns,
                logResId = R.string.worker_nodejs_flag_dup_skip_format,
                logFormatArgs = arrayOf(nodejsFlag)
            )
            else -> {
                // Append "nodejs_compat" at the END, preserving original order.
                val finalFlags = baseFlags + nodejsFlag
                // worker_nodejs_detect_hit_hint_format has exactly 1 placeholder:
                //   %1$s — the comma-separated friendly names of matched patterns.
                val patternCsv = hitPatterns.joinToString(", ")
                WorkerNodejsDetectResult(
                    finalFlags = finalFlags,
                    hitPatterns = hitPatterns,
                    logResId = R.string.worker_nodejs_detect_hit_hint_format,
                    logFormatArgs = arrayOf(patternCsv)
                )
            }
        }
    }
}
