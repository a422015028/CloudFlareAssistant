package com.muort.upworker.core.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountInfo
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val accountDao: AccountDao,
    private val backupRepositoryLazy: Lazy<BackupRepository>,
    private val api: CloudFlareApi
) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 在账号变化后触发自动备份
     */
    private fun triggerAutoBackup() {
        coroutineScope.launch {
            try {
                val backupRepository = backupRepositoryLazy.get()

                // WebDAV 自动备份
                val webDavConfig = backupRepository.getWebDavConfigSync()
                if (webDavConfig?.autoBackup == true) {
                    Timber.d("触发 WebDAV 自动备份")
                    val password = webDavConfig.backupPassword?.takeIf { it.isNotBlank() }
                    val result = backupRepository.backupAccounts(password)
                    if (result.isSuccess) {
                        Timber.d("WebDAV 自动备份成功: ${result.getOrNull()}")
                    } else {
                        Timber.e("WebDAV 自动备份失败: ${result.exceptionOrNull()?.message}")
                    }
                }

                // R2 自动备份
                val r2Config = backupRepository.getR2BackupConfigSync()
                if (r2Config?.autoBackup == true) {
                    Timber.d("触发 R2 自动备份")
                    val password = r2Config.backupPassword?.takeIf { it.isNotBlank() }
                    val result = backupRepository.backupAccountsToR2(password)
                    if (result.isSuccess) {
                        Timber.d("R2 自动备份成功: ${result.getOrNull()}")
                    } else {
                        Timber.e("R2 自动备份失败: ${result.exceptionOrNull()?.message}")
                    }
                }

                // 本地自动备份
                val localConfig = backupRepository.getLocalBackupConfigSync()
                if (localConfig?.autoBackup == true) {
                    Timber.d("触发本地自动备份")
                    val password = localConfig.backupPassword?.takeIf { it.isNotBlank() }
                    val result = backupRepository.backupAccountsLocal(password)
                    if (result.isSuccess) {
                        Timber.d("本地自动备份成功: ${result.getOrNull()}")
                    } else {
                        Timber.e("本地自动备份失败: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "自动备份异常")
            }
        }
    }
    
    fun getAllAccounts(): Flow<Resource<List<Account>>> {
        return accountDao.getAllAccounts()
            .map<List<Account>, Resource<List<Account>>> { Resource.Success(it) }
            .catch { e ->
                Timber.e(e, "Error loading accounts")
                emit(Resource.Error("Failed to load accounts: ${e.message}", e))
            }
    }
    
    fun getDefaultAccount(): Flow<Resource<Account?>> {
        return accountDao.getDefaultAccountFlow()
            .map<Account?, Resource<Account?>> { account ->
                if (account != null) {
                    Timber.d("AccountRepository: Default account fetched from DB - ${account.name} (ID: ${account.id})")
                    Timber.d("Account has r2AccessKeyId: ${account.r2AccessKeyId != null}, r2SecretAccessKey: ${account.r2SecretAccessKey != null}")
                } else {
                    Timber.d("AccountRepository: No default account in database")
                }
                Resource.Success(account)
            }
            .catch { e ->
                Timber.e(e, "Error loading default account")
                emit(Resource.Error("Failed to load default account: ${e.message}", e))
            }
    }
    
    suspend fun getAccountById(id: Long): Account? {
        return try {
            accountDao.getAccountById(id)
        } catch (e: Exception) {
            Timber.e(e, "Error getting account by id: $id")
            null
        }
    }
    
    suspend fun insertAccount(account: Account): Resource<Long> {
        return try {
            val id = accountDao.insertAccount(account)
            triggerAutoBackup() // 触发自动备份
            Resource.Success(id)
        } catch (e: Exception) {
            Timber.e(e, "Error inserting account")
            Resource.Error("Failed to insert account: ${e.message}", e)
        }
    }
    
    suspend fun updateAccount(account: Account): Resource<Unit> {
        return try {
            accountDao.updateAccount(account.copy(updatedAt = System.currentTimeMillis()))
            triggerAutoBackup() // 触发自动备份
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating account")
            Resource.Error("Failed to update account: ${e.message}", e)
        }
    }
    
    suspend fun deleteAccount(account: Account): Resource<Unit> {
        return try {
            accountDao.deleteAccount(account)
            triggerAutoBackup() // 触发自动备份
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting account")
            Resource.Error("Failed to delete account: ${e.message}", e)
        }
    }
    
    suspend fun setDefaultAccount(accountId: Long): Resource<Unit> {
        return try {
            accountDao.setDefaultAccount(accountId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error setting default account")
            Resource.Error("Failed to set default account: ${e.message}", e)
        }
    }
    
    suspend fun importAccounts(accounts: List<Account>): Resource<Unit> {
        return try {
            accountDao.insertAccounts(accounts)
            triggerAutoBackup() // 触发自动备份
            Resource.Success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error importing accounts")
            Resource.Error("Failed to import accounts: ${e.message}", e)
        }
    }
    
    suspend fun exportAccounts(): Resource<List<Account>> {
        return try {
            val accounts = mutableListOf<Account>()
            accountDao.getAllAccounts().collect { accounts.addAll(it) }
            Resource.Success(accounts)
        } catch (e: Exception) {
            Timber.e(e, "Error exporting accounts")
            Resource.Error("Failed to export accounts: ${e.message}", e)
        }
    }
    
    suspend fun getAccountCount(): Int {
        return try {
            accountDao.getAccountCount()
        } catch (e: Exception) {
            Timber.e(e, "Error getting account count")
            0
        }
    }
    
    /**
     * 从 Cloudflare API 获取账号列表
     * 支持 API Token 和 Global API Key 两种认证方式
     */
    suspend fun fetchAccountsFromApi(account: Account): Resource<List<AccountInfo>> {
        return try {
            val response = api.listAccounts(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account)
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    Resource.Success(body.result)
                } else {
                    val errorMsg = body?.errors?.firstOrNull()?.message ?: appContext.getString(R.string.repo_account_fetch_list_failed)
                    Resource.Error(errorMsg)
                }
            } else {
                Resource.Error("HTTP ${response.code()}: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: appContext.getString(R.string.repo_generic_network_error))
        }
    }
}
