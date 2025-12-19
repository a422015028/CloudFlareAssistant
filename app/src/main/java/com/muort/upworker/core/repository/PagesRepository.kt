package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
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
                    token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
    
    suspend fun createDeployment(
        account: Account,
        projectName: String,
        branch: String,
        file: java.io.File
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {
        safeApiCall {
            Timber.d("Creating deployment for project: $projectName, branch: $branch, file: ${file.name}")
            
            // CloudFlare Pages 只支持 .zip 文件部署
            if (!file.name.endsWith(".zip", ignoreCase = true)) {
                return@safeApiCall Resource.Error("仅支持 .zip 文件部署。请上传包含构建输出的 .zip 文件。")
            }
            
            if (!file.exists()) {
                return@safeApiCall Resource.Error("文件不存在: ${file.absolutePath}")
            }
            
            Timber.d("File exists: ${file.exists()}, size: ${file.length()} bytes")
            
            // 先检查项目是否存在，如果不存在则创建
            val projectExists = checkProjectExists(account, projectName)
            if (!projectExists) {
                Timber.d("Project $projectName does not exist, creating it first...")
                when (val createResult = createProject(account, projectName, branch)) {
                    is Resource.Success -> {
                        Timber.d("Project created successfully: ${createResult.data.name}")
                    }
                    is Resource.Error -> {
                        return@safeApiCall Resource.Error("无法创建项目：${createResult.message}")
                    }
                    is Resource.Loading -> {}
                }
            }
            
            // 创建 manifest - CloudFlare Pages 需要这个字段来描述上传的文件
            // manifest 是一个 JSON 字符串，描述文件路径映射
            val manifestJson = """
                {
                  "/": "${file.name}"
                }
            """.trimIndent()
            
            val manifestBody = manifestJson.toRequestBody("application/json".toMediaType())
            
            val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
            
            // 文件 part 名称必须与 manifest 中的路径对应
            val filePart = okhttp3.MultipartBody.Part.createFormData(
                file.name, // 使用文件名作为 part name
                file.name,
                requestBody
            )
            
            Timber.d("Sending deployment request to CloudFlare API (Direct Upload)...")
            Timber.d("File: ${file.name}, Size: ${file.length()} bytes")
            Timber.d("Manifest: $manifestJson")
            Timber.d("Project: $projectName, Account: ${account.accountId}")
            
            val response = api.createPagesDeployment(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName,
                manifest = manifestBody,
                file = filePart
            )
            
            Timber.d("API Response - Code: ${response.code()}, Success: ${response.isSuccessful}")
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Timber.d("Deployment created successfully: ${it.id}")
                    Resource.Success(it)
                } ?: Resource.Error("部署已创建但未返回结果")
            } else {
                val body = response.body()
                val errors = body?.errors
                val errorMsg = errors?.firstOrNull()?.message ?: response.message()
                
                Timber.e("Deployment failed - Code: ${response.code()}")
                Timber.e("Response body: ${response.errorBody()?.string()}")
                errors?.forEach { error ->
                    Timber.e("Error: code=${error.code}, message=${error.message}")
                }
                
                // 详细的错误信息用于调试
                val detailedError = buildString {
                    append("HTTP ${response.code()}: $errorMsg")
                    errors?.forEach { error ->
                        append("\n- ${error.code}: ${error.message}")
                    }
                }
                Timber.e("Complete error details: $detailedError")
                
                val friendlyMsg = when (response.code()) {
                    400 -> "请求参数错误：$errorMsg\n\n详细信息：\n$detailedError"
                    401 -> "认证失败：API Token 无效或已过期"
                    403 -> "权限不足：API Token 没有部署 Pages 项目的权限"
                    404 -> "项目不存在（这不应该发生，因为已自动创建）"
                    413 -> "文件太大：请确保 .zip 文件小于 25MB"
                    else -> "部署失败：$errorMsg"
                }
                
                Resource.Error(friendlyMsg)
            }
        }
    }
    
    private suspend fun checkProjectExists(account: Account, projectName: String): Boolean {
        return try {
            val response = api.getPagesProject(
                token = "Bearer ${account.token}",
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
    
    /**
     * Update Pages project environment variables
     * @param environment "production" or "preview"
     * @param variables Map of variable name to (type, value) pairs
     *                 To delete a variable, set its value to null
     */
    suspend fun updateEnvironmentVariables(
        account: Account,
        projectName: String,
        environment: String,  // "production" or "preview"
        variables: Map<String, Pair<String, String>?>  // Map<name, (type, value)?>
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {
        safeApiCall {
            // Convert variables to EnvVarUpdate map
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
                token = "Bearer ${account.token}",
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
    
    /**
     * Update Pages project KV namespace bindings
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to namespace ID
     *                To delete a binding, set its value to null
     */
    suspend fun updateKvBindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>  // Map<binding_name, namespace_id?>
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
                token = "Bearer ${account.token}",
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
    
    /**
     * Update Pages project R2 bucket bindings
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to bucket name
     *                To delete a binding, set its value to null
     */
    suspend fun updateR2Bindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>  // Map<binding_name, bucket_name?>
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
                token = "Bearer ${account.token}",
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
    
    /**
     * Update Pages project D1 database bindings
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to database ID
     *                To delete a binding, set its value to null
     */
    suspend fun updateD1Bindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>  // Map<binding_name, database_id?>
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
                token = "Bearer ${account.token}",
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
}
