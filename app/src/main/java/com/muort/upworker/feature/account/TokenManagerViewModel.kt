package com.muort.upworker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.ApiToken
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TokenUpsertRequest
import com.muort.upworker.core.model.TokenVerifyResult
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 令牌作用域：用户级 (/user/tokens) / 账户级 (/accounts/{id}/tokens)
 */
enum class TokenScope { USER, ACCOUNT }

@HiltViewModel
class TokenManagerViewModel @Inject constructor(
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TokenUiState>(TokenUiState.Loading)
    val uiState: StateFlow<TokenUiState> = _uiState.asStateFlow()

    private val _scope = MutableStateFlow(TokenScope.USER)
    val scope: StateFlow<TokenScope> = _scope.asStateFlow()

    private val _permissionGroups = MutableStateFlow<List<PermissionGroup>>(emptyList())
    val permissionGroups: StateFlow<List<PermissionGroup>> = _permissionGroups.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableSharedFlow<UiMessage>()
    val message: SharedFlow<UiMessage> = _message.asSharedFlow()

    // 一次性事件
    private val _tokenCreated = MutableSharedFlow<ApiToken>()
    val tokenCreated: SharedFlow<ApiToken> = _tokenCreated.asSharedFlow()

    private val _verifyResult = MutableSharedFlow<TokenVerifyResult>()
    val verifyResult: SharedFlow<TokenVerifyResult> = _verifyResult.asSharedFlow()

    private val _tokenDetail = MutableSharedFlow<ApiToken>()
    val tokenDetail: SharedFlow<ApiToken> = _tokenDetail.asSharedFlow()

    private val _tokenRolled = MutableSharedFlow<String>()
    val tokenRolled: SharedFlow<String> = _tokenRolled.asSharedFlow()

    fun loadTokens(account: Account, scope: TokenScope = _scope.value) {
        _scope.value = scope
        viewModelScope.launch {
            _uiState.value = TokenUiState.Loading
            val result = if (scope == TokenScope.USER) {
                tokenRepository.listTokens(account)
            } else {
                tokenRepository.listAccountTokens(account)
            }
            when (result) {
                is Resource.Success -> {
                    _uiState.value = if (result.data.isEmpty()) {
                        TokenUiState.Empty
                    } else {
                        TokenUiState.Success(result.data)
                    }
                }
                is Resource.Error -> {
                    _uiState.value = TokenUiState.Error(UiMessage.RawString(result.message))
                    Timber.e("Failed to load tokens: ${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * 加载权限组：用户级 Tab 用 /user/tokens/permission_groups，
     * 账户级 Tab 用 /accounts/{id}/tokens/permission_groups（两个列表内容不同）
     */
    fun loadPermissionGroups(account: Account, force: Boolean = false) {
        val scopeChanged = _permissionGroupsScope != _scope.value
        if (!force && !scopeChanged && _permissionGroups.value.isNotEmpty()) return
        _permissionGroupsScope = _scope.value
        viewModelScope.launch {
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.listPermissionGroups(account)
            } else {
                tokenRepository.listAccountPermissionGroups(account)
            }
            when (result) {
                is Resource.Success -> _permissionGroups.value = result.data
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
        }
    }

    private var _permissionGroupsScope: TokenScope? = null

    /**
     * 获取（并缓存）当前凭据的 user id，用于构造 user 级资源
     */
    suspend fun fetchUserId(account: Account): String? {
        cachedUserId?.let { return it }
        return when (val result = tokenRepository.getUserId(account)) {
            is Resource.Success -> {
                cachedUserId = result.data
                result.data
            }
            is Resource.Error -> {
                _message.emit(UiMessage.RawString(result.message))
                null
            }
            is Resource.Loading -> null
        }
    }

    private var cachedUserId: String? = null

    fun showTokenDetail(account: Account, tokenId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.getToken(account, tokenId)
            } else {
                tokenRepository.getAccountToken(account, tokenId)
            }
            when (result) {
                is Resource.Success -> _tokenDetail.emit(result.data)
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun createToken(account: Account, request: TokenUpsertRequest) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.createToken(account, request)
            } else {
                tokenRepository.createAccountToken(account, request)
            }
            when (result) {
                is Resource.Success -> {
                    _message.emit(if (_scope.value == TokenScope.USER) UiMessage.of(R.string.token_created_success) else UiMessage.of(R.string.vm_msg_token_account_scope_created_success))
                    _tokenCreated.emit(result.data)
                    loadTokens(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun updateToken(account: Account, tokenId: String, request: TokenUpsertRequest) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.updateToken(account, tokenId, request)
            } else {
                tokenRepository.updateAccountToken(account, tokenId, request)
            }
            when (result) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.token_updated_success))
                    loadTokens(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    fun deleteToken(account: Account, tokenId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.deleteToken(account, tokenId)
            } else {
                tokenRepository.deleteAccountToken(account, tokenId)
            }
            when (result) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.token_deleted_success))
                    loadTokens(account)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    /**
     * 更换令牌 secret，按当前作用域分发到用户级/账户级端点，返回新值
     */
    fun rollToken(account: Account, tokenId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = if (_scope.value == TokenScope.USER) {
                tokenRepository.rollUserToken(account, tokenId)
            } else {
                tokenRepository.rollAccountToken(account, tokenId)
            }
            when (result) {
                is Resource.Success -> {
                    _message.emit(UiMessage.of(R.string.token_rolled_success))
                    _tokenRolled.emit(result.data)
                }
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }

    /**
     * 验证当前账号凭据（仅用户级令牌支持 verify 端点）
     */
    fun verifyToken(account: Account) {
        viewModelScope.launch {
            _busy.value = true
            when (val result = tokenRepository.verifyToken(account)) {
                is Resource.Success -> _verifyResult.emit(result.data)
                is Resource.Error -> _message.emit(UiMessage.RawString(result.message))
                is Resource.Loading -> {}
            }
            _busy.value = false
        }
    }
}

sealed class TokenUiState {
    object Loading : TokenUiState()
    object Empty : TokenUiState()
    data class Success(val tokens: List<ApiToken>) : TokenUiState()
    data class Error(val message: UiMessage) : TokenUiState()
}
