package com.muort.upworker.core.repository

import android.content.Context
import com.google.gson.Gson
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CatalogBinding
import com.muort.upworker.core.model.CatalogTemplate
import com.muort.upworker.core.model.D1Database
import com.muort.upworker.core.model.DeployBindingConfig
import com.muort.upworker.core.model.DeployPreflightInfo
import com.muort.upworker.core.model.DeployResultInfo
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WorkerBinding
import com.muort.upworker.core.model.WorkerMetadata
import com.muort.upworker.core.model.WorkerScript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模板部署仓库
 * 负责模板的预检、资源创建、脚本上传、绑定配置等完整部署流程
 *
 * 支持的绑定类型：
 * - kv: KV 命名空间
 * - d1: D1 数据库
 * - r2: R2 存储桶
 * - var (plain_text / secret_text): 环境变量 / 密钥
 * - ai: AI 绑定
 *
 * 部署流程：
 * 1. Preflight 预检：检查 Worker 是否已存在，计算配置差异
 * 2. 创建资源：按需创建 KV/D1/R2 等绑定资源
 * 3. 上传脚本：multipart 上传脚本 + metadata + bindings
 * 4. 配置变量：设置环境变量和 Secrets
 * 5. 后处理：启用可观测性、子域名、部署
 */
@Singleton
class TemplateDeployRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val workerRepository: WorkerRepository,
    private val pagesRepository: PagesRepository,
    private val kvRepository: KvRepository,
    private val d1Repository: D1Repository,
    private val catalogRepository: CatalogRepository,
    private val gson: Gson
) {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // ==================== 预检 ====================

    /**
     * 部署预检
     * 检查 Worker 是否已存在，获取现有配置，计算差异
     */
    suspend fun preflightDeploy(
        account: Account,
        template: CatalogTemplate,
        scriptName: String
    ): Resource<DeployPreflightInfo> = withContext(Dispatchers.IO) {
        try {
            // 获取现有 Worker 设置（如果存在）
            val settingsResult = workerRepository.getWorkerSettings(account, scriptName)
            val exists = settingsResult is Resource.Success
            val existingBindings = (settingsResult as? Resource.Success)?.data?.bindings ?: emptyList()

            val templateBindings = catalogRepository.parseBindings(template.bindingsJson)
            val envVars = catalogRepository.parseEnvVars(template.envJson)

            // 计算新增的绑定
            val existingBindingNames = existingBindings.map { it.name }.toSet()
            val newBindings = templateBindings.filter { !existingBindingNames.contains(it.name) }

            // 计算会被覆盖的 Secrets
            val secretNames = templateBindings
                .filter { it.type == "var" && it.secret }
                .map { it.name }
            val existingSecretNames = existingBindings
                .filter { it.type == "secret_text" }
                .map { it.name }
            val secretsToOverride = secretNames.intersect(existingSecretNames.toSet()).toList()

            // 生成警告
            val warnings = mutableListOf<String>()
            if (exists) {
                warnings.add("Worker「$scriptName」已存在，部署将覆盖现有配置")
            }
            if (secretsToOverride.isNotEmpty()) {
                warnings.add("将覆盖 ${secretsToOverride.size} 个现有密钥：${secretsToOverride.joinToString(", ")}")
            }

            Resource.Success(
                DeployPreflightInfo(
                    exists = exists,
                    newBindings = newBindings,
                    existingBindings = existingBindings,
                    secretsToOverride = secretsToOverride,
                    warnings = warnings,
                    envVarCount = envVars.size,
                    secretCount = secretNames.size
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 预检失败")
            Resource.Error(e.message ?: "Preflight failed")
        }
    }

    // ==================== 部署 ====================

    /**
     * 执行部署
     * @param account 目标账户
     * @param template 模板
     * @param scriptName 部署后的 Worker 名称
     * @param bindings 用户选择的绑定配置（资源名 / 是否新建等）
     * @param envValues 用户填写的环境变量值（变量名 → 值）
     * @param secretValues 用户填写的 Secret 值（变量名 → 值）
     * @param enableObservability 是否启用可观测性
     * @param enableTracing 是否启用追踪
     */
    suspend fun deployWorkerTemplate(
        account: Account,
        template: CatalogTemplate,
        scriptName: String,
        bindings: List<DeployBindingConfig>,
        envValues: Map<String, String>,
        secretValues: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") enableObservability: Boolean = false,
        @Suppress("UNUSED_PARAMETER") enableTracing: Boolean = false
    ): Resource<DeployResultInfo> = withContext(Dispatchers.IO) {
        val rollbackSteps = mutableListOf<suspend () -> Unit>()
        val warnings = mutableListOf<String>()
        val createdResources = mutableListOf<String>()

        try {
            Timber.d("[TemplateDeploy] 开始部署模板: ${template.name} -> $scriptName")

            // Step 1: 下载模板源码
            val scriptFile = downloadTemplateScript(template)
                ?: return@withContext Resource.Error("Failed to download template script")

            // Step 2: 创建/查找绑定资源
            val workerBindings = resolveBindings(account, bindings, rollbackSteps, createdResources, warnings)

            // Step 3: 构建 WorkerMetadata 并上传
            val compatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() }

            val metadata = WorkerMetadata(
                compatibilityDate = template.compatibilityDate,
                compatibilityFlags = compatibilityFlags,
                bindings = workerBindings
            )

            val uploadResult = workerRepository.uploadWorkerScriptMultipart(
                account = account,
                scriptName = scriptName,
                scriptFile = scriptFile,
                metadata = metadata
            )

            if (uploadResult !is Resource.Success) {
                val errorMsg = (uploadResult as? Resource.Error)?.message ?: "Upload failed"
                rollbackResources(account, rollbackSteps, createdResources)
                return@withContext Resource.Error("上传失败: $errorMsg")
            }

            Timber.d("[TemplateDeploy] 脚本上传成功")

            // Step 4: 设置环境变量（plain_text）
            val plainVarBindings = envValues.filter { (key, _) ->
                val binding = bindings.find { it.name == key }
                binding?.type == "var" && binding.secret != true
            }.map { (name, value) -> Triple(name, value, "plain_text") }

            if (plainVarBindings.isNotEmpty()) {
                val varsResult = workerRepository.updateWorkerVariables(
                    account = account,
                    scriptName = scriptName,
                    variables = plainVarBindings
                )
                if (varsResult !is Resource.Success) {
                    warnings.add("环境变量设置可能不完整: ${(varsResult as? Resource.Error)?.message}")
                }
            }

            // Step 5: 设置 Secrets
            val secretList = secretValues.toList()
            if (secretList.isNotEmpty()) {
                val secretsResult = workerRepository.updateWorkerSecrets(
                    account = account,
                    scriptName = scriptName,
                    secrets = secretList
                )
                if (secretsResult !is Resource.Success) {
                    warnings.add("密钥设置可能不完整: ${(secretsResult as? Resource.Error)?.message}")
                }
            }

            // Step 6: 后处理（可观测性、子域名、部署）
            val afterUploadResult = workerRepository.afterUpload(
                account = account,
                uploadResult = uploadResult,
                scriptName = scriptName
            )

            // 检查子域名是否启用
            val subdomainEnabled = afterUploadResult.stages.any {
                it.kind == WorkerPostStageKind.Subdomain && it is WorkerPostActionStage.Success
            }

            // 构建访问 URL
            val url = buildWorkerUrl(account, scriptName, subdomainEnabled)

            Timber.d("[TemplateDeploy] 部署完成: $url")

            Resource.Success(
                DeployResultInfo(
                    success = true,
                    url = url,
                    scriptName = scriptName,
                    createdResources = createdResources,
                    warnings = warnings,
                    subdomainEnabled = subdomainEnabled
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 部署异常")
            rollbackResources(account, rollbackSteps, createdResources)
            Resource.Error("部署失败: ${e.message}")
        }
    }

    // ==================== 资源解析 ====================

    /**
     * 解析绑定配置，创建或查找对应的 Cloudflare 资源
     * 返回 WorkerBinding 列表用于上传 metadata
     */
    private suspend fun resolveBindings(
        account: Account,
        bindings: List<DeployBindingConfig>,
        rollbackSteps: MutableList<suspend () -> Unit>,
        createdResources: MutableList<String>,
        warnings: MutableList<String>
    ): List<WorkerBinding> {
        val result = mutableListOf<WorkerBinding>()

        for (binding in bindings) {
            try {
                val workerBinding = when (binding.type) {
                    "kv" -> resolveKvBinding(account, binding, rollbackSteps, createdResources)
                    "d1" -> resolveD1Binding(account, binding, rollbackSteps, createdResources)
                    "r2" -> resolveR2Binding(account, binding, warnings)
                    "ai" -> WorkerBinding(type = "ai", name = binding.name)
                    "var" -> {
                        // 变量在上传后单独设置（plain_text 和 secret_text）
                        // 这里先不加入 bindings 列表
                        continue
                    }
                    "service" -> {
                        // Service 绑定需要用户选择现有 Worker
                        warnings.add("Service 绑定「${binding.name}」暂不支持自动配置，请手动设置")
                        continue
                    }
                    "durable_object" -> {
                        warnings.add("Durable Object「${binding.name}」请手动配置")
                        continue
                    }
                    "queue" -> {
                        warnings.add("Queue 绑定「${binding.name}」请手动配置")
                        continue
                    }
                    else -> {
                        warnings.add("未知绑定类型: ${binding.type} (${binding.name})")
                        continue
                    }
                }
                result.add(workerBinding)
            } catch (e: Exception) {
                throw Exception("绑定「${binding.name}」配置失败: ${e.message}")
            }
        }

        return result
    }

    private suspend fun resolveKvBinding(
        account: Account,
        binding: DeployBindingConfig,
        rollbackSteps: MutableList<suspend () -> Unit>,
        createdResources: MutableList<String>
    ): WorkerBinding {
        val resourceName = binding.resourceName

        if (binding.mode == "existing" && binding.existingId != null) {
            // 使用现有 KV
            return WorkerBinding(
                type = "kv_namespace",
                name = binding.name,
                namespaceId = binding.existingId
            )
        }

        // 自动模式：先查找是否已有同名 KV，没有则创建
        val namespaces = kvRepository.listNamespaces(account)
        val existing = (namespaces as? Resource.Success)?.data?.find { it.title == resourceName }

        if (existing != null) {
            return WorkerBinding(
                type = "kv_namespace",
                name = binding.name,
                namespaceId = existing.id
            )
        }

        // 创建新的 KV 命名空间
        val createResult = kvRepository.createNamespace(account, resourceName)
        val created = (createResult as? Resource.Success)?.data
            ?: throw Exception("创建 KV 命名空间失败: ${(createResult as? Resource.Error)?.message}")

        createdResources.add("KV: $resourceName")
        rollbackSteps.add {
            try {
                kvRepository.deleteNamespace(account, created.id)
            } catch (_: Exception) {}
        }

        return WorkerBinding(
            type = "kv_namespace",
            name = binding.name,
            namespaceId = created.id
        )
    }

    private suspend fun resolveD1Binding(
        account: Account,
        binding: DeployBindingConfig,
        rollbackSteps: MutableList<suspend () -> Unit>,
        createdResources: MutableList<String>
    ): WorkerBinding {
        val resourceName = binding.resourceName

        if (binding.mode == "existing" && binding.existingId != null) {
            return WorkerBinding(
                type = "d1",
                name = binding.name,
                databaseId = binding.existingId
            )
        }

        // 自动模式：先查找，没有则创建
        val databases = d1Repository.listDatabases(account)
        val existing = (databases as? Resource.Success)?.data?.find { it.name == resourceName }

        if (existing != null) {
            return WorkerBinding(
                type = "d1",
                name = binding.name,
                databaseId = existing.uuid
            )
        }

        // 创建新的 D1 数据库
        val createResult = d1Repository.createDatabase(account, resourceName)
        val created = (createResult as? Resource.Success)?.data
            ?: throw Exception("创建 D1 数据库失败: ${(createResult as? Resource.Error)?.message}")

        createdResources.add("D1: $resourceName")
        rollbackSteps.add {
            try {
                d1Repository.deleteDatabase(account, created.uuid)
            } catch (_: Exception) {}
        }

        // TODO: 执行 initSql（如果有）
        if (binding.initSqlUrl != null || binding.initSql != null) {
            // D1 初始化 SQL 暂不在 MVP 中实现
            // 后续可通过 d1Repository.executeQuery 执行
        }

        return WorkerBinding(
            type = "d1",
            name = binding.name,
            databaseId = created.uuid
        )
    }

    private suspend fun resolveR2Binding(
        @Suppress("UNUSED_PARAMETER") account: Account,
        binding: DeployBindingConfig,
        warnings: MutableList<String>
    ): WorkerBinding {
        // R2 存储桶绑定通过 settings PATCH 设置，multipart 上传也支持
        val resourceName = binding.resourceName

        // R2 需要 S3 凭证才能创建，这里只支持选择现有存储桶
        // 自动创建需要 R2 权限和 S3 兼容 API 配置
        if (binding.mode == "existing" && binding.existingId != null) {
            return WorkerBinding(
                type = "r2_bucket",
                name = binding.name,
                bucketName = binding.existingId  // existingId 存储 bucketName
            )
        }

        // 自动模式：仅尝试查找
        warnings.add("R2 存储桶「$resourceName」请确认已存在，自动创建需要 S3 凭证")
        return WorkerBinding(
            type = "r2_bucket",
            name = binding.name,
            bucketName = resourceName
        )
    }

    // ==================== 源码下载 ====================

    /**
     * 下载模板脚本到临时文件
     * 支持 raw / release 两种源码类型
     */
    private suspend fun downloadTemplateScript(template: CatalogTemplate): File? {
        val sourceUrl = template.sourceUrl ?: return null
        val sourceKind = template.sourceKind ?: "raw"

        return try {
            val request = Request.Builder().url(sourceUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载脚本失败: HTTP ${response.code}")
                return null
            }

            val body = response.body?.bytes() ?: return null

            // 创建临时文件
            val fileName = when (sourceKind) {
                "release" -> "template_${template.templateId}.js"
                else -> "index.js"
            }
            val tempFile = File(appContext.cacheDir, "template_${template.templateId}_$fileName")
            tempFile.writeBytes(body)

            Timber.d("[TemplateDeploy] 脚本下载成功: ${tempFile.length()} bytes")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 下载脚本异常")
            null
        }
    }

    // ==================== 回滚 ====================

    /**
     * 回滚已创建的资源
     */
    private suspend fun rollbackResources(
        @Suppress("UNUSED_PARAMETER") account: Account,
        rollbackSteps: List<suspend () -> Unit>,
        createdResources: MutableList<String>
    ) {
        if (rollbackSteps.isEmpty()) return

        Timber.w("[TemplateDeploy] 开始回滚已创建的资源: ${createdResources.joinToString(", ")}")
        // 逆序回滚
        for (step in rollbackSteps.reversed()) {
            try {
                step()
            } catch (e: Exception) {
                Timber.e(e, "[TemplateDeploy] 回滚步骤失败")
            }
        }
    }

    // ==================== 辅助方法 ====================

    private fun buildWorkerUrl(
        account: Account,
        scriptName: String,
        subdomainEnabled: Boolean
    ): String? {
        // 优先使用 subdomain
        if (subdomainEnabled) {
            // subdomain 格式: {script_name}.{account_subdomain}.workers.dev
            // 这里简化处理，返回预估的 URL
            return "$scriptName.${account.name.lowercase().replace(" ", "-")}.workers.dev"
        }
        return null
    }

    /**
     * 获取账户的 KV 命名空间列表（供部署对话框选择使用）
     */
    suspend fun listKvNamespaces(account: Account): Resource<List<KvNamespace>> {
        return kvRepository.listNamespaces(account)
    }

    /**
     * 获取账户的 D1 数据库列表（供部署对话框选择使用）
     */
    suspend fun listD1Databases(account: Account): Resource<List<D1Database>> {
        return d1Repository.listDatabases(account)
    }

    /**
     * 将 CatalogBinding 列表转换为 DeployBindingConfig 列表（带默认值）
     */
    fun buildBindingConfigs(bindings: List<CatalogBinding>): List<DeployBindingConfig> {
        return bindings.map { b ->
            DeployBindingConfig(
                name = b.name,
                type = b.type,
                title = b.title,
                resourceName = b.resourceName ?: b.name,
                required = b.required,
                secret = b.secret,
                mode = "auto",
                existingId = null,
                runInitSql = true,
                initSqlUrl = b.initSqlUrl,
                initSql = b.initSql
            )
        }
    }

    // ==================== Pages 模板部署 ====================

    /**
     * 部署 Pages 模板
     * @param account 目标账户
     * @param template 模板
     * @param projectName Pages 项目名称
     * @param envValues 环境变量
     * @param branch 生产分支
     */
    suspend fun deployPagesTemplate(
        account: Account,
        template: CatalogTemplate,
        projectName: String,
        envValues: Map<String, String> = emptyMap(),
        branch: String = "main"
    ): Resource<DeployResultInfo> = withContext(Dispatchers.IO) {
        try {
            Timber.d("[TemplateDeploy] 开始部署 Pages 模板: ${template.name} -> $projectName")

            // 下载 Pages 源码（ZIP 归档）
            val sourceUrl = template.pagesSourceUrl ?: template.sourceUrl
                ?: return@withContext Resource.Error("模板缺少 Pages 源码地址")

            val zipFile = downloadPagesArchive(template, sourceUrl)
                ?: return@withContext Resource.Error("下载 Pages 模板失败")

            // 调用 PagesRepository 部署
            val result = pagesRepository.createDeployment(
                account = account,
                projectName = projectName,
                branch = branch,
                file = zipFile,
                customCompatibilityDate = template.compatibilityDate,
                customCompatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() },
                extraEnvVars = envValues.ifEmpty { null }
            )

            when (result) {
                is Resource.Success -> {
                    val deployment = result.data
                    val url = deployment.url ?: "https://$projectName.pages.dev"
                    Timber.d("[TemplateDeploy] Pages 部署成功: $url")
                    Resource.Success(
                        DeployResultInfo(
                            success = true,
                            url = url,
                            scriptName = projectName,
                            warnings = emptyList(),
                            subdomainEnabled = true
                        )
                    )
                }
                is Resource.Error -> {
                    Resource.Error("Pages 部署失败: ${result.message}")
                }
                is Resource.Loading -> {
                    Resource.Error("Pages 部署状态异常")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] Pages 部署异常")
            Resource.Error("部署失败: ${e.message}")
        }
    }

    /**
     * 部署 Hybrid 模板（同时部署 Worker 和 Pages）
     */
    suspend fun deployHybridTemplate(
        account: Account,
        template: CatalogTemplate,
        workerName: String,
        pagesName: String,
        bindings: List<DeployBindingConfig>,
        envValues: Map<String, String>,
        secretValues: Map<String, String>,
        deployWorker: Boolean = true,
        deployPages: Boolean = true,
        enableObservability: Boolean = false,
        enableTracing: Boolean = false
    ): Resource<DeployResultInfo> = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        var workerUrl: String? = null
        var pagesUrl: String? = null

        try {
            // 部署 Worker
            if (deployWorker) {
                val workerResult = deployWorkerTemplate(
                    account = account,
                    template = template,
                    scriptName = workerName,
                    bindings = bindings,
                    envValues = envValues,
                    secretValues = secretValues,
                    enableObservability = enableObservability,
                    enableTracing = enableTracing
                )
                when (workerResult) {
                    is Resource.Success -> {
                        workerUrl = workerResult.data.url
                        warnings.addAll(workerResult.data.warnings)
                    }
                    is Resource.Error -> {
                        warnings.add("Worker 部署失败: ${workerResult.message}")
                    }
                    is Resource.Loading -> {}
                }
            }

            // 部署 Pages
            if (deployPages) {
                val pagesResult = deployPagesTemplate(
                    account = account,
                    template = template,
                    projectName = pagesName,
                    envValues = envValues
                )
                when (pagesResult) {
                    is Resource.Success -> {
                        pagesUrl = pagesResult.data.url
                        warnings.addAll(pagesResult.data.warnings)
                    }
                    is Resource.Error -> {
                        warnings.add("Pages 部署失败: ${pagesResult.message}")
                    }
                    is Resource.Loading -> {}
                }
            }

            val success = (deployWorker && workerUrl != null) || (deployPages && pagesUrl != null)
            val primaryUrl = pagesUrl ?: workerUrl

            if (success) {
                Resource.Success(
                    DeployResultInfo(
                        success = true,
                        url = primaryUrl,
                        scriptName = if (deployPages) pagesName else workerName,
                        warnings = warnings,
                        subdomainEnabled = true
                    )
                )
            } else {
                Resource.Error("Hybrid 部署全部失败: ${warnings.joinToString("; ")}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] Hybrid 部署异常")
            Resource.Error("部署失败: ${e.message}")
        }
    }

    /**
     * 下载 Pages 模板 ZIP 归档
     */
    private suspend fun downloadPagesArchive(
        template: CatalogTemplate,
        url: String
    ): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载 Pages 模板失败: HTTP ${response.code}")
                return null
            }

            val body = response.body?.bytes() ?: return null

            val tempFile = File(appContext.cacheDir, "template_${template.templateId}_pages.zip")
            tempFile.writeBytes(body)

            Timber.d("[TemplateDeploy] Pages 模板下载成功: ${tempFile.length()} bytes")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 下载 Pages 模板异常")
            null
        }
    }

    // ==================== Pages 预检 ====================

    /**
     * Pages 模板预检
     */
    suspend fun preflightPagesDeploy(
        account: Account,
        template: CatalogTemplate,
        projectName: String
    ): Resource<DeployPreflightInfo> = withContext(Dispatchers.IO) {
        try {
            val projectsResult = pagesRepository.listProjects(account)
            val exists = when (projectsResult) {
                is Resource.Success -> projectsResult.data.any { it.name == projectName }
                else -> false
            }
            val envVars = catalogRepository.parseEnvVars(template.envJson)

            val warnings = mutableListOf<String>()
            if (exists) {
                warnings.add("Pages 项目「$projectName」已存在，新部署将添加为新版本")
            }

            Resource.Success(
                DeployPreflightInfo(
                    exists = exists,
                    newBindings = emptyList(),
                    existingBindings = emptyList(),
                    secretsToOverride = emptyList(),
                    warnings = warnings,
                    envVarCount = envVars.size,
                    secretCount = 0
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] Pages 预检失败")
            Resource.Error(e.message ?: "Preflight failed")
        }
    }
}

