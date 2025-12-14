package com.muort.upworker.feature.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
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
                    _message.emit("Failed to load projects: ${result.message}")
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
                _message.emit("Please enter project name")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = pagesRepository.createProject(account, name, productionBranch)) {
                is Resource.Success -> {
                    _message.emit("Project created successfully")
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to create project: ${result.message}")
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
                    _message.emit("Project deleted successfully")
                    if (_selectedProject.value?.name == projectName) {
                        _selectedProject.value = null
                        _deployments.value = emptyList()
                    }
                    loadProjects(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete project: ${result.message}")
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
                    _message.emit("Failed to load project detail: ${result.message}")
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
                    _message.emit("Failed to load deployments: ${result.message}")
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
                    _message.emit("Deployment retry started")
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit("Failed to retry deployment: ${result.message}")
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
                    _message.emit("Deployment deleted successfully")
                    loadDeployments(account, projectName)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete deployment: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
}
