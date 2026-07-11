package com.muort.upworker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountInfo
import com.muort.upworker.core.model.AuthType
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.repository.AccountRepository
import com.muort.upworker.core.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val zoneRepository: ZoneRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _defaultAccount = MutableStateFlow<Account?>(null)
    val defaultAccount: StateFlow<Account?> = _defaultAccount.asStateFlow()
    
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()
    
    // Zone management
    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()
    
    private val _selectedZone = MutableStateFlow<Zone?>(null)
    val selectedZone: StateFlow<Zone?> = _selectedZone.asStateFlow()
    
    private val _loadingZones = MutableStateFlow(false)
    val loadingZones: StateFlow<Boolean> = _loadingZones.asStateFlow()
    
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
        r2SecretAccessKey: String? = null,
        email: String? = null,
        globalApiKey: String? = null,
        authType: String = AuthType.TOKEN.name
    ) {
        if (name.isBlank() || accountId.isBlank()) {
            viewModelScope.launch {
                _message.emit("请填写所有必填项")
            }
            return
        }
        
        // 根据认证类型验证凭据
        val authTypeEnum = try {
            AuthType.valueOf(authType)
        } catch (e: Exception) {
            AuthType.TOKEN
        }
        
        val validationError = when (authTypeEnum) {
            AuthType.TOKEN -> if (token.isBlank()) "API Token 不能为空" else null
            AuthType.GLOBAL_API_KEY -> {
                if (email?.isBlank() != false) "邮箱不能为空"
                else if (globalApiKey?.isBlank() != false) "Global API Key 不能为空"
                else null
            }
        }
        
        if (validationError != null) {
            viewModelScope.launch {
                _message.emit(validationError)
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
                r2SecretAccessKey = r2SecretAccessKey?.takeIf { it.isNotBlank() },
                email = email?.takeIf { it.isNotBlank() },
                globalApiKey = globalApiKey?.takeIf { it.isNotBlank() },
                authType = authType
            )
            
            when (val result = accountRepository.insertAccount(account)) {
                is Resource.Success -> {
                    _message.emit("账号添加成功")
                    if (isDefault) {
                        accountRepository.setDefaultAccount(result.data)
                    }
                }
                is Resource.Error -> {
                    _message.emit("添加账号失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun updateAccount(account: Account) {
        viewModelScope.launch {
            when (val result = accountRepository.updateAccount(account)) {
                is Resource.Success -> {
                    _message.emit("账号更新成功")
                    if (account.isDefault) {
                        accountRepository.setDefaultAccount(account.id)
                    }
                }
                is Resource.Error -> {
                    _message.emit("更新账号失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            when (val result = accountRepository.deleteAccount(account)) {
                is Resource.Success -> {
                    _message.emit("账号删除成功")
                }
                is Resource.Error -> {
                    _message.emit("删除账号失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    fun setDefaultAccount(accountId: Long) {
        viewModelScope.launch {
            when (val result = accountRepository.setDefaultAccount(accountId)) {
                is Resource.Success -> {
                    // 账号切换成功，不显示提示
                }
                is Resource.Error -> {
                    _message.emit("设置默认账号失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    suspend fun exportAccounts(): List<Account>? {
        return when (val result = accountRepository.exportAccounts()) {
            is Resource.Success -> result.data
            is Resource.Error -> {
                _message.emit("导出账号失败: ${result.message}")
                null
            }
            is Resource.Loading -> null
        }
    }
    
    fun importAccounts(accounts: List<Account>) {
        viewModelScope.launch {
            when (val result = accountRepository.importAccounts(accounts)) {
                is Resource.Success -> {
                    _message.emit("账号导入成功")
                }
                is Resource.Error -> {
                    _message.emit("导入账号失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    // Zone management functions
    
    fun loadZonesForAccount(accountId: Long) {
        viewModelScope.launch {
            zoneRepository.getZonesByAccount(accountId).collect { zones ->
                _zones.value = zones
                // Load selected zone
                var selected = zoneRepository.getSelectedZone(accountId)
                // 首次加载时如果没有选中域名，自动选中第一个
                if (selected == null && zones.isNotEmpty()) {
                    zoneRepository.setSelectedZone(accountId, zones[0].id)
                    selected = zones[0]
                }
                _selectedZone.value = selected
            }
        }
    }
    
    fun fetchZonesFromApi(account: Account, silent: Boolean = false) {
        viewModelScope.launch {
            _loadingZones.value = true
            when (val result = zoneRepository.fetchAndSaveZones(account)) {
                is Resource.Success -> {
                    if (!silent) _message.emit("成功获取 ${result.data.size} 个域名")
                    loadZonesForAccount(account.id)
                }
                is Resource.Error -> {
                    if (!silent) _message.emit("获取域名失败: ${result.message}")
                }
                is Resource.Loading -> {}
            }
            _loadingZones.value = false
        }
    }
    
    fun selectZone(accountId: Long, zoneId: String) {
        viewModelScope.launch {
            zoneRepository.setSelectedZone(accountId, zoneId)
            // 域名选择成功，不显示提示
            loadZonesForAccount(accountId)
        }
    }
    
    fun getSelectedZoneId(@Suppress("UNUSED_PARAMETER") accountId: Long): String? {
        return _selectedZone.value?.id
    }
    
    /**
     * 从 API 获取账号列表
     * 用于 Global API Key 模式下自动获取 Account ID
     */
    fun fetchAccountsFromApi(account: Account, onResult: (List<AccountInfo>) -> Unit) {
        viewModelScope.launch {
            when (val result = accountRepository.fetchAccountsFromApi(account)) {
                is Resource.Success -> {
                    _message.emit("成功获取 ${result.data.size} 个账号")
                    onResult(result.data)
                }
                is Resource.Error -> {
                    _message.emit("获取账号列表失败: ${result.message}")
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
