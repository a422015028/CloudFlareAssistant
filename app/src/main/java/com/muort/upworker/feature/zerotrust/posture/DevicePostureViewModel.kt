package com.muort.upworker.feature.zerotrust.posture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DevicePostureRule
import com.muort.upworker.core.model.DevicePostureRuleRequest
import com.muort.upworker.core.model.PostureIntegration
import com.muort.upworker.core.model.PostureIntegrationRequest
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.ZeroTrustRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for device posture rules and third-party posture integrations.
 */
@HiltViewModel
class DevicePostureViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {

    private val _rules = MutableStateFlow<List<DevicePostureRule>>(emptyList())
    val rules: StateFlow<List<DevicePostureRule>> = _rules.asStateFlow()

    private val _integrations = MutableStateFlow<List<PostureIntegration>>(emptyList())
    val integrations: StateFlow<List<PostureIntegration>> = _integrations.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()

    // ==================== Rules ====================

    fun loadRules(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listPostureRules(account)) {
                is Resource.Success -> {
                    _rules.value = result.data
                    Timber.d("Loaded ${result.data.size} posture rules")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_rules_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun createRule(account: Account, request: DevicePostureRuleRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.createPostureRule(account, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_created))
                    loadRules(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun updateRule(account: Account, ruleId: String, request: DevicePostureRuleRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.updatePostureRule(account, ruleId, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_updated))
                    loadRules(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteRule(account: Account, ruleId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.deletePostureRule(account, ruleId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_deleted))
                    loadRules(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_rule_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== Integrations ====================

    fun loadIntegrations(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listPostureIntegrations(account)) {
                is Resource.Success -> {
                    _integrations.value = result.data
                    Timber.d("Loaded ${result.data.size} posture integrations")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_integrations_load_failed, result.message))
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun createIntegration(account: Account, request: PostureIntegrationRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.createPostureIntegration(account, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_created))
                    loadIntegrations(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun updateIntegration(account: Account, integrationId: String, request: PostureIntegrationRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.updatePostureIntegration(account, integrationId, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_updated))
                    loadIntegrations(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteIntegration(account: Account, integrationId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.deletePostureIntegration(account, integrationId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_deleted))
                    loadIntegrations(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_posture_integration_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }
}
