package com.muort.upworker.feature.worker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CustomDomain
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Route
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.repository.WorkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WorkerViewModel @Inject constructor(
    private val workerRepository: WorkerRepository
) : ViewModel() {
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    
    private val _scripts = MutableStateFlow<List<WorkerScript>>(emptyList())
    val scripts: StateFlow<List<WorkerScript>> = _scripts.asStateFlow()
    
    private val _routes = MutableStateFlow<List<Route>>(emptyList())
    val routes: StateFlow<List<Route>> = _routes.asStateFlow()
    
    private val _customDomains = MutableStateFlow<List<CustomDomain>>(emptyList())
    val customDomains: StateFlow<List<CustomDomain>> = _customDomains.asStateFlow()
    
    private val _loadingState = MutableStateFlow(false)
    val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    fun uploadWorkerScript(account: Account, scriptName: String, scriptFile: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.uploadWorkerScript(account, scriptName, scriptFile)) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit("Worker script uploaded successfully")
                    Timber.d("Script uploaded: $scriptName")
                    
                    // Reload scripts list
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("Upload failed: ${result.message}")
                    Timber.e("Failed to upload script: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Upload Worker Script with KV Namespace bindings
     * @param kvBindings List of pairs containing (binding_name, namespace_id)
     */
    fun uploadWorkerScriptWithKvBindings(
        account: Account,
        scriptName: String,
        scriptFile: File,
        kvBindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.uploadWorkerScriptWithKvBindings(
                account, scriptName, scriptFile, kvBindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit("Worker script with KV bindings uploaded successfully")
                    Timber.d("Script with KV bindings uploaded: $scriptName")
                    
                    // Reload scripts list
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("Upload failed: ${result.message}")
                    Timber.e("Failed to upload script with KV bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    /**
     * Update KV bindings for an existing Worker Script
     * Only updates the bindings configuration, does NOT re-upload script code
     * @param scriptName Name of the existing script
     * @param kvBindings List of pairs containing (binding_name, namespace_id)
     */
    fun updateWorkerKvBindings(
        account: Account,
        scriptName: String,
        kvBindings: List<Pair<String, String>>
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            
            when (val result = workerRepository.updateWorkerKvBindings(
                account, scriptName, kvBindings
            )) {
                is Resource.Success -> {
                    _uploadState.value = UploadState.Success
                    _message.emit("KV bindings updated successfully for '$scriptName'")
                    Timber.d("KV bindings updated for script: $scriptName")
                    
                    // Reload scripts list
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                    _message.emit("Failed to update bindings: ${result.message}")
                    Timber.e("Failed to update KV bindings: ${result.message}")
                }
                is Resource.Loading -> {
                    _uploadState.value = UploadState.Uploading
                }
            }
        }
    }
    
    fun loadWorkerScripts(account: Account) {
        viewModelScope.launch {
            when (val result = workerRepository.listWorkerScripts(account)) {
                is Resource.Success -> {
                    _scripts.value = result.data
                    Timber.d("Loaded ${result.data.size} worker scripts")
                }
                is Resource.Error -> {
                    _message.emit("Failed to load scripts: ${result.message}")
                    Timber.e("Failed to load scripts: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun getWorkerScript(account: Account, scriptName: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.getWorkerScript(account, scriptName)) {
                is Resource.Success -> {
                    onSuccess(result.data)
                }
                is Resource.Error -> {
                    _message.emit("Failed to load script: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    /**
     * Get Worker Script settings (includes bindings)
     */
    fun getWorkerSettings(
        account: Account,
        scriptName: String,
        onResult: (Resource<WorkerScript>) -> Unit
    ) {
        viewModelScope.launch {
            when (val result = workerRepository.getWorkerSettings(account, scriptName)) {
                is Resource.Success -> {
                    Timber.d("Fetched settings for '$scriptName' with ${result.data.bindings?.size ?: 0} bindings")
                    onResult(result)
                }
                is Resource.Error -> {
                    Timber.e("Failed to fetch settings: ${result.message}")
                    _message.emit("Failed to load settings: ${result.message}")
                    onResult(result)
                }
                is Resource.Loading -> {
                    onResult(result)
                }
            }
        }
    }
    
    fun deleteWorkerScript(account: Account, scriptName: String) {
        viewModelScope.launch {
            when (val result = workerRepository.deleteWorkerScript(account, scriptName)) {
                is Resource.Success -> {
                    _message.emit("Script deleted successfully")
                    loadWorkerScripts(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete script: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    // Routes
    fun loadRoutes(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.listRoutes(account)) {
                is Resource.Success -> {
                    _routes.value = result.data
                    Timber.d("Loaded ${result.data.size} routes")
                }
                is Resource.Error -> {
                    _message.emit("Failed to load routes: ${result.message}")
                    Timber.e("Failed to load routes: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun createRoute(account: Account, pattern: String, scriptName: String) {
        if (pattern.isBlank() || scriptName.isBlank()) {
            viewModelScope.launch {
                _message.emit("Pattern and script name are required")
            }
            return
        }
        
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.createRoute(account, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit("Route created successfully")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to create route: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun updateRoute(account: Account, routeId: String, pattern: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.updateRoute(account, routeId, pattern, scriptName)) {
                is Resource.Success -> {
                    _message.emit("Route updated successfully")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to update route: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteRoute(account: Account, routeId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.deleteRoute(account, routeId)) {
                is Resource.Success -> {
                    _message.emit("Route deleted successfully")
                    loadRoutes(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete route: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun loadCustomDomains(account: Account) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.listCustomDomains(account)) {
                is Resource.Success -> {
                    _customDomains.value = result.data
                }
                is Resource.Error -> {
                    _message.emit("Failed to load custom domains: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun addCustomDomain(account: Account, hostname: String, scriptName: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.addCustomDomain(account, hostname, scriptName)) {
                is Resource.Success -> {
                    _message.emit("Custom domain added successfully")
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to add custom domain: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun deleteCustomDomain(account: Account, domainId: String) {
        viewModelScope.launch {
            _loadingState.value = true
            
            when (val result = workerRepository.deleteCustomDomain(account, domainId)) {
                is Resource.Success -> {
                    _message.emit("Custom domain deleted successfully")
                    loadCustomDomains(account)
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete custom domain: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            
            _loadingState.value = false
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }
}

sealed class UploadState {
    object Idle : UploadState()
    object Uploading : UploadState()
    object Success : UploadState()
    data class Error(val message: String) : UploadState()
}
