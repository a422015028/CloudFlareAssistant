package com.muort.upworker.feature.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.PagesDomain
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.core.model.PagesProjectDetail
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.PagesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PagesViewModel @Inject constructor(
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
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun loadProjects(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.listProjects(account)) {
                is Resource.Success -> {
                    _projects.value = result.data
                    Timber.d("Loaded ${result.data.size} projects")
                }
                is Resource.Error -> {
                    _message.emit("加载项目失败: ${result.message}")
                    Timber.e("Failed to load projects: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createProject(account: Account, name: String, productionBranch: String = "main") {
        if (name.isBlank()) {
            viewModelScope.launch {
                _message.emit("请输入项目名称")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.createProject(account, name, productionBranch)) {
                is Resource.Success -> {
                    _message.emit("项目创建成功")
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit("创建项目失败: ${result.message}")
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
                    _message.emit("项目删除成功")
                    if (_selectedProject.value?.name == projectName) {
                        _selectedProject.value = null
                        _deployments.value = emptyList()
                    }
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit("删除项目失败: ${result.message}")
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
                    _message.emit("加载项目详情失败: ${result.message}")
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
                    _message.emit("加载部署列表失败: ${result.message}")
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
                    _message.emit("已重新发起部署")
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit("重新部署失败: ${result.message}")
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
                    _message.emit("部署删除成功")
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit("删除部署失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createDeployment(
        account: Account,
        projectName: String,
        branch: String,
        file: java.io.File
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.createDeployment(account, projectName, branch, file)) {
                is Resource.Success -> {
                    _message.emit("部署创建成功")
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit("部署失败: ${result.message}")
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
                    _message.emit("环境变量更新成功")
                    _projectDetail.value = result.data
                    Timber.d("Environment variables updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit("环境变量更新失败: ${result.message}")
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
                    _message.emit("KV 绑定更新成功")
                    _projectDetail.value = result.data
                    Timber.d("KV bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit("KV 绑定更新失败: ${result.message}")
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
                    _message.emit("R2 绑定更新成功")
                    _projectDetail.value = result.data
                    Timber.d("R2 bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit("R2 绑定更新失败: ${result.message}")
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
                    _message.emit("D1 绑定更新成功")
                    _projectDetail.value = result.data
                    Timber.d("D1 bindings updated for $projectName")
                }
                is Resource.Error -> {
                    _message.emit("D1 绑定更新失败: ${result.message}")
                    Timber.e("Failed to update D1 bindings: ${result.message}")
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
                    _message.emit("自定义域添加成功")
                    Timber.d("Domain $domainName added to $projectName")
                    callback(result)
                }
                is Resource.Error -> {
                    _message.emit("自定义域添加失败: ${result.message}")
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
}
