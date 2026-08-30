package com.muort.upworker.feature.pages

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.PagesDomain
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesDeploymentLogs
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.core.model.PagesProjectDetail
import com.muort.upworker.core.model.EnvironmentConfig
import com.muort.upworker.core.model.PagesEnvSyncResult
import com.muort.upworker.core.model.Placement
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TailResult
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.PagesPollResult
import com.muort.upworker.core.repository.PagesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class CleanupResult(
    val projectName: String,
    val totalDeployments: Int,
    val deletedCount: Int,
    val success: Boolean,
    val errorMessage: String? = null
)

@HiltViewModel
class PagesViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pagesRepository: PagesRepository
) : ViewModel() {
    
    private val _projects = MutableStateFlow<List<PagesProject>>(emptyList())
    val projects: StateFlow<List<PagesProject>> = _projects.asStateFlow()
    
    private val _selectedProject = MutableStateFlow<PagesProject?>(null)
    val selectedProject: StateFlow<PagesProject?> = _selectedProject.asStateFlow()
    
    private val _projectDetail = MutableStateFlow<PagesProjectDetail?>(null)
    val projectDetail: StateFlow<PagesProjectDetail?> = _projectDetail.asStateFlow()
    
    private val _deployments = MutableStateFlow<List<PagesDeployment>>(emptyList())
    val deployments: StateFlow<List<PagesDeployment>> = _deployments.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()
    
    private val _cleanupResults = MutableStateFlow<List<CleanupResult>>(emptyList())
    val cleanupResults: StateFlow<List<CleanupResult>> = _cleanupResults.asStateFlow()
    
    fun clearCleanupResults() {
        _cleanupResults.value = emptyList()
    }
    
    fun loadProjects(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.listProjects(account)) {
                is Resource.Success -> {
                    _projects.value = result.data
                    Timber.d("Loaded ${result.data.size} projects")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_projects_load_failed, result.message))
                    Timber.e("Failed to load projects: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createProject(
        account: Account,
        name: String,
        productionBranch: String = "main",
        buildCommand: String? = null,
        destinationDir: String? = null,
        rootDir: String? = null,
        buildCaching: Boolean? = null,
        compatibilityDate: String? = null
    ) {
        if (name.isBlank()) {
            viewModelScope.launch {
                _message.emit(UiMessage.of(R.string.vm_msg_pages_project_name_required))
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.createProject(
                account = account,
                name = name,
                productionBranch = productionBranch,
                buildCommand = buildCommand,
                destinationDir = destinationDir,
                rootDir = rootDir,
                buildCaching = buildCaching,
                compatibilityDate = compatibilityDate
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_project_create_success))
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_project_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteProject(account: Account, projectName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.deleteProject(account, projectName)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_project_delete_success))
                    if (_selectedProject.value?.name == projectName) {
                        _selectedProject.value = null
                        _deployments.value = emptyList()
                    }
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_project_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun selectProject(project: PagesProject) {
        _selectedProject.value = project
    }
    
    fun loadProjectDetail(account: Account, projectName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.getProject(account, projectName)) {
                is Resource.Success -> {
                    _projectDetail.value = result.data
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_project_detail_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun loadDeployments(account: Account, projectName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.listDeployments(account, projectName)) {
                is Resource.Success -> {
                    _deployments.value = result.data
                    Timber.d("Loaded ${result.data.size} deployments")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployments_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun retryDeployment(account: Account, projectName: String, deploymentId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.retryDeployment(account, projectName, deploymentId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_retry_success))
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_retry_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteDeployment(account: Account, projectName: String, deploymentId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.deleteDeployment(account, projectName, deploymentId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_delete_success))
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun rollbackDeployment(account: Account, projectName: String, deploymentId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.rollbackDeployment(account, projectName, deploymentId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_rollback_success))
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_rollback_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    suspend fun getDeploymentListSuspend(
        account: Account,
        projectName: String
    ): Resource<List<PagesDeployment>> {
        return pagesRepository.listDeployments(account, projectName)
    }
    
    suspend fun getDeploymentLogs(
        account: Account,
        projectName: String,
        deploymentId: String
    ): Resource<PagesDeploymentLogs> {
        return pagesRepository.getDeploymentLogs(account, projectName, deploymentId)
    }

    suspend fun createDeploymentTail(
        account: Account,
        projectName: String,
        deploymentId: String
    ): Resource<TailResult> {
        return pagesRepository.createDeploymentTail(account, projectName, deploymentId)
    }

    suspend fun deleteDeploymentTail(
        account: Account,
        projectName: String,
        deploymentId: String,
        tailId: String
    ): Resource<Unit> {
        return pagesRepository.deleteDeploymentTail(account, projectName, deploymentId, tailId)
    }

    suspend fun listDomainsSuspend(
        account: Account,
        projectName: String
    ): Resource<List<PagesDomain>> {
        return pagesRepository.listDomains(account, projectName)
    }

    suspend fun deleteDomainSuspend(
        account: Account,
        projectName: String,
        domainName: String
    ): Resource<Unit> {
        return pagesRepository.deleteDomain(account, projectName, domainName)
    }
    
    fun createDeployment(
        account: Account,
        projectName: String,
        branch: String,
        file: java.io.File,
        customCompatibilityDate: String? = null,
        customCompatibilityFlags: List<String>? = null
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.createDeployment(account, projectName, branch, file, customCompatibilityDate, customCompatibilityFlags)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_create_success))
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }

            // 清理临时 zip 文件
            file.delete()

            _loadingState.value = false
        }
    }

    /**
     * 带实时日志回调的部署方法（老签名，兼容现有调用方）。
     * @param onLog 每条日志回调（主线程派发由调用方负责）
     * @param onComplete 部署完成回调，参数为成功/失败标志和错误消息
     */
    fun createDeploymentWithLogs(
        account: Account,
        projectName: String,
        branch: String,
        file: java.io.File,
        customCompatibilityDate: String? = null,
        customCompatibilityFlags: List<String>? = null,
        onLog: (String) -> Unit,
        onComplete: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            _loadingState.value = true

            val result = pagesRepository.createDeployment(
                account, projectName, branch, file,
                customCompatibilityDate, customCompatibilityFlags,
                onLog = onLog
            )

            when (result) {
                is Resource.Success -> {
                    onLog("◇ 刷新项目列表...")
                    loadProjects(account)
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_create_success))
                    onComplete(true, null)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_create_failed, result.message))
                    onComplete(false, result.message)
                }
                is Resource.Loading -> {}
            }

            // 清理临时 zip 文件
            file.delete()

            _loadingState.value = false
        }
    }

    /**
     * Task 2 签名：带部署轮询 + buildSpecialFormData logEvents 的 createDeploymentWithLogs。
     *
     *  - 内部调用 Repository.createDeployment(baseDir 映射为 file 参数)，Repository
     *    在 buildSpecialFormData 完成后会逐条把 logEvents 通过 onLog(String) 推出；
     *  - Resource.Success 后马上调用 pollDeployment 跟踪部署阶段，把
     *    pages_poll_progress_format / pages_poll_backoff_format 也推给 onLog；
     *  - 轮询结束按结果派发 UiMessage。
     */
    fun createDeploymentWithLogs(
        account: Account,
        projectName: String,
        baseDir: File,
        prodBranch: String,
        compatDate: String? = null,
        compatFlags: List<String>? = null,
        buildMode: String? = null,
        extraEnvVars: Map<String, String>? = null,
        onLog: suspend (String) -> Unit
    ) {
        viewModelScope.launch outerLaunch@{
            _loadingState.value = true

            val result = pagesRepository.createDeployment(
                account = account,
                projectName = projectName,
                branch = prodBranch,
                file = baseDir,
                customCompatibilityDate = compatDate,
                customCompatibilityFlags = compatFlags,
                buildMode = buildMode,
                extraEnvVars = extraEnvVars,
                onLog = { line ->
                    // PagesRepository.createDeployment's onLog is non-suspend.
                    // Fire via this (viewModel) scope so the suspend onLog can run.
                    this@outerLaunch.launch { onLog(line) }
                }
            )

            when (result) {
                is Resource.Success -> {
                    val deploymentId = result.data.id
                    onLog("◇ 刷新项目列表...")
                    loadProjects(account)

                    val poll = pagesRepository.pollDeployment(
                        account = account,
                        projectName = projectName,
                        deploymentId = deploymentId,
                        onProgress = { stageText, pollCount, maxPolls, backoffMs ->
                            val progressStr = appContext.getString(
                                R.string.pages_poll_progress_format, pollCount, maxPolls, stageText
                            )
                            val backoffStr = if (backoffMs != null) {
                                appContext.getString(R.string.pages_poll_backoff_format, backoffMs)
                            } else null
                            // Dispatch onLog through this scope's CoroutineContext.
                            // `onProgress` is a non-suspend lambda (pages repo uses
                            // withContext(IO) but onProgress itself is not suspend),
                            // so we must bridge via launch.
                            this@outerLaunch.launch {
                                onLog(progressStr)
                                if (backoffStr != null) onLog(backoffStr)
                            }
                            Unit
                        }
                    )

                    when (poll) {
                        is PagesPollResult.Success -> {
                            val aliasesJoined = poll.aliases.joinToString(", ")
                            _message.emit(
                                UiMessage.of(
                                    R.string.pages_poll_success_format,
                                    poll.projectName, aliasesJoined
                                )
                            )
                        }
                        is PagesPollResult.Failure -> {
                            _message.emit(
                                UiMessage.of(
                                    R.string.pages_poll_failed_format,
                                    poll.latestStageName ?: "",
                                    poll.errorMessage ?: ""
                                )
                            )
                        }
                        is PagesPollResult.Timeout -> {
                            _message.emit(
                                UiMessage.of(
                                    R.string.pages_poll_timeout_format,
                                    poll.maxPolls
                                )
                            )
                        }
                        is PagesPollResult.Aborted -> {
                            val causeMsg = poll.cause.message ?: poll.cause.toString()
                            _message.emit(
                                UiMessage.of(
                                    R.string.pages_poll_aborted_format,
                                    causeMsg
                                )
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_deployment_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }

            _loadingState.value = false
        }
    }
    
    // ==================== Configuration Management ====================
    
    /**
     * Update environment variables for a Pages project
     * @param environment "production" or "preview"
     * @param variables Map of variable name to (type, value) pairs
     */
    fun updateEnvironmentVariables(
        account: Account,
        projectName: String,
        environment: String,
        variables: Map<String, Pair<String, String>?>
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.updateEnvironmentVariables(
                account, projectName, environment, variables
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_env_vars_update_success))
                    _projectDetail.value = result.data
                    Timber.d("Environment variables updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_env_vars_update_failed, result.message))
                    Timber.e("Failed to update environment variables: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update KV namespace bindings for a Pages project
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to namespace ID
     */
    fun updateKvBindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.updateKvBindings(
                account, projectName, environment, bindings
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_kv_bindings_update_success))
                    _projectDetail.value = result.data
                    Timber.d("KV bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_kv_bindings_update_failed, result.message))
                    Timber.e("Failed to update KV bindings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update R2 bucket bindings for a Pages project
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to bucket name
     */
    fun updateR2Bindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.updateR2Bindings(
                account, projectName, environment, bindings
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_r2_bindings_update_success))
                    _projectDetail.value = result.data
                    Timber.d("R2 bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_r2_bindings_update_failed, result.message))
                    Timber.e("Failed to update R2 bindings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update D1 database bindings for a Pages project
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to database ID
     */
    fun updateD1Bindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, String?>
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.updateD1Bindings(
                account, projectName, environment, bindings
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_d1_bindings_update_success))
                    _projectDetail.value = result.data
                    Timber.d("D1 bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_d1_bindings_update_failed, result.message))
                    Timber.e("Failed to update D1 bindings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update service bindings for a Pages project
     * @param environment "production" or "preview"
     * @param bindings Map of binding name to (service name, environment) pair, null to delete
     */
    fun updateServiceBindings(
        account: Account,
        projectName: String,
        environment: String,
        bindings: Map<String, Pair<String, String>?>
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.updateServiceBindings(
                account, projectName, environment, bindings
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_service_bindings_update_success))
                    _projectDetail.value = result.data
                    Timber.d("Service bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_service_bindings_update_failed, result.message))
                    Timber.e("Failed to update service bindings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    /**
     * Get current project detail with callback for synchronous access
     */
    fun getProjectDetail(
        account: Account,
        projectName: String,
        callback: (Resource<PagesProjectDetail>) -> Unit
    ) {
        viewModelScope.launch {
            val result = pagesRepository.getProject(account, projectName)
            callback(result)
        }
    }
    
    suspend fun getProjectDetailSuspend(account: Account, projectName: String): Resource<PagesProjectDetail> {
        return pagesRepository.getProject(account, projectName)
    }
    
    /**
     * 更新项目的运行时设置（兼容日期、兼容性标志、放置）
     */
    fun updateRuntimeSettings(
        account: Account,
        projectName: String,
        compatibilityDate: String,
        compatibilityFlags: List<String>,
        placement: Placement?,
        callback: (Resource<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = pagesRepository.updateRuntimeSettings(
                account, projectName, compatibilityDate, compatibilityFlags, placement
            )
            when (result) {
                is Resource.Success -> _message.emit(UiMessage.of(R.string.vm_msg_pages_runtime_settings_updated))
                is Resource.Error -> _message.emit(UiMessage.of(R.string.vm_msg_pages_runtime_settings_update_failed, result.message))
                else -> {}
            }
            callback(result)
        }
    }

    // ==================== Pages Env Sync (Task 3) ====================

    /**
     * 同步共享 EnvironmentConfig 到 production + preview 两套 deployment_configs。
     * Repository 内部顺序执行：先 PATCH production，成功再 PATCH preview；任一步失败都立即返回
     * 对应 PagesEnvSyncResult 子类。VM 负责 loadingState 切换与 UiMessage 派发。
     */
    fun syncDualEnvConfigs(
        account: Account,
        projectName: String,
        sharedEnvConfig: EnvironmentConfig
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            _message.emit(UiMessage.of(R.string.pages_env_sync_in_progress))

            val r = pagesRepository.syncDualEnvConfigs(account, projectName, sharedEnvConfig)
            when (r) {
                is PagesEnvSyncResult.Success -> {
                    _message.emit(
                        UiMessage.of(
                            R.string.pages_env_sync_ok_format,
                            r.envVarsCount, r.kvCount, r.d1Count, r.r2Count, r.servicesCount
                        )
                    )
                }
                is PagesEnvSyncResult.ProductionFail -> {
                    _message.emit(
                        UiMessage.of(
                            R.string.pages_env_sync_production_fail_format,
                            r.errorMessage ?: ""
                        )
                    )
                }
                is PagesEnvSyncResult.PreviewFail -> {
                    _message.emit(
                        UiMessage.of(
                            R.string.pages_env_sync_preview_fail_format,
                            r.errorMessage ?: ""
                        )
                    )
                }
            }

            _loadingState.value = false
        }
    }
    
    // ==================== Pages Domains ====================
    
    /**
     * Add custom domain to Pages project
     * @param callback Callback with domain result for showing DNS configuration
     */
    fun addCustomDomain(
        account: Account,
        projectName: String,
        domainName: String,
        callback: (Resource<PagesDomain>) -> Unit
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = pagesRepository.addDomain(
                account, projectName, domainName
            )) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_custom_domain_add_success))
                    Timber.d("Domain $domainName added to $projectName")
                    callback(result)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_pages_custom_domain_add_failed, result.message))
                    Timber.e("Failed to add domain: ${result.message}")
                    callback(result)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load all custom domains from all Pages projects
     */
    fun loadAllCustomDomains(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            // 获取所有项目的自定义域
            val allDomains = mutableListOf<Pair<String, PagesDomain>>() // Pair<ProjectName, Domain>
            
            projects.value.forEach { project ->
                when (val result = pagesRepository.listDomains(account, project.name)) {
                    is Resource.Success -> {
                        result.data.forEach { domain ->
                            allDomains.add(project.name to domain)
                        }
                    }
                    is Resource.Error -> {
                        Timber.e("Failed to load domains for ${project.name}: ${result.message}")
                    }
                    is Resource.Loading -> {}
                }
            }
            
            _loadingState.value = false
        }
    }
    
    fun cleanupDeploymentsForAllProjects(account: Account, retainCount: Int) {
        viewModelScope.launch {
            _loadingState.value = true
            _cleanupResults.value = emptyList()
            
            val results = mutableListOf<CleanupResult>()
            
            projects.value.forEach { project ->
                val result = cleanupDeploymentsForProject(account, project.name, retainCount)
                results.add(result)
            }
            
            _cleanupResults.value = results.toList()
            
            val totalDeleted = results.sumOf { it.deletedCount }
            // plurals 场景用 RawString 透传；Collector 层可按需用 getQuantityString 自行格式化
            _message.emit(UiMessage.of(R.string.vm_msg_pages_cleanup_finished_total, totalDeleted))
            
            _loadingState.value = false
        }
    }
    
    fun cleanupDeploymentsForSingleProject(account: Account, projectName: String, retainCount: Int) {
        viewModelScope.launch {
            _loadingState.value = true
            _cleanupResults.value = emptyList()
            
            val result = cleanupDeploymentsForProject(account, projectName, retainCount)
            _cleanupResults.value = listOf(result)
            
            if (result.success) {
                _message.emit(UiMessage.of(R.string.vm_msg_pages_project_cleanup_finished, result.projectName, result.deletedCount))
            } else {
                _message.emit(UiMessage.of(R.string.vm_msg_pages_cleanup_failed, result.errorMessage ?: ""))
            }
            
            _loadingState.value = false
        }
    }
    
    private suspend fun cleanupDeploymentsForProject(account: Account, projectName: String, retainCount: Int): CleanupResult {
        return try {
            when (val result = pagesRepository.listDeployments(account, projectName)) {
                is Resource.Success -> {
                    val deployments = result.data
                    val totalDeployments = deployments.size
                    
                    if (totalDeployments <= retainCount) {
                        CleanupResult(projectName, totalDeployments, 0, true)
                    } else {
                        val sortedDeployments = deployments.sortedByDescending { it.createdOn }
                        val deploymentsToDelete = sortedDeployments.drop(retainCount)
                        var deletedCount = 0
                        
                        deploymentsToDelete.forEach { deployment ->
                            when (val deleteResult = pagesRepository.deleteDeployment(account, projectName, deployment.id)) {
                                is Resource.Success -> {
                                    deletedCount++
                                }
                                is Resource.Error -> {
                                    Timber.e("Failed to delete deployment ${deployment.id} in project $projectName: ${deleteResult.message}")
                                }
                                is Resource.Loading -> {}
                            }
                        }
                        
                        CleanupResult(projectName, totalDeployments, deletedCount, true)
                    }
                }
                is Resource.Error -> {
                    CleanupResult(projectName, 0, 0, false, result.message)
                }
                is Resource.Loading -> {
                    CleanupResult(projectName, 0, 0, false, "加载部署列表中")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up deployments for project $projectName")
            CleanupResult(projectName, 0, 0, false, e.message)
        }
    }
}
