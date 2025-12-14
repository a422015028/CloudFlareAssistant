package com.muort.upworker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _defaultAccount = MutableStateFlow<Account?>(null)
    val defaultAccount: StateFlow<Account?> = _defaultAccount.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    init {
        loadAccounts()
        loadDefaultAccount()
    }
    
    fun loadAccounts() {
        viewModelScope.launch {
            _uiState.value = AccountUiState.Loading
            
            accountRepository.getAllAccounts().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _accounts.value = resource.data
                        _uiState.value = if (resource.data.isEmpty()) {
                            AccountUiState.Empty
                        } else {
                            AccountUiState.Success(resource.data)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.value = AccountUiState.Error(resource.message)
                        Timber.e("Failed to load accounts: ${resource.message}")
                    }
                    is Resource.Loading -> {
                        _uiState.value = AccountUiState.Loading
                    }
                }
            }
        }
    }
    
    private fun loadDefaultAccount() {
        viewModelScope.launch {
            accountRepository.getDefaultAccount().collect { resource ->
                when (resource) {
                    is Resource.Success -> {
                        _defaultAccount.value = resource.data
                        if (resource.data != null) {
                            Timber.d("Default account loaded: ${resource.data.name} (ID: ${resource.data.id})")
                            Timber.d("Account fields - token: ${resource.data.token.take(10)}..., zoneId: ${resource.data.zoneId}")
                        } else {
                            Timber.w("Default account is null - no default account set")
                        }
                    }
                    is Resource.Error -> {
                        Timber.e("Failed to load default account: ${resource.message}")
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }
    
    fun addAccount(
        name: String,
        accountId: String,
        token: String,
        zoneId: String?,
        isDefault: Boolean = false,
        r2AccessKeyId: String? = null,
        r2SecretAccessKey: String? = null
    ) {
        if (name.isBlank() || accountId.isBlank() || token.isBlank()) {
            viewModelScope.launch {
                _message.emit("Please fill in all required fields")
            }
            return
        }
        
        viewModelScope.launch {
            val account = Account(
                name = name,
                accountId = accountId,
                token = token,
                zoneId = zoneId?.takeIf { it.isNotBlank() },
                isDefault = isDefault,
                r2AccessKeyId = r2AccessKeyId?.takeIf { it.isNotBlank() },
                r2SecretAccessKey = r2SecretAccessKey?.takeIf { it.isNotBlank() }
            )
            
            when (val result = accountRepository.insertAccount(account)) {
                is Resource.Success -> {
                    _message.emit("Account added successfully")
                    if (isDefault) {
                        accountRepository.setDefaultAccount(result.data)
                    }
                }
                is Resource.Error -> {
                    _message.emit("Failed to add account: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun updateAccount(account: Account) {
        viewModelScope.launch {
            when (val result = accountRepository.updateAccount(account)) {
                is Resource.Success -> {
                    _message.emit("Account updated successfully")
                    if (account.isDefault) {
                        accountRepository.setDefaultAccount(account.id)
                    }
                }
                is Resource.Error -> {
                    _message.emit("Failed to update account: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            when (val result = accountRepository.deleteAccount(account)) {
                is Resource.Success -> {
                    _message.emit("Account deleted successfully")
                }
                is Resource.Error -> {
                    _message.emit("Failed to delete account: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun setDefaultAccount(accountId: Long) {
        viewModelScope.launch {
            when (val result = accountRepository.setDefaultAccount(accountId)) {
                is Resource.Success -> {
                    _message.emit("Default account updated")
                }
                is Resource.Error -> {
                    _message.emit("Failed to set default account: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    suspend fun exportAccounts(): List<Account>? {
        return when (val result = accountRepository.exportAccounts()) {
            is Resource.Success -> result.data
            is Resource.Error -> {
                _message.emit("Failed to export accounts: ${result.message}")
                null
            }
            is Resource.Loading -> null
        }
    }
    
    fun importAccounts(accounts: List<Account>) {
        viewModelScope.launch {
            when (val result = accountRepository.importAccounts(accounts)) {
                is Resource.Success -> {
                    _message.emit("Accounts imported successfully")
                }
                is Resource.Error -> {
                    _message.emit("Failed to import accounts: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
}

sealed class AccountUiState {
    object Loading : AccountUiState()
    object Empty : AccountUiState()
    data class Success(val accounts: List<Account>) : AccountUiState()
    data class Error(val message: String) : AccountUiState()
}
