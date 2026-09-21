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
        Timber.d("Revoking device: accountId=%s, deviceId=%s", account.accountId, deviceId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.revokeDevice(account, deviceId)) {
                is Resource.Success -> {
                    Timber.d("Device revoked: deviceId=%s", deviceId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_revoked))
                    loadDevices(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to revoke device: deviceId=%s, error=%s", deviceId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_revoke_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun deleteDevice(account: Account, deviceId: String) {
        Timber.d("Deleting device: accountId=%s, deviceId=%s", account.accountId, deviceId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteDevice(account, deviceId)) {
                is Resource.Success -> {
                    Timber.d("Device deleted: deviceId=%s", deviceId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_deleted))
                    loadDevices(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to delete device: deviceId=%s, error=%s", deviceId, result.message)
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
                    Timber.e("Failed to load device policies: accountId=%s, error=%s", account.accountId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policies_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun createPolicy(account: Account, request: DeviceSettingsPolicyRequest) {
        Timber.d("Creating device policy: accountId=%s, name=%s", account.accountId, request.name)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createDevicePolicy(account, request)) {
                is Resource.Success -> {
                    Timber.d("Device policy created: name=%s, id=%s", request.name, result.data.policyId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_created))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to create device policy: name=%s, error=%s", request.name, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun updatePolicy(account: Account, policyId: String, request: DeviceSettingsPolicyRequest) {
        Timber.d("Updating device policy: accountId=%s, policyId=%s", account.accountId, policyId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateDevicePolicy(account, policyId, request)) {
                is Resource.Success -> {
                    Timber.d("Device policy updated: policyId=%s", policyId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_updated))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to update device policy: policyId=%s, error=%s", policyId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun deletePolicy(account: Account, policyId: String) {
        Timber.d("Deleting device policy: accountId=%s, policyId=%s", account.accountId, policyId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteDevicePolicy(account, policyId)) {
                is Resource.Success -> {
                    Timber.d("Device policy deleted: policyId=%s", policyId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_deleted))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to delete device policy: policyId=%s, error=%s", policyId, result.message)
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_device_policy_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun updateDefaultPolicy(account: Account, update: DevicePolicyUpdate) {
        Timber.d("Updating default device policy: accountId=%s", account.accountId)
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateDefaultDevicePolicy(account, update)) {
                is Resource.Success -> {
                    Timber.d("Default device policy updated: accountId=%s", account.accountId)
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_device_default_policy_updated))
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    Timber.e("Failed to update default device policy: accountId=%s, error=%s", account.accountId, result.message)
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
        Timber.d("Setting split tunnel: accountId=%s, policyId=%s, hasExclude=%s, hasInclude=%s",
            account.accountId, policyId, excludeItems != null, includeItems != null)
        viewModelScope.launch {
            _loadingState.value = true

            if (policyId.isNullOrBlank()) {
                if (excludeItems != null) {
                    when (val result = zeroTrustRepository.setDefaultSplitTunnelExclude(account, excludeItems)) {
                        is Resource.Error -> {
                            Timber.e("Failed to set default split tunnel exclude: accountId=%s, error=%s", account.accountId, result.message)
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_exclude_update_failed, result.message))
                        }
                        else -> {
                            Timber.d("Default split tunnel exclude set: accountId=%s, count=%d", account.accountId, excludeItems.size)
                        }
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setDefaultSplitTunnelInclude(account, includeItems)) {
                        is Resource.Error -> {
                            Timber.e("Failed to set default split tunnel include: accountId=%s, error=%s", account.accountId, result.message)
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_include_update_failed, result.message))
                        }
                        else -> {
                            Timber.d("Default split tunnel include set: accountId=%s, count=%d", account.accountId, includeItems.size)
                        }
                    }
                }
            } else {
                if (excludeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelExclude(account, policyId, excludeItems)) {
                        is Resource.Error -> {
                            Timber.e("Failed to set split tunnel exclude: policyId=%s, error=%s", policyId, result.message)
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_exclude_update_failed, result.message))
                        }
                        else -> {
                            Timber.d("Split tunnel exclude set: policyId=%s, count=%d", policyId, excludeItems.size)
                        }
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelInclude(account, policyId, includeItems)) {
                        is Resource.Error -> {
                            Timber.e("Failed to set split tunnel include: policyId=%s, error=%s", policyId, result.message)
                            _error.emit(UiMessage.of(R.string.vm_msg_zt_device_split_tunnel_include_update_failed, result.message))
                        }
                        else -> {
                            Timber.d("Split tunnel include set: policyId=%s, count=%d", policyId, includeItems.size)
                        }
                    }
                }
            }

            loadPolicies(account)
            _loadingState.value = false
        }
    }
}
