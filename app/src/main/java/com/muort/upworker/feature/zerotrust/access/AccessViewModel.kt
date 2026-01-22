package com.muort.upworker.feature.zerotrust.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.*
import com.muort.upworker.core.repository.ZeroTrustRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Access Applications and Policies management
 */
@HiltViewModel
class AccessViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {
    
    // Applications
    private val _applications = MutableStateFlow<List<AccessApplication>>(emptyList())
    val applications: StateFlow<List<AccessApplication>> = _applications.asStateFlow()
    
    // Groups
    private val _groups = MutableStateFlow<List<AccessGroup>>(emptyList())
    val groups: StateFlow<List<AccessGroup>> = _groups.asStateFlow()
    
    // Policies for selected app
    private val _policies = MutableStateFlow<List<AccessPolicy>>(emptyList())
    val policies: StateFlow<List<AccessPolicy>> = _policies.asStateFlow()
    
    // Selected application
    private val _selectedApp = MutableStateFlow<AccessApplication?>(null)
    val selectedApp: StateFlow<AccessApplication?> = _selectedApp.asStateFlow()
    
    // Loading state
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    // Message events
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    // Error events
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()
    
    /**
     * Load all Access applications
     */
    fun loadApplications(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listAccessApplications(account)) {
                is Resource.Success -> {
                    _applications.value = result.data
                    Timber.d("Loaded ${result.data.size} Access applications")
                }
                is Resource.Error -> {
                    val errorMsg = "加载应用失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load all Access groups
     */
    fun loadGroups(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listAccessGroups(account)) {
                is Resource.Success -> {
                    _groups.value = result.data
                    Timber.d("Loaded ${result.data.size} Access groups")
                }
                is Resource.Error -> {
                    val errorMsg = "加载组失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load policies for a specific application
     */
    fun loadAppPolicies(account: Account, appId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listAppPolicies(account, appId)) {
                is Resource.Success -> {
                    _policies.value = result.data
                    Timber.d("Loaded ${result.data.size} policies for app $appId")
                }
                is Resource.Error -> {
                    val errorMsg = "加载策略失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load application detail and its policies
     */
    fun loadAppDetail(account: Account, appId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            // Load application details
            when (val result = zeroTrustRepository.getAccessApplication(account, appId)) {
                is Resource.Success -> {
                    _selectedApp.value = result.data
                    Timber.d("Loaded app detail: ${result.data.name}")
                }
                is Resource.Error -> {
                    val errorMsg = "加载应用详情失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            
            // Load application policies
            loadAppPolicies(account, appId)
            
            _loadingState.value = false
        }
    }
    
    /**
     * Select an application
     */
    fun selectApplication(app: AccessApplication?) {
        _selectedApp.value = app
    }
    
    /**
     * Create a new Access application
     */
    fun createApplication(account: Account, request: AccessApplicationRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAccessApplication(account, request)) {
                is Resource.Success -> {
                    _message.emit("应用创建成功: ${result.data.name}")
                    loadApplications(account) // Reload list
                }
                is Resource.Error -> {
                    val errorMsg = "创建应用失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update an Access application
     */
    fun updateApplication(
        account: Account,
        appId: String,
        request: AccessApplicationRequest
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAccessApplication(account, appId, request)) {
                is Resource.Success -> {
                    _message.emit("应用更新成功: ${result.data.name}")
                    loadApplications(account)
                }
                is Resource.Error -> {
                    val errorMsg = "更新应用失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete an Access application
     */
    fun deleteApplication(account: Account, appId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAccessApplication(account, appId)) {
                is Resource.Success -> {
                    _message.emit("应用删除成功")
                    loadApplications(account)
                }
                is Resource.Error -> {
                    val errorMsg = "删除应用失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Create an Access group
     */
    fun createGroup(account: Account, request: AccessGroupRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAccessGroup(account, request)) {
                is Resource.Success -> {
                    _message.emit("组创建成功: ${result.data.name}")
                    loadGroups(account)
                }
                is Resource.Error -> {
                    val errorMsg = "创建组失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update an Access group
     */
    fun updateGroup(account: Account, groupId: String, request: AccessGroupRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAccessGroup(account, groupId, request)) {
                is Resource.Success -> {
                    _message.emit("组更新成功: ${result.data.name}")
                    loadGroups(account)
                }
                is Resource.Error -> {
                    val errorMsg = "更新组失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete an Access group
     */
    fun deleteGroup(account: Account, groupId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAccessGroup(account, groupId)) {
                is Resource.Success -> {
                    _message.emit("组删除成功")
                    loadGroups(account)
                }
                is Resource.Error -> {
                    val errorMsg = "删除组失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Create a policy for an application
     */
    fun createAppPolicy(account: Account, appId: String, request: AccessPolicyRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAppPolicy(account, appId, request)) {
                is Resource.Success -> {
                    _message.emit("策略创建成功: ${result.data.name}")
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    val errorMsg = "创建策略失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update an application policy
     */
    fun updateAppPolicy(
        account: Account,
        appId: String,
        policyId: String,
        request: AccessPolicyRequest
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAppPolicy(account, appId, policyId, request)) {
                is Resource.Success -> {
                    _message.emit("策略更新成功: ${result.data.name}")
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    val errorMsg = "更新策略失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete an application policy
     */
    fun deleteAppPolicy(account: Account, appId: String, policyId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAppPolicy(account, appId, policyId)) {
                is Resource.Success -> {
                    _message.emit("策略删除成功")
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    val errorMsg = "删除策略失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
