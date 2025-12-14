package com.muort.upworker.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {
    
    val webDavConfig = backupRepository.webDavConfig
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _backupFiles = MutableStateFlow<List<String>>(emptyList())
    val backupFiles: StateFlow<List<String>> = _backupFiles.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    /**
     * 保存WebDAV配置
     */
    fun saveWebDavConfig(
        url: String,
        username: String,
        password: String,
        backupPath: String,
        autoBackup: Boolean
    ) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val config = WebDavConfig(
                    url = url.trim().trimEnd('/'),
                    username = username.trim(),
                    password = password,
                    backupPath = backupPath.trim().trimStart('/'),
                    autoBackup = autoBackup,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                backupRepository.saveWebDavConfig(config)
                _message.value = "配置保存成功"
                
            } catch (e: Exception) {
                _message.value = "配置保存失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 测试WebDAV连接
     */
    fun testConnection(url: String, username: String, password: String) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.testConnection(
                    url.trim().trimEnd('/'),
                    username.trim(),
                    password
                )
                
                if (result.isSuccess) {
                    _message.value = "连接成功"
                } else {
                    _message.value = "连接失败: ${result.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                _message.value = "连接失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 备份账号列表
     */
    fun backupAccounts() {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.backupAccounts()
                
                if (result.isSuccess) {
                    val fileName = result.getOrNull()
                    _message.value = "备份成功: $fileName"
                    loadBackupFiles() // 刷新文件列表
                } else {
                    _message.value = "备份失败: ${result.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                _message.value = "备份失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 恢复账号列表
     */
    fun restoreAccounts(fileName: String) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.restoreAccounts(fileName)
                
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    _message.value = "恢复成功，导入了 $count 个账号"
                } else {
                    _message.value = "恢复失败: ${result.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                _message.value = "恢复失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 加载备份文件列表
     */
    fun loadBackupFiles() {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.listBackupFiles()
                
                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    _backupFiles.value = files
                    if (files.isEmpty()) {
                        _message.value = "未找到备份文件"
                    } else {
                        _message.value = "找到 ${files.size} 个备份文件"
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMsg = error?.message ?: "未知错误"
                    val stackTrace = error?.stackTraceToString()?.take(200) ?: ""
                    _message.value = "加载文件列表失败: $errorMsg\n$stackTrace"
                    _backupFiles.value = emptyList()
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知错误"
                val stackTrace = e.stackTraceToString().take(200)
                _message.value = "加载文件列表失败: $errorMsg\n$stackTrace"
                _backupFiles.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 删除备份文件
     */
    fun deleteBackupFile(fileName: String) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.deleteBackupFile(fileName)
                
                if (result.isSuccess) {
                    _message.value = "删除成功"
                    loadBackupFiles() // 刷新文件列表
                } else {
                    _message.value = "删除失败: ${result.exceptionOrNull()?.message}"
                }
                
            } catch (e: Exception) {
                _message.value = "删除失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 清除消息
     */
    fun clearMessage() {
        _message.value = null
    }
}
