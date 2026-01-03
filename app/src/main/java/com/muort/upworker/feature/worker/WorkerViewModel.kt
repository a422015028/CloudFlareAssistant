package com.muort.upworker.feature.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CustomDomain
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Route
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WorkerViewModel @Inject constructor(
    private val workerRepository: WorkerRepository
) : ViewModel() {

    /**
     * 上传脚本内容并自动保留原有 bindings（KV/R2/变量等）
     * 使用与编辑器相同的上传逻辑
     */
    fun uploadWorkerScriptWithBindings(account: Account, scriptName: String, scriptFile: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            try {
                // 读取文件内容
                val content = scriptFile.readText(Charsets.UTF_8)
                
                // 创建临时文件
                val tempDir = java.io.File(System.getProperty("java.io.tmpdir"))
                val tempFile = java.io.File(tempDir, "$scriptName.js")
                
                try {
                    // 获取原有配置以保留bindings
                    when (val settings = workerRepository.getWorkerSettings(account, scriptName)) {
                        is Resource.Success -> {
                            val originalBindings = settings.data.bindings
                            val hasBindings = !originalBindings.isNullOrEmpty()
                            
                            // 检测脚本格式
                            val isServiceWorker = content.contains("addEventListener")
                            val isESModule = content.contains("export default")
                            
                            // 只有当是Service Worker格式且有bindings时才转换
                            val finalContent = if (isServiceWorker && !isESModule && hasBindings) {
                                Timber.d("Converting Service Worker to ES Module (has bindings)")
                                convertServiceWorkerToESModule(content)
                            } else {
                                content
                            }
                            
                            // 以 UTF-8 无 BOM 格式写入临时文件
                            tempFile.writeText(finalContent, Charsets.UTF_8)
                            
                            Timber.d("Script written to temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes")
                            
                            // 过滤掉 secret_text bindings（无法获取值）
                            val cleanedBindings = originalBindings?.filterNot { it.type == "secret_text" }
                            
                            // 创建metadata并保留清理后的bindings
                            val metadata = com.muort.upworker.core.model.WorkerMetadata(
                                mainModule = tempFile.name,
                                compatibilityDate = "2024-12-01",
                                bindings = cleanedBindings
                            )
                            
                            when (val result = workerRepository.uploadWorkerScriptMultipart(account, scriptName, tempFile, metadata)) {
                                is Resource.Success -> {
                                    _uploadState.value = UploadState.Success
                                    _message.emit("Worker 脚本上传成功（保留原有绑定）")
                                    Timber.d("Script uploaded with preserved bindings: $scriptName")
                                    loadWorkerScripts(account)
                                }
                                is Resource.Error -> {
                                    _uploadState.value = UploadState.Error(result.message)
                                    _message.emit("上传失败: ${result.message}")
                                    Timber.e("Failed to upload script: ${result.message}")
                                }
                                is Resource.Loading -> {
                                    _uploadState.value = UploadState.Uploading
                                }
                            }
                        }
                        is Resource.Error -> {
                            _uploadState.value = UploadState.Error(settings.message)
                            _message.emit("获取原有绑定失败: ${settings.message}")
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
                _uploadState.value = UploadState.Error(e.message ?: "Unknown error")
                _message.emit("上传失败: ${e.message}")
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
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun uploadWorkerScript(account: Account, scriptName: String, scriptFile: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.uploadWorkerScript(account, scriptName, scriptFile)) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit("Worker 脚本上传成功")
                    Timber.d("Script uploaded: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("上传失败: ${result.message}")
                    Timber.e("Failed to upload script: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Upload Worker Script with KV Namespace bindings
     * @param kvBindings List of pairs containing (binding_name, namespace_id)
     */
    fun uploadWorkerScriptWithKvBindings(
        account: Account,
        scriptName: String,
        scriptFile: File,
        kvBindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.uploadWorkerScriptWithKvBindings(
                account, scriptName, scriptFile, kvBindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit("Worker 脚本上传成功（保留原有绑定）")
                    Timber.d("Script with KV bindings uploaded: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("上传失败: ${result.message}")
                    Timber.e("Failed to upload script with KV bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
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
                    _message.emit("KV 绑定已成功更新（'$scriptName'）")
                    Timber.d("KV bindings updated for script: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("更新绑定失败: ${result.message}")
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
                    _message.emit("R2 绑定已成功更新（'$scriptName'）")
                    Timber.d("R2 bindings updated for script: $scriptName")
                    // 重新加载脚本列表
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("更新绑定失败: ${result.message}")
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
                    _message.emit("D1 绑定已成功更新（'$scriptName'）")
                    Timber.d("D1 bindings updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("更新 D1 绑定失败: ${result.message}")
                    Timber.e("Failed to update D1 bindings: ${result.message}")
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
                    _message.emit("环境变量已成功更新（'$scriptName'）")
                    Timber.d("Variables updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("更新环境变量失败: ${result.message}")
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
                    _message.emit("密钥已成功更新（'$scriptName'）")
                    Timber.d("Secrets updated for script: $scriptName")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("更新密钥失败: ${result.message}")
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
                    _message.emit("加载 Worker 脚本列表失败: ${result.message}")
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
                            _message.emit("加载脚本失败: ${result.message}")
                        }
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: OutOfMemoryError) {
                if (!silent) {
                    _message.emit("内存不足，无法加载脚本")
                }
                Timber.e(e, "内存不足")
            } catch (e: Exception) {
                if (!silent) {
                    _message.emit("加载脚本时出错: ${e.message}")
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
                    _message.emit("加载脚本设置失败: ${result.message}")
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
                    _message.emit("脚本删除成功")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _message.emit("删除脚本失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    // Routes
    fun loadRoutes(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.listRoutes(account)) {
                is Resource.Success -> {
                    _routes.value = result.data
                    Timber.d("Loaded ${result.data.size} routes")
                }
                is Resource.Error -> {
                    _message.emit("加载路由列表失败: ${result.message}")
                    Timber.e("Failed to load routes: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createRoute(account: Account, pattern: String, scriptName: String) {
        if (pattern.isBlank() || scriptName.isBlank()) {
            viewModelScope.launch {
                _message.emit("请填写路由规则和脚本名称")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.createRoute(account, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit("路由创建成功")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("创建路由失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun updateRoute(account: Account, routeId: String, pattern: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.updateRoute(account, routeId, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit("路由更新成功")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("更新路由失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteRoute(account: Account, routeId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.deleteRoute(account, routeId)) {
                is Resource.Success -> {
                    _message.emit("路由删除成功")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("删除路由失败: ${result.message}")
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
                    _message.emit("加载自定义域名失败: ${result.message}")
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
                    _message.emit("自定义域名添加成功")
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit("添加自定义域名失败: ${result.message}")
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
                    _message.emit("自定义域名删除成功")
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit("删除自定义域名失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
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
                    _message.emit("自定义域名已更新")
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit("更新自定义域名失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * 将Service Worker格式转换为ES Module格式
     * Service Worker格式不支持bindings，需要转换为ES Module
     */
    private fun convertServiceWorkerToESModule(serviceWorkerCode: String): String {
        // 移除 addEventListener 部分，提取处理函数
        var esModuleCode = serviceWorkerCode
        
        // 查找 addEventListener('fetch', ...) 模式
        val addEventListenerPattern = Regex(
            """addEventListener\s*\(\s*['"]fetch['"]\s*,\s*(?:event|e)\s*=>\s*\{[^}]*event\.respondWith\s*\(\s*(\w+)\s*\([^)]*\)\s*\)\s*\}\s*\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        
        val match = addEventListenerPattern.find(esModuleCode)
        val handlerFunctionName = match?.groupValues?.get(1) ?: "handleRequest"
        
        // 移除 addEventListener
        esModuleCode = esModuleCode.replace(addEventListenerPattern, "")
        
        // 添加 ES Module export default
        val exportDefault = """
            export default {
              async fetch(request, env, ctx) {
                return $handlerFunctionName(request);
              }
            };
        """.trimIndent()
        
        // 将 export default 添加到代码末尾
        esModuleCode = esModuleCode.trim() + "\n\n" + exportDefault
        
        return esModuleCode
    }
}

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
}
