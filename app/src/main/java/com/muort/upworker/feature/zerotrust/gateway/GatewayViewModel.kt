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

    /**
     * Load all Gateway rules
     */
    suspend fun loadRules(account: Account): Resource<List<GatewayRule>> {
        _loadingState.value = true
        val result = zeroTrustRepository.listGatewayRules(account)
        if (result is Resource.Success) {
            _rules.value = result.data
            Timber.d("Loaded ${result.data.size} Gateway rules")
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Create a Gateway rule
     */
    suspend fun createRule(account: Account, request: GatewayRuleRequest): Resource<GatewayRule> {
        _loadingState.value = true
        val result = zeroTrustRepository.createGatewayRule(account, request)
        if (result is Resource.Success) {
            loadRules(account)
        }
        _loadingState.value = false
        return result
    }

    /**
     * Update a Gateway rule
     */
    suspend fun updateRule(account: Account, ruleId: String, request: GatewayRuleRequest): Resource<GatewayRule> {
        _loadingState.value = true
        val result = zeroTrustRepository.updateGatewayRule(account, ruleId, request)
        if (result is Resource.Success) {
            loadRules(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Delete a Gateway rule
     */
    suspend fun deleteRule(account: Account, ruleId: String): Resource<Unit> {
        _loadingState.value = true
        val result = zeroTrustRepository.deleteGatewayRule(account, ruleId)
        if (result is Resource.Success) {
            loadRules(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Load all Gateway lists with details
     */
    suspend fun loadLists(account: Account): Resource<List<GatewayList>> {
        _loadingState.value = true
        val result = zeroTrustRepository.listGatewayLists(account)
        if (result is Resource.Success) {
            _lists.value = result.data
            Timber.d("Loaded ${result.data.size} Gateway lists")

            val listsWithDetails = result.data.map { list ->
                val detail = zeroTrustRepository.getGatewayList(account, list.id)
                val itemsResult = zeroTrustRepository.getGatewayListItems(account, list.id)
                val items = (itemsResult as? Resource.Success)?.data
                if (detail is Resource.Success) {
                    detail.data.copy(items = items ?: detail.data.items)
                } else {
                    list.copy(items = items ?: list.items)
                }
            }
            _lists.value = listsWithDetails
            Timber.d("Loaded details for ${listsWithDetails.size} lists")
        }
        _loadingState.value = false
        return result
    }

    /**
     * Load items for a single list (on-demand fetch for editing)
     */
    fun loadListItems(account: Account, listId: String, onLoaded: (List<GatewayListItem>) -> Unit) {
        viewModelScope.launch {
            val result = zeroTrustRepository.getGatewayListItems(account, listId)
            if (result is Resource.Success) {
                val updatedLists = _lists.value.map { list ->
                    if (list.id == listId) list.copy(items = result.data) else list
                }
                _lists.value = updatedLists
                onLoaded(result.data)
            } else {
                onLoaded(emptyList())
            }
        }
    }

    /**
     * Create a Gateway list
     */
    suspend fun createList(account: Account, request: GatewayListRequest): Resource<GatewayList> {
        _loadingState.value = true
        val result = zeroTrustRepository.createGatewayList(account, request)
        if (result is Resource.Success) {
            loadLists(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Delete a Gateway list
     */
    suspend fun deleteList(account: Account, listId: String): Resource<Unit> {
        _loadingState.value = true
        val result = zeroTrustRepository.deleteGatewayList(account, listId)
        if (result is Resource.Success) {
            loadLists(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Get Gateway list details
     */
    suspend fun getList(account: Account, listId: String): GatewayList? {
        return when (val result = zeroTrustRepository.getGatewayList(account, listId)) {
            is Resource.Success -> {
                result.data
            }
            else -> null
        }
    }

    /**
     * Update a Gateway list
     */
    suspend fun updateList(account: Account, listId: String, request: GatewayListRequest): Resource<GatewayList> {
        _loadingState.value = true
        val result = zeroTrustRepository.updateGatewayList(account, listId, request)
        if (result is Resource.Success) {
            loadLists(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Load all Gateway locations
     */
    suspend fun loadLocations(account: Account): Resource<List<GatewayLocation>> {
        _loadingState.value = true
        val result = zeroTrustRepository.listGatewayLocations(account)
        if (result is Resource.Success) {
            _locations.value = result.data
            Timber.d("Loaded ${result.data.size} Gateway locations")
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Create a Gateway location
     */
    suspend fun createLocation(account: Account, request: GatewayLocationRequest): Resource<GatewayLocation> {
        _loadingState.value = true
        val result = zeroTrustRepository.createGatewayLocation(account, request)
        if (result is Resource.Success) {
            loadLocations(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Delete a Gateway location
     */
    suspend fun deleteLocation(account: Account, locationId: String): Resource<Unit> {
        _loadingState.value = true
        val result = zeroTrustRepository.deleteGatewayLocation(account, locationId)
        if (result is Resource.Success) {
            loadLocations(account)
        }
        _loadingState.value = false
        return result
    }
    
    /**
     * Update a Gateway location
     */
    suspend fun updateLocation(account: Account, locationId: String, request: GatewayLocationRequest): Resource<GatewayLocation> {
        _loadingState.value = true
        val result = zeroTrustRepository.updateGatewayLocation(account, locationId, request)
        if (result is Resource.Success) {
            loadLocations(account)
        }
        _loadingState.value = false
        return result
    }
}
