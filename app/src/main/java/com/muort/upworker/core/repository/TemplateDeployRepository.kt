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
import com.muort.upworker.core.model.EnvVarUpdate
import com.muort.upworker.core.model.KvBindingUpdate
import com.muort.upworker.core.model.D1BindingUpdate
import com.muort.upworker.core.model.R2BindingUpdate
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.core.model.R2Bucket
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
import java.util.zip.ZipInputStream
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
    private val r2Repository: R2Repository,
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
     * @param overrideSourceUrl 覆盖源码地址（用于 Hybrid 模板的 Worker 端源码，默认使用 template.sourceUrl）
     * @param overrideSourceKind 覆盖源码类型（用于 Hybrid 模板的 Worker 端类型，默认使用 template.sourceKind）
     * @param overrideMainModule 覆盖主模块入口（用于 Hybrid 模板，默认使用 template.mainModule）
     */
    suspend fun deployWorkerTemplate(
        account: Account,
        template: CatalogTemplate,
        scriptName: String,
        bindings: List<DeployBindingConfig>,
        envValues: Map<String, String>,
        secretValues: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") enableObservability: Boolean = false,
        @Suppress("UNUSED_PARAMETER") enableTracing: Boolean = false,
        overrideSourceUrl: String? = null,
        overrideSourceKind: String? = null,
        overrideMainModule: String? = null
    ): Resource<DeployResultInfo> = withContext(Dispatchers.IO) {
        val rollbackSteps = mutableListOf<suspend () -> Unit>()
        val warnings = mutableListOf<String>()
        val createdResources = mutableListOf<String>()
        // 需要在部署结束后清理的临时文件/目录（无论成功失败）
        val tempFilesToClean = mutableListOf<File>()

        try {
            Timber.d("[TemplateDeploy] 开始部署模板: ${template.name} -> $scriptName")

            // Step 0: 必填项校验
            val missingRequired = bindings.filter {
                it.type == "var" && it.required
            }.filter {
                val varValue = if (it.secret) secretValues[it.name] else envValues[it.name]
                varValue.isNullOrBlank() && it.value.isNullOrBlank()
            }
            if (missingRequired.isNotEmpty()) {
                val names = missingRequired.joinToString(", ") { it.title ?: it.name }
                return@withContext Resource.Error("以下必填项不能为空: $names")
            }

            val sourceKind = overrideSourceKind ?: template.sourceKind ?: "raw"
            val sourceUrl = overrideSourceUrl ?: template.sourceUrl
                ?: return@withContext Resource.Error("模板缺少源码地址")
            val mainModule = overrideMainModule ?: template.mainModule
            // release 和 repo-archive 都是 ZIP 多文件格式，需要解压后收集模块
            val isMultiFile = sourceKind == "release" || sourceKind == "repo-archive"

            // Step 1: 下载模板源码
            val moduleFiles = if (isMultiFile) {
                // release 类型：下载 ZIP → 解压 → 收集所有模块文件
                val zipFile = downloadTemplateArchive(template, sourceUrl)
                    ?: return@withContext Resource.Error("Failed to download template archive")
                tempFilesToClean.add(zipFile)
                val tempDir = File(appContext.cacheDir, "template_${template.templateId}_unzipped_${System.currentTimeMillis()}")
                tempDir.mkdirs()
                tempFilesToClean.add(tempDir)
                unzip(zipFile, tempDir)
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
                collectModuleFiles(baseDir)
            } else {
                // raw 类型：单文件
                val scriptFile = downloadTemplateScript(template, sourceUrl)
                    ?: return@withContext Resource.Error("Failed to download template script")
                tempFilesToClean.add(scriptFile)
                mapOf(scriptFile.name to scriptFile)
            }

            // Step 2: 创建/查找绑定资源
            val workerBindings = resolveBindings(account, bindings, rollbackSteps, createdResources, warnings)

            // Step 3: 构建 WorkerMetadata 并上传
            val compatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() }

            // 智能确定 mainModule：
            // 1. 如果模板显式指定了 mainModule 且文件存在，优先使用
            // 2. 否则根据文件名自动检测（兼容模板配置错误的情况）
            val autoMainModule = if (isMultiFile) findMainModule(moduleFiles.keys) else null
            val explicitMainModule = mainModule?.takeIf { moduleFiles.containsKey(it) }
                ?: mainModule?.let { configured ->
                    // 尝试兼容：去掉 src/ 前缀后再检查
                    val withoutSrc = configured.removePrefix("src/")
                    if (moduleFiles.containsKey(withoutSrc)) {
                        Timber.w("[TemplateDeploy] 模板配置的 mainModule='$configured' 不存在，自动修正为 '$withoutSrc'")
                        warnings.add("模板入口文件路径已自动修正")
                        withoutSrc
                    } else {
                        Timber.w("[TemplateDeploy] 模板配置的 mainModule='$configured' 不存在，回退到自动检测: $autoMainModule")
                        warnings.add("模板入口配置有误，已自动检测入口文件")
                        autoMainModule
                    }
                }
                ?: autoMainModule

            val metadata = WorkerMetadata(
                mainModule = explicitMainModule,
                compatibilityDate = template.compatibilityDate,
                compatibilityFlags = compatibilityFlags,
                bindings = workerBindings
            )

            val uploadResult = if (isMultiFile && moduleFiles.size > 1) {
                // 多文件上传
                workerRepository.uploadWorkerScriptMultiFile(
                    account = account,
                    scriptName = scriptName,
                    moduleFiles = moduleFiles,
                    metadata = metadata
                )
            } else {
                // 单文件上传（raw 类型 或 release 但只有一个文件）
                val singleFile = moduleFiles.values.first()
                workerRepository.uploadWorkerScriptMultipart(
                    account = account,
                    scriptName = scriptName,
                    scriptFile = singleFile,
                    metadata = metadata
                )
            }

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
        } finally {
            // 清理所有临时文件/目录（无论成功失败）
            for (file in tempFilesToClean) {
                try {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.delete()
                    }
                    Timber.d("[TemplateDeploy] 已清理临时文件: ${file.name}")
                } catch (e: Exception) {
                    Timber.w(e, "[TemplateDeploy] 清理临时文件失败: ${file.name}")
                }
            }
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
                    "r2" -> resolveR2Binding(account, binding, rollbackSteps, createdResources, warnings)
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
            // 用户手动选择的现有数据库：跳过 initSql，避免重复执行
            Timber.d("[TemplateDeploy] 使用现有 D1 数据库，跳过 initSql: ${binding.existingId}")
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
            // 已有数据库：跳过 initSql，避免重复执行建表语句导致报错
            // initSql 仅在首次创建数据库时执行
            Timber.d("[TemplateDeploy] D1 数据库已存在，跳过 initSql: $resourceName")
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

        // 执行初始化 SQL
        if (binding.runInitSql && (binding.initSql != null || binding.initSqlUrl != null)) {
            try {
                executeD1InitSql(account, created.uuid, binding)
                Timber.d("[TemplateDeploy] D1 初始化 SQL 执行成功: $resourceName")
            } catch (e: Exception) {
                Timber.w(e, "[TemplateDeploy] D1 初始化 SQL 执行失败: $resourceName")
                throw Exception("D1 初始化失败: ${e.message}")
            }
        }

        return WorkerBinding(
            type = "d1",
            name = binding.name,
            databaseId = created.uuid
        )
    }

    /**
     * 执行 D1 初始化 SQL
     * 支持内联 SQL 和 URL 两种方式
     */
    private suspend fun executeD1InitSql(
        account: Account,
        databaseId: String,
        binding: DeployBindingConfig
    ) {
        val sqlContent = if (!binding.initSql.isNullOrBlank()) {
            binding.initSql
        } else if (!binding.initSqlUrl.isNullOrBlank()) {
            downloadInitSql(binding.initSqlUrl)
                ?: throw Exception("下载初始化 SQL 失败")
        } else {
            return  // 没有 SQL 需要执行
        }

        // 分割 SQL 语句
        // 优先检测 Drizzle 格式的 --> statement-breakpoint 分隔符，否则按分号分割
        val statements = splitSqlStatements(sqlContent)

        for (sql in statements) {
            val result = d1Repository.executeQuery(account, databaseId, sql)
            if (result !is Resource.Success) {
                throw Exception("SQL 执行失败: ${(result as? Resource.Error)?.message}")
            }
        }

        Timber.d("[TemplateDeploy] D1 初始化 SQL 完成，共 ${statements.size} 条语句")
    }

    /**
     * 智能分割 SQL 语句
     * 优先使用 Drizzle 风格的 `--> statement-breakpoint` 分隔符（更精确），
     * 否则回退到按 `;` 分割（简单模式）
     */
    private fun splitSqlStatements(sql: String): List<String> {
        val drizzleSeparator = "--> statement-breakpoint"
        return if (sql.contains(drizzleSeparator)) {
            // Drizzle 迁移格式：用 --> statement-breakpoint 分割
            sql.split(drizzleSeparator)
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } else {
            // 传统格式：按分号分割（基本处理）
            sql.split(";")
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("--") }
        }
    }

    /**
     * 下载初始化 SQL 文件
     */
    private suspend fun downloadInitSql(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载 init.sql 失败: HTTP ${response.code}")
                return null
            }
            response.body?.string()
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 下载 init.sql 异常")
            null
        }
    }

    private suspend fun resolveR2Binding(
        account: Account,
        binding: DeployBindingConfig,
        rollbackSteps: MutableList<suspend () -> Unit>,
        createdResources: MutableList<String>,
        warnings: MutableList<String>
    ): WorkerBinding {
        val resourceName = binding.resourceName

        if (binding.mode == "existing" && binding.existingId != null) {
            // 使用现有 R2 存储桶
            return WorkerBinding(
                type = "r2_bucket",
                name = binding.name,
                bucketName = binding.existingId  // existingId 存储 bucketName
            )
        }

        // 自动模式：先查找是否已有同名存储桶，没有则创建
        val buckets = r2Repository.listBuckets(account)
        val existing = (buckets as? Resource.Success)?.data?.find { it.name == resourceName }

        if (existing != null) {
            return WorkerBinding(
                type = "r2_bucket",
                name = binding.name,
                bucketName = existing.name
            )
        }

        // 创建新的 R2 存储桶
        val createResult = r2Repository.createBucket(account, resourceName)
        val created = (createResult as? Resource.Success)?.data
            ?: throw Exception("创建 R2 存储桶失败: ${(createResult as? Resource.Error)?.message}")

        createdResources.add("R2: $resourceName")
        rollbackSteps.add {
            try {
                r2Repository.deleteBucket(account, created.name)
            } catch (_: Exception) {
                // R2 存储桶可能有内容，删除可能失败，忽略即可
                warnings.add("回滚时 R2 存储桶「$resourceName」删除失败，需手动清理")
            }
        }

        return WorkerBinding(
            type = "r2_bucket",
            name = binding.name,
            bucketName = created.name
        )
    }

    // ==================== 源码下载 ====================

    /**
     * 下载模板脚本到临时文件（单文件 raw 类型）
     */
    private suspend fun downloadTemplateScript(template: CatalogTemplate, url: String): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载脚本失败: HTTP ${response.code}")
                return null
            }

            val body = response.body?.bytes() ?: return null

            val tempFile = File(appContext.cacheDir, "template_${template.templateId}_index.js")
            tempFile.writeBytes(body)

            Timber.d("[TemplateDeploy] 脚本下载成功: ${tempFile.length()} bytes")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 下载脚本异常")
            null
        }
    }

    /**
     * 下载模板 ZIP 归档到临时文件（release 类型）
     */
    private suspend fun downloadTemplateArchive(template: CatalogTemplate, url: String): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载归档失败: HTTP ${response.code}")
                return null
            }

            val body = response.body?.bytes() ?: return null

            val tempFile = File(appContext.cacheDir, "template_${template.templateId}.zip")
            tempFile.writeBytes(body)

            Timber.d("[TemplateDeploy] 归档下载成功: ${tempFile.length()} bytes")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] 下载归档异常")
            null
        }
    }

    /**
     * 解压 ZIP 文件到目标目录
     */
    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name.replace("\\", "/")
                val newFile = File(targetDir, entryName)
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
        Timber.d("[TemplateDeploy] ZIP 解压完成: ${targetDir.absolutePath}")
    }

    /**
     * 递归收集目录中的所有模块文件（JS、WASM、Python 等）
     * 返回 相对路径 → 文件 的映射
     */
    private fun collectModuleFiles(baseDir: File): Map<String, File> {
        val result = mutableMapOf<String, File>()
        val supportedExt = setOf("js", "mjs", "cjs", "wasm", "py", "json")

        fun collect(dir: File, relativePath: String) {
            dir.listFiles()?.forEach { file ->
                val newPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
                if (file.isDirectory) {
                    // 跳过隐藏目录和 node_modules
                    if (!file.name.startsWith(".") && file.name != "node_modules") {
                        collect(file, newPath)
                    }
                } else {
                    val ext = file.extension.lowercase()
                    if (ext in supportedExt) {
                        result[newPath] = file
                    }
                }
            }
        }

        collect(baseDir, "")
        Timber.d("[TemplateDeploy] 收集到 ${result.size} 个模块文件: ${result.keys.joinToString(", ")}")
        return result
    }

    /**
     * 自动查找多文件模块的入口文件
     */
    private fun findMainModule(filePaths: Set<String>): String? {
        // 优先级：index.js > main.js > 第一个找到的根目录 JS 文件
        return when {
            filePaths.contains("index.js") -> "index.js"
            filePaths.contains("main.js") -> "main.js"
            filePaths.contains("src/index.js") -> "src/index.js"
            else -> filePaths.firstOrNull { !it.contains("/") && it.endsWith(".js") }
                ?: filePaths.firstOrNull { it.endsWith(".js") }
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

    /**
     * 构建 Worker 访问 URL
     * 优先通过 API 获取真实的账户级 Workers subdomain，失败则用账户名兜底
     */
    private suspend fun buildWorkerUrl(
        account: Account,
        scriptName: String,
        subdomainEnabled: Boolean
    ): String? {
        if (!subdomainEnabled) return null

        // 优先通过 API 获取真实的账户级 subdomain
        val realSubdomain = runCatching {
            val result = workerRepository.getAccountSubdomain(account)
            if (result is Resource.Success) result.data else null
        }.getOrNull()

        val subdomain = realSubdomain ?: account.name.lowercase().replace(" ", "-")
        return "$scriptName.$subdomain.workers.dev"
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
     * 获取账户的 R2 存储桶列表（供部署对话框选择使用）
     */
    suspend fun listR2Buckets(account: Account): Resource<List<R2Bucket>> {
        return r2Repository.listBuckets(account)
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
                value = b.value,
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
     * @param bindings 绑定配置列表
     * @param envValues 普通环境变量
     * @param secretValues Secret 环境变量
     * @param branch 生产分支
     */
    suspend fun deployPagesTemplate(
        account: Account,
        template: CatalogTemplate,
        projectName: String,
        bindings: List<DeployBindingConfig> = emptyList(),
        envValues: Map<String, String> = emptyMap(),
        secretValues: Map<String, String> = emptyMap(),
        branch: String = "main"
    ): Resource<DeployResultInfo> = withContext(Dispatchers.IO) {
        val tempFilesToClean = mutableListOf<java.io.File>()
        val rollbackSteps = mutableListOf<suspend () -> Unit>()
        val createdResources = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var deploymentSucceeded = false
        try {
            Timber.d("[TemplateDeploy] 开始部署 Pages 模板: ${template.name} -> $projectName")

            // ====== Step 0: required 变量校验 ======
            val requiredMissing = bindings.filter { it.type == "var" && it.required == true }
                .filter {
                    val userValue = if (it.secret == true) secretValues[it.name] else envValues[it.name]
                    userValue.isNullOrBlank() && it.value.isNullOrBlank()
                }
            if (requiredMissing.isNotEmpty()) {
                val names = requiredMissing.joinToString("、") { it.title ?: it.name }
                return@withContext Resource.Error("以下必填变量未填写：$names")
            }

            // ====== Step 1: 下载 Pages 源码 ======
            val sourceUrl = template.pagesSourceUrl ?: template.sourceUrl
                ?: return@withContext Resource.Error("模板缺少 Pages 源码地址")
            val sourceKind = template.pagesSourceKind ?: template.sourceKind

            val sourceFile = downloadPagesArchive(template, sourceUrl, sourceKind)
                ?: return@withContext Resource.Error("下载 Pages 模板失败")
            tempFilesToClean.add(sourceFile)

            // ====== Step 2: 资源解析（KV / D1 / R2） ======
            val kvBindings = mutableMapOf<String, String>()   // bindingName -> namespaceId
            val d1Bindings = mutableMapOf<String, String>()   // bindingName -> databaseId
            val r2Bindings = mutableMapOf<String, String>()   // bindingName -> bucketName
            val plainEnvVars = mutableMapOf<String, String>()
            val secretEnvVars = mutableMapOf<String, String>()

            for (b in bindings) {
                try {
                    when (b.type) {
                        "kv" -> {
                            val wb = resolveKvBinding(account, b, rollbackSteps, createdResources)
                            kvBindings[b.name] = wb.namespaceId ?: throw Exception("KV 命名空间 ID 为空")
                        }
                        "d1" -> {
                            val wb = resolveD1Binding(account, b, rollbackSteps, createdResources)
                            d1Bindings[b.name] = wb.databaseId ?: throw Exception("D1 数据库 ID 为空")
                        }
                        "r2" -> {
                            val wb = resolveR2Binding(account, b, rollbackSteps, createdResources, warnings)
                            r2Bindings[b.name] = wb.bucketName ?: throw Exception("R2 存储桶名称为空")
                        }
                        "var" -> {
                            val userValue = if (b.secret == true) secretValues[b.name] else envValues[b.name]
                            val finalValue = userValue ?: b.value ?: ""
                            if (b.secret == true) {
                                secretEnvVars[b.name] = finalValue
                            } else {
                                plainEnvVars[b.name] = finalValue
                            }
                        }
                        "ai" -> {
                            warnings.add("AI 绑定在 Pages 模板中暂不支持：${b.name}")
                        }
                        else -> {
                            warnings.add("跳过不支持的绑定类型: ${b.type} (${b.name})")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[TemplateDeploy] 解析绑定失败: ${b.name}")
                    return@withContext Resource.Error("解析绑定 ${b.title ?: b.name} 失败: ${e.message}")
                }
            }

            // ====== Step 3: 调用 PagesRepository 部署 ======
            val deployResult = pagesRepository.createDeployment(
                account = account,
                projectName = projectName,
                branch = branch,
                file = sourceFile,
                customCompatibilityDate = template.compatibilityDate,
                customCompatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() },
                extraEnvVars = envValues.ifEmpty { null }
            )

            when (deployResult) {
                is Resource.Success -> {
                    deploymentSucceeded = true
                    val deployment = deployResult.data

                    // 获取真实的 Pages subdomain（可能与项目名不同，如被占用时会加随机后缀）
                    val actualUrl = runCatching {
                        val projectResult = pagesRepository.getProject(account, projectName)
                        if (projectResult is Resource.Success) {
                            val subdomain = projectResult.data.subdomain
                            if (!subdomain.isNullOrBlank()) {
                                "https://$subdomain"
                            } else null
                        } else null
                    }.getOrNull() ?: deployment.url?.takeIf { it.startsWith("http") }
                        ?: "https://$projectName.pages.dev"

                    Timber.d("[TemplateDeploy] Pages 部署成功: $actualUrl")

                    // ====== Step 4: 配置生产环境绑定 ======
                    if (bindings.isNotEmpty()) {
                        val bindingResult = pagesRepository.updateProductionBindings(
                            account = account,
                            projectName = projectName,
                            envVars = buildPagesEnvVars(plainEnvVars, secretEnvVars),
                            kvNamespaces = kvBindings.mapValues { KvBindingUpdate(it.value) }.ifEmpty { null },
                            d1Databases = d1Bindings.mapValues { D1BindingUpdate(it.value) }.ifEmpty { null },
                            r2Buckets = r2Bindings.mapValues { R2BindingUpdate(it.value) }.ifEmpty { null },
                            compatibilityDate = template.compatibilityDate,
                            compatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() }
                        )
                        when (bindingResult) {
                            is Resource.Success -> {
                                Timber.d("[TemplateDeploy] Pages 绑定配置成功")
                            }
                            is Resource.Error -> {
                                warnings.add("绑定配置失败: ${bindingResult.message}（项目已部署，请到控制台手动配置）")
                            }
                            is Resource.Loading -> {}
                        }
                    }

                    Resource.Success(
                        DeployResultInfo(
                            success = true,
                            url = actualUrl,
                            scriptName = projectName,
                            warnings = warnings,
                            subdomainEnabled = true
                        )
                    )
                }
                is Resource.Error -> {
                    Resource.Error("Pages 部署失败: ${deployResult.message}")
                }
                is Resource.Loading -> {
                    Resource.Error("Pages 部署状态异常")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[TemplateDeploy] Pages 部署异常")
            Resource.Error("部署失败: ${e.message}")
        } finally {
            // ====== 回滚：部署失败时执行回滚步骤 ======
            if (!deploymentSucceeded && rollbackSteps.isNotEmpty()) {
                Timber.d("[TemplateDeploy] 部署失败，开始回滚资源 (${rollbackSteps.size} 个)")
                for (step in rollbackSteps.reversed()) {
                    try {
                        step()
                    } catch (e: Exception) {
                        Timber.w(e, "[TemplateDeploy] 回滚步骤执行失败")
                    }
                }
            }
            // ====== 清理临时文件（无论成功失败） ======
            for (file in tempFilesToClean) {
                try {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                    Timber.d("[TemplateDeploy] 已清理 Pages 临时文件: ${file.name}")
                } catch (e: Exception) {
                    Timber.w(e, "[TemplateDeploy] 清理 Pages 临时文件失败: ${file.name}")
                }
            }
        }
    }

    /**
     * 构建 Pages 环境变量 map（区分 plain_text 和 secret_text）
     */
    private fun buildPagesEnvVars(
        plainVars: Map<String, String>,
        secretVars: Map<String, String>
    ): Map<String, EnvVarUpdate>? {
        if (plainVars.isEmpty() && secretVars.isEmpty()) return null
        val result = mutableMapOf<String, EnvVarUpdate>()
        plainVars.forEach { (k, v) -> result[k] = EnvVarUpdate(type = "plain_text", value = v) }
        secretVars.forEach { (k, v) -> result[k] = EnvVarUpdate(type = "secret_text", value = v) }
        return result
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
            // 部署 Worker（使用 Hybrid 模板的 Worker 端源码配置）
            if (deployWorker) {
                val workerResult = deployWorkerTemplate(
                    account = account,
                    template = template,
                    scriptName = workerName,
                    bindings = bindings,
                    envValues = envValues,
                    secretValues = secretValues,
                    enableObservability = enableObservability,
                    enableTracing = enableTracing,
                    overrideSourceUrl = template.workerSourceUrl ?: template.sourceUrl,
                    overrideSourceKind = template.workerSourceKind ?: template.sourceKind,
                    overrideMainModule = template.workerMainModule ?: template.mainModule
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
                    bindings = bindings,
                    envValues = envValues,
                    secretValues = secretValues
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
     * 下载 Pages 模板源码
     * @param template 模板
     * @param url 源码地址
     * @param sourceKind 源码类型 (raw/release/repo-archive)，决定文件扩展名
     * @return 下载后的文件。raw 类型为单 JS 文件（_worker.js 模式）；其他类型为 zip 归档
     */
    private suspend fun downloadPagesArchive(
        template: CatalogTemplate,
        url: String,
        sourceKind: String?
    ): File? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("[TemplateDeploy] 下载 Pages 模板失败: HTTP ${response.code}")
                return null
            }

            val body = response.body?.bytes() ?: return null

            // raw 类型是单 JS 文件（_worker.js 高级模式），保存为 .js 后缀
            // 以便 PagesRepository.createDeployment() 识别为单文件 Worker 模式
            // 注意：buildWorkerBundle() 内部会将内容包装为 _worker.js，磁盘文件名不影响最终部署
            val fileName = if (sourceKind == "raw") {
                "template_${template.templateId}_pages_worker.js"
            } else {
                "template_${template.templateId}_pages.zip"
            }
            val tempFile = File(appContext.cacheDir, fileName)
            tempFile.writeBytes(body)

            Timber.d("[TemplateDeploy] Pages 模板下载成功: ${tempFile.length()} bytes, kind=$sourceKind, file=$fileName")
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

