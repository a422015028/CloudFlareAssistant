package com.muort.upworker.feature.zerotrust.sshca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.GatewayCa
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
 * ViewModel for the Access for Infrastructure SSH Certificate Authority (gateway_ca).
 * An account has at most one CA; generating it twice returns the existing one.
 */
@HiltViewModel
class SshCaViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {

    private val _ca = MutableStateFlow<GatewayCa?>(null)
    val ca: StateFlow<GatewayCa?> = _ca.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()

    fun loadCa(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.getGatewayCa(account)) {
                is Resource.Success -> {
                    _ca.value = result.data
                    Timber.d("SSH CA present: ${_ca.value != null}")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_ssh_ca_load_failed, result.message))
                    Timber.e("Failed to load SSH CA: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun generateCa(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.createGatewayCa(account)) {
                is Resource.Success -> {
                    _ca.value = result.data
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_ssh_ca_generated))
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_ssh_ca_generate_failed, result.message))
                    Timber.e("Failed to generate SSH CA: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
