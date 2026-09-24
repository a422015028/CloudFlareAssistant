package com.muort.upworker.feature.zerotrust.mtls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.MtlsCertificate
import com.muort.upworker.core.model.MtlsCertificateCreateRequest
import com.muort.upworker.core.model.MtlsCertificateSetting
import com.muort.upworker.core.model.MtlsCertificateUpdateRequest
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
 * ViewModel for Access mTLS root certificates and per-hostname edge settings.
 */
@HiltViewModel
class MtlsViewModel @Inject constructor(
    private val zeroTrustRepository: ZeroTrustRepository
) : ViewModel() {

    private val _certificates = MutableStateFlow<List<MtlsCertificate>>(emptyList())
    val certificates: StateFlow<List<MtlsCertificate>> = _certificates.asStateFlow()

    private val _settings = MutableStateFlow<List<MtlsCertificateSetting>>(emptyList())
    val settings: StateFlow<List<MtlsCertificateSetting>> = _settings.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    private val _error = MutableSharedFlow<UiMessage>()
    val error: SharedFlow<UiMessage> = _error.asSharedFlow()

    // ==================== Certificates ====================

    fun loadCertificates(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listMtlsCertificates(account)) {
                is Resource.Success -> {
                    _certificates.value = result.data
                    Timber.d("Loaded ${result.data.size} mTLS certificates")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_certs_load_failed, result.message))
                    Timber.e("Failed to load mTLS certificates: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    fun createCertificate(account: Account, request: MtlsCertificateCreateRequest) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.createMtlsCertificate(account, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_created))
                    loadCertificates(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_create_failed, result.message))
                    Timber.e("Failed to create mTLS certificate: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun updateCertificate(
        account: Account,
        certificateId: String,
        request: MtlsCertificateUpdateRequest
    ) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.updateMtlsCertificate(account, certificateId, request)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_updated))
                    loadCertificates(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_update_failed, result.message))
                    Timber.e("Failed to update mTLS certificate: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteCertificate(account: Account, certificateId: String) {
        viewModelScope.launch {
            when (val result = zeroTrustRepository.deleteMtlsCertificate(account, certificateId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_deleted))
                    loadCertificates(account)
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_cert_delete_failed, result.message))
                    Timber.e("Failed to delete mTLS certificate: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== Hostname settings ====================

    fun loadSettings(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.listMtlsCertificateSettings(account)) {
                is Resource.Success -> {
                    _settings.value = result.data
                    Timber.d("Loaded ${result.data.size} mTLS hostname settings")
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_settings_load_failed, result.message))
                    Timber.e("Failed to load mTLS settings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }

    /**
     * Persist the whole settings list (PUT semantics replace the account-level list).
     */
    fun saveSettings(account: Account, settings: List<MtlsCertificateSetting>) {
        viewModelScope.launch {
            _loadingState.value = true
            when (val result = zeroTrustRepository.updateMtlsCertificateSettings(account, settings)) {
                is Resource.Success -> {
                    _settings.value = result.data
                    _message.emit(UiMessage.of(R.string.vm_msg_zt_mtls_settings_saved))
                }
                is Resource.Error -> {
                    _error.emit(UiMessage.of(R.string.vm_msg_zt_mtls_settings_save_failed, result.message))
                    Timber.e("Failed to save mTLS settings: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingState.value = false
        }
    }
}
