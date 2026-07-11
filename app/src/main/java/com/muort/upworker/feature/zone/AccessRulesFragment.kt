package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccessRuleConfigInput
import com.muort.upworker.core.model.AccessRuleCreate
import com.muort.upworker.core.model.FirewallAccessRule
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.AccessRuleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * IP 访问规则页（legacy firewall access rules）：
 * 列表 / 删除 / 添加（IP / IP 段 / 国家 / ASN，配合 block/challenge/whitelist/js_challenge）。
 */
@AndroidEntryPoint
class AccessRulesFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var accessRepo: AccessRuleRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<FirewallAccessRule> = emptyList()

    override val emptyText: String = "暂无 IP 访问规则"
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onDelete = { _, item ->
                account?.let { deleteRule(it, item.id) }
            },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() = showAddDialog()

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = accessRepo.listRules(account, zoneId)) {
                is Resource.Success -> {
                    loaded = r.data
                    val items = r.data.map { it.toZoneRuleItem() }
                    if (items.isEmpty()) showEmpty() else { showList(); adapter.submitList(items) }
                }
                is Resource.Error -> showError(r.message)
                is Resource.Loading -> {}
            }
        }
    }

    private fun deleteRule(account: Account, ruleId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = accessRepo.deleteRule(account, zoneId, ruleId)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun showAddDialog() {
        val targetEdit = EditText(requireContext()).apply {
            hint = "目标（如 1.2.3.4 / 1.2.3.0/24 / CN / AS12345）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val targetAuto = AutoCompleteTextView(requireContext()).apply {
            hint = "目标类型"
            val types = listOf("ip", "ip_range", "country", "asn")
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, types))
            setText("ip", false)
            setPadding(48, 32, 48, 32)
        }
        val modeAuto = AutoCompleteTextView(requireContext()).apply {
            hint = "动作"
            val modes = listOf("block", "challenge", "whitelist", "js_challenge", "managed_challenge")
            setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, modes))
            setText("block", false)
            setPadding(48, 32, 48, 32)
        }
        val notesEdit = EditText(requireContext()).apply {
            hint = "备注（可选）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(targetAuto); addView(targetEdit); addView(modeAuto); addView(notesEdit)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加 IP 访问规则")
            .setView(container)
            .setPositiveButton("添加") { _, _ ->
                val target = targetEdit.text.toString().trim()
                val type = targetAuto.text.toString().trim()
                val mode = modeAuto.text.toString().trim()
                val notes = notesEdit.text.toString().trim().ifBlank { null }
                if (target.isEmpty()) { toast("目标不能为空"); return@setPositiveButton }
                account?.let { create(it, type, target, mode, notes) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun create(account: Account, type: String, target: String, mode: String, notes: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val rule = AccessRuleCreate(mode, AccessRuleConfigInput(type, target), notes)
            when (val r = accessRepo.createRule(account, zoneId, rule)) {
                is Resource.Success -> { toast("添加成功"); load(account) }
                is Resource.Error -> toast("添加失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun FirewallAccessRule.toZoneRuleItem(): ZoneRuleItem = ZoneRuleItem(
        id = id,
        title = configuration?.value ?: id,
        subtitle = (configuration?.target ?: "") + " · " + (mode ?: ""),
        meta = notes?.takeIf { it.isNotBlank() },
        enabled = null,
        canDelete = true,
    )
}
