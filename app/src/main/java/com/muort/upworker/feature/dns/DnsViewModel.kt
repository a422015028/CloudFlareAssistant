package com.muort.upworker.feature.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DnsRecord
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.Resource
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
    
    private val _dnsRecords = MutableStateFlow<List<DnsRecord>>(emptyList())
    val dnsRecords: StateFlow<List<DnsRecord>> = _dnsRecords.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun loadDnsRecords(account: Account, type: String? = null, name: String? = null) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = dnsRepository.listDnsRecords(account, type, name)) {
                is Resource.Success -> {
                    _dnsRecords.value = result.data
                    Timber.d("Loaded ${result.data.size} DNS records")
                }
                is Resource.Error -> {
                    _message.emit("Failed to load DNS records: ${result.message}")
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
        content: String,
        ttl: Int = 1,
        proxied: Boolean = true
    ) {
        val record = DnsRecordRequest(
            type = type,
            name = name,
            content = content,
            ttl = ttl,
            proxied = proxied
        )
        createDnsRecord(account, record)
    }
    
    fun createDnsRecord(account: Account, record: DnsRecordRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = dnsRepository.createDnsRecord(account, record)) {
                is Resource.Success -> {
                    _message.emit("DNS record created successfully")
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to create DNS record: ${result.message}")
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
        content: String,
        ttl: Int = 1,
        proxied: Boolean = true
    ) {
        val record = DnsRecordRequest(
            type = type,
            name = name,
            content = content,
            ttl = ttl,
            proxied = proxied
        )
        updateDnsRecord(account, recordId, record)
    }
    
    fun updateDnsRecord(account: Account, recordId: String, record: DnsRecordRequest) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = dnsRepository.updateDnsRecord(account, recordId, record)) {
                is Resource.Success -> {
                    _message.emit("DNS record updated successfully")
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to update DNS record: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteDnsRecord(account: Account, recordId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = dnsRepository.deleteDnsRecord(account, recordId)) {
                is Resource.Success -> {
                    _message.emit("DNS record deleted successfully")
                    loadDnsRecords(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete DNS record: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
}
