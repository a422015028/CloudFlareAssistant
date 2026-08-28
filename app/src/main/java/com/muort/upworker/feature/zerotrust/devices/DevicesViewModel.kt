package com.muort.upworker.feature.zerotrust.devices

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
 * ViewModel for Devices and Device Policies management
 */
@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _policies = MutableStateFlow<List<DeviceSettingsPolicy>>(emptyList())
    val policies: StateFlow<List<DeviceSettingsPolicy>> = _policies.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()
    
    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()
    
    fun loadDevices(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listDevices(account)) {
                is Resource.Success -> {
                    _devices.value = result.data
                    Timber.d("Loaded ${result.data.size} devices")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_list_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun revokeDevice(account: Account, deviceId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.revokeDevice(account, deviceId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_revoked))
                    loadDevices(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_revoke_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun deleteDevice(account: Account, deviceId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteDevice(account, deviceId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_deleted))
                    loadDevices(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun loadPolicies(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listDevicePolicies(account)) {
                is Resource.Success -> {
                    _policies.value = result.data
                    Timber.d("Loaded ${result.data.size} device policies")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policies_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun createPolicy(account: Account, request: DeviceSettingsPolicyRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createDevicePolicy(account, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_created))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun updatePolicy(account: Account, policyId: String, request: DeviceSettingsPolicyRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateDevicePolicy(account, policyId, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_updated))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun deletePolicy(account: Account, policyId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteDevicePolicy(account, policyId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_deleted))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun updateDefaultPolicy(account: Account, update: DevicePolicyUpdate) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateDefaultDevicePolicy(account, update)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_default_policy_updated))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_default_policy_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun setSplitTunnel(
        account: Account,
        policyId: String?,
        excludeItems: List<SplitTunnel>?,
        includeItems: List<SplitTunnel>?
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            
            if (policyId.isNullOrBlank()) {
                if (excludeItems != null) {
                    when (val result = zeroTrustRepository.setDefaultSplitTunnelExclude(account, excludeItems)) {
                        is Resource.Error -> {
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_exclude_update_failed, result.message))
                        }
                        else -> {}
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setDefaultSplitTunnelInclude(account, includeItems)) {
                        is Resource.Error -> {
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_include_update_failed, result.message))
                        }
                        else -> {}
                    }
                }
            } else {
                if (excludeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelExclude(account, policyId, excludeItems)) {
                        is Resource.Error -> {
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_exclude_update_failed, result.message))
                        }
                        else -> {}
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelInclude(account, policyId, includeItems)) {
                        is Resource.Error -> {
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_include_update_failed, result.message))
                        }
                        else -> {}
                    }
                }
            }
            
            loadPolicies(account)
            _loadingState.value = false
        }
    }
}
