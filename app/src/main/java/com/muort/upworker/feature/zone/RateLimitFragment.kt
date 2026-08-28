package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.RateLimitRule
import com.muort.upworker.databinding.DialogRateLimitBinding
import com.muort.upworker.databinding.ItemRateLimitBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 速率限制规则页（phase = http_ratelimit）。
 * 对齐 orange-cloud ZoneRateLimitScreen：
 * - 列表项：橙色徽章（N 次 / N 秒）+ 描述 + 表达式 + 启停 + 删除
 * - 表单：描述 + 表达式 + 请求数 + 周期 + 动作 + 缓解超时 + 启用
 * - characteristics 固定为 [ip.src, cf.colo.id]（按 IP+colo 本地计数）
 */
@AndroidEntryPoint
class RateLimitFragment : BaseZoneFeatureFragment() {

    override val emptyTextResId: Int = R.string.rate_empty_rules
    override val showAddFab: Boolean = true

    private val viewModel: RateLimitViewModel by viewModels()

    private lateinit var adapter: RateLimitAdapter

    // 周期选项（秒 → 显示标签）
    private val periods = listOf(
        10 to 10,  // we'll map with format below (static for unit consistency)
        60 to 60,
        600 to 600,
        3600 to 3600,
    )

    private fun periodLabels(ctx: android.content.Context): List<Pair<Int, String>> = listOf(
        10 to "10 " + ctx.getString(R.string.lbl_seconds_short),
        60 to "1 " + ctx.getString(R.string.lbl_minute_short),
        600 to "10 " + ctx.getString(R.string.lbl_minutes_short),
        3600 to "1 " + ctx.getString(R.string.lbl_hour_short),
    )

    private fun actions(ctx: android.content.Context) = listOf(
        "block" to ctx.getString(R.string.rate_action_block),
        "managed_challenge" to ctx.getString(R.string.rate_action_managed_challenge),
        "js_challenge" to ctx.getString(R.string.rate_action_js_challenge),
        "log" to ctx.getString(R.string.rate_action_log),
    )

