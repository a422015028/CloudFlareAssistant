package com.muort.upworker.feature.zerotrust.devices

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
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()
    
    fun loadDevices(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listDevices(account)) {
                is Resource.Success -> {
                    _devices.value = result.data
                    Timber.d("Loaded ${result.data.size} devices")
                }
                is Resource.Error -> {
                    _error.emit("加载设备失败: ${result.message}")
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
                    _message.emit("设备已撤销")
                    loadDevices(account)
                }
                is Resource.Error -> {
                    _error.emit("撤销设备失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun updateDevicePolicyAssignment(account: Account, deviceId: String, policyId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateDevicePolicyAssignment(account, deviceId, policyId)) {
                is Resource.Success -> {
                    _message.emit("配置文件已更新")
                    loadDevices(account)
                }
                is Resource.Error -> {
                    _error.emit("更新配置文件失败: ${result.message}")
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
                    _error.emit("加载配置文件失败: ${result.message}")
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
                    _message.emit("配置文件已创建")
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit("创建配置文件失败: ${result.message}")
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
                    _message.emit("配置文件已更新")
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit("更新配置文件失败: ${result.message}")
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
                    _message.emit("配置文件已删除")
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit("删除配置文件失败: ${result.message}")
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
                    _message.emit("默认配置文件已更新")
                    loadPolicies(account)
                }
                is Resource.Error -> {
                    _error.emit("更新默认配置文件失败: ${result.message}")
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
                            _error.emit("更新拆分隧道排除列表失败: ${result.message}")
                        }
                        else -> {}
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setDefaultSplitTunnelInclude(account, includeItems)) {
                        is Resource.Error -> {
                            _error.emit("更新拆分隧道包含列表失败: ${result.message}")
                        }
                        else -> {}
                    }
                }
            } else {
                if (excludeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelExclude(account, policyId, excludeItems)) {
                        is Resource.Error -> {
                            _error.emit("更新拆分隧道排除列表失败: ${result.message}")
                        }
                        else -> {}
                    }
                }
                if (includeItems != null) {
                    when (val result = zeroTrustRepository.setSplitTunnelInclude(account, policyId, includeItems)) {
                        is Resource.Error -> {
                            _error.emit("更新拆分隧道包含列表失败: ${result.message}")
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
