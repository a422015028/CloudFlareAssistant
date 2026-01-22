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
    
    fun loadPolicies(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listDevicePolicies(account)) {
                is Resource.Success -> {
                    _policies.value = result.data
                    Timber.d("Loaded ${result.data.size} device policies")
                }
                is Resource.Error -> {
                    _error.emit("加载策略失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
