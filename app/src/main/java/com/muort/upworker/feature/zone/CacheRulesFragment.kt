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
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CacheActionParameters
import com.muort.upworker.core.model.CacheBrowserTTL
import com.muort.upworker.core.model.CacheEdgeTTL
import com.muort.upworker.core.model.CacheRule
import com.muort.upworker.databinding.DialogCacheRuleBinding
import com.muort.upworker.databinding.ItemCacheRuleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 缓存规则页（phase = http_request_cache_settings, action = set_cache_settings）。
 * 对齐 orange-cloud ZoneCacheRulesScreen：
 * - 列表项：橙色徽章（绕过缓存/可缓存/默认缓存设置）+ 描述 + 表达式 + 高级设置只读提示 + 启停 + 删除
 * - 表单：描述 + 表达式 + 可缓存开关 + 边缘/浏览器 TTL 模式与秒数 + 启用开关
 * - 含高级设置的规则不可编辑（避免 PATCH 覆盖丢配置）
 */
@AndroidEntryPoint
class CacheRulesFragment : BaseZoneFeatureFragment() {

    override val emptyText: String = "暂无缓存规则"
    override val showAddFab: Boolean = true

    private val viewModel: CacheRulesViewModel by viewModels()

    private lateinit var adapter: CacheRuleAdapter

