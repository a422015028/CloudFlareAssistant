package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.EmailDestinationAddress
import com.muort.upworker.core.model.EmailRoutingAction
import com.muort.upworker.core.model.EmailRoutingMatcher
import com.muort.upworker.core.model.EmailRoutingRule
import com.muort.upworker.core.model.EmailRoutingRuleInput
import com.muort.upworker.core.model.EmailRoutingSettings
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.EmailRoutingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 电子邮件路由页：
 * - 顶部展示 Email Routing 开关
 * - 规则列表（启停 / 删除）
 * - 目的地址列表（删除 / 添加）
 * 当前实现把规则作为主要列表，开关 / 目的地址通过对话框操作。
 */
@AndroidEntryPoint
class EmailRoutingFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var emailRepo: EmailRoutingRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var settings: EmailRoutingSettings? = null

    override val emptyText: String = "暂无邮件路由规则"
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onToggle = { _, _, _ ->
                account?.let { /* 规则启停需 PATCH，本版仅展示，提示用户在 Web 控制台操作 */
                    toast("请在 Cloudflare 控制台调整规则")
                }
            },
            onDelete = { _, item ->
                account?.let { deleteRule(it, item.id) }
            },
            onItemClick = { _, item ->
                if (item.id == "HEADER_ENABLED") account?.let { toggleEnabled(it) }
                else if (item.id.startsWith("DEST:")) account?.let { acct ->
                    deleteDest(acct, item.id.removePrefix("DEST:"))
                }
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
            // 并行加载设置 + 规则 + 目的地址
            val settingsRes = emailRepo.getSettings(account, zoneId)
            val rulesRes = emailRepo.listRules(account, zoneId)
            val destsRes = emailRepo.listAddresses(account)

            if (settingsRes is Resource.Error) {
                showError(settingsRes.message); return@launch
            }
            if (rulesRes is Resource.Error) {
                showError(rulesRes.message); return@launch
            }
            settings = (settingsRes as Resource.Success).data
            val rules = (rulesRes as Resource.Success).data
            val dests = (destsRes as? Resource.Success)?.data ?: emptyList()
            renderAll(settings, rules, dests)
        }
    }

    private fun renderAll(
        settings: EmailRoutingSettings?,
        rules: List<EmailRoutingRule>,
        dests: List<EmailDestinationAddress>,
    ) {
        val items = mutableListOf<ZoneRuleItem>()
        // 头部：Email Routing 总开关
        items += ZoneRuleItem(
            id = "HEADER_ENABLED",
            title = "Email Routing 总开关",
            subtitle = "状态：${settings?.status ?: "未知"}",
            meta = if (settings?.isEnabled == true) "已启用（点击关闭）" else "未启用（点击开启）",
            enabled = settings?.isEnabled,
            canDelete = false,
        )
        // 规则
        rules.forEach { rule ->
            items += ZoneRuleItem(
                id = rule.id,
                title = rule.matchAddress ?: "Catch-All",
                subtitle = "转发 → " + (rule.actions.firstOrNull()?.value?.joinToString() ?: ""),
                meta = "优先级 ${rule.priority} · " + if (rule.isEnabled) "启用" else "停用",
                enabled = rule.isEnabled,
                canDelete = true,
            )
        }
        // 目的地址
        if (dests.isNotEmpty()) {
            items += ZoneRuleItem(
                id = "DEST_HEADER",
                title = "—— 目的地址 ——",
                subtitle = null,
                meta = null,
                enabled = null,
                canDelete = false,
            )
            dests.forEach { d ->
                items += ZoneRuleItem(
                    id = "DEST:${d.id}",
                    title = d.email,
                    subtitle = if (d.isVerified) "已验证" else "待验证",
                    meta = "点击删除",
                    enabled = null,
                    canDelete = true,
                )
            }
        }
        if (items.isEmpty()) showEmpty() else { showList(); adapter.submitList(items) }
    }

    private fun toggleEnabled(account: Account) {
        val current = settings?.isEnabled == true
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = emailRepo.setEnabled(account, zoneId, !current)) {
                is Resource.Success -> { toast(if (!current) "已启用" else "已关闭"); load(account) }
                is Resource.Error -> toast("操作失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun deleteRule(account: Account, ruleId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (emailRepo.deleteRule(account, zoneId, ruleId)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败")
                is Resource.Loading -> {}
            }
        }
    }

    private fun deleteDest(account: Account, addressId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (emailRepo.deleteAddress(account, addressId)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败")
                is Resource.Loading -> {}
            }
        }
    }

    private fun showAddDialog() {
        val matchEdit = EditText(requireContext()).apply {
            hint = "匹配地址（如 alice@${zoneName.ifBlank { "example.com" }}）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val destEdit = EditText(requireContext()).apply {
            hint = "转发到（外部邮箱）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(matchEdit); addView(destEdit)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加邮件路由规则")
            .setView(container)
            .setPositiveButton("添加") { _, _ ->
                val match = matchEdit.text.toString().trim()
                val dest = destEdit.text.toString().trim()
                if (match.isEmpty() || dest.isEmpty()) {
                    toast("匹配地址和转发地址不能为空"); return@setPositiveButton
                }
                account?.let { createRule(it, match, dest) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createRule(account: Account, match: String, dest: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val input = EmailRoutingRuleInput(
                name = match,
                enabled = true,
                matchers = listOf(EmailRoutingMatcher(type = "literal", field = "to", value = match)),
                actions = listOf(EmailRoutingAction(type = "forward", value = listOf(dest))),
            )
            when (emailRepo.createRule(account, zoneId, input)) {
                is Resource.Success -> { toast("已添加"); load(account) }
                is Resource.Error -> toast("添加失败")
                is Resource.Loading -> {}
            }
        }
    }
}
