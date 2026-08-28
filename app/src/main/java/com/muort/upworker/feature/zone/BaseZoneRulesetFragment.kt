package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.WafRule
import com.muort.upworker.core.model.WafRuleCreate
import com.muort.upworker.databinding.FragmentZoneFeatureBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * WAF / Cache / RateLimit / Transform 四个 Ruleset 功能页的共享基类。
 * 子类只需提供 [phase] 与 [addDialogTitleResId]。
 */
@AndroidEntryPoint
abstract class BaseZoneRulesetFragment : BaseZoneFeatureFragment() {

    protected abstract val phase: String
    protected open val addDialogTitleResId: Int = R.string.add

    private lateinit var rulesetAdapter: ZoneRuleAdapter

    override val emptyTextResId: Int = R.string.empty_list
    override val showAddFab: Boolean = true

    /** 供子类访问 ViewModel。 */
    protected val rulesetViewModel: ZoneRulesetViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rulesetAdapter = createAdapter()
        binding.recyclerView.adapter = rulesetAdapter
        observeViewModel()
    }

    /** 子类可重写以自定义适配器行为（如点击编辑）。 */
    protected open fun createAdapter(): ZoneRuleAdapter = ZoneRuleAdapter(
        onToggle = { position, _, enabled ->
            val rule = rulesetViewModel.state.value.rules.getOrNull(position) ?: return@ZoneRuleAdapter
            account?.let { rulesetViewModel.toggleRule(it, zoneId, rule, enabled) }
        },
        onDelete = { position, _ ->
            val rule = rulesetViewModel.state.value.rules.getOrNull(position) ?: return@ZoneRuleAdapter
            onRuleDeleteRequested(rule)
        },
        onItemClick = { position, _ ->
            val rule = rulesetViewModel.state.value.rules.getOrNull(position) ?: return@ZoneRuleAdapter
            onRuleClicked(rule)
        },
    )

    /** 规则被点击：子类可重写以打开编辑表单。 */
    protected open fun onRuleClicked(rule: WafRule) {}

    /** 删除按钮被点击：子类可重写以显示确认对话框。 */
    protected open fun onRuleDeleteRequested(rule: WafRule) {
        account?.let { rulesetViewModel.deleteRule(it, zoneId, rule) }
    }

    override suspend fun onAccountReady(account: Account) {
        rulesetViewModel.bind(account, zoneId, phase)
    }

    override fun onRetry() {
        account?.let { rulesetViewModel.load(it, zoneId) }
    }

    override fun onAddClicked() {
        showAddRuleDialog()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rulesetViewModel.state.collect { state ->
                    if (state.isLoading) showLoading() else showList()
                    if (state.error != null) {
                        showError(state.error.asString(requireContext()))
                    } else if (state.rules.isEmpty() && !state.isLoading) {
                        showEmpty()
                    } else {
                        rulesetAdapter.submitList(state.rules.map { it.toZoneRuleItem() })
                    }
                }
            }
        }
    }

    /** 将 WafRule 映射为列表项展示数据，子类可重写以自定义展示。 */
    protected open fun WafRule.toZoneRuleItem(): ZoneRuleItem {
        val title = description?.takeIf { it.isNotBlank() } ?: expression?.take(50) ?: id
        val actionLabel = action ?: ""
        val exprPreview = expression?.take(120) ?: ""
        return ZoneRuleItem(
            id = id,
            title = title,
            subtitle = exprPreview,
            meta = requireContext().getString(R.string.waf_action_label, actionLabel),
            enabled = enabled,
            canDelete = true,
        )
    }

    /** 弹出添加规则对话框，子类可重写以自定义表单。 */
    protected open fun showAddRuleDialog() {
        val ctx = requireContext()
        val editText = EditText(ctx).apply {
            hint = ctx.getString(R.string.waf_expression_edit_hint)
            setSingleLine(false)
            setPadding(48, 32, 48, 32)
        }
        val descEditText = EditText(ctx).apply {
            hint = ctx.getString(R.string.waf_desc_hint)
            setSingleLine(false)
            setPadding(48, 32, 48, 32)
        }
        val actionAutoComplete = AutoCompleteTextView(ctx).apply {
            hint = ctx.getString(R.string.waf_action_hint)
            val actions = listOf("block", "challenge", "managed_challenge", "js_challenge", "log", "skip")
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, actions))
            setText("block", false)
            setPadding(48, 32, 48, 32)
        }
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(descEditText)
            addView(actionAutoComplete)
            addView(editText)
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(addDialogTitleResId)
            .setView(container)
            .setPositiveButton(R.string.add) { _, _ ->
                val expr = editText.text.toString().trim()
                val desc = descEditText.text.toString().trim().ifBlank { null }
                val action = actionAutoComplete.text.toString().trim()
                if (expr.isEmpty()) {
                    toast(getString(R.string.msg_expression_empty))
                    return@setPositiveButton
                }
                account?.let {
                    rulesetViewModel.addRule(it, zoneId, WafRuleCreate(action, expr, desc, true)) { ok, err ->
                        toast(if (ok) getString(R.string.msg_added) else getString(R.string.msg_add_failed, err?.asString(requireContext()).orEmpty()))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