    // TTL 模式
    private val ttlModes = listOf(
        "respect_origin" to "遵循源站",
        "override_origin" to "覆盖为固定值",
        "bypass_by_default" to "源站无指令则不缓存",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CacheRuleAdapter(
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
                        showError(state.error)
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

    private inner class CacheRuleAdapter(
        private val onToggle: (CacheRule, Boolean) -> Unit,
        private val onEdit: (CacheRule) -> Unit,
        private val onDelete: (CacheRule) -> Unit,
    ) : RecyclerView.Adapter<CacheRuleAdapter.VH>() {

        private val items = mutableListOf<CacheRule>()

        fun submitList(newItems: List<CacheRule>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemCacheRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class VH(private val b: ItemCacheRuleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rule: CacheRule) {
                // 徽章
                b.badgeText.text = cacheSummary(rule)

                // 描述
                val desc = rule.description?.takeIf { it.isNotBlank() }
                b.descriptionText.text = desc ?: ""
                b.descriptionText.visibility = if (desc != null) View.VISIBLE else View.GONE

                // 表达式
                b.expressionText.text = rule.expression ?: ""
                b.expressionText.visibility = if (rule.expression.isNullOrBlank()) View.GONE else View.VISIBLE

                // 高级设置只读提示
                val hasAdvanced = rule.actionParameters?.hasAdvancedSettings == true
                b.advancedReadonlyText.visibility = if (hasAdvanced) View.VISIBLE else View.GONE

                // 开关
                b.toggleSwitch.isChecked = rule.enabled ?: false
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.setOnCheckedChangeListener { _, checked ->
                    onToggle(rule, checked)
                }

                // 删除
                b.deleteButton.setOnClickListener { onDelete(rule) }

                // 点击编辑（含高级设置的规则不可编辑）
                b.root.setOnClickListener {
                    if (hasAdvanced) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage("该规则含高级设置（自定义缓存键、Cache Reserve 等），无法在此编辑。请到 Cloudflare 控制台修改。")
                            .setPositiveButton("确定", null)
                            .show()
                    } else {
                        onEdit(rule)
                    }
                }
            }
        }
    }

    /** 缓存摘要徽章文本。 */
    private fun cacheSummary(rule: CacheRule): String {
        val p = rule.actionParameters ?: return "默认缓存设置"
        if (p.cache == false) return "绕过缓存"
        return "可缓存"
    }

    // ==================== 删除确认 ====================

    private fun confirmDelete(rule: CacheRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除此缓存规则？")
            .setMessage("删除后将不再按此规则缓存，操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                account?.let { viewModel.deleteRule(it, zoneId, rule) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 添加 / 编辑表单 ====================

    private fun showRuleDialog(editingRule: CacheRule?) {
        val isEdit = editingRule != null
        val b = DialogCacheRuleBinding.inflate(LayoutInflater.from(requireContext()))

        // 描述
        b.descriptionInput.setText(editingRule?.description ?: "")

        // 表达式
        b.expressionInput.setText(editingRule?.expression ?: "")

        // 可缓存开关
        val eligible = editingRule?.actionParameters?.cache != false
        b.eligibleSwitch.isChecked = eligible

        // TTL 模式下拉
        val ttlLabels = ttlModes.map { it.second }
        val edgeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ttlLabels)
        val browserAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ttlLabels)
        b.edgeTtlModeInput.setAdapter(edgeAdapter)
        b.browserTtlModeInput.setAdapter(browserAdapter)

        val edgeMode = editingRule?.actionParameters?.edgeTtl?.mode ?: "respect_origin"
        val browserMode = editingRule?.actionParameters?.browserTtl?.mode ?: "respect_origin"
        val edgeModeIndex = ttlModes.indexOfFirst { it.first == edgeMode }.let { if (it >= 0) it else 0 }
        val browserModeIndex = ttlModes.indexOfFirst { it.first == browserMode }.let { if (it >= 0) it else 0 }
        b.edgeTtlModeInput.setText(ttlLabels[edgeModeIndex], false)
        b.browserTtlModeInput.setText(ttlLabels[browserModeIndex], false)

        // TTL 秒数
        b.edgeTtlSecondsInput.setText(editingRule?.actionParameters?.edgeTtl?.defaultTtl?.toString() ?: "")
        b.browserTtlSecondsInput.setText(editingRule?.actionParameters?.browserTtl?.defaultTtl?.toString() ?: "")

        // 启用开关
        b.enabledSwitch.isChecked = editingRule?.enabled ?: true

        // 可缓存开关 → TTL 区域显隐
        fun updateTtlVisibility() {
            val isEligible = b.eligibleSwitch.isChecked
            b.ttlSettingsLayout.visibility = if (isEligible) View.VISIBLE else View.GONE
        }
        updateTtlVisibility()
        b.eligibleSwitch.setOnCheckedChangeListener { _, _ -> updateTtlVisibility() }

        // TTL 模式 → 秒数输入框显隐
        fun updateSecondsVisibility() {
            val edgeIdx = ttlLabels.indexOf(b.edgeTtlModeInput.text.toString())
            val browserIdx = ttlLabels.indexOf(b.browserTtlModeInput.text.toString())
            b.edgeTtlSecondsLayout.visibility =
                if (edgeIdx >= 0 && ttlModes[edgeIdx].first == "override_origin") View.VISIBLE else View.GONE
            b.browserTtlSecondsLayout.visibility =
                if (browserIdx >= 0 && ttlModes[browserIdx].first == "override_origin") View.VISIBLE else View.GONE
        }
        updateSecondsVisibility()
        b.edgeTtlModeInput.setOnItemClickListener { _, _, _, _ -> updateSecondsVisibility() }
        b.browserTtlModeInput.setOnItemClickListener { _, _, _, _ -> updateSecondsVisibility() }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setPositiveButton(if (isEdit) "保存" else "添加") { _, _ ->
                val expression = b.expressionInput.text.toString().trim()
                if (expression.isEmpty()) {
                    toast("表达式不能为空")
                    return@setPositiveButton
                }
                val description = b.descriptionInput.text.toString().trim().ifBlank { null }
                val enabled = b.enabledSwitch.isChecked
                val isEligible = b.eligibleSwitch.isChecked

                val params = if (!isEligible) {
                    CacheActionParameters(cache = false)
                } else {
                    val edgeIdx = ttlLabels.indexOf(b.edgeTtlModeInput.text.toString())
                    val browserIdx = ttlLabels.indexOf(b.browserTtlModeInput.text.toString())
                    val eMode = if (edgeIdx >= 0) ttlModes[edgeIdx].first else "respect_origin"
                    val bMode = if (browserIdx >= 0) ttlModes[browserIdx].first else "respect_origin"
                    CacheActionParameters(
                        cache = true,
                        edgeTtl = CacheEdgeTTL(
                            mode = eMode,
                            defaultTtl = b.edgeTtlSecondsInput.text.toString().toIntOrNull()
                                ?.takeIf { eMode == "override_origin" },
                        ),
                        browserTtl = CacheBrowserTTL(
                            mode = bMode,
                            defaultTtl = b.browserTtlSecondsInput.text.toString().toIntOrNull()
                                ?.takeIf { bMode == "override_origin" },
                        ),
                    )
                }

                account?.let { acct ->
                    viewModel.saveRule(
                        acct, zoneId,
                        ruleId = editingRule?.id,
                        expression = expression,
                        description = description,
                        enabled = enabled,
                        params = params,
                    ) { ok, err ->
                        toast(if (ok) "已保存" else "保存失败: $err")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 保存时禁用按钮防止重复提交
        val saveButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                saveButton.isEnabled = !state.isSaving
                saveButton.text = if (state.isSaving) "保存中..." else if (isEdit) "保存" else "添加"
            }
        }
    }
}
