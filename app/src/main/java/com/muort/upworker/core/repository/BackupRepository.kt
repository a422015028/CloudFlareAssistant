package com.muort.upworker.core.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.muort.upworker.R
import com.muort.upworker.core.database.AccountDao
import com.muort.upworker.core.database.WebDavConfigDao
import com.muort.upworker.core.database.R2BackupConfigDao
import com.muort.upworker.core.database.LocalBackupConfigDao
import com.muort.upworker.core.model.AccountBackup
import com.muort.upworker.core.model.WebDavConfig
import com.muort.upworker.core.model.R2BackupConfig
import com.muort.upworker.core.model.LocalBackupConfig
import com.muort.upworker.core.model.toAccount
import com.muort.upworker.core.model.toAccountData
import com.muort.upworker.core.util.BackupCrypto
import com.muort.upworker.core.webdav.WebDavClient
import com.muort.upworker.core.network.R2S3Client
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val webDavConfigDao: WebDavConfigDao,
    private val r2BackupConfigDao: R2BackupConfigDao,
    private val localBackupConfigDao: LocalBackupConfigDao,
    private val webDavClient: WebDavClient,
    private val r2S3Client: R2S3Client,
    private val gson: Gson,
    private val zoneDao: com.muort.upworker.core.database.ZoneDao
) {

    private val localBackupDir: File by lazy {
        File(context.filesDir, "backups").apply { mkdirs() }
    }

    val webDavConfig: Flow<WebDavConfig?> = webDavConfigDao.getConfig()
    val r2BackupConfig: Flow<R2BackupConfig?> = r2BackupConfigDao.getConfig()
    val localBackupConfig: Flow<LocalBackupConfig?> = localBackupConfigDao.getConfig()

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
     * 保存本地备份配置
     */
    suspend fun saveLocalBackupConfig(config: LocalBackupConfig) {
        val existing = localBackupConfigDao.getConfigSync()
        if (existing != null) {
            localBackupConfigDao.update(config.copy(id = existing.id))
        } else {
            localBackupConfigDao.insert(config)
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
     * 获取本地备份配置（同步）
     */
    suspend fun getLocalBackupConfigSync(): LocalBackupConfig? {
        return localBackupConfigDao.getConfigSync()
    }

    /**
     * 测试WebDAV连接
     */
    suspend fun testConnection(url: String, username: String, password: String): Result<Unit> {
        return webDavClient.testConnection(url, username, password)
    }

    // ==================== 构建备份数据 ====================

    private fun buildBackupJson(): String {
        val accounts = runBlocking { accountDao.getAllAccountsSync() }
        if (accounts.isEmpty()) {
            throw Exception(context.getString(R.string.repo_generic_no_account))
        }
        val accountsData = accounts.map { account ->
            val zones = runBlocking {
                zoneDao.getZonesByAccount(account.id).first()
            }
            account.toAccountData().copy(zones = zones)
        }
        val backup = AccountBackup(
            version = "2.0",
            backupDate = System.currentTimeMillis(),
            accounts = accountsData
        )
        return gson.toJson(backup)
    }

    /**
     * 直接从文件内容恢复（用于导入外部备份文件）
     * @param content 备份文件内容（明文或加密后的 Base64）
     * @param password 密码，null 表示按明文处理
     */
    suspend fun restoreFromContent(content: String, password: String?): Result<Int> {
        return try {
            val json = if (password != null) {
                try {
                    BackupCrypto.decrypt(content.trim(), password, context)
                } catch (e: Exception) {
                    return Result.failure(Exception(context.getString(R.string.repo_generic_decrypt_failed)))
                }
            } else {
                content
            }
            restoreFromJson(json)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun restoreFromJson(json: String): Result<Int> {
        return try {
            val backup = gson.fromJson(json, AccountBackup::class.java)

            zoneDao.deleteAllZones()
            accountDao.deleteAllAccounts()

            val accountList = backup.accounts.map { it.toAccount() }
            accountDao.insertAccounts(accountList)

            val allAccounts = runBlocking { accountDao.getAllAccountsSync() }
            val accountIdMap = allAccounts.associateBy({ it.accountId }, { it.id })

            backup.accounts.forEach { acc ->
                val newAccountId = accountIdMap[acc.accountId] ?: 0L
                acc.zones?.forEach { zone ->
                    zoneDao.insertZone(zone.copy(accountId = newAccountId))
                }
            }

            Result.success(accountList.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateFileName(encrypted: Boolean): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val ext = if (encrypted) "enc" else "json"
        return "cloudflare_backup_$timestamp.$ext"
    }

    // ==================== WebDAV ====================

    /**
     * 备份账号列表到WebDAV
     * @param password 密码，为 null 则不加密
     */
    suspend fun backupAccounts(password: String?): Result<String> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception(context.getString(R.string.repo_backup_webdav_not_configured)))

            val json = buildBackupJson()
            val content = if (password.isNullOrBlank()) json else BackupCrypto.encrypt(json, password)
            val fileName = generateFileName(!password.isNullOrBlank())
            val filePath = if (config.backupPath.endsWith("/")) {
                "${config.backupPath}$fileName"
            } else {
                "${config.backupPath}/$fileName"
            }

            val result = webDavClient.uploadFile(
                config.url,
                config.username,
                config.password,
                filePath,
                content
            )

            return if (result.isSuccess) {
                Result.success(fileName)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception(context.getString(R.string.repo_generic_upload_failed)))
            }

        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * 从WebDAV恢复账号列表
     * @param password 密码，为 null 则按明文处理
     */
    suspend fun restoreAccounts(fileName: String, password: String?): Result<Int> {
        try {
            val config = webDavConfigDao.getConfigSync()
                ?: return Result.failure(Exception(context.getString(R.string.repo_backup_webdav_not_configured)))

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
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception(context.getString(R.string.repo_generic_download_failed)))
            }

            val content = downloadResult.getOrNull() ?: return Result.failure(Exception(context.getString(R.string.repo_generic_download_empty)))

            val json = if (password != null) {
                try {
                    BackupCrypto.decrypt(content.trim(), password, context)
                } catch (e: Exception) {
                    return Result.failure(Exception(context.getString(R.string.repo_generic_decrypt_failed)))
                }
            } else {
                content
            }

            return restoreFromJson(json)

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
                ?: return Result.failure(Exception(context.getString(R.string.repo_backup_webdav_not_configured)))

            val result = webDavClient.listFiles(
                config.url,
                config.username,
                config.password,
                config.backupPath
            )

            return if (result.isSuccess) {
                val files = result.getOrNull() ?: emptyList()
                val backupFiles = files
                    .filter {
                        (it.startsWith("cloudflare_backup_") && it.endsWith(".json")) ||
                        (it.startsWith("cloudflare_backup_") && it.endsWith(".enc"))
                    }
                    .sortedDescending()
                Result.success(backupFiles)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception(context.getString(R.string.repo_generic_list_failed)))
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
                ?: return Result.failure(Exception(context.getString(R.string.repo_backup_webdav_not_configured)))

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

    // ==================== R2 ====================

    /**
     * 备份账号列表到R2
     * @param password 密码，为 null 则不加密
     */
    suspend fun backupAccountsToR2(password: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_r2_not_configured)))

                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_not_found)))

                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_no_r2_credential)))
                }

                val s3Config = R2S3Client.S3Config(
                    accountId = account.accountId,
                    accessKeyId = account.r2AccessKeyId,
                    secretAccessKey = account.r2SecretAccessKey
                )

                val json = buildBackupJson()
                val content = if (password.isNullOrBlank()) json else BackupCrypto.encrypt(json, password)
                val fileName = generateFileName(!password.isNullOrBlank())
                val objectKey = if (r2Config.backupPath.endsWith("/")) {
                    "${r2Config.backupPath}$fileName"
                } else {
                    "${r2Config.backupPath}/$fileName"
                }

                val tempFile = File.createTempFile("backup_", ".tmp")
                try {
                    tempFile.writeText(content)
                    val mimeType = if (password.isNullOrBlank()) "application/json" else "application/octet-stream"
                    r2S3Client.uploadObject(s3Config, r2Config.bucketName, objectKey, tempFile, mimeType)
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
     * @param password 密码，为 null 则按明文处理
     */
    suspend fun restoreAccountsFromR2(fileName: String, password: String?): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val r2Config = r2BackupConfigDao.getConfigSync()
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_r2_not_configured)))

                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_not_found)))

                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_no_r2_credential)))
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
                val content = String(data)

                val json = if (password != null) {
                    try {
                        BackupCrypto.decrypt(content.trim(), password, context)
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception(context.getString(R.string.repo_crypto_decrypt_failed)))
                    }
                } else {
                    content
                }

                val backup = gson.fromJson(json, AccountBackup::class.java)

                zoneDao.deleteAllZones()
                accountDao.deleteAllAccounts()

                val accountList = backup.accounts.map { it.toAccount() }
                accountDao.insertAccounts(accountList)

                val allAccounts = runBlocking { accountDao.getAllAccountsSync() }
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
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_r2_not_configured)))

                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_not_found)))

                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_no_r2_credential)))
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
                        (fileName.startsWith("cloudflare_backup_") && fileName.endsWith(".json")) ||
                        (fileName.startsWith("cloudflare_backup_") && fileName.endsWith(".enc"))
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
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_r2_not_configured)))

                val account = accountDao.getAccountById(r2Config.accountId)
                    ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_not_found)))

                if (account.r2AccessKeyId.isNullOrEmpty() || account.r2SecretAccessKey.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.repo_backup_account_no_r2_credential)))
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

    // ==================== 本地备份 ====================

    /**
     * 获取本地备份目录。
     * 优先使用用户选择的 SAF 目录，未选择则回退到应用私有目录。
     */
    private fun getLocalBackupDir(): LocalBackupDir {
        val config = runBlocking { localBackupConfigDao.getConfigSync() }
        val uriStr = config?.directoryUri
        if (!uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            try {
                val docFile = DocumentFile.fromTreeUri(context, uri)
                if (docFile != null && docFile.canWrite()) {
                    return LocalBackupDir.UserSelected(docFile, uriStr)
                }
            } catch (e: Exception) {
                // 权限丢失或无效，回退到私有目录
            }
        }
        return LocalBackupDir.AppPrivate(localBackupDir)
    }

    private sealed class LocalBackupDir {
        data class UserSelected(val docFile: DocumentFile, val uri: String) : LocalBackupDir()
        data class AppPrivate(val dir: File) : LocalBackupDir()
    }

    /**
     * 是否使用用户选择的目录
     */
    suspend fun isUsingUserSelectedDir(): Boolean {
        val config = localBackupConfigDao.getConfigSync()
        val uriStr = config?.directoryUri
        if (uriStr.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriStr)
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.canWrite() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 备份到本地
     * @param password 密码，为 null 则不加密
     */
    suspend fun backupAccountsLocal(password: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = getLocalBackupDir()
                val json = buildBackupJson()
                val content = if (password.isNullOrBlank()) json else BackupCrypto.encrypt(json, password)
                val fileName = generateFileName(!password.isNullOrBlank())

                when (dir) {
                    is LocalBackupDir.UserSelected -> {
                        val mimeType = if (password.isNullOrBlank()) "application/json" else "application/octet-stream"
                        val file = dir.docFile.createFile(mimeType, fileName)
                            ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_create_file_failed)))
                        context.contentResolver.openOutputStream(file.uri)?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        } ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_write_file_failed)))
                    }
                    is LocalBackupDir.AppPrivate -> {
                        val file = File(dir.dir, fileName)
                        file.writeText(content)
                    }
                }

                Result.success(fileName)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 从本地恢复
     * @param password 密码，为 null 则按明文处理
     */
    suspend fun restoreAccountsLocal(fileName: String, password: String?): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = getLocalBackupDir()

                val content = when (dir) {
                    is LocalBackupDir.UserSelected -> {
                        val file = dir.docFile.findFile(fileName)
                            ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_file_not_found)))
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            input.bufferedReader().use { it.readText() }
                        } ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_read_file_failed)))
                    }
                    is LocalBackupDir.AppPrivate -> {
                        val file = File(dir.dir, fileName)
                        if (!file.exists()) return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_file_not_found)))
                        file.readText()
                    }
                }

                val json = if (password != null) {
                    try {
                        BackupCrypto.decrypt(content.trim(), password, context)
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception(context.getString(R.string.repo_crypto_decrypt_failed)))
                    }
                } else {
                    content
                }

                restoreFromJson(json)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 列出本地备份文件
     */
    suspend fun listLocalBackupFiles(): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = getLocalBackupDir()

                val files = when (dir) {
                    is LocalBackupDir.UserSelected -> {
                        dir.docFile.listFiles()
                            .filter {
                                val name = it.name ?: return@filter false
                                (name.startsWith("cloudflare_backup_") && name.endsWith(".json")) ||
                                (name.startsWith("cloudflare_backup_") && name.endsWith(".enc"))
                            }
                            .sortedByDescending { it.lastModified() }
                            .map { it.name ?: "" }
                            .filter { it.isNotBlank() }
                    }
                    is LocalBackupDir.AppPrivate -> {
                        dir.dir.listFiles()
                            ?.filter {
                                (it.name.startsWith("cloudflare_backup_") && it.name.endsWith(".json")) ||
                                (it.name.startsWith("cloudflare_backup_") && it.name.endsWith(".enc"))
                            }
                            ?.sortedByDescending { it.lastModified() }
                            ?.map { it.name }
                            ?: emptyList()
                    }
                }

                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 删除本地备份文件
     */
    suspend fun deleteLocalBackupFile(fileName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = getLocalBackupDir()

                when (dir) {
                    is LocalBackupDir.UserSelected -> {
                        val file = dir.docFile.findFile(fileName)
                        if (file != null && file.exists()) {
                            file.delete()
                        }
                    }
                    is LocalBackupDir.AppPrivate -> {
                        val file = File(dir.dir, fileName)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 将备份文件保存到本地备份目录（导入外部文件时用）
     */
    suspend fun saveLocalBackupFile(content: String, originalFileName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val dir = getLocalBackupDir()
                // 如果文件名不符合规范，生成一个规范的文件名
                val fileName = if (originalFileName.startsWith("cloudflare_backup_") &&
                    (originalFileName.endsWith(".json") || originalFileName.endsWith(".enc"))
                ) {
                    originalFileName
                } else {
                    val isEncrypted = !content.trim().startsWith("{")
                    generateFileName(isEncrypted)
                }

                when (dir) {
                    is LocalBackupDir.UserSelected -> {
                        val mimeType = if (fileName.endsWith(".enc")) "application/octet-stream" else "application/json"
                        val file = dir.docFile.createFile(mimeType, fileName)
                            ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_create_file_failed)))
                        context.contentResolver.openOutputStream(file.uri)?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                        } ?: return@withContext Result.failure(Exception(context.getString(R.string.repo_generic_write_file_failed)))
                    }
                    is LocalBackupDir.AppPrivate -> {
                        val file = File(dir.dir, fileName)
                        file.writeText(content)
                    }
                }

                Result.success(fileName)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 持久化本地备份目录的访问权限
     */
    fun persistLocalDirectoryPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            // 忽略，部分设备可能不支持
        }
    }
}
