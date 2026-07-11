package com.muort.upworker.feature.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WafRule
import com.muort.upworker.core.model.WafRuleCreate
import com.muort.upworker.core.model.WafRuleset
import com.muort.upworker.core.repository.ZoneRulesetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * WAF / Cache / RateLimit / Transform 共享 ViewModel。
 * 四者都是 Rulesets entrypoint，phase 由 [bind] 注入。
 * 对齐 orange-cloud WafRulesViewModel：addRule/updateRule/toggleRule 均返回完整 ruleset。
 */
@HiltViewModel
class ZoneRulesetViewModel @Inject constructor(
    private val repository: ZoneRulesetRepository,
) : ViewModel() {

    private var phase: String = ""
    private var rulesetId: String = ""
    private var hasEntrypoint: Boolean = false

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun bind(account: Account, zoneId: String, phase: String) {
        this.phase = phase
        load(account, zoneId)
    }

    fun load(account: Account, zoneId: String) {
        if (phase.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getRuleset(account, zoneId, phase)) {
                is Resource.Success -> {
                    val ruleset: WafRuleset? = result.data
                    rulesetId = ruleset?.id ?: ""
                    hasEntrypoint = ruleset != null
                    _state.update {
                        it.copy(
                            isLoading = false,
                            rules = ruleset?.rules ?: emptyList(),
                        )
                    }
                }
                is Resource.Error -> {
                    Timber.e("load ruleset error: ${result.message}")
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun toggleRule(account: Account, zoneId: String, rule: WafRule, enabled: Boolean) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.setRuleEnabled(account, zoneId, rsId, rule, enabled)) {
                is Resource.Success -> {
                    val ruleset = result.data
                    rulesetId = ruleset.id
                    _state.update { it.copy(rules = ruleset.rules ?: emptyList()) }
                }
                is Resource.Error -> {
                    Timber.e("toggle rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    fun deleteRule(account: Account, zoneId: String, rule: WafRule) {
        val rsId = rulesetId.ifBlank { return }
        viewModelScope.launch {
            when (val result = repository.deleteRule(account, zoneId, rsId, rule.id)) {
                is Resource.Success -> {
                    _state.update { st -> st.copy(rules = st.rules.filter { it.id != rule.id }) }
                }
                is Resource.Error -> {
                    Timber.e("delete rule error: ${result.message}")
                    load(account, zoneId)
                }
                is Resource.Loading -> {}
            }
        }
    }

    /** 新建规则：Zone 没有规则集时先 PUT entrypoint 建集，否则 POST 追加。 */
    fun addRule(account: Account, zoneId: String, rule: WafRuleCreate, onDone: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = if (hasEntrypoint) {
                repository.addRule(account, zoneId, rulesetId, rule)
            } else {
                repository.createEntrypoint(account, zoneId, phase, rule)
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

    /** 编辑既有规则（整条 PATCH：动作 / 表达式 / 名称 / 启用）。 */
    fun updateRule(account: Account, zoneId: String, ruleId: String, rule: WafRuleCreate, onDone: (Boolean, String?) -> Unit) {
        val rsId = rulesetId.ifBlank {
            onDone(false, "规则集未初始化")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = repository.updateRule(account, zoneId, rsId, ruleId, rule)) {
                is Resource.Success -> {
                    val ruleset = result.data
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
        val rules: List<WafRule> = emptyList(),
        val error: String? = null,
    )
}
