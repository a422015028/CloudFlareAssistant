package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PagesRepository @Inject constructor(
    private val api: CloudFlareApi
) {

    suspend fun listProjects(account: Account): Resource<List<PagesProject>> =   
        withContext(Dispatchers.IO) {  
            safeApiCall {  
                val response = api.listPagesProjects(  
                    token = AuthHelper.getBearerToken(account),  
                    email = AuthHelper.getEmail(account),  
                    apiKey = AuthHelper.getGlobalApiKey(account),  
                    accountId = account.accountId  
                )  
                  
                if (response.isSuccessful && response.body()?.success == true) {  
                    Resource.Success(response.body()?.result ?: emptyList())  
                } else {  
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                        ?: response.message()  
                    Resource.Error("Failed to list projects: $errorMsg")  
                }  
            }  
        }  
      
    suspend fun createProject(  
        account: Account,  
        name: String,  
        productionBranch: String = "main"
    ): Resource<PagesProject> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.createPagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                project = PagesProjectRequest(  
                    name = name,  
                    productionBranch = productionBranch
                )  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Project created but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to create project: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun deleteProject(  
        account: Account,  
        projectName: String  
    ): Resource<Unit> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.deletePagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                Resource.Success(Unit)  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to delete project: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun getProject(  
        account: Account,  
        projectName: String  
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.getPagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Project not found")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to get project: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun listDeployments(  
        account: Account,  
        projectName: String  
    ): Resource<List<PagesDeployment>> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.listPagesDeployments(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                Resource.Success(response.body()?.result ?: emptyList())  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to list deployments: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun retryDeployment(  
        account: Account,  
        projectName: String,  
        deploymentId: String  
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.retryPagesDeployment(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                deploymentId = deploymentId  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Deployment retried but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to retry deployment: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun deleteDeployment(  
        account: Account,  
        projectName: String,  
        deploymentId: String  
    ): Resource<Unit> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.deletePagesDeployment(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                deploymentId = deploymentId  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                Resource.Success(Unit)  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to delete deployment: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun rollbackDeployment(  
        account: Account,  
        projectName: String,  
        deploymentId: String  
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.rollbackPagesDeployment(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                deploymentId = deploymentId  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Deployment rolled back but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to rollback deployment: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun createDeployment(  
        account: Account,  
        projectName: String,  
        branch: String,  
        file: java.io.File  
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            if (!file.name.endsWith(".zip", ignoreCase = true)) {  
                return@safeApiCall Resource.Error("仅支持 .zip 文件部署。")  
            }  
            if (!file.exists()) {  
                return@safeApiCall Resource.Error("文件不存在")  
            }  
              
            // 1. 校验并创建项目
            val projectExists = checkProjectExists(account, projectName)  
            if (!projectExists) {  
                createProject(account, projectName, branch)
            }  

            val tempDir = File(file.parentFile, "cf_pages_unzipped_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            try {
                unzip(file, tempDir)

                // 智能穿透嵌套目录
                var baseDir = tempDir
                while (true) {
                    val validFiles = baseDir.listFiles()?.filter { 
                        it.name != ".DS_Store" && it.name != "__MACOSX" && !it.name.startsWith(".")
                    }
                    if (validFiles != null && validFiles.size == 1 && validFiles[0].isDirectory) {
                        baseDir = validFiles[0]
                    } else {
                        break
                    }
                }

                val allFiles = mutableListOf<File>()
                val manifestMap = mutableMapOf<String, String>()

                fun collectFiles(currentFile: File) {
                    if (currentFile.isDirectory) {
                        currentFile.listFiles()?.forEach { collectFiles(it) }
                    } else {
                        val name = currentFile.name
                        if (name == ".DS_Store" || currentFile.absolutePath.contains("__MACOSX") || name.startsWith(".")) {
                            return
                        }
                        allFiles.add(currentFile)
                        // 注意：Wrangler 规范中，此处需要添加领先斜杠 "/" 来做 CDN 映射路径！
                        val relativePath = "/" + currentFile.relativeTo(baseDir).path.replace("\\", "/")
                        val md5 = getMd5Hash(currentFile)
                        manifestMap[relativePath] = md5
                    }
                }
                baseDir.listFiles()?.forEach { collectFiles(it) }

                if (allFiles.isEmpty()) return@safeApiCall Resource.Error("压缩包内无有效文件")

                // 2. 【Wrangler 步骤一】向 Cloudflare 申请专属资源上传 JWT Token
                val tokenResponse = api.getPagesUploadToken(
                    token = AuthHelper.getBearerToken(account),  
                    email = AuthHelper.getEmail(account),  
                    apiKey = AuthHelper.getGlobalApiKey(account),  
                    accountId = account.accountId,  
                    projectName = projectName
                )
                val jwt = tokenResponse.body()?.result?.jwt ?: return@safeApiCall Resource.Error("无法获取资产上传 Token")

                // 3. 【Wrangler 步骤二】把本地解压出的文件转为 Base64 对象批量上传至资源库
                val assetPayloads = allFiles.map { currentFile ->
                    val relativePath = "/" + currentFile.relativeTo(baseDir).path.replace("\\", "/")
                    val md5 = manifestMap[relativePath] ?: ""
                    val base64Str = android.util.Base64.encodeToString(currentFile.readBytes(), android.util.Base64.NO_WRAP)
                    
                    val contentType = when (currentFile.extension.lowercase()) {
                        "html", "htm"       -> "text/html"
                        "css"               -> "text/css"
                        "txt"               -> "text/plain"
                        "xml"               -> "text/xml"
                        "js", "mjs", "cjs"  -> "application/javascript"
                        "json"              -> "application/json"
                        "map"               -> "application/json"
                        "wasm"              -> "application/wasm"
                        "webmanifest"       -> "application/manifest+json"
                        "png"               -> "image/png"
                        "jpg", "jpeg"       -> "image/jpeg"
                        "gif"               -> "image/gif"
                        "svg"               -> "image/svg+xml"
                        "webp"              -> "image/webp"
                        "avif"              -> "image/avif"
                        "ico"               -> "image/x-icon"
                        "woff2"             -> "font/woff2"
                        "woff"              -> "font/woff"
                        "ttf"               -> "font/ttf"
                        "otf"               -> "font/otf"
                        "eot"               -> "application/vnd.ms-fontobject"
                        "yaml", "yml"       -> "text/yaml"
                        "toml"              -> "text/toml"
                        "mp4"               -> "video/mp4"
                        "webm"              -> "video/webm"
                        "mp3"               -> "audio/mpeg"

                        // 兜底配置
                        else          -> "application/octet-stream"
                }

                    PagesAssetPayload(key = md5, value = base64Str, metadata = AssetMeta(contentType))
                }

                // 执行资产库推送
                val uploadResponse = api.uploadPagesAssets(jwtToken = "Bearer $jwt", assets = assetPayloads)
                if (!uploadResponse.isSuccessful) {
                    return@safeApiCall Resource.Error("资产原子层同步失败，HTTP Code: ${uploadResponse.code()}")
                }

                // 4. 【Wrangler 步骤三】组装纯清单 Manifest JSON 触发 CDN 全球路由刷新
                val manifestJson = manifestMap.entries.joinToString(",", "{", "}") { entry ->
                   "\"${entry.key}\":\"${entry.value}\""
                }
                val manifestBody = manifestJson.toRequestBody("application/json".toMediaType())

                // 最终盖章：通知部署完成
                val response = api.createPagesDeploymentManifestOnly(  
                    token = AuthHelper.getBearerToken(account),  
                    email = AuthHelper.getEmail(account),  
                    apiKey = AuthHelper.getGlobalApiKey(account),  
                    accountId = account.accountId,  
                    projectName = projectName,  
                    manifest = manifestBody
                )  
                  
                if (response.isSuccessful && response.body()?.success == true) {  
                    response.body()?.result?.let {  
                        Resource.Success(it)  
                    } ?: Resource.Error("部署创建成功，但没有返回结果")  
                } else {  
                    Resource.Error("最终部署盖章失败: ${response.message()}")  
                }
            } finally { 
                tempDir.deleteRecursively() // 彻底清理碎片
            }
        }  
    } 
      
    private suspend fun checkProjectExists(account: Account, projectName: String): Boolean {  
        return try {  
            val response = api.getPagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName  
            )  
            response.isSuccessful && response.body()?.success == true  
        } catch (e: Exception) {  
            Timber.e(e, "Error checking if project exists")  
            false  
        }  
    }  
      
    // ==================== Project Configuration Management ====================  
      
    suspend fun updateEnvironmentVariables(  
        account: Account,  
        projectName: String,  
        environment: String,  
        variables: Map<String, Pair<String, String>?>  
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val envVarsMap = variables.mapValues { (_, value) ->  
                value?.let { (type, varValue) ->  
                    EnvVarUpdate(type = type, value = varValue)  
                }  
            }  
              
            val envConfig = EnvironmentConfigUpdate(envVars = envVarsMap)  
              
            val deploymentConfigs = if (environment == "production") {  
                DeploymentConfigsUpdate(production = envConfig)  
            } else {  
                DeploymentConfigsUpdate(preview = envConfig)  
            }  
              
            val updateRequest = PagesProjectUpdateRequest(deploymentConfigs = deploymentConfigs)  
              
            val response = api.updatePagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                updateRequest = updateRequest  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Variables updated but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to update variables: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun updateKvBindings(  
        account: Account,  
        projectName: String,  
        environment: String,  
        bindings: Map<String, String?>  
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val kvNamespacesMap = bindings.mapValues { (_, namespaceId) ->  
                namespaceId?.let { KvBindingUpdate(namespaceId = it) }  
            }  
              
            val envConfig = EnvironmentConfigUpdate(kvNamespaces = kvNamespacesMap)  
              
            val deploymentConfigs = if (environment == "production") {  
                DeploymentConfigsUpdate(production = envConfig)  
            } else {  
                DeploymentConfigsUpdate(preview = envConfig)  
            }  
              
            val updateRequest = PagesProjectUpdateRequest(deploymentConfigs = deploymentConfigs)  
              
            val response = api.updatePagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                updateRequest = updateRequest  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("KV bindings updated but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to update KV bindings: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun updateR2Bindings(  
        account: Account,  
        projectName: String,  
        environment: String,  
        bindings: Map<String, String?>  
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val r2BucketsMap = bindings.mapValues { (_, bucketName) ->  
                bucketName?.let { R2BindingUpdate(name = it) }  
            }  
              
            val envConfig = EnvironmentConfigUpdate(r2Buckets = r2BucketsMap)  
              
            val deploymentConfigs = if (environment == "production") {  
                DeploymentConfigsUpdate(production = envConfig)  
            } else {  
                DeploymentConfigsUpdate(preview = envConfig)  
            }  
              
            val updateRequest = PagesProjectUpdateRequest(deploymentConfigs = deploymentConfigs)  
              
            val response = api.updatePagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                updateRequest = updateRequest  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("R2 bindings updated but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to update R2 bindings: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun updateD1Bindings(  
        account: Account,  
        projectName: String,  
        environment: String,  
        bindings: Map<String, String?>  
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val d1DatabasesMap = bindings.mapValues { (_, databaseId) ->  
                databaseId?.let { D1BindingUpdate(id = it) }  
            }  
              
            val envConfig = EnvironmentConfigUpdate(d1Databases = d1DatabasesMap)  
              
            val deploymentConfigs = if (environment == "production") {  
                DeploymentConfigsUpdate(production = envConfig)  
            } else {  
                DeploymentConfigsUpdate(preview = envConfig)  
            }  
              
            val updateRequest = PagesProjectUpdateRequest(deploymentConfigs = deploymentConfigs)  
              
            val response = api.updatePagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                updateRequest = updateRequest  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("D1 bindings updated but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to update D1 bindings: $errorMsg")  
            }  
        }  
    }  
      
    // ==================== Pages Domains ====================  
      
    suspend fun listDomains(  
        account: Account,  
        projectName: String  
    ): Resource<List<PagesDomain>> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.listPagesDomains(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                Resource.Success(response.body()?.result ?: emptyList())  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to list domains: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun addDomain(  
        account: Account,  
        projectName: String,  
        domainName: String  
    ): Resource<PagesDomain> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.addPagesDomain(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                request = PagesDomainRequest(name = domainName)  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                response.body()?.result?.let {  
                    Resource.Success(it)  
                } ?: Resource.Error("Domain added but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to add domain: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun deleteDomain(  
        account: Account,  
        projectName: String,  
        domainName: String  
    ): Resource<Unit> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.deletePagesDomain(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                projectName = projectName,  
                domainName = domainName  
            )  
              
            if (response.isSuccessful && response.body()?.success == true) {  
                Resource.Success(Unit)  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to delete domain: $errorMsg")  
            }  
        }  
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    newFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun getMd5Hash(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}