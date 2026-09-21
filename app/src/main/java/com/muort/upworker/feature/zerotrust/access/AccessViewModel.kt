package com.muort.upworker.feature.zerotrust.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
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
    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()
    
    // Error events
    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()
    
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
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_apps_load_failed, result.message))
                    Timber.e("Failed to load access apps: ${result.message}")
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
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_groups_load_failed, result.message))
                    Timber.e("Failed to load access groups: ${result.message}")
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
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_policies_load_failed, result.message))
                    Timber.e("Failed to load access policies: ${result.message}")
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
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_app_detail_load_failed, result.message))
                    Timber.e("Failed to load app detail: ${result.message}")
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
        Timber.d("Creating Access application: accountId=%s, name=%s", account.accountId, request.name)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAccessApplication(account, request)) {
                is Resource.Success -> {
                    Timber.d("Access application created: name=%s, id=%s", result.data.name, result.data.id)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_app_created, result.data.name))
                    loadApplications(account) // Reload list
                }
                is Resource.Error -> {
                    Timber.e("Failed to create Access application: name=%s, error=%s", request.name, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_app_create_failed, result.message))
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
        Timber.d("Updating Access application: accountId=%s, appId=%s", account.accountId, appId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAccessApplication(account, appId, request)) {
                is Resource.Success -> {
                    Timber.d("Access application updated: appId=%s, name=%s", appId, result.data.name)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_app_updated, result.data.name))
                    // Cloudflare API响应可能不包含所有字段(如enable_binding_cookie等)，
                    // 用请求数据填充响应中缺失的字段
                    val mergedApp = result.data.copy(
                        appLauncherVisible = result.data.appLauncherVisible ?: request.appLauncherVisible,
                        autoRedirectToIdentity = result.data.autoRedirectToIdentity ?: request.autoRedirectToIdentity,
                        enableBindingCookie = result.data.enableBindingCookie ?: request.enableBindingCookie,
                        skipInterstitial = result.data.skipInterstitial ?: request.skipInterstitial
                    )
                    _selectedApp.value = mergedApp
                    loadApplications(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to update Access application: appId=%s, error=%s", appId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_app_update_failed, result.message))
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
        Timber.d("Deleting Access application: accountId=%s, appId=%s", account.accountId, appId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAccessApplication(account, appId)) {
                is Resource.Success -> {
                    Timber.d("Access application deleted: appId=%s", appId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_app_deleted))
                    loadApplications(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to delete Access application: appId=%s, error=%s", appId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_app_delete_failed, result.message))
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
        Timber.d("Creating Access group: accountId=%s, name=%s", account.accountId, request.name)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAccessGroup(account, request)) {
                is Resource.Success -> {
                    Timber.d("Access group created: name=%s, id=%s", result.data.name, result.data.id)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_group_created, result.data.name))
                    loadGroups(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to create Access group: name=%s, error=%s", request.name, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_group_create_failed, result.message))
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
        Timber.d("Updating Access group: accountId=%s, groupId=%s", account.accountId, groupId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAccessGroup(account, groupId, request)) {
                is Resource.Success -> {
                    Timber.d("Access group updated: groupId=%s, name=%s", groupId, result.data.name)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_group_updated, result.data.name))
                    loadGroups(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to update Access group: groupId=%s, error=%s", groupId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_group_update_failed, result.message))
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
        Timber.d("Deleting Access group: accountId=%s, groupId=%s", account.accountId, groupId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAccessGroup(account, groupId)) {
                is Resource.Success -> {
                    Timber.d("Access group deleted: groupId=%s", groupId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_group_deleted))
                    loadGroups(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to delete Access group: groupId=%s, error=%s", groupId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_group_delete_failed, result.message))
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
        Timber.d("Creating Access policy: accountId=%s, appId=%s, name=%s", account.accountId, appId, request.name)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createAppPolicy(account, appId, request)) {
                is Resource.Success -> {
                    Timber.d("Access policy created: appId=%s, name=%s, id=%s", appId, result.data.name, result.data.id)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_created, result.data.name))
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    Timber.e("Failed to create Access policy: appId=%s, name=%s, error=%s", appId, request.name, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_create_failed, result.message))
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
        Timber.d("Updating Access policy: accountId=%s, appId=%s, policyId=%s", account.accountId, appId, policyId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateAppPolicy(account, appId, policyId, request)) {
                is Resource.Success -> {
                    Timber.d("Access policy updated: appId=%s, policyId=%s, name=%s", appId, policyId, result.data.name)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_updated, result.data.name))
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    Timber.e("Failed to update Access policy: appId=%s, policyId=%s, error=%s", appId, policyId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_update_failed, result.message))
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
        Timber.d("Deleting Access policy: accountId=%s, appId=%s, policyId=%s", account.accountId, appId, policyId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteAppPolicy(account, appId, policyId)) {
                is Resource.Success -> {
                    Timber.d("Access policy deleted: appId=%s, policyId=%s", appId, policyId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_deleted))
                    loadAppPolicies(account, appId)
                }
                is Resource.Error -> {
                    Timber.e("Failed to delete Access policy: appId=%s, policyId=%s, error=%s", appId, policyId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_access_policy_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
