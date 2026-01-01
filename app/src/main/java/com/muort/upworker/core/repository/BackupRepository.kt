package com.muort.upworker.core.repository

import com.google.gson.Gson
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.database.WebDavConfigDao
import com.muort.upworker.core.model.AccountBackup
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.toAccount
import com.muort.upworker.core.model.toAccountData
import com.muort.upworker.core.webdav.WebDavClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val webDavConfigDao: WebDavConfigDao,
    private val webDavClient: WebDavClient,
    private val gson: Gson,
    private val zoneDao: com.muort.upworker.core.database.ZoneDao // 新增注入
) {
    
    val webDavConfig: Flow<WebDavConfig?> = webDavConfigDao.getConfig()
    
    /**
     * 保存WebDAV配置
     */
    suspend fun saveWebDavConfig(config: WebDavConfig) {
        val existing = webDavConfigDao.getConfigSync()
        if (existing != null) {
            webDavConfigDao.update(config.copy(id = existing.id))
        } else {
            webDavConfigDao.insert(config)
        }
    }
    
    /**
     * 获取WebDAV配置（同步）
     */
    suspend fun getWebDavConfigSync(): WebDavConfig? {
        return webDavConfigDao.getConfigSync()
    }
    
    /**
     * 测试WebDAV连接
     */
    suspend fun testConnection(url: String, username: String, password: String): Result<Unit> {
        return webDavClient.testConnection(url, username, password)
    }
    
    /**
     * 备份账号列表到WebDAV
     */
    suspend fun backupAccounts(): Result<String> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception("未配置WebDAV"))

            // 获取所有账号
            val accounts = accountDao.getAllAccountsSync()
            if (accounts.isEmpty()) {
                return Result.failure(Exception("没有账号可备份"))
            }

            // 转换为备份格式，仅附带zones
            val accountsData = accounts.map { account ->
                val zones = runBlocking {
                    zoneDao.getZonesByAccount(account.id).first()
                }
                account.toAccountData().copy(
                    zones = zones
                )
            }
            val backup = com.muort.upworker.core.model.AccountBackup(
                version = "1.1",
                backupDate = System.currentTimeMillis(),
                accounts = accountsData
            )

            // 转换为JSON
            val json = gson.toJson(backup)

            // 生成备份文件名
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "cloudflare_backup_$timestamp.json"
            val filePath = if (config.backupPath.endsWith("/")) {
                "${config.backupPath}$fileName"
            } else {
                "${config.backupPath}/$fileName"
            }

            // 上传到WebDAV
            val result = webDavClient.uploadFile(
                config.url,
                config.username,
                config.password,
                filePath,
                json
            )

            return if (result.isSuccess) {
                Result.success(fileName)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("上传失败"))
            }

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * 从WebDAV恢复账号列表
     */
    suspend fun restoreAccounts(fileName: String): Result<Int> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception("未配置WebDAV"))

            // 下载备份文件
            val filePath = if (config.backupPath.endsWith("/")) {
                "${config.backupPath}$fileName"
            } else {
                "${config.backupPath}/$fileName"
            }

            val downloadResult = webDavClient.downloadFile(
                config.url,
                config.username,
                config.password,
                filePath
            )

            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("下载失败"))
            }

            val json = downloadResult.getOrNull() ?: return Result.failure(Exception("下载内容为空"))

            // 解析JSON
            val backup = gson.fromJson(json, com.muort.upworker.core.model.AccountBackup::class.java)

            // 先清空本地zone表，再清空账号表，避免外键约束错误
            zoneDao.deleteAllZones()
            accountDao.deleteAllAccounts()

            // 恢复账号，建立 accountId 映射表（Cloudflare accountId -> 新主键id）
            val accountList = backup.accounts.map { it.toAccount() }
            accountDao.insertAccounts(accountList)
            // 重新查询所有账号，建立映射
            val allAccounts = accountDao.getAllAccountsSync()
            val accountIdMap = allAccounts.associateBy({ it.accountId }, { it.id })

            // 恢复zones，修正 accountId
            backup.accounts.forEach { acc ->
                val newAccountId = accountIdMap[acc.accountId] ?: 0L
                acc.zones?.forEach { zone ->
                    zoneDao.insertZone(zone.copy(accountId = newAccountId))
                }
            }

            return Result.success(accountList.size)

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * 列出WebDAV上的备份文件
     */
    suspend fun listBackupFiles(): Result<List<String>> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception("未配置WebDAV"))
            
            val result = webDavClient.listFiles(
                config.url,
                config.username,
                config.password,
                config.backupPath
            )
            
            return if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                val backupFiles = files
                    .filter { it.startsWith("cloudflare_backup_") && it.endsWith(".json") }
                    .sortedDescending() // 最新的在前面
                Result.success(backupFiles)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("列表获取失败"))
            }
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * 删除WebDAV上的备份文件
     */
    suspend fun deleteBackupFile(fileName: String): Result<Unit> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception("未配置WebDAV"))
            
            val filePath = if (config.backupPath.endsWith("/")) {
                "${config.backupPath}$fileName"
            } else {
                "${config.backupPath}/$fileName"
            }
            
            return webDavClient.deleteFile(
                config.url,
                config.username,
                config.password,
                filePath
            )
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
