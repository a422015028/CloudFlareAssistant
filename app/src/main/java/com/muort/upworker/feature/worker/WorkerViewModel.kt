package com.muort.upworker.feature.worker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CustomDomain
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Route
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.model.WorkerVersion
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.model.WorkerDeployment
import com.muort.upworker.core.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WorkerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val workerRepository: WorkerRepository
) : ViewModel() {

    /**
     * 上传脚本内容并自动保留原有 bindings（KV/R2/变量等）
     * 使用与编辑器相同的上传逻辑
     * @param customCompatibilityDate 用户自定义的兼容性日期，为空时保留原有配置或使用默认值
     */
    fun uploadWorkerScriptWithBindings(account: Account, scriptName: String, scriptFile: File, customCompatibilityDate: String? = null) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            try {
                // 读取文件内容
                val content = scriptFile.readText(Charsets.UTF_8)
                
                // 创建临时文件
                val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: System.getenv("TEMP") ?: "/tmp")
                val tempFile = java.io.File(tempDir, "$scriptName.js")
                
                try {
                    // 获取原有配置以保留bindings（新脚本可能没有配置，失败时使用空配置继续上传）
                    val (originalBindings, originalCompatibilityDate) = when (val settings = workerRepository.getWorkerSettings(account, scriptName)) {
                        is Resource.Success -> {
                            Pair(settings.data.bindings, settings.data.compatibilityDate)
                        }
                        is Resource.Error -> {
                            Timber.w("No existing settings found for script '$scriptName' (new script?), proceeding with empty bindings")
                            Pair(null, null)
                        }
                        else -> Pair(null, null)
                    }
                    
                    // 直接使用原始内容，不做任何转换
                    tempFile.writeText(content, Charsets.UTF_8)
                    
                    Timber.d("Script written to temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
                    
                    // 过滤掉 secret_text bindings（无法获取值）
                    val cleanedBindings = originalBindings?.filterNot { it.type == "secret_text" }
                    
                    // 用户自定义日期 > 原有日期 > 默认值
                    val finalCompatibilityDate = customCompatibilityDate?.takeIf { it.isNotBlank() } 
                        ?: originalCompatibilityDate
                    
                    // 创建metadata并保留清理后的bindings（脚本类型由Repository自动检测）
                    val metadata = com.muort.upworker.core.model.WorkerMetadata(
                        compatibilityDate = finalCompatibilityDate,
                        bindings = cleanedBindings
                    )
                    
                    when (val result = workerRepository.uploadWorkerScriptMultipart(account, scriptName, tempFile, metadata)) {
                        is Resource.Success -> {
                            _uploadState.value = UploadState.Success
                            _message.emit(UiMessage.of(R.string.vm_msg_worker_upload_with_bindings_success))
                            Timber.d("Script uploaded with preserved bindings: $scriptName")
                            loadWorkerScripts(account)
                        }
                        is Resource.Error -> {
                            _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                            _message.emit(UiMessage.of(R.string.vm_msg_worker_upload_failed, result.message))
                            Timber.e("Failed to upload script: ${result.message}")
                        }
                        is Resource.Loading -> {
                            _uploadState.value = UploadState.Uploading
                        }
                    }
                } finally {
                    // 清理临时文件
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                _uploadState.value = UploadState.Error(UiMessage.RawString(e.message ?: "Unknown error"))
                _message.emit(UiMessage.of(R.string.vm_msg_worker_upload_failed, e.message ?: ""))
                Timber.e(e, "Failed to upload script")
            }
        }
    }

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    
    private val _scripts = MutableStateFlow<List<WorkerScript>>(emptyList())
    val scripts: StateFlow<List<WorkerScript>> = _scripts.asStateFlow()
    
    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()
    
    private val _customDomains = MutableStateFlow<List<CustomDomain>>(emptyList())
    val customDomains: StateFlow<List<CustomDomain>> = _customDomains.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()
    
    private val _cleanupResults = MutableStateFlow<List<WorkerCleanupResult>>(emptyList())
    val cleanupResults: StateFlow<List<WorkerCleanupResult>> = _cleanupResults.asStateFlow()
    
    fun clearCleanupResults() {
        _cleanupResults.value = emptyList()
    }
    
    fun uploadWorkerScript(account: Account, scriptName: String, scriptFile: File, customCompatibilityDate: String? = null) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            val content = scriptFile.readText(Charsets.UTF_8)
            val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: System.getenv("TEMP") ?: "/tmp")
            val tempFile = java.io.File(tempDir, "$scriptName.js")
            
            try {
                tempFile.writeText(content, Charsets.UTF_8)
                
                val metadata = com.muort.upworker.core.model.WorkerMetadata(
                    compatibilityDate = customCompatibilityDate?.takeIf { it.isNotBlank() }
                )
                
                when (val result = workerRepository.uploadWorkerScriptMultipart(account, scriptName, tempFile, metadata)) {
                    is Resource.Success -> {
                        _uploadState.value = UploadState.Success
                        _message.emit(UiMessage.of(R.string.vm_msg_worker_upload_plain_success))
                        Timber.d("Script uploaded: $scriptName")
                        loadWorkerScripts(account)
                    }
                    is Resource.Error -> {
                        _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                        _message.emit(UiMessage.of(R.string.vm_msg_worker_upload_failed, result.message))
                        Timber.e("Failed to upload script: ${result.message}")
                    }
                    is Resource.Loading -> {
                        _uploadState.value = UploadState.Uploading
                    }
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }
    
    /**
     * Update KV bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param scriptName Name of the existing script
     * @param kvBindings List of pairs containing (binding_name, namespace_id)
     */
    fun updateWorkerKvBindings(
        account: Account,
        scriptName: String,
        kvBindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerKvBindings(
                account, scriptName, kvBindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_kv_binding_updated_success, scriptName))
                    Timber.d("KV bindings updated for script: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_binding_update_failed, result.message))
                    Timber.e("Failed to update KV bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Update R2 bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param scriptName Name of the existing script
     * @param r2Bindings List of pairs containing (binding_name, bucket_name)
     */
    fun updateWorkerR2Bindings(
        account: Account,
        scriptName: String,
        r2Bindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerR2Bindings(
                account, scriptName, r2Bindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_r2_binding_updated_success, scriptName))
                    Timber.d("R2 bindings updated for script: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_binding_update_failed, result.message))
                    Timber.e("Failed to update R2 bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Update D1 database bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param scriptName Name of the existing script
     * @param d1Bindings List of pairs containing (binding_name, database_id)
     */
    fun updateWorkerD1Bindings(
        account: Account,
        scriptName: String,
        d1Bindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerD1Bindings(
                account, scriptName, d1Bindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_d1_binding_updated_success, scriptName))
                    Timber.d("D1 bindings updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_d1_binding_update_failed, result.message))
                    Timber.e("Failed to update D1 bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }

    /**
     * Update service bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param scriptName Name of the existing script
     * @param serviceBindings List of triples containing (binding_name, target_worker, target_environment or null)
     */
    fun updateWorkerServiceBindings(
        account: Account,
        scriptName: String,
        serviceBindings: List<Triple<String, String, String?>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading

            when (val result = workerRepository.updateWorkerServiceBindings(
                account, scriptName, serviceBindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_service_binding_updated_success, scriptName))
                    Timber.d("Service bindings updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_binding_update_failed, result.message))
                    Timber.e("Failed to update service bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Update environment variables for an existing Worker Script
     * @param scriptName Name of the existing script
     * @param variables List of triples containing (variable_name, variable_value, variable_type)
     */
    fun updateWorkerVariables(
        account: Account,
        scriptName: String,
        variables: List<Triple<String, String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerVariables(
                account, scriptName, variables
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_variable_updated_success, scriptName))
                    Timber.d("Variables updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_variable_update_failed, result.message))
                    Timber.e("Failed to update variables: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Update secrets for an existing Worker Script
     * @param scriptName Name of the existing script
     * @param secrets List of pairs containing (secret_name, secret_value)
     */
    fun updateWorkerSecrets(
        account: Account,
        scriptName: String,
        secrets: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerSecrets(
                account, scriptName, secrets
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_secret_updated_success, scriptName))
                    Timber.d("Secrets updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(UiMessage.RawString(result.message))
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_secret_update_failed, result.message))
                    Timber.e("Failed to update secrets: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    fun loadWorkerScripts(account: Account) {
        viewModelScope.launch {
            when (val result = workerRepository.listWorkerScripts(account)) {
                is Resource.Success -> {
                    _scripts.value = result.data
                    Timber.d("Loaded ${result.data.size} worker scripts")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_scripts_load_failed, result.message))
                    Timber.e("Failed to load scripts: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun getWorkerScript(account: Account, scriptName: String, silent: Boolean = false, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _loadingState.value = true
            
            try {
                when (val result = workerRepository.getWorkerScript(account, scriptName)) {
                    is Resource.Success -> {
                        onSuccess(result.data)
                    }
                    is Resource.Error -> {
                        if (!silent) {
                            _message.emit(UiMessage.of(R.string.vm_msg_se_script_load_failed, result.message))
                        }
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: OutOfMemoryError) {
                if (!silent) {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_script_out_of_memory))
                }
                Timber.e(e, "内存不足")
            } catch (e: Exception) {
                if (!silent) {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_script_load_exception, e.message ?: ""))
                }
                Timber.e(e, "加载脚本异常")
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * Get Worker Script settings (includes bindings)
     */
    fun getWorkerSettings(
        account: Account,
        scriptName: String,
        silent: Boolean = false,
        onResult: (Resource<WorkerScript>) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = workerRepository.getWorkerSettings(account, scriptName)) {
                is Resource.Success -> {
                    Timber.d("Fetched settings for '$scriptName' with ${result.data.bindings?.size ?: 0} bindings")
                    onResult(result)
                }
                is Resource.Error -> {
                    Timber.e("Failed to fetch settings: ${result.message}")
                    if (!silent) {
                        _message.emit(UiMessage.of(R.string.vm_msg_worker_script_settings_load_failed, result.message))
                    }
                    onResult(result)
                }
                is Resource.Loading -> {
                    onResult(result)
                }
            }
        }
    }
    
    fun deleteWorkerScript(account: Account, scriptName: String) {
        viewModelScope.launch {
            when (val result = workerRepository.deleteWorkerScript(account, scriptName)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_script_delete_success))
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_script_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    // Routes
    fun loadRoutes(account: Account, zoneId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.listRoutes(account, zoneId)) {
                is Resource.Success -> {
                    _routes.value = result.data
                    Timber.d("Loaded ${result.data.size} routes")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_routes_load_failed, result.message))
                    Timber.e("Failed to load routes: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createRoute(account: Account, zoneId: String, pattern: String, scriptName: String) {
        if (pattern.isBlank() || scriptName.isBlank()) {
            viewModelScope.launch {
                _message.emit(UiMessage.of(R.string.vm_msg_worker_route_pattern_and_script_required))
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.createRoute(account, zoneId, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_create_success))
                    loadRoutes(account, zoneId)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun updateRoute(account: Account, zoneId: String, routeId: String, pattern: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.updateRoute(account, zoneId, routeId, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_update_success))
                    loadRoutes(account, zoneId)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteRoute(account: Account, zoneId: String, routeId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.deleteRoute(account, zoneId, routeId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_delete_success))
                    loadRoutes(account, zoneId)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_route_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun loadCustomDomains(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.listCustomDomains(account)) {
                is Resource.Success -> {
                    _customDomains.value = result.data
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domains_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun addCustomDomain(account: Account, hostname: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.addCustomDomain(account, hostname, scriptName)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_add_success))
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_add_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteCustomDomain(account: Account, domainId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.deleteCustomDomain(account, domainId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_delete_success))
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }
    
    /**
     * 更新 Worker 运行时设置（兼容日期、兼容性标志、放置）
     */
    fun updateWorkerRuntimeSettings(
        account: Account,
        scriptName: String,
        compatibilityDate: String,
        compatibilityFlags: List<String>,
        placement: com.muort.upworker.core.model.Placement? = null,
        onResult: (com.muort.upworker.core.model.Resource<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                // 先获取当前设置，保留已有 bindings
                val currentSettings = workerRepository.getWorkerSettings(account, scriptName)

                val settingsRequest = com.muort.upworker.core.model.WorkerSettingsRequest(
                    bindings = (currentSettings as? Resource.Success)?.data?.bindings,
                    compatibilityDate = compatibilityDate,
                    compatibilityFlags = compatibilityFlags,
                    placement = placement
                )

                val result = workerRepository.updateWorkerSettings(account, scriptName, settingsRequest)

                when (result) {
                    is Resource.Success -> onResult(Resource.Success(Unit))
                    is Resource.Error -> onResult(Resource.Error(result.message))
                    else -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update runtime settings")
                onResult(Resource.Error(appContext.getString(R.string.vm_msg_worker_update_failed_template, e.message ?: "")))
            }
        }
    }

    private val _versions = MutableStateFlow<List<WorkerVersion>>(emptyList())
    val versions: StateFlow<List<WorkerVersion>> = _versions

    fun loadWorkerVersions(account: Account, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            val result = workerRepository.listWorkerVersions(account, scriptName)
            when (result) {
                is Resource.Success -> {
                    _versions.value = result.data
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_versions_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    suspend fun fetchWorkerVersions(account: Account, scriptName: String): Resource<List<WorkerVersion>> {
        return workerRepository.listWorkerVersions(account, scriptName)
    }

    suspend fun listWorkerDeployments(account: Account, scriptName: String): Resource<List<WorkerDeployment>> {
        return workerRepository.listWorkerDeployments(account, scriptName)
    }

    suspend fun getWorkerDeployment(account: Account, scriptName: String, deploymentId: String): Resource<WorkerDeployment> {
        return workerRepository.getWorkerDeployment(account, scriptName, deploymentId)
    }

    fun deployWorkerVersion(account: Account, scriptName: String, versionId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            val result = workerRepository.deployWorkerVersion(account, scriptName, versionId)
            when (result) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_rollback_success))
                    loadWorkerVersions(account, scriptName)
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_rollback_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    suspend fun deleteWorkerVersion(account: Account, scriptName: String, versionId: String): Resource<Unit> {
        return workerRepository.deleteWorkerVersion(account, scriptName, versionId)
    }

    fun updateCustomDomain(account: Account, domainId: String, hostname: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            val request = com.muort.upworker.core.model.CustomDomainRequest(
                hostname = hostname,
                service = scriptName,
                environment = "production"
            )
            val result = workerRepository.updateCustomDomain(account, domainId, request)
            when (result) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_updated))
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_worker_custom_domain_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    suspend fun listTails(account: Account, scriptName: String): Resource<List<com.muort.upworker.core.model.TailResult>> {
        return workerRepository.listTails(account, scriptName)
    }

    suspend fun createTail(account: Account, scriptName: String): Resource<com.muort.upworker.core.model.TailResult> {
        return workerRepository.createTail(account, scriptName)
    }

    suspend fun deleteTail(account: Account, scriptName: String, tailId: String): Resource<Unit> {
        return workerRepository.deleteTail(account, scriptName, tailId)
    }

    suspend fun fetchSchedules(account: Account, scriptName: String): Resource<List<com.muort.upworker.core.model.Schedule>> {
        return workerRepository.listSchedules(account, scriptName)
    }

    suspend fun updateSchedules(account: Account, scriptName: String, schedules: List<String>): Resource<List<com.muort.upworker.core.model.Schedule>> {
        return workerRepository.updateSchedules(account, scriptName, schedules)
    }

    fun cleanupVersionsForAllScripts(account: Account, retainCount: Int) {
        viewModelScope.launch {
            _loadingState.value = true
            _cleanupResults.value = emptyList()

            val results = mutableListOf<WorkerCleanupResult>()

            _scripts.value.forEach { script ->
                val result = cleanupVersionsForScript(account, script.id, retainCount)
                results.add(result)
            }

            _cleanupResults.value = results.toList()

            val totalDeleted = results.sumOf { it.deletedCount }
            // plurals 通过 RawString 透传；Fragment 层需用 context.resources.getQuantityString 自行格式化
            _message.emit(UiMessage.of(R.string.vm_msg_worker_cleanup_finished_total, totalDeleted))

            _loadingState.value = false
        }
    }

    fun cleanupVersionsForSingleScript(account: Account, scriptName: String, retainCount: Int) {
        viewModelScope.launch {
            _loadingState.value = true
            _cleanupResults.value = emptyList()

            val result = cleanupVersionsForScript(account, scriptName, retainCount)
            _cleanupResults.value = listOf(result)

            if (result.success) {
                _message.emit(UiMessage.of(R.string.vm_msg_worker_cleanup_single_script_success, result.scriptName, result.deletedCount))
            } else {
                _message.emit(UiMessage.of(R.string.vm_msg_worker_cleanup_failed, result.errorMessage ?: ""))
            }

            _loadingState.value = false
        }
    }

    private suspend fun cleanupVersionsForScript(account: Account, scriptName: String, retainCount: Int): WorkerCleanupResult {
        return try {
            when (val result = workerRepository.listWorkerVersions(account, scriptName)) {
                is Resource.Success -> {
                    val versions = result.data
                    val totalVersions = versions.size

                    if (totalVersions <= retainCount) {
                        WorkerCleanupResult(scriptName, totalVersions, 0, true)
                    } else {
                        val sortedVersions = versions.sortedByDescending { it.number }
                        val versionsToDelete = sortedVersions.drop(retainCount)
                        var deletedCount = 0

                        versionsToDelete.forEach { version ->
                            when (val deleteResult = workerRepository.deleteWorkerVersion(account, scriptName, version.id)) {
                                is Resource.Success -> {
                                    deletedCount++
                                }
                                is Resource.Error -> {
                                    Timber.e("Failed to delete version ${version.id} in script $scriptName: ${deleteResult.message}")
                                }
                                is Resource.Loading -> {}
                            }
                        }

                        WorkerCleanupResult(scriptName, totalVersions, deletedCount, true)
                    }
                }
                is Resource.Error -> {
                    WorkerCleanupResult(scriptName, 0, 0, false, result.message)
                }
                is Resource.Loading -> {
                    WorkerCleanupResult(scriptName, 0, 0, false, appContext.getString(R.string.vm_msg_worker_cleanup_loading_versions))
                }
            }
        } catch (e: Exception) {
            WorkerCleanupResult(scriptName, 0, 0, false, e.message ?: appContext.getString(R.string.msg_unknown_error))
        }
    }
}

data class WorkerCleanupResult(
    val scriptName: String,
    val totalVersions: Int,
    val deletedCount: Int,
    val success: Boolean,
    val errorMessage: String? = null
)

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    object Success : UploadState()
    data class Error(val message: UiMessage) : UploadState()
}
