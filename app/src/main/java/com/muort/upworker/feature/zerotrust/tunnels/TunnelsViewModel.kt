package com.muort.upworker.feature.zerotrust.tunnels

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
 * ViewModel for Cloudflare Tunnels management
 */
@HiltViewModel
class TunnelsViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {
    
    private val _tunnels = MutableStateFlow<List<CloudflareTunnel>>(emptyList())
    val tunnels: StateFlow<List<CloudflareTunnel>> = _tunnels.asStateFlow()
    
    private val _selectedTunnel = MutableStateFlow<CloudflareTunnel?>(null)
    val selectedTunnel: StateFlow<CloudflareTunnel?> = _selectedTunnel.asStateFlow()
    
    private val _tunnelConnections = MutableStateFlow<List<TunnelConnection>>(emptyList())
    val tunnelConnections: StateFlow<List<TunnelConnection>> = _tunnelConnections.asStateFlow()
    
    private val _tunnelConfiguration = MutableStateFlow<TunnelConfiguration?>(null)
    val tunnelConfiguration: StateFlow<TunnelConfiguration?> = _tunnelConfiguration.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()
    
    fun loadTunnels(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listTunnels(account)) {
                is Resource.Success -> {
                    _tunnels.value = result.data
                    Timber.d("Loaded ${result.data.size} tunnels")
                }
                is Resource.Error -> {
                    _error.emit("加载隧道失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun getTunnel(account: Account, tunnelId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.getTunnel(account, tunnelId)) {
                is Resource.Success -> {
                    _selectedTunnel.value = result.data
                    Timber.d("Loaded tunnel: ${result.data.name}")
                }
                is Resource.Error -> {
                    _error.emit("获取隧道详情失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun loadTunnelConnections(account: Account, tunnelId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.listTunnelConnections(account, tunnelId)) {
                is Resource.Success -> {
                    _tunnelConnections.value = result.data
                    Timber.d("Loaded ${result.data.size} connections")
                }
                is Resource.Error -> {
                    Timber.w("Failed to load connections: ${result.message}")
                    _tunnelConnections.value = emptyList()
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun loadTunnelConfiguration(account: Account, tunnelId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.getTunnelConfiguration(account, tunnelId)) {
                is Resource.Success -> {
                    _tunnelConfiguration.value = result.data
                    Timber.d("Loaded tunnel configuration")
                }
                is Resource.Error -> {
                    Timber.w("Failed to load configuration: ${result.message}")
                    _tunnelConfiguration.value = null
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun updateTunnelConfiguration(
        account: Account,
        tunnelId: String,
        request: TunnelConfigurationRequest
    ) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateTunnelConfiguration(account, tunnelId, request)) {
                is Resource.Success -> {
                    _tunnelConfiguration.value = result.data
                    _message.emit("隧道配置已更新")
                }
                is Resource.Error -> {
                    _error.emit("更新配置失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun createTunnel(account: Account, request: TunnelCreateRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createTunnel(account, request)) {
                is Resource.Success -> {
                    _message.emit("隧道创建成功: ${result.data.name}")
                    loadTunnels(account)
                }
                is Resource.Error -> {
                    _error.emit("创建隧道失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun deleteTunnel(account: Account, tunnelId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteTunnel(account, tunnelId)) {
                is Resource.Success -> {
                    _message.emit("隧道删除成功")
                    loadTunnels(account)
                }
                is Resource.Error -> {
                    _error.emit("删除隧道失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    fun clearSelectedTunnel() {
        _selectedTunnel.value = null
        _tunnelConnections.value = emptyList()
        _tunnelConfiguration.value = null
    }
}
