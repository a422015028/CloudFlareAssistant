package com.muort.upworker.feature.zerotrust.gateway

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
 * ViewModel for Gateway Rules, Lists, and Locations management
 */
@HiltViewModel
class GatewayViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {
    
    // Gateway Rules
    private val _rules = MutableStateFlow<List<GatewayRule>>(emptyList())
    val rules: StateFlow<List<GatewayRule>> = _rules.asStateFlow()
    
    // Gateway Lists
    private val _lists = MutableStateFlow<List<GatewayList>>(emptyList())
    val lists: StateFlow<List<GatewayList>> = _lists.asStateFlow()
    
    // Gateway Locations
    private val _locations = MutableStateFlow<List<GatewayLocation>>(emptyList())
    val locations: StateFlow<List<GatewayLocation>> = _locations.asStateFlow()
    
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
     * Load all Gateway rules
     */
    fun loadRules(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listGatewayRules(account)) {
                is Resource.Success -> {
                    _rules.value = result.data
                    Timber.d("Loaded ${result.data.size} Gateway rules")
                }
                is Resource.Error -> {
                    val errorMsg = "加载规则失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Create a Gateway rule
     */
    fun createRule(account: Account, request: GatewayRuleRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createGatewayRule(account, request)) {
                is Resource.Success -> {
                    _message.emit("规则创建成功: ${result.data.name}")
                    loadRules(account)
                }
                is Resource.Error -> {
                    val errorMsg = "创建规则失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update a Gateway rule
     */
    fun updateRule(account: Account, ruleId: String, request: GatewayRuleRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateGatewayRule(account, ruleId, request)) {
                is Resource.Success -> {
                    _message.emit("规则更新成功: ${result.data.name}")
                    loadRules(account)
                }
                is Resource.Error -> {
                    val errorMsg = "更新规则失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete a Gateway rule
     */
    fun deleteRule(account: Account, ruleId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteGatewayRule(account, ruleId)) {
                is Resource.Success -> {
                    _message.emit("规则删除成功")
                    loadRules(account)
                }
                is Resource.Error -> {
                    val errorMsg = "删除规则失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load all Gateway lists
     */
    fun loadLists(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listGatewayLists(account)) {
                is Resource.Success -> {
                    _lists.value = result.data
                    Timber.d("Loaded ${result.data.size} Gateway lists")
                }
                is Resource.Error -> {
                    val errorMsg = "加载列表失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Create a Gateway list
     */
    fun createList(account: Account, request: GatewayListRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createGatewayList(account, request)) {
                is Resource.Success -> {
                    _message.emit("列表创建成功: ${result.data.name}")
                    loadLists(account)
                }
                is Resource.Error -> {
                    val errorMsg = "创建列表失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete a Gateway list
     */
    fun deleteList(account: Account, listId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteGatewayList(account, listId)) {
                is Resource.Success -> {
                    _message.emit("列表删除成功")
                    loadLists(account)
                }
                is Resource.Error -> {
                    val errorMsg = "删除列表失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update a Gateway list
     */
    fun updateList(account: Account, listId: String, request: GatewayListRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateGatewayList(account, listId, request)) {
                is Resource.Success -> {
                    _message.emit("列表更新成功: ${result.data.name}")
                    loadLists(account)
                }
                is Resource.Error -> {
                    val errorMsg = "更新列表失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Load all Gateway locations
     */
    fun loadLocations(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listGatewayLocations(account)) {
                is Resource.Success -> {
                    _locations.value = result.data
                    Timber.d("Loaded ${result.data.size} Gateway locations")
                }
                is Resource.Error -> {
                    val errorMsg = "加载位置失败: ${result.message}"
                    _error.emit(errorMsg)
                    Timber.e(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Create a Gateway location
     */
    fun createLocation(account: Account, request: GatewayLocationRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createGatewayLocation(account, request)) {
                is Resource.Success -> {
                    _message.emit("位置创建成功: ${result.data.name}")
                    loadLocations(account)
                }
                is Resource.Error -> {
                    val errorMsg = "创建位置失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Delete a Gateway location
     */
    fun deleteLocation(account: Account, locationId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.deleteGatewayLocation(account, locationId)) {
                is Resource.Success -> {
                    _message.emit("位置删除成功")
                    loadLocations(account)
                }
                is Resource.Error -> {
                    val errorMsg = "删除位置失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
    
    /**
     * Update a Gateway location
     */
    fun updateLocation(account: Account, locationId: String, request: GatewayLocationRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateGatewayLocation(account, locationId, request)) {
                is Resource.Success -> {
                    _message.emit("位置更新成功: ${result.data.name}")
                    loadLocations(account)
                }
                is Resource.Error -> {
                    val errorMsg = "更新位置失败: ${result.message}"
                    _error.emit(errorMsg)
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
