package com.muort.upworker.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.R2BackupConfig
import com.muort.upworker.core.model.LocalBackupConfig
import com.muort.upworker.core.model.StorageType
import com.muort.upworker.core.repository.BackupRepository
import com.muort.upworker.core.repository.AccountRepository
import com.muort.upworker.core.repository.R2Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository,
    private val r2Repository: R2Repository
) : ViewModel() {

    val webDavConfig = backupRepository.webDavConfig
    val r2BackupConfig = backupRepository.r2BackupConfig
    val localBackupConfig = backupRepository.localBackupConfig
    val accounts = accountRepository.getAllAccounts()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _backupFiles = MutableStateFlow<List<String>>(emptyList())
    val backupFiles: StateFlow<List<String>> = _backupFiles.asStateFlow()

    private val _message = MutableStateFlow<UiMessage>(UiMessage.Empty)
    val message: StateFlow<UiMessage> = _message.asStateFlow()

    private val _selectedStorageType = MutableStateFlow(StorageType.LOCAL)
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
        autoBackup: Boolean,
        backupPassword: String? = null
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
                    backupPassword = backupPassword?.takeIf { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                backupRepository.saveWebDavConfig(config)
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_saved_success)

            } catch (e: Exception) {
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_save_failed, e.message ?: "")
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
                    _message.value = UiMessage.of(R.string.vm_msg_backup_connection_success)
                } else {
                    _message.value = UiMessage.of(R.string.status_connection_failed, result.exceptionOrNull()?.message ?: "")
                }

            } catch (e: Exception) {
                _message.value = UiMessage.of(R.string.status_connection_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    // ==================== WebDAV 备份/恢复 ====================

    fun backupAccounts(password: String?) {
        Timber.d("Starting WebDAV backup, hasPassword=%s", password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.backupAccounts(password)

                if (result.isSuccess) {
                    val fileName = result.getOrNull() ?: ""
                    Timber.d("WebDAV backup success: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_success_filename, fileName)
                    loadBackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("WebDAV backup failed: %s", err)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "WebDAV backup exception")
                _message.value = UiMessage.of(R.string.vm_msg_backup_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun restoreAccounts(fileName: String, password: String?) {
        Timber.d("Starting WebDAV restore: %s, hasPassword=%s", fileName, password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.restoreAccounts(fileName, password)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Timber.d("WebDAV restore success: %s, restored %d accounts", fileName, count)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_success_count, count)
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("WebDAV restore failed: %s, error=%s", fileName, err)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "WebDAV restore exception: %s", fileName)
                _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun loadBackupFiles() {
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.listBackupFiles()

                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    Timber.d("WebDAV loaded %d backup files", files.size)
                    _backupFiles.value = files
                    if (files.isEmpty()) {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_files_not_found)
                    } else {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_files_found_count, files.size)
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val errorMsg = error?.message ?: "未知错误"
                    Timber.e("WebDAV load backup files failed: %s", errorMsg)
                    val stackTrace = error?.stackTraceToString()?.take(200) ?: ""
                    _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, errorMsg + "\n" + stackTrace)
                    _backupFiles.value = emptyList()
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知错误"
                Timber.e(e, "WebDAV load backup files exception: %s", errorMsg)
                val stackTrace = e.stackTraceToString().take(200)
                _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, errorMsg + "\n" + stackTrace)
                _backupFiles.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun deleteBackupFile(fileName: String) {
        Timber.d("Deleting WebDAV backup file: %s", fileName)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.deleteBackupFile(fileName)

                if (result.isSuccess) {
                    Timber.d("WebDAV backup file deleted: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_delete_success)
                    loadBackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("WebDAV delete backup file failed: %s, error=%s", fileName, err)
                    _message.value = UiMessage.of(R.string.msg_delete_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "WebDAV delete backup file exception: %s", fileName)
                _message.value = UiMessage.of(R.string.msg_delete_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    // ==================== R2 备份/恢复 ====================

    fun backupAccountsToR2(password: String?) {
        Timber.d("Starting R2 backup, hasPassword=%s", password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.backupAccountsToR2(password)

                if (result.isSuccess) {
                    val fileName = result.getOrNull() ?: ""
                    Timber.d("R2 backup success: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_success_filename, fileName)
                    loadR2BackupFiles()
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    Timber.e("R2 backup failed: %s", errorMsg)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_failed, errorMsg)
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                Timber.e(e, "R2 backup exception: %s", errorMsg)
                _message.value = UiMessage.of(R.string.vm_msg_backup_failed, errorMsg)
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun restoreAccountsFromR2(fileName: String, password: String?) {
        Timber.d("Starting R2 restore: %s, hasPassword=%s", fileName, password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.restoreAccountsFromR2(fileName, password)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Timber.d("R2 restore success: %s, restored %d accounts", fileName, count)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_success_count, count)
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    Timber.e("R2 restore failed: %s, error=%s", fileName, errorMsg)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, errorMsg)
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                Timber.e(e, "R2 restore exception: %s", errorMsg)
                _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, errorMsg)
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun loadR2BackupFiles() {
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.listR2BackupFiles()

                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    Timber.d("R2 loaded %d backup files", files.size)
                    _backupFiles.value = files
                    if (files.isEmpty()) {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_files_not_found)
                    } else {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_files_found_count, files.size)
                    }
                } else {
                    val exception = result.exceptionOrNull()
                    val errorMsg = exception?.message ?: exception?.toString() ?: "未知错误"
                    Timber.e("R2 load backup files failed: %s", errorMsg)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, errorMsg)
                    _backupFiles.value = emptyList()
                }

            } catch (e: Exception) {
                val errorMsg = e.message ?: e.toString()
                Timber.e(e, "R2 load backup files exception: %s", errorMsg)
                _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, errorMsg)
                _backupFiles.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun deleteR2BackupFile(fileName: String) {
        Timber.d("Deleting R2 backup file: %s", fileName)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.deleteR2BackupFile(fileName)

                if (result.isSuccess) {
                    Timber.d("R2 backup file deleted: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_delete_success)
                    loadR2BackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("R2 delete backup file failed: %s, error=%s", fileName, err)
                    _message.value = UiMessage.of(R.string.msg_delete_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "R2 delete backup file exception: %s", fileName)
                _message.value = UiMessage.of(R.string.msg_delete_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    // ==================== 本地备份 ====================

    fun backupAccountsLocal(password: String?) {
        Timber.d("Starting local backup, hasPassword=%s", password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.backupAccountsLocal(password)

                if (result.isSuccess) {
                    val fileName = result.getOrNull() ?: ""
                    Timber.d("Local backup success: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_success_filename, fileName)
                    loadLocalBackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("Local backup failed: %s", err)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "Local backup exception")
                _message.value = UiMessage.of(R.string.vm_msg_backup_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun restoreAccountsLocal(fileName: String, password: String?) {
        Timber.d("Starting local restore: %s, hasPassword=%s", fileName, password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.restoreAccountsLocal(fileName, password)

                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Timber.d("Local restore success: %s, restored %d accounts", fileName, count)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_success_count, count)
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("Local restore failed: %s, error=%s", fileName, err)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "Local restore exception: %s", fileName)
                _message.value = UiMessage.of(R.string.vm_msg_backup_restore_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun loadLocalBackupFiles() {
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.listLocalBackupFiles()

                if (result.isSuccess) {
                    val files = result.getOrNull() ?: emptyList()
                    Timber.d("Local loaded %d backup files", files.size)
                    _backupFiles.value = files
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("Local load backup files failed: %s", err)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, err)
                    _backupFiles.value = emptyList()
                }

            } catch (e: Exception) {
                Timber.e(e, "Local load backup files exception")
                _message.value = UiMessage.of(R.string.vm_msg_backup_filelist_load_failed, e.message ?: "")
                _backupFiles.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun deleteLocalBackupFile(fileName: String) {
        Timber.d("Deleting local backup file: %s", fileName)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val result = backupRepository.deleteLocalBackupFile(fileName)

                if (result.isSuccess) {
                    Timber.d("Local backup file deleted: %s", fileName)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_delete_success)
                    loadLocalBackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("Local delete backup file failed: %s, error=%s", fileName, err)
                    _message.value = UiMessage.of(R.string.msg_delete_failed, err)
                }

            } catch (e: Exception) {
                Timber.e(e, "Local delete backup file exception: %s", fileName)
                _message.value = UiMessage.of(R.string.msg_delete_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    /**
     * 从外部文件内容导入/恢复备份，并将文件保存到本地备份目录
     */
    fun importBackupFromContent(content: String, password: String?, originalFileName: String) {
        Timber.d("Importing backup from content: %s, contentLen=%d, hasPassword=%s", originalFileName, content.length, password != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true
                val result = backupRepository.restoreFromContent(content, password)
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    Timber.d("Backup import restore success: restored %d accounts from %s", count, originalFileName)
                    // 恢复成功后，把文件保存到本地备份目录
                    val saveResult = backupRepository.saveLocalBackupFile(content, originalFileName)
                    if (saveResult.isSuccess) {
                        Timber.d("Backup file saved locally: %s", originalFileName)
                        _message.value = UiMessage.of(R.string.vm_msg_backup_import_success_count, count)
                    } else {
                        val saveErr = saveResult.exceptionOrNull()?.message ?: ""
                        Timber.e("Backup imported but save failed: %s, error=%s", originalFileName, saveErr)
                        _message.value = UiMessage.of(R.string.vm_msg_backup_imported_save_failed, count, saveErr)
                    }
                    loadLocalBackupFiles()
                } else {
                    val err = result.exceptionOrNull()?.message ?: ""
                    Timber.e("Backup import failed: %s, error=%s", originalFileName, err)
                    _message.value = UiMessage.of(R.string.msg_import_failed, err)
                }
            } catch (e: Exception) {
                Timber.e(e, "Backup import exception: %s", originalFileName)
                _message.value = UiMessage.of(R.string.msg_import_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    /**
     * 生成备份内容（用于导出到用户选择的文件）
     * @return Pair(文件内容, 建议的文件名)，失败时返回 null
     */
    suspend fun buildBackupForExport(password: String?): Pair<String, String>? {
        return try {
            Timber.d("Building backup for export, hasPassword=%s", password != null)
            _loadingState.value = true
            val result = backupRepository.buildBackupContent(password)
            if (result.isSuccess) {
                Timber.d("Backup content built for export")
                result.getOrNull()
            } else {
                val err = result.exceptionOrNull()?.message ?: ""
                Timber.e("Build backup for export failed: %s", err)
                _message.value = UiMessage.of(R.string.vm_msg_backup_export_failed, err)
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Build backup for export exception")
            _message.value = UiMessage.of(R.string.vm_msg_backup_export_failed, e.message ?: "")
            null
        } finally {
            _loadingState.value = false
        }
    }

    fun notifyExportSuccess() {
        _message.value = UiMessage.of(R.string.vm_msg_backup_export_success)
    }

    /**
     * 设置本地备份目录并持久化权限
     */
    fun setLocalBackupDirectory(uri: Uri) {
        Timber.d("Setting local backup directory: %s", uri)
        viewModelScope.launch {
            try {
                _loadingState.value = true
                backupRepository.persistLocalDirectoryPermission(uri)
                val existing = backupRepository.getLocalBackupConfigSync()
                val config = LocalBackupConfig(
                    id = existing?.id ?: 0,
                    directoryUri = uri.toString(),
                    autoBackup = existing?.autoBackup ?: false,
                    backupPassword = existing?.backupPassword,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                backupRepository.saveLocalBackupConfig(config)
                Timber.d("Local backup directory set successfully")
                _message.value = UiMessage.of(R.string.vm_msg_backup_directory_set_success)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set local backup directory")
                _message.value = UiMessage.of(R.string.vm_msg_backup_directory_set_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    /**
     * 保存本地备份配置（自动备份和密码）
     */
    fun saveLocalBackupConfig(autoBackup: Boolean, backupPassword: String?) {
        Timber.d("Saving local backup config: autoBackup=%s, hasPassword=%s", autoBackup, backupPassword != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true
                val existing = backupRepository.getLocalBackupConfigSync()
                val config = LocalBackupConfig(
                    id = existing?.id ?: 0,
                    directoryUri = existing?.directoryUri,
                    autoBackup = autoBackup,
                    backupPassword = backupPassword?.takeIf { it.isNotBlank() },
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                backupRepository.saveLocalBackupConfig(config)
                Timber.d("Local backup config saved: autoBackup=%s", autoBackup)
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_saved_success)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save local backup config")
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_save_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = UiMessage.Empty
    }

    fun selectStorageType(type: StorageType) {
        _selectedStorageType.value = type
        _backupFiles.value = emptyList()
        _availableBuckets.value = emptyList()
    }

    fun loadBucketsForAccount(accountId: Long) {
        Timber.d("Loading R2 buckets for account dbId=%d", accountId)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val account = accountRepository.getAccountById(accountId)
                if (account == null) {
                    Timber.e("Account not found for dbId=%d", accountId)
                    _message.value = UiMessage.of(R.string.vm_msg_backup_account_not_found)
                    _availableBuckets.value = emptyList()
                    return@launch
                }

                val result = r2Repository.listBuckets(account)

                if (result is com.muort.upworker.core.model.Resource.Success) {
                    val buckets = result.data.map { it.name }
                    Timber.d("Loaded %d R2 buckets for accountId=%s", buckets.size, account.accountId)
                    _availableBuckets.value = buckets
                    if (buckets.isEmpty()) {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_r2_buckets_empty)
                    } else {
                        _message.value = UiMessage.of(R.string.vm_msg_backup_r2_buckets_found_count, buckets.size)
                    }
                } else {
                    val errorMsg = if (result is com.muort.upworker.core.model.Resource.Error) {
                        result.message
                    } else {
                        "未知错误"
                    }
                    Timber.e("Failed to load R2 buckets for accountId=%s: %s", account.accountId, errorMsg)
                    _message.value = UiMessage.of(R.string.pages_r2_load_buckets_failed_template, errorMsg)
                    _availableBuckets.value = emptyList()
                }

            } catch (e: Exception) {
                Timber.e(e, "Load R2 buckets exception for dbId=%d", accountId)
                _message.value = UiMessage.of(R.string.pages_r2_load_buckets_failed_template, e.message ?: "")
                _availableBuckets.value = emptyList()
            } finally {
                _loadingState.value = false
            }
        }
    }

    fun saveR2BackupConfig(
        accountId: Long,
        bucketName: String,
        backupPath: String,
        autoBackup: Boolean,
        backupPassword: String? = null
    ) {
        Timber.d("Saving R2 backup config: accountDbId=%d, bucket=%s, path=%s, autoBackup=%s, hasPassword=%s",
            accountId, bucketName, backupPath, autoBackup, backupPassword != null)
        viewModelScope.launch {
            try {
                _loadingState.value = true

                val config = R2BackupConfig(
                    accountId = accountId,
                    bucketName = bucketName,
                    backupPath = backupPath.trim().trimStart('/'),
                    autoBackup = autoBackup,
                    backupPassword = backupPassword?.takeIf { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                backupRepository.saveR2BackupConfig(config)
                Timber.d("R2 backup config saved: bucket=%s, path=%s", bucketName, config.backupPath)
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_saved_success)

            } catch (e: Exception) {
                Timber.e(e, "Failed to save R2 backup config for accountDbId=%d", accountId)
                _message.value = UiMessage.of(R.string.vm_msg_backup_config_save_failed, e.message ?: "")
            } finally {
                _loadingState.value = false
            }
        }
    }
}
