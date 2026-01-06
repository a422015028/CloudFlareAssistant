package com.muort.upworker.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.R2BackupConfig
import com.muort.upworker.core.model.StorageType
import com.muort.upworker.core.repository.BackupRepository
import com.muort.upworker.core.repository.AccountRepository
import com.muort.upworker.core.repository.R2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository,
    private val r2Repository: R2Repository
) : ViewModel() {
    
    val webDavConfig = backupRepository.webDavConfig
    val r2BackupConfig = backupRepository.r2BackupConfig
    val accounts = accountRepository.getAllAccounts()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _backupFiles = MutableStateFlow<List<String>>(emptyList())
    val backupFiles: StateFlow<List<String>> = _backupFiles.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    private val _selectedStorageType = MutableStateFlow(StorageType.WEBDAV)
    val selectedStorageType: StateFlow<StorageType> = _selectedStorageType.asStateFlow()
    
    private val _availableBuckets = MutableStateFlow<List<String>>(emptyList())
    val availableBuckets: StateFlow<List<String>> = _availableBuckets.asStateFlow()
    
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
    
    /**
     * 选择存储类型
     */
    fun selectStorageType(type: StorageType) {
        _selectedStorageType.value = type
        _backupFiles.value = emptyList()
        _availableBuckets.value = emptyList()
    }
    
    /**
     * 加载指定账号的R2 bucket列表
     */
    fun loadBucketsForAccount(accountId: Long) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    _message.value = "账号不存在"
                    _availableBuckets.value = emptyList()
                    return@launch
                }
                
                val result = r2Repository.listBuckets(account)
                
                if (result is com.muort.upworker.core.model.Resource.Success) {
                    val buckets = result.data.map { it.name }
                    _availableBuckets.value = buckets
                    if (buckets.isEmpty()) {
                        _message.value = "该账号没有可用的bucket"
                    } else {
                        _message.value = "找到 ${buckets.size} 个bucket"
                    }
                } else {
                    val errorMsg = if (result is com.muort.upworker.core.model.Resource.Error) {
                        result.message
                    } else {
                        "未知错误"
                    }
                    _message.value = "加载bucket列表失败: $errorMsg"
                    _availableBuckets.value = emptyList()
                }
                
            } catch (e: Exception) {
                _message.value = "加载bucket列表失败: ${e.message}"
                _availableBuckets.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 保存R2备份配置
     */
    fun saveR2BackupConfig(
        accountId: Long,
        bucketName: String,
        backupPath: String,
        autoBackup: Boolean
    ) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val config = R2BackupConfig(
                    accountId = accountId,
                    bucketName = bucketName,
                    backupPath = backupPath.trim().trimStart('/'),
                    autoBackup = autoBackup,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                
                backupRepository.saveR2BackupConfig(config)
                _message.value = "配置保存成功"
                
            } catch (e: Exception) {
                _message.value = "配置保存失败: ${e.message}"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 备份账号列表到R2
     */
    fun backupAccountsToR2() {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.backupAccountsToR2()
                
                if (result.isSuccess) {
                    val fileName = result.getOrNull()
                    _message.value = "备份成功: $fileName"
                    loadR2BackupFiles()
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    _message.value = "备份失败: $errorMsg"
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString() ?: "未知错误"
                _message.value = "备份失败: $errorMsg"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 从R2恢复账号列表
     */
    fun restoreAccountsFromR2(fileName: String) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.restoreAccountsFromR2(fileName)
                
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    _message.value = "恢复成功，导入了 $count 个账号"
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    _message.value = "恢复失败: $errorMsg"
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString() ?: "未知错误"
                _message.value = "恢复失败: $errorMsg"
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 加载R2备份文件列表
     */
    fun loadR2BackupFiles() {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.listR2BackupFiles()
                
                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    _backupFiles.value = files
                    if (files.isEmpty()) {
                        _message.value = "未找到备份文件"
                    } else {
                        _message.value = "找到 ${files.size} 个备份文件"
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    _message.value = "加载文件列表失败: $errorMsg"
                    _backupFiles.value = emptyList()
                }
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString() ?: "未知错误"
                _message.value = "加载文件列表失败: $errorMsg"
                _backupFiles.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }
    
    /**
     * 删除R2备份文件
     */
    fun deleteR2BackupFile(fileName: String) {
        viewModelScope.launch {
            try {
                _loadingState.value = true
                
                val result = backupRepository.deleteR2BackupFile(fileName)
                
                if (result.isSuccess) {
                    _message.value = "删除成功"
                    loadR2BackupFiles()
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
}
