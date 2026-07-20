package com.muort.upworker.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountInfo
import com.muort.upworker.core.model.ApiToken
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.ScopeCategory
import com.muort.upworker.core.model.ZoneInfo
import com.muort.upworker.core.repository.ApiTokenRepository
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

@HiltViewModel
class ApiTokenViewModel @Inject constructor(
    private val apiTokenRepository: ApiTokenRepository
) : ViewModel() {

    /** 用于创建/校验的凭据账号（即左侧填写的 Token 对应的临时账号） */
    private val _creatorAccount = MutableStateFlow<Account?>(null)
    val creatorAccount: StateFlow<Account?> = _creatorAccount.asStateFlow()

    private val _permissionGroups = MutableStateFlow<List<PermissionGroup>>(emptyList())
    val permissionGroups: StateFlow<List<PermissionGroup>> = _permissionGroups.asStateFlow()

    private val _permAccounts = MutableStateFlow<List<AccountInfo>>(emptyList())
    val permAccounts: StateFlow<List<AccountInfo>> = _permAccounts.asStateFlow()

    private val _permZones = MutableStateFlow<List<ZoneInfo>>(emptyList())
    val permZones: StateFlow<List<ZoneInfo>> = _permZones.asStateFlow()

    private val _currentTokenDetail = MutableStateFlow<ApiToken?>(null)
    val currentTokenDetail: StateFlow<ApiToken?> = _currentTokenDetail.asStateFlow()

    private val _createdToken = MutableStateFlow<ApiToken?>(null)
    val createdToken: StateFlow<ApiToken?> = _createdToken.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun setCreatorAccount(account: Account) {
        _creatorAccount.value = account
    }

    /**
     * 加载权限管理所需数据：权限组、账号、域名，并尝试校验+获取当前 Token 详情
     */
    fun loadPermissionData(account: Account) {
        _creatorAccount.value = account
        viewModelScope.launch {
            _loading.value = true

            // 并行加载权限组、账号、域名
            val groupsJob = launch {
                when (val r = apiTokenRepository.getPermissionGroups(account)) {
                    is Resource.Success -> _permissionGroups.value = r.data
                    is Resource.Error -> _message.emit("权限组加载失败: ${r.message}")
                    is Resource.Loading -> {}
                }
            }
            val accountsJob = launch {
                when (val r = apiTokenRepository.listAccounts(account)) {
                    is Resource.Success -> _permAccounts.value = r.data
                    is Resource.Error -> Timber.w("perm accounts failed: ${r.message}")
                    is Resource.Loading -> {}
                }
            }
            val zonesJob = launch {
                when (val r = apiTokenRepository.listZones(account)) {
                    is Resource.Success -> _permZones.value = r.data
                    is Resource.Error -> Timber.w("perm zones failed: ${r.message}")
                    is Resource.Loading -> {}
                }
            }

            groupsJob.join(); accountsJob.join(); zonesJob.join()

            // 校验当前 Token 并获取详情（await，使 loading 覆盖整个加载过程）
            loadCurrentTokenDetailImpl(account)

            _loading.value = false
        }
    }

    /** 校验当前 Token 并获取其完整权限详情（供刷新按钮调用，异步） */
    fun loadCurrentTokenDetail(account: Account) {
        viewModelScope.launch {
            _loading.value = true
            loadCurrentTokenDetailImpl(account)
            _loading.value = false
        }
    }

    private suspend fun loadCurrentTokenDetailImpl(account: Account) {
        when (val v = apiTokenRepository.verifyToken(account)) {
            is Resource.Success -> {
                val id = v.data.id
                if (!id.isNullOrBlank()) {
                    when (val detail = apiTokenRepository.getTokenDetail(account, id)) {
                        is Resource.Success -> _currentTokenDetail.value = detail.data
                        is Resource.Error -> {
                            // 部分权限下 verify 成功但无法读取详情
                            _currentTokenDetail.value = v.data
                            _message.emit("无法读取 Token 详情: ${detail.message}")
                        }
                        is Resource.Loading -> {}
                    }
                } else {
                    _currentTokenDetail.value = v.data
                }
            }
            is Resource.Error -> {
                _currentTokenDetail.value = null
                _message.emit("Token 校验失败: ${v.message}")
            }
            is Resource.Loading -> {}
        }
    }

    /**
     * 创建自定义权限 Token
     */
    fun createApiToken(
        account: Account,
        name: String,
        selectedGroups: Map<ScopeCategory, List<PermissionGroup>>,
        accountScope: ApiTokenRepository.AccountResourceScope,
        zoneScope: ApiTokenRepository.ZoneResourceScope,
        expiresOn: String?
    ) {
        viewModelScope.launch {
            _loading.value = true
            when (val r = apiTokenRepository.createApiToken(
                account, name, selectedGroups, accountScope, zoneScope, expiresOn
            )) {
                is Resource.Success -> {
                    _createdToken.value = r.data
                    _message.emit("Token 创建成功")
                }
                is Resource.Error -> _message.emit("创建失败: ${r.message}")
                is Resource.Loading -> {}
            }
            _loading.value = false
        }
    }

    fun clearCreatedToken() {
        _createdToken.value = null
    }
}
