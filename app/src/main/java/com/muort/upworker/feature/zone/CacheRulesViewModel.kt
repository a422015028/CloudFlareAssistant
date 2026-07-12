package com.muort.upworker.feature.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CacheActionParameters
import com.muort.upworker.core.model.CacheBrowserTTL
import com.muort.upworker.core.model.CacheEdgeTTL
import com.muort.upworker.core.model.CacheRule
import com.muort.upworker.core.model.CacheRuleCreate
import com.muort.upworker.core.model.CacheRuleset
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.ZoneRulesetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 缓存规则 ViewModel（phase = http_request_cache_settings, action = set_cache_settings）。
 * 对齐 orange-cloud ZoneCacheRulesViewModel，使用 CacheRule 模型保留 action_parameters。
 */
@HiltViewModel
class CacheRulesViewModel @Inject constructor(
    private val repository: ZoneRulesetRepository,
) : ViewModel() {

    private var rulesetId: String = ""
    private var hasEntrypoint: Boolean = false

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun load(account: Account, zoneId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getCacheRuleset(account, zoneId)) {
                is Resource.Success -> {
                    val ruleset: CacheRuleset? = result.data
                    rulesetId = ruleset?.id ?: ""
                    hasEntrypoint = ruleset != null
                    _state.update {
                        it.copy(isLoading = false, rules = ruleset?.rules ?: emptyList())
                    }
                }
                is Resource.Error -> {
                    Timber.e("load cache ruleset error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleRule(account: Account, zoneId: String, rule: CacheRule, enabled: Boolean) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.setCacheRuleEnabled(account, zoneId, rsId, rule, enabled)) {
                is Resource.Success -> {
                    val ruleset = result.data
                    rulesetId = ruleset.id
                    _state.update { it.copy(rules = ruleset.rules ?: emptyList()) }
                }
                is Resource.Error -> {
                    Timber.e("toggle cache rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteRule(account: Account, zoneId: String, rule: CacheRule) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.deleteCacheRule(account, zoneId, rsId, rule.id)) {
                is Resource.Success -> {
                    _state.update { st -> st.copy(rules = st.rules.filter { it.id != rule.id }) }
                }
                is Resource.Error -> {
                    Timber.e("delete cache rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** 新建或更新缓存规则。ruleId == null 表示新建。 */
    fun saveRule(
        account: Account, zoneId: String,
        ruleId: String?,
        expression: String,
        description: String?,
        enabled: Boolean,
        params: CacheActionParameters,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val draft = CacheRuleCreate(
                action = "set_cache_settings",
                expression = expression,
                description = description?.takeIf { it.isNotBlank() },
                enabled = enabled,
                actionParameters = params,
            )
            val result = when {
                ruleId != null && hasEntrypoint ->
                    repository.updateCacheRule(account, zoneId, rulesetId, ruleId, draft)
                hasEntrypoint ->
                    repository.addCacheRule(account, zoneId, rulesetId, draft)
                else ->
                    repository.createCacheEntrypoint(account, zoneId, draft)
            }
            when (result) {
                is Resource.Success -> {
                    val ruleset = result.data
                    hasEntrypoint = true
                    rulesetId = ruleset.id
                    _state.update { it.copy(isSaving = false, rules = ruleset.rules ?: emptyList()) }
                    onDone(true, null)
                }
                is Resource.Error -> {
                    _state.update { it.copy(isSaving = false) }
                    onDone(false, result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    data class State(
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val rules: List<CacheRule> = emptyList(),
        val error: String? = null,
    )
}
