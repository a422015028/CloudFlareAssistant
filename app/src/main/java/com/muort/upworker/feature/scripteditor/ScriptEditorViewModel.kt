package com.muort.upworker.feature.scripteditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.database.ScriptVersionDao
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.ScriptVersion
import com.muort.upworker.core.repository.WorkerRepository
import kotlinx.coroutines.flow.first
import com.muort.upworker.feature.worker.UploadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ScriptEditorViewModel @Inject constructor(
    private val workerRepository: WorkerRepository,
    private val scriptVersionDao: ScriptVersionDao,
    private val accountDao: AccountDao
) : ViewModel() {
    
    private val _scriptContent = MutableStateFlow<String?>(null)
    val scriptContent: StateFlow<String?> = _scriptContent.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _uploadSuccess = MutableStateFlow(false)
    val uploadSuccess: StateFlow<Boolean> = _uploadSuccess.asStateFlow()
    
    fun loadScript(accountEmail: String, scriptName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 优先从API加载最新的已部署脚本
                val account = accountDao.getAllAccountsSync().firstOrNull { it.accountId == accountEmail }
                
                if (account == null) {
                    _error.value = "未找到账号"
                    return@launch
                }
                
                when (val result = workerRepository.getWorkerScript(account, scriptName)) {
                    is Resource.Success -> {
                        _scriptContent.value = result.data
                        
                        // 检查是否已存在相同内容的Cloudflare版本
                        val cloudflareVersions = scriptVersionDao.getCloudflareVersions(accountEmail, scriptName)
                        val existingVersion = cloudflareVersions.firstOrNull { it.content == result.data }
                        
                        if (existingVersion != null) {
                            // 更新现有版本的时间戳，使其成为最新版本
                            scriptVersionDao.updateVersionTimestamp(existingVersion.id, System.currentTimeMillis())
                            Timber.d("Updated existing Cloudflare version timestamp (duplicate content)")
                        } else {
                            // 保存为新版本
                            saveVersion(
                                accountEmail = accountEmail,
                                scriptName = scriptName,
                                content = result.data,
                                isAutoSave = false,
                                description = "从Cloudflare加载"
                            )
                            Timber.d("Saved new Cloudflare version")
                        }
                        Timber.d("Loaded script from Cloudflare API")
                    }
                    is Resource.Error -> {
                        // API加载失败，尝试从本地历史记录加载
                        val latestVersion = scriptVersionDao.getLatestVersion(accountEmail, scriptName)
                        if (latestVersion != null) {
                            _scriptContent.value = latestVersion.content
                            _error.value = "无法从Cloudflare加载，已加载本地缓存版本"
                            Timber.d("Loaded script from local cache (API failed)")
                        } else {
                            _error.value = "加载脚本失败: ${result.message}"
                            Timber.e("Failed to load script: ${result.message}")
                        }
                    }
                    is Resource.Loading -> {
                        // Already loading
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading script")
                _error.value = "加载脚本失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun saveVersion(
        accountEmail: String,
        scriptName: String,
        content: String,
        isAutoSave: Boolean,
        description: String? = null
    ) {
        viewModelScope.launch {
            try {
                val version = ScriptVersion(
                    accountEmail = accountEmail,
                    scriptName = scriptName,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    isAutoSave = isAutoSave,
                    description = description
                )
                
                scriptVersionDao.insertVersion(version)
                Timber.d("Saved version: isAutoSave=$isAutoSave")
                
                // Clean up old auto-save versions (keep last 50)
                if (isAutoSave) {
                    scriptVersionDao.cleanOldAutoSaves(accountEmail, scriptName, keepCount = 50)
                }
                
                // Update current content
                _scriptContent.value = content
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving version")
                if (!isAutoSave) {
                    _error.value = "保存失败: ${e.message}"
                }
            }
        }
    }
    
    fun uploadScript(accountEmail: String, scriptName: String, content: String) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            _isLoading.value = true
            
            try {
                val account = accountDao.getAllAccountsSync().firstOrNull { it.accountId == accountEmail }
                
                if (account == null) {
                    _error.value = "未找到账号"
                    _uploadState.value = UploadState.Error("未找到账号")
                    return@launch
                }
                
                Timber.d("Uploading script content length: ${content.length}")
                
                // Create temporary file
                val tempDir = java.io.File(System.getProperty("java.io.tmpdir") ?: System.getenv("TEMP") ?: "/tmp")
                val tempFile = java.io.File(tempDir, "$scriptName.js")
                
                try {
                    // 获取原有配置以保留bindings
                    when (val settings = workerRepository.getWorkerSettings(account, scriptName)) {
                        is Resource.Success -> {
                            val originalBindings = settings.data.bindings
                            
                            // 直接使用原始内容，不做任何转换
                            tempFile.writeText(content, Charsets.UTF_8)
                            
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
                                    _uploadSuccess.value = true
                                    Timber.d("Script uploaded with preserved bindings: $scriptName")
                                }
                                is Resource.Error -> {
                                    _uploadState.value = UploadState.Error(result.message)
                                    _error.value = "上传失败: ${result.message}"
                                    Timber.e("Failed to upload script: ${result.message}")
                                }
                                is Resource.Loading -> {
                                    _uploadState.value = UploadState.Uploading
                                }
                            }
                        }
                        is Resource.Error -> {
                            _uploadState.value = UploadState.Error(settings.message)
                            _error.value = "获取原有绑定失败: ${settings.message}"
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
                
            } catch (e: Exception) {
                Timber.e(e, "Error uploading script")
                _error.value = "上传失败: ${e.message}"
                _uploadState.value = UploadState.Error(e.message ?: "未知错误")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    suspend fun getVersionHistory(accountEmail: String, scriptName: String): List<ScriptVersion> {
        return try {
            scriptVersionDao.getVersionHistory(accountEmail, scriptName).first()
        } catch (e: Exception) {
            Timber.e(e, "Error getting version history")
            emptyList()
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    fun clearUploadSuccess() {
        _uploadSuccess.value = false
    }
    
    fun clearNonCloudflareVersions(accountEmail: String, scriptName: String) {
        viewModelScope.launch {
            try {
                val deletedCount = scriptVersionDao.deleteNonCloudflareVersions(accountEmail, scriptName)
                Timber.d("Deleted $deletedCount non-Cloudflare versions")
            } catch (e: Exception) {
                Timber.e(e, "Error clearing non-Cloudflare versions")
                _error.value = "清除失败: ${e.message}"
            }
        }
    }
    
    fun deleteVersion(version: ScriptVersion) {
        viewModelScope.launch {
            try {
                scriptVersionDao.deleteVersion(version)
                Timber.d("Deleted version: ${version.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting version")
                _error.value = "删除失败: ${e.message}"
            }
        }
    }
}
