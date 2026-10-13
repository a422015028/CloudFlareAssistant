package com.muort.upworker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SecretQuota
import com.muort.upworker.core.model.SecretScope
import com.muort.upworker.core.model.SecretStore
import com.muort.upworker.core.model.StoredSecret
import com.muort.upworker.core.model.StoredSecretCreateBody
import com.muort.upworker.core.model.StoredSecretDuplicateBody
import com.muort.upworker.core.model.StoredSecretEditBody
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.SecretsStoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Secrets Store 面板 UI 状态（两级：密钥库列表 → 库内密钥列表） */
sealed class SecretsStoreUiState {
    object Loading : SecretsStoreUiState()
    object Empty : SecretsStoreUiState()
    data class Content(val stores: List<SecretStore>) : SecretsStoreUiState()
    data class Error(val message: UiMessage) : SecretsStoreUiState()
}

sealed class StoredSecretUiState {
    object Loading : StoredSecretUiState()
    object Empty : StoredSecretUiState()
    data class Content(val secrets: List<StoredSecret>) : StoredSecretUiState()
    data class Error(val message: UiMessage) : StoredSecretUiState()
}

@HiltViewModel
class SecretsStoreViewModel @Inject constructor(
    private val repository: SecretsStoreRepository
) : ViewModel() {

    private val _storesState = MutableStateFlow<SecretsStoreUiState>(SecretsStoreUiState.Loading)
    val storesState: StateFlow<SecretsStoreUiState> = _storesState.asStateFlow()

    private val _selectedStore = MutableStateFlow<SecretStore?>(null)
    val selectedStore: StateFlow<SecretStore?> = _selectedStore.asStateFlow()

    private val _secretsState = MutableStateFlow<StoredSecretUiState>(StoredSecretUiState.Loading)
    val secretsState: StateFlow<StoredSecretUiState> = _secretsState.asStateFlow()

    private val _quota = MutableStateFlow<SecretQuota?>(null)
    val quota: StateFlow<SecretQuota?> = _quota.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message = _message.asSharedFlow()

    /** 非空库普通删除被 409 拒绝时触发，引导用户确认级联强制删除 */
    private val _forceDeleteStorePrompt = MutableSharedFlow<SecretStore>()
    val forceDeleteStorePrompt = _forceDeleteStorePrompt.asSharedFlow()

    // 已加载的全量密钥 + 客户端搜索/scope 过滤条件
    private val allSecrets = MutableStateFlow<List<StoredSecret>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val scopeFilter = MutableStateFlow<String?>(null)

    /** 当前过滤条件下是否无匹配结果（供面板区分空库/无搜索结果） */
    private val _filterActive = MutableStateFlow(false)
    val filterActive: StateFlow<Boolean> = _filterActive.asStateFlow()

    private var loadedAccountId: String? = null
    private var currentAccount: Account? = null

    // ==================== 导航 ====================

    fun bindAccount(account: Account) {
        currentAccount = account
    }

    fun openStore(store: SecretStore) {
        _selectedStore.value = store
        searchQuery.value = ""
        scopeFilter.value = null
        _filterActive.value = false
        allSecrets.value = emptyList()
        currentAccount?.let { loadSecrets(it, store) }
    }

    fun backToStores() {
        _selectedStore.value = null
    }

    // ==================== Stores ====================

    fun loadStores(account: Account, force: Boolean = false) {
        currentAccount = account
        if (!force && loadedAccountId == account.accountId &&
            _storesState.value is SecretsStoreUiState.Content
        ) return
        loadedAccountId = account.accountId
        viewModelScope.launch {
            _storesState.value = SecretsStoreUiState.Loading
            when (val result = repository.listStores(account)) {
                is Resource.Success -> {
                    _storesState.value = if (result.data.isEmpty()) {
                        SecretsStoreUiState.Empty
                    } else {
                        SecretsStoreUiState.Content(result.data)
                    }
                }
                is Resource.Error -> {
                    Timber.e("Failed to list secret stores: %s", result.message)
                    _storesState.value = SecretsStoreUiState.Error(UiMessage.RawString(result.message))
                }
                is Resource.Loading -> {}
            }
        }
        loadQuota(account)
    }

    fun refresh(account: Account) {
        val store = _selectedStore.value
        if (store != null) loadSecrets(account, store) else loadStores(account, force = true)
    }

    fun createStore(account: Account, name: String) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.createStore(account, name.trim())) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_store_created_success))
                    loadStores(account, force = true)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    /**
     * 删除密钥库。force=true 级联删除库内全部密钥。
     * 非空库未传 force 时服务端返回 409 store_not_empty，由面板引导二次确认。
     */
    fun deleteStore(account: Account, store: SecretStore, force: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.deleteStore(account, store.id, force)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_store_deleted_success))
                    if (_selectedStore.value?.id == store.id) _selectedStore.value = null
                    loadStores(account, force = true)
                }
                is Resource.Error -> {
                    if (!force && result.message.contains("store_not_empty", ignoreCase = true)) {
                        _forceDeleteStorePrompt.emit(store)
                    } else {
                        _message.emit(UiMessage.RawString(result.message))
                    }
                }
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun loadQuota(account: Account) {
        viewModelScope.launch {
            (repository.getQuota(account) as? Resource.Success)?.data?.let { _quota.value = it }
        }
    }

    // ==================== Secrets ====================

    fun loadSecrets(account: Account, store: SecretStore) {
        currentAccount = account
        viewModelScope.launch {
            _secretsState.value = StoredSecretUiState.Loading
            when (val result = repository.listSecrets(account, store.id)) {
                is Resource.Success -> {
                    allSecrets.value = result.data
                    applyFilter()
                }
                is Resource.Error -> {
                    Timber.e("Failed to list secrets: %s", result.message)
                    _secretsState.value = StoredSecretUiState.Error(UiMessage.RawString(result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        applyFilter()
    }

    fun setScopeFilter(scope: String?) {
        scopeFilter.value = scope
        applyFilter()
    }

    private fun applyFilter() {
        val query = searchQuery.value.trim()
        val scope = scopeFilter.value
        _filterActive.value = query.isNotEmpty() || scope != null
        val filtered = allSecrets.value.filter { secret ->
            (scope == null || secret.scopes?.contains(scope) == true) &&
                (query.isBlank() ||
                    secret.name.contains(query, ignoreCase = true) ||
                    secret.comment?.contains(query, ignoreCase = true) == true)
        }
        _secretsState.value = if (filtered.isEmpty()) {
            StoredSecretUiState.Empty
        } else {
            StoredSecretUiState.Content(filtered)
        }
    }

    fun createSecret(account: Account, storeId: String, body: StoredSecretCreateBody) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.createSecret(account, storeId, body)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_secret_created_success))
                    _selectedStore.value?.let { loadSecrets(account, it) }
                    loadQuota(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    /**
     * 编辑密钥。newValue 为空表示不修改值；comment/scopes 全量覆盖。
     */
    fun editSecret(
        account: Account,
        storeId: String,
        secretId: String,
        comment: String,
        scopes: List<String>,
        newValue: String?
    ) {
        viewModelScope.launch {
            _busy.value = true
            val body = StoredSecretEditBody(
                comment = comment.ifBlank { null },
                scopes = scopes,
                value = newValue?.takeIf { it.isNotEmpty() }
            )
            when (val result = repository.editSecret(account, storeId, secretId, body)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_secret_updated_success))
                    _selectedStore.value?.let { loadSecrets(account, it) }
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun deleteSecret(account: Account, secret: StoredSecret) {
        val storeId = secret.storeId ?: _selectedStore.value?.id ?: return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.deleteSecret(account, storeId, secret.id)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_secret_deleted_success))
                    _selectedStore.value?.let { loadSecrets(account, it) }
                    loadQuota(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    /** 清空密钥库中的全部密钥 */
    fun bulkDeleteSecrets(account: Account, store: SecretStore) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.bulkDeleteSecrets(account, store.id)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_secret_bulk_deleted_success))
                    loadSecrets(account, store)
                    loadQuota(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun duplicateSecret(account: Account, secret: StoredSecret, body: StoredSecretDuplicateBody) {
        val storeId = secret.storeId ?: _selectedStore.value?.id ?: return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.duplicateSecret(account, storeId, secret.id, body)) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.ss_secret_duplicated_success))
                    _selectedStore.value?.let { loadSecrets(account, it) }
                    loadQuota(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    companion object {
        /** 全部可选 scope，单一数据源 */
        val SCOPES: List<String> = SecretScope.ALL
    }
}
