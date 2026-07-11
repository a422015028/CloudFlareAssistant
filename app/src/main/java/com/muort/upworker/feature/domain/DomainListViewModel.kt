package com.muort.upworker.feature.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DomainListUiState(
    val zones: List<Zone> = emptyList(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
)

/** 添加域名状态：createdZone 非空时表单切到「名称服务器」结果页。 */
data class AddDomainUiState(
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdZone: Zone? = null,
)

@HiltViewModel
class DomainListViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
) : ViewModel() {

    private var currentAccountId: Long? = null

    private val _uiState = MutableStateFlow(DomainListUiState(isLoading = true))
    val uiState: StateFlow<DomainListUiState> = _uiState.asStateFlow()

    private val _addState = MutableStateFlow(AddDomainUiState())
    val addState: StateFlow<AddDomainUiState> = _addState.asStateFlow()

    fun bind(account: Account) {
        if (currentAccountId != account.id) {
            currentAccountId = account.id
            observeZones(account.id)
        }
        // 每次进入页面都自动刷新，同步云端删除/状态变更到本地数据库
        refresh(account)
    }

    private fun observeZones(accountId: Long) {
        viewModelScope.launch {
            zoneRepository.getZonesByAccount(accountId).collect { zones ->
                _uiState.update { it.copy(zones = zones, isLoading = false) }
            }
        }
    }

    fun refresh(account: Account) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            when (val result = zoneRepository.fetchAndSaveZones(account)) {
                is com.muort.upworker.core.model.Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
                is com.muort.upworker.core.model.Resource.Error -> {
                    Timber.e("refresh zones error: ${result.message}")
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                }
                is com.muort.upworker.core.model.Resource.Loading -> {}
            }
        }
    }

    /** 新建域名。成功后 createdZone 非空，表单切到名称服务器结果页。 */
    fun createZone(account: Account, domain: String) {
        if (_addState.value.isSaving) return
        viewModelScope.launch {
            _addState.update { it.copy(isSaving = true, error = null) }
            when (val result = zoneRepository.createZone(account, domain)) {
                is com.muort.upworker.core.model.Resource.Success -> {
                    _addState.update { it.copy(createdZone = result.data, isSaving = false) }
                }
                is com.muort.upworker.core.model.Resource.Error -> {
                    _addState.update { it.copy(error = result.message, isSaving = false) }
                }
                is com.muort.upworker.core.model.Resource.Loading -> {}
            }
        }
    }

    fun resetAddState() {
        _addState.value = AddDomainUiState()
    }
}
