package com.muort.upworker.feature.zerotrust.servicetoken

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.ServiceToken
import com.muort.upworker.core.model.ServiceTokenRequest
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
 * ViewModel for Access Service Tokens management.
 * Service tokens provide non-user (service-to-service / CI / scripts) authentication.
 */
@HiltViewModel
class ServiceTokensViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {

    private val _tokens = MutableStateFlow<List<ServiceToken>>(emptyList())
    val tokens: StateFlow<List<ServiceToken>> = _tokens.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()

    /**
     * Emitted once right after a token is created. The client secret is only
     * available in this response, so the UI must show a save/copy dialog.
     */
    private val _createdToken = MutableSharedFlow<ServiceToken>(extraBufferCapacity = 1)
    val createdToken: SharedFlow<ServiceToken> = _createdToken.asSharedFlow()

    /**
     * Emitted after a secret rotation. Carries the rotated token (with the new
     * client secret) and the grace period label flag via [RotatedSecret].
     */
    private val _rotatedSecret = MutableSharedFlow<RotatedSecret>(extraBufferCapacity = 1)
    val rotatedSecret: SharedFlow<RotatedSecret> = _rotatedSecret.asSharedFlow()

    data class RotatedSecret(val token: ServiceToken, val previousRevokedImmediately: Boolean)

    /**
     * Emitted when a fresh single-token fetch (GET) completes, used by the detail dialog.
     */
    private val _detailToken = MutableSharedFlow<ServiceToken>(extraBufferCapacity = 1)
    val detailToken: SharedFlow<ServiceToken> = _detailToken.asSharedFlow()

    fun loadTokens(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listServiceTokens(account)) {
                is Resource.Success -> {
                    _tokens.value = result.data
                    Timber.d("Loaded ${result.data.size} service tokens")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_tokens_load_failed, result.message))
                    Timber.e("Failed to load service tokens: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    /**
     * Fetch a single token's latest state (GET) for the detail dialog.
     */
    fun loadTokenDetail(account: Account, tokenId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.getServiceToken(account, tokenId)) {
                is Resource.Success -> {
                    _detailToken.emit(result.data)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_load_failed, result.message))
                    Timber.e("Failed to load service token detail: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun createToken(account: Account, request: ServiceTokenRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.createServiceToken(account, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_service_token_created))
                    _createdToken.emit(result.data)
                    loadTokens(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_create_failed, result.message))
                    Timber.e("Failed to create service token: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun updateToken(account: Account, tokenId: String, request: ServiceTokenRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.updateServiceToken(account, tokenId, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_service_token_updated))
                    loadTokens(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_update_failed, result.message))
                    Timber.e("Failed to update service token: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteToken(account: Account, tokenId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.deleteServiceToken(account, tokenId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_service_token_deleted))
                    loadTokens(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_delete_failed, result.message))
                    Timber.e("Failed to delete service token: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * Refresh the token's expiration (extends by its configured duration, secret unchanged).
     */
    fun refreshToken(account: Account, tokenId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.refreshServiceToken(account, tokenId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_service_token_refreshed))
                    loadTokens(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_refresh_failed, result.message))
                    Timber.e("Failed to refresh service token: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    /**
     * Rotate the client secret.
     * @param previousSecretExpiresAt RFC3339 instant when the old secret stops working;
     *        null means immediate revocation.
     */
    fun rotateToken(account: Account, tokenId: String, previousSecretExpiresAt: String?) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.rotateServiceToken(account, tokenId, previousSecretExpiresAt)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_service_token_rotated))
                    _rotatedSecret.emit(
                        RotatedSecret(result.data, previousRevokedImmediately = previousSecretExpiresAt == null)
                    )
                    loadTokens(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_service_token_rotate_failed, result.message))
                    Timber.e("Failed to rotate service token: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
