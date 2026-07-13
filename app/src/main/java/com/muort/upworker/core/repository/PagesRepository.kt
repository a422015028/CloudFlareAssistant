package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.SucraseInput
import com.muort.upworker.core.util.SucraseTransformer
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
    private val api: CloudFlareApi,
    private val sucraseTransformer: SucraseTransformer
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
      
    /**
     * 更新项目的兼容性日期
     */
    private suspend fun updateProjectCompatibilityDate(
        account: Account,
        projectName: String,
        compatibilityDate: String
    ) {
        try {
            val updateRequest = PagesProjectUpdateRequest(
                deploymentConfigs = DeploymentConfigsUpdate(
                    preview = EnvironmentConfigUpdate(compatibilityDate = compatibilityDate),
                    production = EnvironmentConfigUpdate(compatibilityDate = compatibilityDate)
                )
            )
            api.updatePagesProject(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                projectName = projectName,
                updateRequest = updateRequest
            )
            Timber.d("项目 $projectName 的 compatibility_date 更新为 $compatibilityDate")
        } catch (e: Exception) {
            Timber.w("更新项目 compatibility_date 失败: ${e.message}")
        }
    }

    /**
     * 更新项目的 compatibility_flags
     */
    private suspend fun updateProjectCompatibilityFlags(
        account: Account,
        projectName: String,
        compatibilityFlags: List<String>
    ) {
        try {
            val updateRequest = PagesProjectUpdateRequest(
                deploymentConfigs = DeploymentConfigsUpdate(
                    preview = EnvironmentConfigUpdate(compatibilityFlags = compatibilityFlags),
                    production = EnvironmentConfigUpdate(compatibilityFlags = compatibilityFlags)
                )
            )
            api.updatePagesProject(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                projectName = projectName,
                updateRequest = updateRequest
            )
            Timber.d("项目 $projectName 的 compatibility_flags 更新为 $compatibilityFlags")
        } catch (e: Exception) {
            Timber.w("更新项目 compatibility_flags 失败: ${e.message}")
        }
    }

    /**
     * 更新项目的 asset 配置（not_found_page, pretty_urls）
     * 注意：Cloudflare API 通过 PATCH 项目接口更新 deployment_configs 中的设置
     */
    suspend fun updateAssetConfig(
        account: Account,
        projectName: String,
        notFoundPage: String? = null,
        prettyUrls: Boolean? = null
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {
        safeApiCall {
            val updateRequest = PagesProjectUpdateRequest(
                notFoundPage = notFoundPage,
                prettyUrls = prettyUrls
            )
            val response = api.updatePagesProject(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                projectName = projectName,
                updateRequest = updateRequest
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let { Resource.Success(it) }
                    ?: Resource.Error("Asset config updated but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error("Failed to update asset config: $errorMsg")
            }
        }
    }

    /**
     * 更新 Durable Objects 绑定
     */
    suspend fun updateDurableObjectBindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, Pair<String, String>?>
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {
        safeApiCall {
            val doMap = bindings.mapValues { (_, classInfo) ->
                classInfo?.let { (className, _) ->
                    DurableObjectBindingUpdate(className = className)
                }
            }
            val envConfig = EnvironmentConfigUpdate(durableObjects = doMap)
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
                response.body()?.result?.let { Resource.Success(it) }
                    ?: Resource.Error("Durable Objects bindings updated but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error("Failed to update Durable Objects bindings: $errorMsg")
            }
        }
    }

    /**
     * 更新 Service Bindings
     */
    suspend fun updateServiceBindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, Pair<String, String>?>
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {
        safeApiCall {
            val svcMap = bindings.mapValues { (_, svcInfo) ->
                svcInfo?.let { (service, env) ->
                    ServiceBindingUpdate(service = service, environment = env)
                }
            }
            val envConfig = EnvironmentConfigUpdate(services = svcMap)
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
                response.body()?.result?.let { Resource.Success(it) }
                    ?: Resource.Error("Service bindings updated but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error("Failed to update service bindings: $errorMsg")
            }
        }
    }

    suspend fun createProject(
        account: Account,  
        name: String,  
        productionBranch: String = "main",
        buildCommand: String? = null,
        destinationDir: String? = null,
        rootDir: String? = null,
        buildCaching: Boolean? = null,
        compatibilityDate: String? = null
    ): Resource<PagesProject> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val buildConfig = if (buildCommand != null || destinationDir != null || 
                rootDir != null || buildCaching != null) {
                BuildConfig(
                    buildCommand = buildCommand,
                    destinationDir = destinationDir,
                    rootDir = rootDir,
                    buildCaching = buildCaching,
                    webAnalyticsTag = null,
                    webAnalyticsToken = null
                )
            } else {
                null
            }

            val deploymentConfigs = if (compatibilityDate != null) {
                PagesDeploymentConfigs(
                    preview = PagesDeploymentConfig(compatibilityDate = compatibilityDate),
                    production = PagesDeploymentConfig(compatibilityDate = compatibilityDate)
                )
            } else {
                null
            }

            val response = api.createPagesProject(  
                token = AuthHelper.getBearerToken(account),  
                email = AuthHelper.getEmail(account),  
                apiKey = AuthHelper.getGlobalApiKey(account),  
                accountId = account.accountId,  
                project = PagesProjectRequest(  
                    name = name,  
                    productionBranch = productionBranch,
                    buildConfig = buildConfig,
                    deploymentConfigs = deploymentConfigs
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
      
    suspend fun getDeploymentLogs(  
        account: Account,  
        projectName: String,  
        deploymentId: String  
    ): Resource<PagesDeploymentLogs> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            val response = api.getPagesDeploymentLogs(  
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
                } ?: Resource.Error("Logs fetched but no result returned")  
            } else {  
                val errorMsg = response.body()?.errors?.firstOrNull()?.message   
                    ?: response.message()  
                Resource.Error("Failed to get deployment logs: $errorMsg")  
            }  
        }  
    }  
      
    suspend fun createDeployment(  
        account: Account,  
        projectName: String,  
        branch: String,  
        file: java.io.File,
        customCompatibilityDate: String? = null,
        customCompatibilityFlags: List<String>? = null
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {  
        safeApiCall {  
            if (!file.exists()) {  
                return@safeApiCall Resource.Error("文件不存在")  
            }

            val isZip = file.name.endsWith(".zip", ignoreCase = true)
            val isJs = file.name.endsWith(".js", ignoreCase = true)
            val isHtml = file.name.endsWith(".htm", ignoreCase = true) ||
                         file.name.endsWith(".html", ignoreCase = true)
            if (!isZip && !isJs && !isHtml) {
                return@safeApiCall Resource.Error("仅支持 .zip、.js 或 .html 文件部署。")
            }

            val finalCompatibilityDate = customCompatibilityDate ?: DEFAULT_COMPATIBILITY_DATE
  
            // 1. 校验并创建项目
            val projectExists = checkProjectExists(account, projectName)  
            if (!projectExists) {  
                // 新项目：用户自定义日期 > 默认兼容日期
                createProject(account, projectName, branch, finalCompatibilityDate)
            } else {
                // 已有项目：只有用户自定义日期时才更新，没有自定义则不修改
                customCompatibilityDate?.let {
                    updateProjectCompatibilityDate(account, projectName, it)
                }
            }

            // 更新 compatibility_flags（无论项目是否新建，都确保 flags 同步）
            if (customCompatibilityFlags != null) {
                updateProjectCompatibilityFlags(account, projectName, customCompatibilityFlags)
            }  

            // 单文件 .js 部署：直接作为 Worker 脚本上传（bundle 接口）
            if (isJs) {
                return@safeApiCall deployWorkerOnly(account, projectName, file)
            }

            // 单文件 .htm/.html 部署：走原有 manifest-only 流程
            if (isHtml) {
                return@safeApiCall deployStaticAssetOnly(account, projectName, file)
            }

            // zip 文件部署
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
                    if (validFiles != null && validFiles.size == 1 && validFiles[0].isDirectory
                        && validFiles[0].name != "_worker.js"  // 不要穿透 _worker.js 目录
                        && validFiles[0].name != "functions") {  // 不要穿透 functions 目录
                        baseDir = validFiles[0]
                    } else {
                        break
                    }
                }

                val allFiles = mutableListOf<File>()
                val manifestMap = mutableMapOf<String, String>()
                var workerJsFile: File? = null
                var hasFunctionsDir = false

                // 读取 .assetsignore 文件
                val assetsignoreFile = File(baseDir, ".assetsignore")
                val ignorePatterns = if (assetsignoreFile.exists()) {
                    assetsignoreFile.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .also { Timber.d("collectFiles: 读取到 .assetsignore, ${it.size} 条规则") }
                } else {
                    emptyList()
                }

                // 检查路径是否匹配 .assetsignore 规则
                fun isIgnored(relativePath: String): Boolean {
                    if (ignorePatterns.isEmpty()) return false
                    // 去掉开头的 /
                    val path = relativePath.removePrefix("/")
                    for (pattern in ignorePatterns) {
                        if (matchGlob(path, pattern)) {
                            Timber.d("collectFiles: $path 被 .assetsignore 规则 '$pattern' 忽略")
                            return true
                        }
                    }
                    return false
                }

                fun collectFiles(currentFile: File) {
                    if (currentFile.isDirectory) {
                        // 检测 functions 目录
                        if (currentFile.name == "functions" && currentFile.parentFile == baseDir) {
                            hasFunctionsDir = true
                            Timber.d("collectFiles: 检测到 functions 目录，跳过静态文件收集")
                            return // 不收集 functions 目录下的文件作为静态资源
                        }
                        currentFile.listFiles()?.forEach { collectFiles(it) }
                    } else {
                        val name = currentFile.name
                        if (name == ".DS_Store" || currentFile.absolutePath.contains("__MACOSX") || name.startsWith(".")) {
                            return
                        }
                        // 跳过 .assetsignore 本身
                        if (name == ".assetsignore") return
                        val relativePath = "/" + currentFile.relativeTo(baseDir).path.replace("\\", "/")
                        if (name == "_worker.js" && relativePath == "/_worker.js") {
                            Timber.d("collectFiles: 找到根目录 _worker.js, 路径=${currentFile.absolutePath}, 大小=${currentFile.length()}字节")
                            workerJsFile = currentFile
                            return
                        }
                        // 检查 .assetsignore
                        if (isIgnored(relativePath)) return
                        allFiles.add(currentFile)
                        val cfHash = getCfHash(currentFile)
                        manifestMap[relativePath] = cfHash
                    }
                }
                baseDir.listFiles()?.forEach { collectFiles(it) }

                // 如果是 _worker.js 单文件模式，构建 bundle
                val effectiveWorkerBundle: RequestBody? = if (workerJsFile != null) {
                    Timber.d("使用 _worker.js 单文件模式")
                    buildWorkerBundle(workerJsFile!!)
                } else {
                    null
                }

                if (allFiles.isEmpty() && effectiveWorkerBundle == null && !hasFunctionsDir) {
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

                // 最终盖章：通知部署完成（根据部署类型选择不同接口）
                // 优先级: _worker.js (高级模式, 单文件) > functions/ (标准模式) > 纯静态
                val response = if (effectiveWorkerBundle != null) {
                    Timber.d("_worker.bundle 大小: ${effectiveWorkerBundle.contentLength()} 字节")
                    val workerBundlePart = MultipartBody.Part.createFormData(
                        "_worker.bundle", "_worker.bundle", effectiveWorkerBundle
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
                } else if (hasFunctionsDir) {
                    // Pages Functions 标准模式：检测到 functions/ 目录
                    Timber.d("检测到 functions 目录，使用 Pages Functions 标准模式部署")
                    return@safeApiCall deployWithFunctions(
                        account = account,
                        projectName = projectName,
                        baseDir = baseDir,
                        manifestMap = manifestMap
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
      
    /**
     * 单文件 .htm/.html 部署：走原有 manifest-only 流程（作为静态资产上传）
     */
    private suspend fun deployStaticAssetOnly(
        account: Account,
        projectName: String,
        htmlFile: File
    ): Resource<PagesDeployment> {
        if (htmlFile.length() == 0L) {
            return Resource.Error("文件内容为空（0字节）")
        }

        // 1. 获取 upload token
        val tokenResponse = api.getPagesUploadToken(
            token = AuthHelper.getBearerToken(account),
            email = AuthHelper.getEmail(account),
            apiKey = AuthHelper.getGlobalApiKey(account),
            accountId = account.accountId,
            projectName = projectName
        )
        val jwt = tokenResponse.body()?.result?.jwt
            ?: return Resource.Error("无法获取资产上传 Token")

        // 2. 计算文件 hash，构建 manifest
        val relativePath = "/index.html"
        val cfHash = getCfHash(htmlFile)
        val manifestMap = mapOf(relativePath to cfHash)

        // 3. 上传资产（Base64）
        val base64Str = android.util.Base64.encodeToString(htmlFile.readBytes(), android.util.Base64.NO_WRAP)
        val assetPayload = PagesAssetPayload(
            key = cfHash,
            value = base64Str,
            metadata = AssetMeta(contentType = "text/html")
        )
        val uploadResponse = api.uploadPagesAssets(jwtToken = "Bearer $jwt", assets = listOf(assetPayload))
        if (!uploadResponse.isSuccessful) {
            return Resource.Error("资产上传失败，HTTP Code: ${uploadResponse.code()}")
        }

        // 4. upsert-hashes
        api.upsertPagesAssetHashes(
            jwtToken = "Bearer $jwt",
            body = PagesUpsertHashesPayload(hashes = manifestMap.values.toList())
        )

        // 5. 创建部署（manifest-only）
        val manifestJson = manifestMap.entries.joinToString(",", "{", "}") { entry ->
            "\"${entry.key}\":\"${entry.value}\""
        }
        val manifestBody = manifestJson.toRequestBody("application/json".toMediaType())
        val response = api.createPagesDeploymentManifestOnly(
            token = AuthHelper.getBearerToken(account),
            email = AuthHelper.getEmail(account),
            apiKey = AuthHelper.getGlobalApiKey(account),
            accountId = account.accountId,
            projectName = projectName,
            manifest = manifestBody
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.result?.let { Resource.Success(it) }
                ?: Resource.Error("部署创建成功，但没有返回结果")
        } else {
            val errorBody = response.errorBody()?.string() ?: "无错误响应体"
            Resource.Error("部署失败: HTTP ${response.code()}, ${response.message()}. 响应体: $errorBody")
        }
    }

    /**
     * 单文件 .js 部署：直接作为 Worker 脚本上传（无静态资产）
     */
    private suspend fun deployWorkerOnly(
        account: Account,
        projectName: String,
        workerFile: File
    ): Resource<PagesDeployment> {
        if (workerFile.length() == 0L) {
            return Resource.Error("Worker 脚本文件内容为空（0字节）")
        }

        // 获取 upload token（即使没有静态资产也需要）
        val tokenResponse = api.getPagesUploadToken(
            token = AuthHelper.getBearerToken(account),
            email = AuthHelper.getEmail(account),
            apiKey = AuthHelper.getGlobalApiKey(account),
            accountId = account.accountId,
            projectName = projectName
        )
        val jwt = tokenResponse.body()?.result?.jwt
            ?: return Resource.Error("无法获取资产上传 Token")

        // upsert-hashes（空列表，初始化部署会话）
        api.upsertPagesAssetHashes(
            jwtToken = "Bearer $jwt",
            body = PagesUpsertHashesPayload(hashes = emptyList())
        )

        // 空 manifest + _worker.bundle
        val manifestBody = "{}".toRequestBody("application/json".toMediaType())
        val workerBundle = buildWorkerBundle(workerFile)
        val workerBundlePart = MultipartBody.Part.createFormData(
            "_worker.bundle", "_worker.bundle", workerBundle
        )
        val response = api.createPagesDeploymentWithWorker(
            token = AuthHelper.getBearerToken(account),
            email = AuthHelper.getEmail(account),
            apiKey = AuthHelper.getGlobalApiKey(account),
            accountId = account.accountId,
            projectName = projectName,
            manifest = manifestBody,
            workerBundle = workerBundlePart
        )

        return if (response.isSuccessful && response.body()?.success == true) {
            response.body()?.result?.let { Resource.Success(it) }
                ?: Resource.Error("部署创建成功，但没有返回结果")
        } else {
            val errorBody = response.errorBody()?.string() ?: "无错误响应体"
            Resource.Error("部署失败: HTTP ${response.code()}, ${response.message()}. 响应体: $errorBody")
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

    // 简单 glob 匹配，支持 * 和精确路径匹配
    // 支持的格式：
    // - "README.md"  匹配根目录的 README.md
    // - "*.md"  匹配根目录所有 .md 文件
    // - "**\u002F*.md"  递归匹配所有 .md 文件
    // - "docs\u002F*"  匹配 docs/ 下的文件
    // - "docs\u002F**\u002F*.md"  递归匹配 docs/ 下所有 .md 文件
    private fun matchGlob(path: String, pattern: String): Boolean {
        // 将 glob 转为 regex
        val regex = buildString {
            append("^")
            var i = 0
            while (i < pattern.length) {
                when {
                    pattern.startsWith("**/", i) -> {
                        append("(.*/)?")
                        i += 3
                    }
                    pattern[i] == '*' && i + 1 < pattern.length && pattern[i + 1] == '*' -> {
                        append(".*")
                        i += 2
                    }
                    pattern[i] == '*' -> {
                        append("[^/]*")
                        i += 1
                    }
                    pattern[i] == '?' -> {
                        append("[^/]")
                        i += 1
                    }
                    pattern[i] == '.' || pattern[i] == '+' || pattern[i] == '(' || pattern[i] == ')'
                        || pattern[i] == '[' || pattern[i] == ']' || pattern[i] == '{' || pattern[i] == '}'
                        || pattern[i] == '^' || pattern[i] == '$' || pattern[i] == '|' || pattern[i] == '\\' -> {
                        append("\\").append(pattern[i])
                        i += 1
                    }
                    else -> {
                        append(pattern[i])
                        i += 1
                    }
                }
            }
            append("$")
        }
        return Regex(regex).matches(path)
    }

    // ==================== Pages Functions (标准模式) ====================

    /**
     * 从解压目录中收集 Functions 文件
     * @return Pair<Functions 文件列表, 路由配置列表> 或 null（如果没有 functions 目录）
     */
    private data class FunctionFile(
        val relativePath: String,  // 相对 functions 目录的路径，如 "hello.js", "api/time.js"
        val routePath: String,     // 路由路径，如 "/hello", "/api/time"
        val method: String,        // HTTP 方法，空字符串表示所有方法
        val content: String,       // 文件内容
        val moduleExport: String,  // 导出的函数名，如 "onRequest" 或 "onRequestGet"
        val isMiddleware: Boolean = false,  // 是否是中间件
        val mountPath: String = "/"         // 中间件的挂载路径
    )

    /**
     * 扫描 functions 目录，收集所有 Function 文件并生成路由配置
     */
    private fun collectFunctions(baseDir: File): List<FunctionFile>? {
        val functionsDir = File(baseDir, "functions")
        if (!functionsDir.exists() || !functionsDir.isDirectory) {
            return null
        }

        val functionFiles = mutableListOf<FunctionFile>()

        fun scanDirectory(dir: File, relativePrefix: String = "") {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirectory(file, "$relativePrefix${file.name}/")
                } else if (file.extension in listOf("js", "ts", "tsx", "jsx", "mjs")) {
                    val relativePath = relativePrefix + file.name
                    val content = file.readText(Charsets.UTF_8)

                    // 检测是否是中间件文件
                    val isMiddleware = file.nameWithoutExtension == "_middleware"
                    val mountPath = if (relativePrefix.isEmpty()) {
                        "/"
                    } else {
                        "/" + relativePrefix.removeSuffix("/")
                    }

                    if (isMiddleware) {
                        // 中间件：导出 onRequest，作为中间件处理
                        functionFiles.add(
                            FunctionFile(
                                relativePath = relativePath,
                                routePath = "/",
                                method = "",
                                content = content,
                                moduleExport = "onRequest",
                                isMiddleware = true,
                                mountPath = mountPath
                            )
                        )
                        Timber.d("collectFunctions: 找到中间件 $relativePath → 挂载: $mountPath")
                    } else {
                        // 普通路由：检测所有导出的处理函数
                        val routePath = convertFunctionPathToRoute(relativePath)
                        val exports = detectExportFunctions(content)
                        for (exportName in exports) {
                            val method = extractMethodFromExport(exportName)
                            functionFiles.add(
                                FunctionFile(
                                    relativePath = relativePath,
                                    routePath = routePath,
                                    method = method,
                                    content = content,
                                    moduleExport = exportName,
                                    isMiddleware = false,
                                    mountPath = mountPath
                                )
                            )
                            Timber.d("collectFunctions: 找到 Function $relativePath → 路由: $routePath, 方法: ${method.ifEmpty { "ALL" }}, 导出: $exportName")
                        }
                    }
                }
            }
        }

        scanDirectory(functionsDir)
        return functionFiles.ifEmpty { null }
    }

    /**
     * 将 Functions 文件路径转换为路由路径
     * 例如:
     *   hello.js → /hello
     *   api/time.js → /api/time
     *   api/user/[id].js → /api/user/:id
     *   index.js → /
     *   api/index.js → /api
     */
    private fun convertFunctionPathToRoute(relativePath: String): String {
        var path = relativePath
            .removeSuffix(".js")
            .removeSuffix(".ts")
            .removeSuffix(".tsx")
            .removeSuffix(".jsx")
            .removeSuffix(".mjs")

        // 处理 index 文件
        if (path.endsWith("/index")) {
            path = path.removeSuffix("/index")
        } else if (path == "index") {
            return "/"
        }

        // 处理动态路由参数: [param] → :param, [[param]] → :param*
        path = path.replace("\\[\\[([^]]+)]\\]".toRegex(), ":$1*")
        path = path.replace("\\[([^]]+)]".toRegex(), ":$1")

        return "/$path"
    }

    /**
     * 检测文件中所有导出的处理函数名
     * 返回导出函数名列表，如 ["onRequestGet", "onRequestPost", "onRequestDelete"]
     * 如果没有方法特定的导出，但有 onRequest，则返回 ["onRequest"]
     * 如果什么都没找到，默认返回 ["onRequest"]
     */
    private fun detectExportFunctions(content: String): List<String> {
        val methodSpecificExports = listOf(
            "onRequestGet", "onRequestPost", "onRequestPut", "onRequestPatch",
            "onRequestDelete", "onRequestOptions", "onRequestHead"
        )

        val found = methodSpecificExports.filter { export ->
            content.contains("export.*\\b$export\\b".toRegex()) ||
            content.contains("export.*=.*$export".toRegex())
        }

        if (found.isNotEmpty()) return found

        if (content.contains("export.*\\bonRequest\\b".toRegex()) ||
            content.contains("export.*=.*onRequest".toRegex())) {
            return listOf("onRequest")
        }

        return listOf("onRequest") // 默认
    }

    /**
     * 从导出函数名中提取 HTTP 方法
     * onRequestGet → GET, onRequestPost → POST, onRequest → "" (所有方法)
     */
    private fun extractMethodFromExport(exportName: String): String {
        return when {
            exportName == "onRequest" -> ""
            exportName.startsWith("onRequest") -> exportName.removePrefix("onRequest").uppercase()
            else -> ""
        }
    }

    /**
     * 构建 Pages Functions 的 Worker Bundle
     * 生成一个统一的入口文件，根据路由分发到对应的处理函数
     * 
     * 注意：同一个 relativePath 的多个导出只上传一次模块文件，
     * 但入口脚本中通过不同的 exportName 引用同一个模块
     */
    private fun buildFunctionsBundle(functionFiles: List<FunctionFile>): RequestBody {
        val boundary = "----CloudFlareFunctionsBundle${System.currentTimeMillis()}----"
        val mainModuleName = "_worker.js"

        // 按 relativePath 去重，建立模块映射
        val moduleMap = mutableMapOf<String, Int>()
        val uniqueModules = mutableListOf<FunctionFile>()
        for (func in functionFiles) {
            if (!moduleMap.containsKey(func.relativePath)) {
                val index = uniqueModules.size
                moduleMap[func.relativePath] = index
                uniqueModules.add(func)
            }
        }
        Timber.d("buildFunctionsBundle: moduleMap keys = ${moduleMap.keys}")

        // 生成入口 Worker 代码（路由分发器）
        val entryScript = buildFunctionsEntryScript(functionFiles, moduleMap)
        Timber.d("buildFunctionsBundle: 入口脚本大小 ${entryScript.length} 字符, 共 ${functionFiles.size} 个路由, ${uniqueModules.size} 个模块")

        val baos = ByteArrayOutputStream()

        // metadata part
        val metadataJson = """{"main_module":"$mainModuleName"}"""
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"metadata\"\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Type: application/json\r\n".toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        baos.write(metadataJson.toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))

        // 主模块 (入口分发器)
        baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Disposition: form-data; name=\"$mainModuleName\"; filename=\"$mainModuleName\"\r\n".toByteArray(Charsets.UTF_8))
        baos.write("Content-Type: application/javascript+module\r\n".toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))
        baos.write(entryScript.toByteArray(Charsets.UTF_8))
        baos.write("\r\n".toByteArray(Charsets.UTF_8))

        // 各个 Function 模块文件（去重后，import 路径已重写）
        val rewrittenModules = mutableListOf<Pair<String, String>>() // moduleName -> content
        uniqueModules.forEachIndexed { index, func ->
            val moduleName = "func_$index.js"
            // 检查是否包含相对导入
            val hasRelativeImports = Regex("""(from\s*|import\s+)["']\.\.?/""").containsMatchIn(func.content)
            if (hasRelativeImports) {
                Timber.d("buildFunctionsBundle: func_$index.js ($func.relativePath) 含相对导入, 重写前预览: ${func.content.take(200).replace("\n", "\\n")}")
            }
            val rewrittenContent = rewriteImports(func.content, func.relativePath, moduleMap)
            if (hasRelativeImports) {
                Timber.d("buildFunctionsBundle: func_$index.js ($func.relativePath) 重写后预览: ${rewrittenContent.take(200).replace("\n", "\\n")}")
            }
            rewrittenModules.add(moduleName to rewrittenContent)
        }

        // 部署前验证：扫描所有模块，确保没有未重写的相对导入
        val validateRegex = Regex("""["'](\.\.?/[^"']+)["']""")
        var unresolvedCount = 0
        rewrittenModules.forEach { (moduleName, content) ->
            val unresolved = validateRegex.findAll(content).map { it.groupValues[1] }.toList()
                .filter { !it.startsWith("./func_") }
            if (unresolved.isNotEmpty()) {
                unresolvedCount += unresolved.size
                Timber.e("buildFunctionsBundle: ⚠️ $moduleName 包含未解析的相对导入: $unresolved")
            }
        }
        if (unresolvedCount > 0) {
            Timber.e("buildFunctionsBundle: ⚠️ 共 $unresolvedCount 个未解析的导入，部署可能失败！moduleMap keys: ${moduleMap.keys}")
        }

        // 写入 bundle
        rewrittenModules.forEach { (moduleName, content) ->
            baos.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            baos.write("Content-Disposition: form-data; name=\"$moduleName\"; filename=\"$moduleName\"\r\n".toByteArray(Charsets.UTF_8))
            baos.write("Content-Type: application/javascript+module\r\n".toByteArray(Charsets.UTF_8))
            baos.write("\r\n".toByteArray(Charsets.UTF_8))
            baos.write(content.toByteArray(Charsets.UTF_8))
            baos.write("\r\n".toByteArray(Charsets.UTF_8))
        }

        // closing boundary
        baos.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val bundleBytes = baos.toByteArray()
        Timber.d("buildFunctionsBundle: bundle 总大小 ${bundleBytes.size} 字节")
        return bundleBytes.toRequestBody("multipart/form-data; boundary=$boundary".toMediaType())
    }

    /**
     * 重写模块文件中的相对 import 路径为 ./func_N.js
     *
     * 处理三种 import 形式：
     * 1. import/export ... from "./path"  — 静态导入/导出
     * 2. import "./path"                  — 副作用导入
     * 3. import("./path")                 — 动态导入
     *
     * 只重写以 . 或 .. 开头的相对路径，bare specifier（如 "react"）保持不变。
     */
    private fun rewriteImports(
        content: String,
        currentRelativePath: String,
        moduleMap: Map<String, Int>
    ): String {
        // from "..." 或 from '...'（匹配 import/export ... from "相对路径"）
        // 使用 \s* 而非 \s+ 以兼容 from"path" 无空格的情况
        val fromRegex = Regex("""(from\s*)(["'])(\.\.?/[^"']+)(["'])""")

        // import "..." 或 import '...'（副作用导入，不匹配 import xxx from）
        val sideEffectRegex = Regex("""(^|[\s;])(import\s+)(["'])(\.\.?/[^"']+)(["'])""")

        // import("...") 或 import('...')（动态导入）
        val dynamicImportRegex = Regex("""(import\s*\(\s*)(["'])(\.\.?/[^"']+)(["'])""")

        var result = content
        var rewriteCount = 0
        var failCount = 0

        // 1. 重写 from 导入
        result = fromRegex.replace(result) { match ->
            val prefix = match.groupValues[1]
            val importPath = match.groupValues[3]
            val resolved = resolveImportPath(importPath, currentRelativePath, moduleMap)
            if (resolved != null) {
                rewriteCount++
                "$prefix\"$resolved\""
            } else {
                failCount++
                Timber.w("rewriteImports: 未解析 from \"$importPath\" (in $currentRelativePath)")
                match.value
            }
        }

        // 2. 重写副作用导入
        result = sideEffectRegex.replace(result) { match ->
            val leading = match.groupValues[1]
            val importKw = match.groupValues[2]
            val importPath = match.groupValues[4]
            val resolved = resolveImportPath(importPath, currentRelativePath, moduleMap)
            if (resolved != null) {
                rewriteCount++
                "$leading$importKw\"$resolved\""
            } else {
                failCount++
                match.value
            }
        }

        // 3. 重写动态导入
        result = dynamicImportRegex.replace(result) { match ->
            val prefix = match.groupValues[1]
            val importPath = match.groupValues[3]
            val resolved = resolveImportPath(importPath, currentRelativePath, moduleMap)
            if (resolved != null) {
                rewriteCount++
                "$prefix\"$resolved\""
            } else {
                failCount++
                match.value
            }
        }

        Timber.d("rewriteImports: $currentRelativePath 重写 $rewriteCount 个导入, $failCount 个失败")

        // 4. 终极兜底：统一扫描所有剩余的相对路径导入（不以 ./func_ 开头的）
        // 覆盖 from、side-effect import、dynamic import 三种形式
        val catchAllFromRegex = Regex("""(from\s*)(["'])(\.\.?/[^"']+)(["'])""")
        val catchAllSideEffectRegex = Regex("""(^|[\s;])(import\s+)(["'])(\.\.?/[^"']+)(["'])""")
        val catchAllDynamicRegex = Regex("""(import\s*\(\s*)(["'])(\.\.?/[^"']+)(["'])""")

        fun isUnresolved(path: String): Boolean = !path.startsWith("./func_")

        fun resolveOrFallback(importPath: String): String {
            val resolved = resolveImportPath(importPath, currentRelativePath, moduleMap)
            if (resolved != null) return resolved
            val fuzzyResolved = resolveImportPathFuzzy(importPath, currentRelativePath, moduleMap)
            if (fuzzyResolved != null) {
                Timber.w("rewriteImports: 模糊匹配成功 \"$importPath\" -> \"$fuzzyResolved\" (in $currentRelativePath)")
                return fuzzyResolved
            }
            Timber.e("rewriteImports: 无法解析 \"$importPath\" (in $currentRelativePath), 替换为 func_0")
            return "./func_0.js"
        }

        // 4a. 兜底 from 导入
        val remainingFrom = catchAllFromRegex.findAll(result).map { it.groupValues[3] }.toList().filter(::isUnresolved)
        if (remainingFrom.isNotEmpty()) {
            Timber.w("rewriteImports: $currentRelativePath 进入兜底(from), 剩余: $remainingFrom")
            result = catchAllFromRegex.replace(result) { match ->
                val prefix = match.groupValues[1]
                val importPath = match.groupValues[3]
                if (!isUnresolved(importPath)) return@replace match.value
                "$prefix\"${resolveOrFallback(importPath)}\""
            }
        }

        // 4b. 兜底副作用导入
        val remainingSideEffect = catchAllSideEffectRegex.findAll(result).map { it.groupValues[4] }.toList().filter(::isUnresolved)
        if (remainingSideEffect.isNotEmpty()) {
            Timber.w("rewriteImports: $currentRelativePath 进入兜底(side-effect), 剩余: $remainingSideEffect")
            result = catchAllSideEffectRegex.replace(result) { match ->
                val leading = match.groupValues[1]
                val importKw = match.groupValues[2]
                val importPath = match.groupValues[4]
                if (!isUnresolved(importPath)) return@replace match.value
                "$leading$importKw\"${resolveOrFallback(importPath)}\""
            }
        }

        // 4c. 兜底动态导入
        val remainingDynamic = catchAllDynamicRegex.findAll(result).map { it.groupValues[3] }.toList().filter(::isUnresolved)
        if (remainingDynamic.isNotEmpty()) {
            Timber.w("rewriteImports: $currentRelativePath 进入兜底(dynamic), 剩余: $remainingDynamic")
            result = catchAllDynamicRegex.replace(result) { match ->
                val prefix = match.groupValues[1]
                val importPath = match.groupValues[3]
                if (!isUnresolved(importPath)) return@replace match.value
                "$prefix\"${resolveOrFallback(importPath)}\""
            }
        }

        // 最终检查
        val allRegexes = listOf(catchAllFromRegex, catchAllSideEffectRegex, catchAllDynamicRegex)
        val finalRemaining = allRegexes.flatMap { regex ->
            val groupIdx = if (regex == catchAllFromRegex || regex == catchAllDynamicRegex) 3 else 4
            regex.findAll(result).map { it.groupValues[groupIdx] }.toList()
        }.filter(::isUnresolved)
        if (finalRemaining.isNotEmpty()) {
            Timber.e("rewriteImports: $currentRelativePath 最终仍有未重写的导入: $finalRemaining")
        }

        return result
    }

    /**
     * 解析相对 import 路径，在 moduleMap 中查找对应模块索引。
     *
     * 尝试多种扩展名和 index 文件变体：
     * ./utils → utils, utils.js, utils.ts, utils.tsx, utils.jsx, utils/index.js, ...
     *
     * @return "./func_N.js" 或 null（未找到）
     */
    private fun resolveImportPath(
        importPath: String,
        currentRelativePath: String,
        moduleMap: Map<String, Int>
    ): String? {
        // 获取当前文件所在目录
        val currentDir = currentRelativePath.substringBeforeLast("/", "")

        // 拆分路径并解析 .. 和 .
        val dirParts = if (currentDir.isEmpty()) emptyList() else currentDir.split("/")
        val importParts = importPath.split("/").filter { it.isNotEmpty() }

        val resolved = mutableListOf<String>()
        resolved.addAll(dirParts)
        for (part in importParts) {
            when {
                part == "." -> { /* 当前目录，跳过 */ }
                part == ".." -> { if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex) }
                else -> resolved.add(part)
            }
        }
        val basePath = resolved.joinToString("/")

        Timber.d("resolveImportPath: importPath=\"$importPath\", currentRelativePath=\"$currentRelativePath\", currentDir=\"$currentDir\", basePath=\"$basePath\"")

        // 尝试各种扩展名
        val extensions = listOf("", ".js", ".ts", ".tsx", ".jsx", ".mjs", ".cjs")
        for (ext in extensions) {
            val candidate = basePath + ext
            moduleMap[candidate]?.let { return "./func_$it.js" }
        }

        // 尝试 index 文件
        val indexVariants = listOf("/index.js", "/index.ts", "/index.tsx", "/index.jsx")
        for (variant in indexVariants) {
            val candidate = basePath + variant
            moduleMap[candidate]?.let { return "./func_$it.js" }
        }

        Timber.w("rewriteImports: 无法解析 \"$importPath\" (from $currentRelativePath, basePath=$basePath)")
        Timber.w("rewriteImports: 可用模块键: ${moduleMap.keys}")
        return null
    }

    /**
     * 模糊匹配兜底：当精确解析失败时，尝试通过路径后缀匹配模块。
     *
     * 例如 basePath="utils/types" 时，会查找 moduleMap 中以 "utils/types" 结尾的键
     * （如 "some/prefix/utils/types.ts"）。
     */
    private fun resolveImportPathFuzzy(
        importPath: String,
        currentRelativePath: String,
        moduleMap: Map<String, Int>
    ): String? {
        // 先计算 basePath（与 resolveImportPath 相同的逻辑）
        val currentDir = currentRelativePath.substringBeforeLast("/", "")
        val dirParts = if (currentDir.isEmpty()) emptyList() else currentDir.split("/")
        val importParts = importPath.split("/").filter { it.isNotEmpty() }

        val resolved = mutableListOf<String>()
        resolved.addAll(dirParts)
        for (part in importParts) {
            when {
                part == "." -> { }
                part == ".." -> { if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex) }
                else -> resolved.add(part)
            }
        }
        val basePath = resolved.joinToString("/")

        // 模糊匹配：查找 moduleMap 中以 basePath 结尾的键（带各种扩展名）
        val extensions = listOf("", ".js", ".ts", ".tsx", ".jsx", ".mjs", ".cjs")
        for (ext in extensions) {
            val suffix = basePath + ext
            // 精确匹配
            moduleMap[suffix]?.let { return "./func_$it.js" }
            // 后缀匹配（basePath 是 moduleMap 键的后缀）
            for ((key, index) in moduleMap) {
                if (key == suffix || key.endsWith("/$suffix")) {
                    return "./func_$index.js"
                }
            }
        }

        // 尝试 index 文件的后缀匹配
        val indexVariants = listOf("/index.js", "/index.ts", "/index.tsx", "/index.jsx")
        for (variant in indexVariants) {
            val suffix = basePath + variant
            for ((key, index) in moduleMap) {
                if (key == suffix || key.endsWith("/$suffix")) {
                    return "./func_$index.js"
                }
            }
        }

        // 最后尝试：直接用 importPath 的最后几个部分进行匹配
        val pathEnd = importParts.filterNot { it == "." || it == ".." }.joinToString("/")
        if (pathEnd.isNotEmpty()) {
            for (ext in extensions) {
                val suffix = pathEnd + ext
                for ((key, index) in moduleMap) {
                    if (key == suffix || key.endsWith("/$suffix")) {
                        Timber.d("resolveImportPathFuzzy: 通过路径尾部 \"$pathEnd\" 匹配到 \"$key\"")
                        return "./func_$index.js"
                    }
                }
            }
        }

        return null
    }

    /**
     * 使用 Sucrase 批量转换 TypeScript/JSX 文件为纯 JavaScript。
     *
     * 对 .ts 文件启用 typescript transform（剥离类型注解）
     * 对 .tsx 文件启用 typescript + jsx transform
     * 对 .jsx 文件启用 jsx transform
     * .js 文件跳过
     *
     * 转换失败时保留原始内容并记录警告（.ts 文件可能在运行时报错，但 .js 文件不受影响）。
     */
    private suspend fun transformFunctionFiles(files: List<FunctionFile>): List<FunctionFile> {
        // 找出需要转换的唯一文件（按 relativePath 去重）
        val needsTransform = files.filter { f ->
            f.relativePath.endsWith(".ts") ||
            f.relativePath.endsWith(".tsx") ||
            f.relativePath.endsWith(".jsx")
        }.distinctBy { it.relativePath }

        if (needsTransform.isEmpty()) return files

        Timber.d("transformFunctionFiles: ${needsTransform.size} 个文件需要 Sucrase 转换")

        val inputs = needsTransform.map { f ->
            SucraseInput(
                id = f.relativePath,
                content = f.content,
                isTS = f.relativePath.endsWith(".ts") || f.relativePath.endsWith(".tsx"),
                isJSX = f.relativePath.endsWith(".tsx") || f.relativePath.endsWith(".jsx")
            )
        }

        val results = sucraseTransformer.transformBatch(inputs)
        val resultMap = results.associateBy { it.id }

        // 日志输出转换结果
        results.forEach { r ->
            if (r.success) {
                Timber.d("transformFunctionFiles: ✓ ${r.id} 转换成功 (${r.code?.length} 字符)")
            } else {
                Timber.w("transformFunctionFiles: ✗ ${r.id} 转换失败: ${r.error}")
            }
        }

        // 更新文件内容
        return files.map { file ->
            val result = resultMap[file.relativePath]
            if (result != null && result.success && result.code != null) {
                file.copy(content = result.code)
            } else if (result != null && !result.success) {
                Timber.w("Sucrase 转换失败 ${file.relativePath}: ${result.error}")
                file // 保留原始内容
            } else {
                file
            }
        }
    }

    /**
     * 构建 Functions 入口脚本（路由分发器）
     * 
     * 支持：
     * 1. 文件路由（含动态参数）
     * 2. 方法级别导出（onRequestGet/Post/etc）
     * 3. 中间件（_middleware.js，通过 context.next() 链式调用）
     * 4. 静态资源回退（未匹配路由时通过 env.ASSETS.fetch 获取静态文件）
     */
    private fun buildFunctionsEntryScript(functionFiles: List<FunctionFile>, moduleMap: Map<String, Int>): String {
        val sb = StringBuilder()

        // 分离中间件和普通路由
        val middlewares = functionFiles.filter { it.isMiddleware }.sortedBy { it.mountPath.length }
        val routes = functionFiles.filter { !it.isMiddleware }

        sb.appendLine("// ========================================")
        sb.appendLine("// Pages Functions 自动生成的入口分发器")
        sb.appendLine("// 由 upworker 生成")
        sb.appendLine("// 中间件: ${middlewares.size}, 路由: ${routes.size}")
        sb.appendLine("// ========================================")
        sb.appendLine()

        // 导入所有唯一模块（按 moduleMap 的索引）
        val maxModuleIndex = (moduleMap.values.maxOrNull() ?: -1)
        for (i in 0..maxModuleIndex) {
            sb.appendLine("import * as module_$i from './func_$i.js';")
        }
        sb.appendLine()

        // 在 Kotlin 层面预构建每个路由的正则表达式和参数名
        data class RouteInfo(
            val regexStr: String,
            val paramNames: List<String>,
            val paramFlags: List<Boolean>,  // true = catch-all (返回数组)
            val method: String,
            val moduleIndex: Int,
            val exportName: String,
            val specificity: Int  // 路由特异性分数，越高越优先
        )

        val routeInfos = routes.map { func ->
            val (regexStr, paramNames, paramFlags) = buildRouteRegex(func.routePath)
            val moduleIndex = moduleMap[func.relativePath] ?: 0
            // 特异性：静态段 > 动态段 > catch-all
            val staticSegments = func.routePath.split("/").count { it.isNotEmpty() && !it.startsWith(":") }
            val dynamicSegments = paramFlags.count { !it }
            val catchAllSegments = paramFlags.count { it }
            val specificity = staticSegments * 100 + dynamicSegments * 10 + catchAllSegments
            RouteInfo(regexStr, paramNames, paramFlags, func.method, moduleIndex, func.moduleExport, specificity)
        }.sortedByDescending { it.specificity }  // 按特异性降序排列，更具体的路由优先匹配

        // 中间件列表
        if (middlewares.isNotEmpty()) {
            sb.appendLine("// 中间件列表（按挂载路径深度排序）")
            sb.appendLine("const middlewares = [")
            middlewares.forEach { mw ->
                val moduleIndex = moduleMap[mw.relativePath] ?: 0
                sb.appendLine("  { module: module_$moduleIndex, exportName: \"${mw.moduleExport}\", mountPath: \"${mw.mountPath}\" },")
            }
            sb.appendLine("];")
            sb.appendLine()
        } else {
            sb.appendLine("const middlewares = [];")
            sb.appendLine()
        }

        // 路由表（按特异性排序，更具体的路由优先匹配）
        sb.appendLine("// 路由表（按特异性排序）")
        sb.appendLine("const routes = [")
        routeInfos.forEach { info ->
            val paramNamesJson = info.paramNames.joinToString(",", "[", "]") { "\"$it\"" }
            val paramFlagsJson = info.paramFlags.joinToString(",", "[", "]") { if (it) "true" else "false" }
            sb.appendLine("  { regex: new RegExp(\"${info.regexStr}\"), paramNames: $paramNamesJson, paramFlags: $paramFlagsJson, method: \"${info.method}\", module: module_${info.moduleIndex}, exportName: \"${info.exportName}\" },")
        }
        sb.appendLine("];")
        sb.appendLine()

        // 路由匹配函数
        sb.appendLine("// 路由匹配")
        sb.appendLine("function matchRoute(path, method) {")
        sb.appendLine("  for (const route of routes) {")
        sb.appendLine("    if (route.method && route.method !== method) continue;")
        sb.appendLine("    const m = path.match(route.regex);")
        sb.appendLine("    if (!m) continue;")
        sb.appendLine("    const params = {};")
        sb.appendLine("    for (let i = 0; i < route.paramNames.length; i++) {")
        sb.appendLine("      const name = route.paramNames[i];")
        sb.appendLine("      const isCatchAll = route.paramFlags && route.paramFlags[i];")
        sb.appendLine("      if (isCatchAll) {")
        sb.appendLine("        // catch-all 参数返回数组")
        sb.appendLine("        params[name] = m[i + 1] ? m[i + 1].split('/').filter(Boolean) : [];")
        sb.appendLine("      } else {")
        sb.appendLine("        params[name] = m[i + 1];")
        sb.appendLine("      }")
        sb.appendLine("    }")
        sb.appendLine("    return { handler: route.module[route.exportName], params: params };")
        sb.appendLine("  }")
        sb.appendLine("  return null;")
        sb.appendLine("}")
        sb.appendLine()

        // 中间件链执行函数
        sb.appendLine("// 执行中间件链")
        sb.appendLine("async function runMiddlewareChain(context, mwList, mwIndex, finalHandler) {")
        sb.appendLine("  if (mwIndex >= mwList.length) {")
        sb.appendLine("    return finalHandler(context);")
        sb.appendLine("  }")
        sb.appendLine("  const mw = mwList[mwIndex];")
        sb.appendLine("  // context.data 用于中间件间传递数据")
        sb.appendLine("  const data = context.data || {};")
        sb.appendLine("  const nextContext = {")
        sb.appendLine("    ...context,")
        sb.appendLine("    data: data,")
        sb.appendLine("    next: async () => {")
        sb.appendLine("      // 将中间件可能修改的 data 传递到下一层")
        sb.appendLine("      const nextCtx = { ...context, data: nextContext.data };")
        sb.appendLine("      return runMiddlewareChain(nextCtx, mwList, mwIndex + 1, finalHandler);")
        sb.appendLine("    }")
        sb.appendLine("  };")
        sb.appendLine("  const handler = mw.module[mw.exportName];")
        sb.appendLine("  if (typeof handler === 'function') {")
        sb.appendLine("    return handler(nextContext);")
        sb.appendLine("  }")
        sb.appendLine("  // 中间件没有处理函数，跳到下一个")
        sb.appendLine("  return runMiddlewareChain(context, mwList, mwIndex + 1, finalHandler);")
        sb.appendLine("}")
        sb.appendLine()

        // 静态资源回退函数
        sb.appendLine("// 静态资源回退")
        sb.appendLine("async function fetchStaticAsset(request, env) {")
        sb.appendLine("  if (env && env.ASSETS) {")
        sb.appendLine("    try {")
        sb.appendLine("      return await env.ASSETS.fetch(request);")
        sb.appendLine("    } catch (e) {")
        sb.appendLine("      // ASSETS.fetch 失败，返回 404")
        sb.appendLine("    }")
        sb.appendLine("  }")
        sb.appendLine("  return new Response('Not Found', { status: 404 });")
        sb.appendLine("}")
        sb.appendLine()

        // 主处理函数 - Pages Functions 风格 (onRequest)
        sb.appendLine("// Pages Functions 入口")
        sb.appendLine("export async function onRequest(context) {")
        sb.appendLine("  const { request, env } = context;")
        sb.appendLine("  const url = new URL(request.url);")
        sb.appendLine("  const path = url.pathname;")
        sb.appendLine("  const method = request.method;")
        sb.appendLine()
        sb.appendLine("  // 匹配路由")
        sb.appendLine("  const matched = matchRoute(path, method);")
        sb.appendLine()
        sb.appendLine("  // 最终处理函数：有路由匹配则调用，否则回退到静态资源")
        sb.appendLine("  const finalHandler = (ctx) => {")
        sb.appendLine("    if (matched && typeof matched.handler === 'function') {")
        sb.appendLine("      const newCtx = { ...ctx, params: matched.params };")
        sb.appendLine("      return matched.handler(newCtx);")
        sb.appendLine("    }")
        sb.appendLine("    // 回退到静态资源")
        sb.appendLine("    return fetchStaticAsset(ctx.request, ctx.env);")
        sb.appendLine("  };")
        sb.appendLine()
        sb.appendLine("  // 查找适用的中间件（挂载路径匹配当前请求路径）")
        sb.appendLine("  // 根中间件 mountPath=/ 匹配所有路径")
        sb.appendLine("  const applicableMW = middlewares.filter(mw => {")
        sb.appendLine("    if (mw.mountPath === '/') return true; // 根中间件匹配所有")
        sb.appendLine("    return path === mw.mountPath || path.startsWith(mw.mountPath + '/');")
        sb.appendLine("  });")
        sb.appendLine()
        sb.appendLine("  if (applicableMW.length > 0) {")
        sb.appendLine("    return runMiddlewareChain(context, applicableMW, 0, finalHandler);")
        sb.appendLine("  }")
        sb.appendLine()
        sb.appendLine("  return finalHandler(context);")
        sb.appendLine("}")
        sb.appendLine()

        // Worker 风格的默认导出
        sb.appendLine("// Worker 风格默认导出")
        sb.appendLine("export default {")
        sb.appendLine("  async fetch(request, env, ctx) {")
        sb.appendLine("    const context = {")
        sb.appendLine("      request,")
        sb.appendLine("      env,")
        sb.appendLine("      params: {},")
        sb.appendLine("      data: {},")
        sb.appendLine("      next: async () => fetchStaticAsset(request, env),")
        sb.appendLine("      waitUntil: ctx && typeof ctx.waitUntil === 'function' ? ctx.waitUntil.bind(ctx) : () => {},")
        sb.appendLine("      passThroughOnException: ctx && typeof ctx.passThroughOnException === 'function' ? ctx.passThroughOnException.bind(ctx) : () => {},")
        sb.appendLine("    };")
        sb.appendLine("    return onRequest(context);")
        sb.appendLine("  }")
        sb.appendLine("};")
        sb.appendLine()

        return sb.toString()
    }

    /**
     * 在 Kotlin 层面构建路由的正则表达式字符串
     * 返回 Triple<JS 正则字符串, 参数名列表, catch-all标志列表>
     */
    private fun buildRouteRegex(pattern: String): Triple<String, List<String>, List<Boolean>> {
        val paramNames = mutableListOf<String>()
        val paramFlags = mutableListOf<Boolean>()  // true = catch-all
        val regexSb = StringBuilder()
        regexSb.append("^")

        var i = 0
        while (i < pattern.length) {
            when {
                pattern[i] == ':' -> {
                    // 解析参数名
                    var j = i + 1
                    val paramNameSb = StringBuilder()
                    while (j < pattern.length && (pattern[j].isLetterOrDigit() || pattern[j] == '_')) {
                        paramNameSb.append(pattern[j])
                        j++
                    }
                    val paramName = paramNameSb.toString()
                    paramNames.add(paramName)

                    // 检查是否是 catch-all (*)
                    val isWildcard = j < pattern.length && pattern[j] == '*'
                    if (isWildcard) j++
                    paramFlags.add(isWildcard)

                    // 添加正则捕获组
                    if (isWildcard) {
                        regexSb.append("(.*)")
                    } else {
                        regexSb.append("([^/]+)")
                    }

                    i = j
                }
                pattern[i] == '/' -> {
                    regexSb.append("\\/")
                    i++
                }
                // 转义正则特殊字符
                ".*+?^\$()|[]\\".contains(pattern[i]) -> {
                    regexSb.append("\\").append(pattern[i])
                    i++
                }
                else -> {
                    regexSb.append(pattern[i])
                    i++
                }
            }
        }

        regexSb.append("\$")
        return Triple(regexSb.toString(), paramNames, paramFlags)
    }

    /**
     * 生成 functions-filepath-routing-config.json 内容
     * 中间件以 middleware 字段表示，普通路由以 module 字段表示
     */
    private fun buildFunctionsRoutingConfig(functionFiles: List<FunctionFile>): String {
        val routes = functionFiles.map { func ->
            if (func.isMiddleware) {
                """
                {
                  "mountPath": "${func.mountPath}",
                  "middleware": ["${func.relativePath.replace('\\', '/')}:${func.moduleExport}"]
                }
                """.trimIndent()
            } else {
                """
                {
                  "routePath": "${func.routePath}",
                  "mountPath": "/",
                  "method": "${func.method}",
                  "module": ["${func.relativePath.replace('\\', '/')}:${func.moduleExport}"]
                }
                """.trimIndent()
            }
        }

        return """
        {
          "baseURL": "/",
          "routes": [${routes.joinToString(",")}]
        }
        """.trimIndent()
    }

    /**
     * 生成 _routes.json 内容
     * 将所有 Function 路由和中间件挂载路径转换为 include 模式
     */
    private fun buildRoutesConfig(functionFiles: List<FunctionFile>): String {
        val includePatterns = functionFiles.map { func ->
            if (func.isMiddleware) {
                // 中间件的挂载路径
                func.mountPath
            } else {
                var pattern = func.routePath
                if (pattern.contains(":")) {
                    pattern = pattern.replace(":[a-zA-Z_][a-zA-Z0-9_]*\\*".toRegex(), "*")
                    pattern = pattern.replace(":[a-zA-Z_][a-zA-Z0-9_]*".toRegex(), "*")
                }
                pattern
            }
        }.distinct()

        return """
        {
          "version": 1,
          "description": "Generated by upworker",
          "include": [${includePatterns.joinToString(",") { "\"$it\"" }}],
          "exclude": []
        }
        """.trimIndent()
    }

    /**
     * 部署包含 Pages Functions 的项目
     * 与 zip 部署类似，但额外处理 functions 目录
     */
    private suspend fun deployWithFunctions(
        account: Account,
        projectName: String,
        baseDir: File,
        manifestMap: Map<String, String>
    ): Resource<PagesDeployment> {
        val rawFunctionFiles = collectFunctions(baseDir)
            ?: return Resource.Error("未找到 functions 目录或目录为空")

        Timber.d("deployWithFunctions: 找到 ${rawFunctionFiles.size} 个 Function 文件")

        // 0. Sucrase 转换 TypeScript/JSX → 纯 JavaScript
        val functionFiles = transformFunctionFiles(rawFunctionFiles)

        // 1. 构建 Functions bundle（内部会重写 import 路径）
        val workerBundle = buildFunctionsBundle(functionFiles)
        val workerBundlePart = MultipartBody.Part.createFormData(
            "_worker.bundle", "_worker.bundle", workerBundle
        )

        // 2. 构建 functions-filepath-routing-config.json
        val routingConfig = buildFunctionsRoutingConfig(functionFiles)
        val routingConfigBody = routingConfig.toRequestBody("application/json".toMediaType())
        val routingConfigPart = MultipartBody.Part.createFormData(
            "functions-filepath-routing-config.json",
            "functions-filepath-routing-config.json",
            routingConfigBody
        )

        // 3. 检查用户是否提供了 _routes.json，如果没有则自动生成
        val routesJsonFile = File(baseDir, "_routes.json")
        val routesJsonContent = if (routesJsonFile.exists()) {
            routesJsonFile.readText(Charsets.UTF_8)
        } else {
            buildRoutesConfig(functionFiles)
        }
        val routesJsonBody = routesJsonContent.toRequestBody("application/json".toMediaType())
        val routesJsonPart = MultipartBody.Part.createFormData(
            "_routes.json", "_routes.json", routesJsonBody
        )

        // 4. 构建 manifest（作为表单字段而非文件上传）
        val manifestJson = manifestMap.entries.joinToString(",", "{", "}") { entry ->
            "\"${entry.key}\":\"${entry.value}\""
        }
        val manifestBody = manifestJson.toRequestBody("application/json".toMediaType())
        val manifestPart = MultipartBody.Part.createFormData("manifest", null, manifestBody)

        // 5. 组装所有 multipart 部分
        val parts = mutableListOf<MultipartBody.Part>()
        parts.add(manifestPart)
        parts.add(workerBundlePart)
        parts.add(routingConfigPart)
        parts.add(routesJsonPart)

        // 6. 发送部署请求
        val response = api.createPagesDeploymentWithFunctions(
            token = AuthHelper.getBearerToken(account),
            email = AuthHelper.getEmail(account),
            apiKey = AuthHelper.getGlobalApiKey(account),
            accountId = account.accountId,
            projectName = projectName,
            parts = parts
        )

        return if (response.isSuccessful && response.body()?.success == true) {
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