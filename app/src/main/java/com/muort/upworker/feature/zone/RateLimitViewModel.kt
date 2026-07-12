package com.muort.upworker.feature.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.RateLimitConfigInput
import com.muort.upworker.core.model.RateLimitRule
import com.muort.upworker.core.model.RateLimitRuleCreate
import com.muort.upworker.core.model.RateLimitRuleset
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
 * 速率限制 ViewModel（phase = http_ratelimit）。
 * 对齐 orange-cloud ZoneRateLimitViewModel，使用 RateLimitRule 模型保留 ratelimit 配置。
 */
@HiltViewModel
class RateLimitViewModel @Inject constructor(
    private val repository: ZoneRulesetRepository,
) : ViewModel() {

    private var rulesetId: String = ""
    private var hasEntrypoint: Boolean = false

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun load(account: Account, zoneId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getRateLimitRuleset(account, zoneId)) {
                is Resource.Success -> {
                    val ruleset: RateLimitRuleset? = result.data
                    rulesetId = ruleset?.id ?: ""
                    hasEntrypoint = ruleset != null
                    _state.update {
                        it.copy(isLoading = false, rules = ruleset?.rules ?: emptyList())
                    }
                }
                is Resource.Error -> {
                    Timber.e("load rate limit ruleset error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleRule(account: Account, zoneId: String, rule: RateLimitRule, enabled: Boolean) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.setRateLimitRuleEnabled(account, zoneId, rsId, rule, enabled)) {
                is Resource.Success -> {
                    val ruleset = result.data
                    rulesetId = ruleset.id
                    _state.update { it.copy(rules = ruleset.rules ?: emptyList()) }
                }
                is Resource.Error -> {
                    Timber.e("toggle rate limit rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteRule(account: Account, zoneId: String, rule: RateLimitRule) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.deleteRateLimitRule(account, zoneId, rsId, rule.id)) {
                is Resource.Success -> {
                    _state.update { st -> st.copy(rules = st.rules.filter { it.id != rule.id }) }
                }
                is Resource.Error -> {
                    Timber.e("delete rate limit rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** 新建或更新速率限制规则。ruleId == null 表示新建。 */
    fun saveRule(
        account: Account, zoneId: String,
        ruleId: String?,
        expression: String,
        requests: Int,
        period: Int,
        action: String,
        mitigationTimeout: Int,
        description: String?,
        enabled: Boolean,
        onDone: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val draft = RateLimitRuleCreate(
                action = action,
                expression = expression,
                description = description?.takeIf { it.isNotBlank() },
                enabled = enabled,
                ratelimit = RateLimitConfigInput(
                    characteristics = listOf("ip.src", "cf.colo.id"),
                    period = period,
                    requestsPerPeriod = requests,
                    mitigationTimeout = mitigationTimeout,
                ),
            )
            val result = when {
                ruleId != null && hasEntrypoint ->
                    repository.updateRateLimitRule(account, zoneId, rulesetId, ruleId, draft)
                hasEntrypoint ->
                    repository.addRateLimitRule(account, zoneId, rulesetId, draft)
                else ->
                    repository.createRateLimitEntrypoint(account, zoneId, draft)
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
        val rules: List<RateLimitRule> = emptyList(),
        val error: String? = null,
    )
}
