package com.muort.upworker.core.repository

import com.google.gson.Gson
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.database.WebDavConfigDao
import com.muort.upworker.core.database.R2BackupConfigDao
import com.muort.upworker.core.model.AccountBackup
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.R2BackupConfig
import com.muort.upworker.core.model.toAccount
import com.muort.upworker.core.model.toAccountData
import com.muort.upworker.core.webdav.WebDavClient
import com.muort.upworker.core.network.R2S3Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val webDavConfigDao: WebDavConfigDao,
    private val r2BackupConfigDao: R2BackupConfigDao,
    private val webDavClient: WebDavClient,
    private val r2S3Client: R2S3Client,
    private val gson: Gson,
    private val zoneDao: com.muort.upworker.core.database.ZoneDao
) {
    
    val webDavConfig: Flow<WebDavConfig?> = webDavConfigDao.getConfig()
    val r2BackupConfig: Flow<R2BackupConfig?> = r2BackupConfigDao.getConfig()
    
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
     * 保存R2备份配置
     */
    suspend fun saveR2BackupConfig(config: R2BackupConfig) {
        val existing = r2BackupConfigDao.getConfigSync()
        if (existing != null) {
            r2BackupConfigDao.update(config.copy(id = existing.id))
        } else {
            r2BackupConfigDao.insert(config)
        }
    }
    
    /**
     * 获取WebDAV配置（同步）
     */
    suspend fun getWebDavConfigSync(): WebDavConfig? {
        return webDavConfigDao.getConfigSync()
    }
    
    /**
     * 获取R2备份配置（同步）
     */
    suspend fun getR2BackupConfigSync(): R2BackupConfig? {
        return r2BackupConfigDao.getConfigSync()
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
    
    /**
     * 备份账号列表到R2
     */
    suspend fun backupAccountsToR2(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception("未配置R2备份"))
                
                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception("未找到指定的账号"))
                
                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("账号未配置R2访问凭证"))
                }
                
                val s3Config = R2S3Client.S3Config(
                    accountId = account.accountId,
                    accessKeyId = account.r2AccessKeyId,
                    secretAccessKey = account.r2SecretAccessKey
                )
                
                val accounts = accountDao.getAllAccountsSync()
                if (accounts.isEmpty()) {
                    return@withContext Result.failure(Exception("没有账号可备份"))
                }
                
                val accountsData = accounts.map { acc ->
                    val zones = runBlocking {
                        zoneDao.getZonesByAccount(acc.id).first()
                    }
                    acc.toAccountData().copy(
                        zones = zones
                    )
                }
                
                val backup = com.muort.upworker.core.model.AccountBackup(
                    version = "1.1",
                    backupDate = System.currentTimeMillis(),
                    accounts = accountsData
                )
                
                val json = gson.toJson(backup)
                
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val fileName = "cloudflare_backup_$timestamp.json"
                val objectKey = if (r2Config.backupPath.endsWith("/")) {
                    "${r2Config.backupPath}$fileName"
                } else {
                    "${r2Config.backupPath}/$fileName"
                }
                
                val tempFile = java.io.File.createTempFile("backup_", ".json")
                try {
                    tempFile.writeText(json)
                    r2S3Client.uploadObject(s3Config, r2Config.bucketName, objectKey, tempFile, "application/json")
                    Result.success(fileName)
                } finally {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                }
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 从R2恢复账号列表
     */
    suspend fun restoreAccountsFromR2(fileName: String): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception("未配置R2备份"))
                
                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception("未找到指定的账号"))
                
                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("账号未配置R2访问凭证"))
                }
                
                val s3Config = R2S3Client.S3Config(
                    accountId = account.accountId,
                    accessKeyId = account.r2AccessKeyId,
                    secretAccessKey = account.r2SecretAccessKey
                )
                
                val objectKey = if (r2Config.backupPath.endsWith("/")) {
                    "${r2Config.backupPath}$fileName"
                } else {
                    "${r2Config.backupPath}/$fileName"
                }
                
                val data = r2S3Client.downloadObject(s3Config, r2Config.bucketName, objectKey)
                val json = String(data)
                
                val backup = gson.fromJson(json, com.muort.upworker.core.model.AccountBackup::class.java)
                
                zoneDao.deleteAllZones()
                accountDao.deleteAllAccounts()
                
                val accountList = backup.accounts.map { it.toAccount() }
                accountDao.insertAccounts(accountList)
                
                val allAccounts = accountDao.getAllAccountsSync()
                val accountIdMap = allAccounts.associateBy({ it.accountId }, { it.id })
                
                backup.accounts.forEach { acc ->
                    val newAccountId = accountIdMap[acc.accountId] ?: 0L
                    acc.zones?.forEach { zone ->
                        zoneDao.insertZone(zone.copy(accountId = newAccountId))
                    }
                }
                
                val newAccountId = accountIdMap[account.accountId]
                if (newAccountId != null) {
                    r2BackupConfigDao.update(r2Config.copy(id = r2Config.id, accountId = newAccountId))
                }
                
                Result.success(accountList.size)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 列出R2上的备份文件
     */
    suspend fun listR2BackupFiles(): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception("未配置R2备份"))
                
                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception("未找到指定的账号"))
                
                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("账号未配置R2访问凭证"))
                }
                
                val s3Config = R2S3Client.S3Config(
                    accountId = account.accountId,
                    accessKeyId = account.r2AccessKeyId,
                    secretAccessKey = account.r2SecretAccessKey
                )
                
                val result = r2S3Client.listObjects(s3Config, r2Config.bucketName, r2Config.backupPath)
                
                val backupFiles = result.objects
                    ?.filter { obj ->
                        val fileName = obj.key.substringAfterLast('/')
                        fileName.startsWith("cloudflare_backup_") && fileName.endsWith(".json")
                    }
                    ?.map { it.key.substringAfterLast('/') }
                    ?.sortedDescending()
                    ?: emptyList()
                
                Result.success(backupFiles)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 删除R2上的备份文件
     */
    suspend fun deleteR2BackupFile(fileName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception("未配置R2备份"))
                
                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception("未找到指定的账号"))
                
                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("账号未配置R2访问凭证"))
                }
                
                val s3Config = R2S3Client.S3Config(
                    accountId = account.accountId,
                    accessKeyId = account.r2AccessKeyId,
                    secretAccessKey = account.r2SecretAccessKey
                )
                
                val objectKey = if (r2Config.backupPath.endsWith("/")) {
                    "${r2Config.backupPath}$fileName"
                } else {
                    "${r2Config.backupPath}/$fileName"
                }
                
                r2S3Client.deleteObject(s3Config, r2Config.bucketName, objectKey)
                Result.success(Unit)
                
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
