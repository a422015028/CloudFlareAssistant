package com.muort.upworker.feature.kv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.KvKey
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.KvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class KvViewModel @Inject constructor(
    private val kvRepository: KvRepository
) : ViewModel() {
    
    private val _namespaces = MutableStateFlow<List<KvNamespace>>(emptyList())
    val namespaces: StateFlow<List<KvNamespace>> = _namespaces.asStateFlow()
    
    private val _selectedNamespace = MutableStateFlow<KvNamespace?>(null)
    val selectedNamespace: StateFlow<KvNamespace?> = _selectedNamespace.asStateFlow()
    
    private val _keys = MutableStateFlow<List<KvKey>>(emptyList())
    val keys: StateFlow<List<KvKey>> = _keys.asStateFlow()
    
    private val _selectedKey = MutableStateFlow<KvKey?>(null)
    val selectedKey: StateFlow<KvKey?> = _selectedKey.asStateFlow()
    
    private val _keyValue = MutableStateFlow<String>("")
    val keyValue: StateFlow<String> = _keyValue.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun loadNamespaces(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.listNamespaces(account)) {
                is Resource.Success -> {
                    _namespaces.value = result.data
                    Timber.d("Loaded ${result.data.size} namespaces")
                }
                is Resource.Error -> {
                    _message.emit("Failed to load namespaces: ${result.message}")
                    Timber.e("Failed to load namespaces: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createNamespace(account: Account, title: String) {
        if (title.isBlank()) {
            viewModelScope.launch {
                _message.emit("Please enter namespace title")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.createNamespace(account, title)) {
                is Resource.Success -> {
                    _message.emit("Namespace created successfully")
                    loadNamespaces(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to create namespace: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteNamespace(account: Account, namespaceId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.deleteNamespace(account, namespaceId)) {
                is Resource.Success -> {
                    _message.emit("Namespace deleted successfully")
                    if (_selectedNamespace.value?.id == namespaceId) {
                        _selectedNamespace.value = null
                        _keys.value = emptyList()
                    }
                    loadNamespaces(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete namespace: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun selectNamespace(namespace: KvNamespace) {
        _selectedNamespace.value = namespace
        _keys.value = emptyList()
        _selectedKey.value = null
        _keyValue.value = ""
    }
    
    fun loadKeys(account: Account, namespaceId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.listKeys(account, namespaceId)) {
                is Resource.Success -> {
                    val keysWithValues = result.data.map { key ->
                        // 为每个键异步获取值
                        val valueResult = kvRepository.getValue(account, namespaceId, key.name)
                        key.apply {
                            value = if (valueResult is Resource.Success) valueResult.data else null
                        }
                    }
                    _keys.value = keysWithValues
                    Timber.d("Loaded ${keysWithValues.size} keys with values")
                }
                is Resource.Error -> {
                    _message.emit("Failed to load keys: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun getValue(account: Account, namespaceId: String, keyName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.getValue(account, namespaceId, keyName)) {
                is Resource.Success -> {
                    _keyValue.value = result.data
                    _selectedKey.value = _keys.value.find { it.name == keyName }
                    onResult(result.data)
                }
                is Resource.Error -> {
                    _message.emit("Failed to get value: ${result.message}")
                    onResult(null)
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun putValue(account: Account, namespaceId: String, keyName: String, value: String) {
        if (keyName.isBlank()) {
            viewModelScope.launch {
                _message.emit("Please enter key name")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.putValue(account, namespaceId, keyName, value)) {
                is Resource.Success -> {
                    _message.emit("Value saved successfully")
                    loadKeys(account, namespaceId)
                }
                is Resource.Error -> {
                    _message.emit("Failed to save value: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteValue(account: Account, namespaceId: String, keyName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = kvRepository.deleteValue(account, namespaceId, keyName)) {
                is Resource.Success -> {
                    _message.emit("Value deleted successfully")
                    if (_selectedKey.value?.name == keyName) {
                        _selectedKey.value = null
                        _keyValue.value = ""
                    }
                    loadKeys(account, namespaceId)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete value: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun clearSelection() {
        _selectedKey.value = null
        _keyValue.value = ""
    }
    
    fun clearKeyValue() {
        _keyValue.value = ""
    }
}
