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
import java.io.ByteArrayOutputStream
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
                var workerJsFile: File? = null

                fun collectFiles(currentFile: File) {
                    if (currentFile.isDirectory) {
                        currentFile.listFiles()?.forEach { collectFiles(it) }
                    } else {
                        val name = currentFile.name
                        if (name == ".DS_Store" || currentFile.absolutePath.contains("__MACOSX") || name.startsWith(".")) {
                            return
                        }
                        if (name == "_worker.js") {
                            Timber.d("collectFiles: 找到 _worker.js, 路径=${currentFile.absolutePath}, 大小=${currentFile.length()}字节")
                            workerJsFile = currentFile
                            return
                        }
                        allFiles.add(currentFile)
                        val relativePath = "/" + currentFile.relativeTo(baseDir).path.replace("\\", "/")
                        val cfHash = getCfHash(currentFile)
                        manifestMap[relativePath] = cfHash
                    }
                }
                baseDir.listFiles()?.forEach { collectFiles(it) }

                if (allFiles.isEmpty() && workerJsFile == null) {
                    return@safeApiCall Resource.Error("压缩包内无有效文件")
                }

                // 2. 【Wrangler 步骤一】向 Cloudflare 申请专属资源上传 JWT Token
                // 如果只有 _worker.js 没有静态资产，也需要获取 token（资产库为空时也可创建部署）
                val tokenResponse = api.getPagesUploadToken(
                    token = AuthHelper.getBearerToken(account),  
                    email = AuthHelper.getEmail(account),  
                    apiKey = AuthHelper.getGlobalApiKey(account),  
                    accountId = account.accountId,  
                    projectName = projectName
                )
                val jwt = tokenResponse.body()?.result?.jwt ?: return@safeApiCall Resource.Error("无法获取资产上传 Token")

                // 3. 【Wrangler 步骤二】把本地解压出的文件转为 Base64 对象批量上传至资源库
                if (allFiles.isNotEmpty()) {
                    val assetPayloads = allFiles.map { currentFile ->
                        val relativePath = "/" + currentFile.relativeTo(baseDir).path.replace("\\", "/")
                        val cfHash = manifestMap[relativePath] ?: ""
                        val base64Str = android.util.Base64.encodeToString(currentFile.readBytes(), android.util.Base64.NO_WRAP)
                        
                        val ext = currentFile.extension.lowercase()
                        val contentType = when (ext) {
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
                            else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) 
                                    ?: "application/octet-stream" // 3. 查不到才兜底
                    }

                        PagesAssetPayload(key = cfHash, value = base64Str, metadata = AssetMeta(contentType))
                    }

                    // 执行资产库推送
                    val uploadResponse = api.uploadPagesAssets(jwtToken = "Bearer $jwt", assets = assetPayloads)
                    if (!uploadResponse.isSuccessful) {
                        return@safeApiCall Resource.Error("资产原子层同步失败，HTTP Code: ${uploadResponse.code()}")
                    }
                }

                // 3.b 【Wrangler upsert-hashes】更新资产哈希列表，初始化部署会话（即使没有静态资产也需要）
                val allHashes = manifestMap.values.toList()
                val upsertResponse = api.upsertPagesAssetHashes(
                    jwtToken = "Bearer $jwt",
                    body = PagesUpsertHashesPayload(hashes = allHashes)
                )
                if (!upsertResponse.isSuccessful) {
                    Timber.w("upsert-hashes 返回非成功状态: HTTP ${upsertResponse.code()}，继续尝试部署")
                }

                // 4. 【Wrangler 步骤三】组装纯清单 Manifest JSON 触发 CDN 全球路由刷新
                val manifestJson = manifestMap.entries.joinToString(",", "{", "}") { entry ->
                   "\"${entry.key}\":\"${entry.value}\""
                }
                val manifestBody = manifestJson.toRequestBody("application/json".toMediaType())

                // 最终盖章：通知部署完成（根据是否有 _worker.js 选择不同接口）
                val response = if (workerJsFile != null) {
                    val workerFileSize = workerJsFile!!.length()
                    Timber.d("workerJsFile 路径: ${workerJsFile!!.absolutePath}")
                    Timber.d("workerJsFile 文件大小: $workerFileSize 字节")
                    if (workerFileSize == 0L) {
                        return@safeApiCall Resource.Error("_worker.js 文件内容为空（0字节），请检查 zip 包中的文件是否完整")
                    }
                    // 构建 _worker.bundle: 手动构建 multipart 字节流
                    val workerBundle = buildWorkerBundle(workerJsFile!!)
                    Timber.d("_worker.bundle 大小: ${workerBundle.contentLength()} 字节")
                    val workerBundlePart = MultipartBody.Part.createFormData(
                        "_worker.bundle", "_worker.bundle", workerBundle
                    )
                    api.createPagesDeploymentWithWorker(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId,
                        projectName = projectName,
                        manifest = manifestBody,
                        workerBundle = workerBundlePart
                    )
                } else {
                    api.createPagesDeploymentManifestOnly(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId,
                        projectName = projectName,
                        manifest = manifestBody
                    )
                }
                  
                if (response.isSuccessful && response.body()?.success == true) {  
                    response.body()?.result?.let {  
                        Resource.Success(it)  
                    } ?: Resource.Error("部署创建成功，但没有返回结果")  
                } else {  
                    val errorBody = response.errorBody()?.string() ?: "无错误响应体"
                    val apiErrors = response.body()?.errors?.joinToString("; ") { "${it.code}: ${it.message}" }
                    val errorMsg = "部署失败: HTTP ${response.code()}, ${response.message()}. API错误: $apiErrors. 响应体: $errorBody"
                    Timber.e(errorMsg)
                    Resource.Error(errorMsg)  
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
      
    private fun buildWorkerBundle(workerJsFile: File): RequestBody {
        val boundary = "----CloudFlareWorkerBundle${System.currentTimeMillis()}----"
        val metadataJson = """{"main_module":"_worker.js"}"""
        val workerContent = workerJsFile.readBytes()
        Timber.d("buildWorkerBundle: 读取到 ${workerContent.size} 字节, 文件路径: ${workerJsFile.absolutePath}")

        val baos = ByteArrayOutputStream()
        // metadata part
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"metadata\"\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Type: application/json\r\n".toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        baos.write(metadataJson.toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        // _worker.js part
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"_worker.js\"; filename=\"_worker.js\"\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Type: application/javascript+module\r\n".toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        baos.write(workerContent)
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        // closing boundary
        baos.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val bundleBytes = baos.toByteArray()
        Timber.d("buildWorkerBundle: bundle 总大小 ${bundleBytes.size} 字节")
        return bundleBytes.toRequestBody("multipart/form-data; boundary=$boundary".toMediaType())
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
        Timber.d("unzip: zip文件大小=${zipFile.length()}字节, 路径=${zipFile.absolutePath}")
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                Timber.d("unzip: entry=${entry.name}, size=${entry.size}, compressedSize=${entry.compressedSize}, isDirectory=${entry.isDirectory}")
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    newFile.outputStream().use { fos ->
                        val copied = zis.copyTo(fos)
                        Timber.d("unzip: 解压 ${entry.name} 完成, 复制了 $copied 字节, 文件大小=${newFile.length()}")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun getCfHash(file: File): String {
        val bytes = file.readBytes()
        val base64Content = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val ext = file.extension.lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(base64Content.toByteArray(Charsets.UTF_8))
        digest.update(ext.toByteArray(Charsets.UTF_8))
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }
}