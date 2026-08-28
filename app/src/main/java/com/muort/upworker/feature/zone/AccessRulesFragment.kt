package com.muort.upworker.feature.zone

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccessRuleConfigInput
import com.muort.upworker.core.model.AccessRuleCreate
import com.muort.upworker.core.model.AccessRuleUpdate
import com.muort.upworker.core.model.FirewallAccessRule
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.AccessRuleRepository
import com.muort.upworker.databinding.DialogAccessRuleBinding
import com.muort.upworker.databinding.ItemAccessRuleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * IP 访问规则页（legacy firewall access rules）。对齐 orange-cloud ZoneAccessRules：
 * - 列表：动作徽章（颜色区分 封锁/允许/质询）+ 匹配对象标签 + 值（monospace）+ 备注 + 删除
 * - 点击卡片编辑：匹配对象只读，仅可改动作与备注
 * - 新建：匹配类型 Chip 选择 + 值 + 动作 Chip 选择 + 备注
 */
@AndroidEntryPoint
class AccessRulesFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var accessRepo: AccessRuleRepository

    private lateinit var adapter: AccessRuleAdapter
    private var loaded: List<FirewallAccessRule> = emptyList()

    override val emptyTextResId: Int = R.string.ar_empty_rules
    override val showAddFab: Boolean = true

    // MODES / TARGETS / PLACEHOLDERS —— 改为函数形式以避免静态 Context 泄漏
    private fun modes(ctx: android.content.Context) = listOf(
        "block" to ctx.getString(R.string.ar_action_block),
        "managed_challenge" to ctx.getString(R.string.ar_action_managed_challenge),
        "js_challenge" to "JS" + ctx.getString(R.string.ar_action_challenge),
        "challenge" to ctx.getString(R.string.ar_action_challenge),
        "whitelist" to ctx.getString(R.string.ar_action_whitelist),
    )
    private fun targets(ctx: android.content.Context) = listOf(
        "ip" to ctx.getString(R.string.ar_target_ip),
        "ip6" to "IPv6",
        "ip_range" to ctx.getString(R.string.ar_target_ip_range),
        "asn" to ctx.getString(R.string.ar_target_asn),
        "country" to ctx.getString(R.string.ar_target_country),
    )
    private fun modeLabelCtx(ctx: android.content.Context, mode: String?): String = when (mode) {
        "block" -> ctx.getString(R.string.ar_action_block)
        "managed_challenge" -> ctx.getString(R.string.ar_action_managed_challenge)
        "js_challenge" -> "JS" + ctx.getString(R.string.ar_action_challenge)
        "challenge" -> ctx.getString(R.string.ar_action_challenge)
        "whitelist" -> ctx.getString(R.string.ar_action_whitelist)
        else -> mode ?: "—"
    }
    private fun targetLabelCtx(ctx: android.content.Context, target: String?): String = when (target) {
        "ip" -> ctx.getString(R.string.ar_target_ip)
        "ip6" -> "IPv6"
        "ip_range" -> ctx.getString(R.string.ar_target_ip_range)
        "asn" -> ctx.getString(R.string.ar_target_asn)
        "country" -> ctx.getString(R.string.ar_target_country)
        else -> target ?: "—"
    }
    private val TARGET_PLACEHOLDERS = mapOf(
        "ip" to "192.0.2.1",
        "ip6" to "2001:db8::1",
        "ip_range" to "192.0.2.0/24",
        "asn" to "AS13335",
        "country" to "US",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AccessRuleAdapter(
            onEdit = { rule -> showEditor(rule) },
            onDelete = { rule -> confirmDelete(rule) },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() = showEditor(null)

    // ==================== 加载 ====================

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = accessRepo.listRules(account)) {
                is Resource.Success -> {
                    loaded = r.data
                    if (loaded.isEmpty()) showEmpty() else { showList(); adapter.submitList(loaded) }
                }
                is Resource.Error -> showError(r.message)
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 编辑器（新建 / 编辑） ====================

    private fun showEditor(existing: FirewallAccessRule?) {
        val ctx = requireContext()
        val modes = modes(ctx)
        val targets = targets(ctx)
        val b = DialogAccessRuleBinding.inflate(LayoutInflater.from(ctx))
        val isEdit = existing != null

        // 动作 Chip
        for ((raw, label) in modes) {
            val chip = Chip(ctx).apply {
                text = label
                isCheckable = true
                tag = raw
            }
            b.modeChips.addView(chip)
        }
        selectChip(b.modeChips, existing?.mode ?: "block")

        if (isEdit) {
            // 编辑：匹配对象只读
            b.targetChips.visibility = View.GONE
            b.valueLayout.visibility = View.GONE
            b.targetReadonly.visibility = View.VISIBLE
            b.editHint.visibility = View.VISIBLE
            val targetLabel = targetLabelCtx(ctx, existing?.configuration?.target)
            val value = existing?.configuration?.value ?: ""
            b.targetReadonly.text = "$targetLabel · $value"
            b.notesInput.setText(existing?.notes ?: "")
        } else {
            // 新建：匹配类型 Chip + 值输入
            b.targetChips.visibility = View.VISIBLE
            b.valueLayout.visibility = View.VISIBLE
            b.targetReadonly.visibility = View.GONE
            b.editHint.visibility = View.GONE
            for ((raw, label) in targets) {
                val chip = Chip(ctx).apply {
                    text = label
                    isCheckable = true
                    tag = raw
                }
                b.targetChips.addView(chip)
            }
            selectChip(b.targetChips, "ip")
            b.valueLayout.hint = ctx.getString(R.string.ar_target_hint)
            b.targetChips.setOnCheckedStateChangeListener { group, _ ->
                val raw = selectedRaw(group) ?: "ip"
                b.valueLayout.hint = TARGET_PLACEHOLDERS[raw] ?: ctx.getString(R.string.ar_target_hint)
            }
        }

        val title = if (isEdit) R.string.ar_edit_rule_title else R.string.ar_new_rule_title
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(b.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val mode = selectedRaw(b.modeChips) ?: "block"
                val notes = b.notesInput.text.toString().trim().ifBlank { null }
                if (isEdit) {
                    val rule = existing!!
                    account?.let { update(it, rule.id, mode, notes) }
                } else {
                    val target = selectedRaw(b.targetChips) ?: "ip"
                    val value = b.valueInput.text.toString().trim()
                    if (value.isEmpty()) {
                        toast(getString(R.string.msg_target_empty))
                        return@setPositiveButton
                    }
                    account?.let { create(it, target, value, mode, notes) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun create(account: Account, target: String, value: String, mode: String, notes: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rule = AccessRuleCreate(mode, AccessRuleConfigInput(target, value), notes)
            when (val r = accessRepo.createRule(account, rule)) {
                is Resource.Success -> { toast(getString(R.string.msg_added)); load(account) }
                is Resource.Error -> toast(getString(R.string.msg_add_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    private fun update(account: Account, ruleId: String, mode: String, notes: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val update = AccessRuleUpdate(mode, notes)
            when (val r = accessRepo.updateRule(account, ruleId, update)) {
                is Resource.Success -> { toast(getString(R.string.msg_saved)); load(account) }
                is Resource.Error -> toast(getString(R.string.msg_save_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 删除 ====================

    private fun confirmDelete(rule: FirewallAccessRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ar_delete_rule_title)
            .setMessage(rule.configuration?.value ?: rule.id)
            .setPositiveButton(R.string.delete) { _, _ -> account?.let { deleteRule(it, rule.id) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteRule(account: Account, ruleId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = accessRepo.deleteRule(account, ruleId)) {
                is Resource.Success -> { toast(getString(R.string.msg_deleted)); load(account) }
                is Resource.Error -> toast(getString(R.string.msg_delete_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== Chip 辅助 ====================

    private fun selectChip(group: ChipGroup, raw: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as Chip
            if (chip.tag == raw) { chip.isChecked = true; return }
        }
    }

    private fun selectedRaw(group: ChipGroup): String? {
        val id = group.checkedChipId
        if (id == View.NO_ID) return null
        return group.findViewById<Chip>(id).tag as String
    }

    /** @return 文字颜色（全不透明） */
    private fun modeColor(mode: String?): Int = when (mode) {
        "block" -> Color.parseColor("#E5484D")
        "whitelist" -> Color.parseColor("#18A058")
        else -> Color.parseColor("#F57C00")
    }

    // ==================== 适配器 ====================

    private inner class AccessRuleAdapter(
        private val onEdit: (FirewallAccessRule) -> Unit,
        private val onDelete: (FirewallAccessRule) -> Unit,
    ) : RecyclerView.Adapter<AccessRuleAdapter.VH>() {

        private val items = mutableListOf<FirewallAccessRule>()

        fun submitList(newItems: List<FirewallAccessRule>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAccessRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], onEdit, onDelete)
        }

        override fun getItemCount(): Int = items.size

        inner class VH(private val b: ItemAccessRuleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(
                rule: FirewallAccessRule,
                onEdit: (FirewallAccessRule) -> Unit,
                onDelete: (FirewallAccessRule) -> Unit,
            ) {
                val ctx = itemView.context
                val mode = rule.mode
                val color = modeColor(mode)
                b.modeBadge.text = modeLabelCtx(ctx, mode)
                b.modeBadge.setTextColor(color)
                b.modeBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 50f * itemView.resources.displayMetrics.density
                    setColor(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)))
                }

                b.targetLabel.text = targetLabelCtx(ctx, rule.configuration?.target)
                b.valueText.text = rule.configuration?.value ?: "—"

                val notes = rule.notes
                if (notes.isNullOrBlank()) {
                    b.notesText.visibility = View.GONE
                } else {
                    b.notesText.visibility = View.VISIBLE
                    b.notesText.text = notes
                }

                b.contentArea.setOnClickListener { onEdit(rule) }
                b.deleteButton.setOnClickListener { onDelete(rule) }
            }
        }
    }
}