    private fun requestsLabel(ctx: android.content.Context, requests: Int, period: Int) =
        ctx.getString(R.string.rate_requests_period_format, requests, period)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RateLimitAdapter(
            onToggle = { rule, enabled ->
                account?.let { viewModel.toggleRule(it, zoneId, rule, enabled) }
            },
            onEdit = { rule -> showRuleDialog(rule) },
            onDelete = { rule -> confirmDelete(rule) },
        )
        binding.recyclerView.adapter = adapter
        observeViewModel()
    }

    override suspend fun onAccountReady(account: Account) {
        viewModel.load(account, zoneId)
    }

    override fun onRetry() {
        account?.let { viewModel.load(it, zoneId) }
    }

    override fun onAddClicked() {
        showRuleDialog(null)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state.isLoading) showLoading() else showList()
                    if (state.error != null) {
                        showError(state.error.asString(requireContext()))
                    } else if (state.rules.isEmpty() && !state.isLoading) {
                        showEmpty()
                    } else {
                        adapter.submitList(state.rules)
                    }
                }
            }
        }
    }

    // ==================== 列表适配器 ====================

    private inner class RateLimitAdapter(
        private val onToggle: (RateLimitRule, Boolean) -> Unit,
        private val onEdit: (RateLimitRule) -> Unit,
        private val onDelete: (RateLimitRule) -> Unit,
    ) : RecyclerView.Adapter<RateLimitAdapter.VH>() {

        private val items = mutableListOf<RateLimitRule>()

        fun submitList(newItems: List<RateLimitRule>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemRateLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class VH(private val b: ItemRateLimitBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rule: RateLimitRule) {
                val ctx = itemView.context
                // 徽章：N 次 / N 秒
                val r = rule.ratelimit
                b.badgeText.text = requestsLabel(ctx, r?.requestsPerPeriod ?: 0, r?.period ?: 0)

                // 描述
                val desc = rule.description?.takeIf { it.isNotBlank() }
                b.descriptionText.text = desc ?: ""
                b.descriptionText.visibility = if (desc != null) View.VISIBLE else View.GONE

                // 表达式
                b.expressionText.text = rule.expression ?: ""
                b.expressionText.visibility = if (rule.expression.isNullOrBlank()) View.GONE else View.VISIBLE

                // 开关
                b.toggleSwitch.isChecked = rule.enabled ?: false
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.setOnCheckedChangeListener { _, checked ->
                    onToggle(rule, checked)
                }

                // 删除
                b.deleteButton.setOnClickListener { onDelete(rule) }

                // 点击编辑
                b.root.setOnClickListener { onEdit(rule) }
            }
        }
    }

    // ==================== 删除确认 ====================

    private fun confirmDelete(rule: RateLimitRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rate_delete_rule_title)
            .setMessage(R.string.rate_delete_rule_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                account?.let { viewModel.deleteRule(it, zoneId, rule) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 添加 / 编辑表单 ====================

    private fun showRuleDialog(editingRule: RateLimitRule?) {
        val ctx = requireContext()
        val isEdit = editingRule != null
        val b = DialogRateLimitBinding.inflate(LayoutInflater.from(ctx))

        val periodPairs = periodLabels(ctx)
        val periodLabels = periodPairs.map { it.second }
        val actionPairs = actions(ctx)
        val actionLabels = actionPairs.map { it.second }

        // 描述
        b.descriptionInput.setText(editingRule?.description ?: "")

        // 表达式
        b.expressionInput.setText(editingRule?.expression ?: "")

        // 请求数
        b.requestsInput.setText(
            (editingRule?.ratelimit?.requestsPerPeriod ?: 100).toString()
        )

        // 周期下拉
        val periodAdapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, periodLabels)
        b.periodInput.setAdapter(periodAdapter)
        val periodSeconds = editingRule?.ratelimit?.period ?: 60
        val periodIdx = periodPairs.indexOfFirst { it.first == periodSeconds }.let { if (it >= 0) it else 1 }
        b.periodInput.setText(periodLabels[periodIdx], false)

        // 动作下拉
        val actionAdapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, actionLabels)
        b.actionInput.setAdapter(actionAdapter)
        val actionValue = editingRule?.action ?: "block"
        val actionIdx = actionPairs.indexOfFirst { it.first == actionValue }.let { if (it >= 0) it else 0 }
        b.actionInput.setText(actionLabels[actionIdx], false)

        // 缓解超时下拉
        val mitigationAdapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, periodLabels)
        b.mitigationInput.setAdapter(mitigationAdapter)
        val mitigationSeconds = editingRule?.ratelimit?.mitigationTimeout ?: 60
        val mitigationIdx = periodPairs.indexOfFirst { it.first == mitigationSeconds }.let { if (it >= 0) it else 1 }
        b.mitigationInput.setText(periodLabels[mitigationIdx], false)

        // 启用开关
        b.enabledSwitch.isChecked = editingRule?.enabled ?: true

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(b.root)
            .setPositiveButton(if (isEdit) R.string.save else R.string.add) { _, _ ->
                val expression = b.expressionInput.text.toString().trim()
                if (expression.isEmpty()) {
                    toast(getString(R.string.msg_expression_empty))
                    return@setPositiveButton
                }
                val requests = b.requestsInput.text.toString().toIntOrNull() ?: 0
                if (requests <= 0) {
                    toast(getString(R.string.msg_requests_must_be_positive))
                    return@setPositiveButton
                }
                val description = b.descriptionInput.text.toString().trim().ifBlank { null }
                val enabled = b.enabledSwitch.isChecked

                val pIdx = periodLabels.indexOf(b.periodInput.text.toString())
                val period = if (pIdx >= 0) periodPairs[pIdx].first else 60

                val aIdx = actionLabels.indexOf(b.actionInput.text.toString())
                val action = if (aIdx >= 0) actionPairs[aIdx].first else "block"

                val mIdx = periodLabels.indexOf(b.mitigationInput.text.toString())
                val mitigation = if (mIdx >= 0) periodPairs[mIdx].first else 60

                account?.let { acct ->
                    viewModel.saveRule(
                        acct, zoneId,
                        ruleId = editingRule?.id,
                        expression = expression,
                        requests = requests,
                        period = period,
                        action = action,
                        mitigationTimeout = mitigation,
                        description = description,
                        enabled = enabled,
                    ) { ok, err ->
                        toast(if (ok) getString(R.string.msg_saved) else getString(R.string.msg_save_failed, err?.asString(requireContext()).orEmpty()))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        // 保存时禁用按钮防止重复提交
        val saveButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                saveButton.isEnabled = !state.isSaving
                saveButton.text = if (state.isSaving) getString(R.string.msg_saving) else if (isEdit) getString(R.string.save) else getString(R.string.add)
            }
        }
    }

    companion object {
        // static companion needed for R.string references; we use instance functions above instead
    }
}
