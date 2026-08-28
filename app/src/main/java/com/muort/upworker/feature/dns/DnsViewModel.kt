package com.muort.upworker.feature.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DnsRecord
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.DnsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DnsViewModel @Inject constructor(
    private val dnsRepository: DnsRepository
) : ViewModel() {

    private var currentZoneId: String = ""

    private val _dnsRecords = MutableStateFlow<List<DnsRecord>>(emptyList())
    val dnsRecords: StateFlow<List<DnsRecord>> = _dnsRecords.asStateFlow()

    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    fun setZoneId(zoneId: String) {
        currentZoneId = zoneId
    }

    fun loadDnsRecords(account: Account, type: String? = null, name: String? = null) {
        if (currentZoneId.isBlank()) {
            viewModelScope.launch { _message.emit(UiMessage.of(R.string.vm_msg_dns_zone_id_unset)) }
            return
        }
        viewModelScope.launch {
            _loadingState.value = true

            when (val result = dnsRepository.listDnsRecords(account, currentZoneId, type, name)) {
                is Resource.Success -> {
                    _dnsRecords.value = result.data
                    Timber.d("Loaded ${result.data.size} DNS records")
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_load_failed, result.message))
                    Timber.e("Failed to load DNS records: ${result.message}")
                }
                is Resource.Loading -> {}
            }

            _loadingState.value = false
        }
    }

    fun createDnsRecord(
        account: Account,
        type: String,
        name: String,
        content: String? = null,
        ttl: Int = 1,
        proxied: Boolean = true,
        priority: Int? = null,
        data: Map<String, Any?>? = null
    ) {
        val record = DnsRecordRequest(
            type = type,
            name = name,
            content = content,
            ttl = ttl,
            proxied = proxied,
            priority = priority,
            data = data
        )
        createDnsRecord(account, record)
    }

    fun createDnsRecord(account: Account, record: DnsRecordRequest) {
        viewModelScope.launch {
            _loadingState.value = true

            when (val result = dnsRepository.createDnsRecord(account, currentZoneId, record)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_create_success))
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_create_failed, result.message))
                }
                is Resource.Loading -> {}
            }

            _loadingState.value = false
        }
    }

    fun updateDnsRecord(
        account: Account,
        recordId: String,
        type: String,
        name: String,
        content: String? = null,
        ttl: Int = 1,
        proxied: Boolean = true,
        priority: Int? = null,
        data: Map<String, Any?>? = null
    ) {
        val record = DnsRecordRequest(
            type = type,
            name = name,
            content = content,
            ttl = ttl,
            proxied = proxied,
            priority = priority,
            data = data
        )
        updateDnsRecord(account, recordId, record)
    }

    fun updateDnsRecord(account: Account, recordId: String, record: DnsRecordRequest) {
        viewModelScope.launch {
            _loadingState.value = true

            when (val result = dnsRepository.updateDnsRecord(account, currentZoneId, recordId, record)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_update_success))
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_update_failed, result.message))
                }
                is Resource.Loading -> {}
            }

            _loadingState.value = false
        }
    }

    fun deleteDnsRecord(account: Account, recordId: String) {
        viewModelScope.launch {
            _loadingState.value = true

            when (val result = dnsRepository.deleteDnsRecord(account, currentZoneId, recordId)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_delete_success))
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit(UiMessage.of(R.string.vm_msg_dns_delete_failed, result.message))
                }
                is Resource.Loading -> {}
            }

            _loadingState.value = false
        }
    }
}
