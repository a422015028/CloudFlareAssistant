package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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
import com.muort.upworker.databinding.DialogEmailAddAddressBinding
import com.muort.upworker.databinding.DialogEmailAddRuleBinding
import com.muort.upworker.databinding.ItemEmailAddressBinding
import com.muort.upworker.databinding.ItemEmailRuleBinding
import com.muort.upworker.databinding.ItemEmailSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 电子邮件路由页。对齐 orange-cloud EmailRoutingScreen：
 * - 顶部：Email Routing 总开关卡片（开关 + 状态）
 * - 路由规则区：区头 + 规则列表 + 添加按钮
 * - 目的地址区：区头 + 地址列表 + 添加按钮
 * - 规则：名称（或匹配地址 / 全部邮件）+ "匹配地址 → 目标" + 删除
 * - 地址：邮箱图标 + 邮箱 + 验证状态 + 删除
 */
@AndroidEntryPoint
class EmailRoutingFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var emailRepo: EmailRoutingRepository

    private lateinit var adapter: EmailRoutingAdapter

    private var settings: EmailRoutingSettings? = null
    private var rules: List<EmailRoutingRule> = emptyList()
    private var addresses: List<EmailDestinationAddress> = emptyList()

    override val emptyText: String = ""
    override val showAddFab: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = EmailRoutingAdapter(
            onToggleEnabled = { toggleEnabled() },
            onAddRule = { showAddRuleDialog() },
            onAddAddress = { showAddAddressDialog() },
            onDeleteRule = { rule -> confirmDeleteRule(rule) },
            onDeleteAddress = { addr -> confirmDeleteAddress(addr) },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() = showAddRuleDialog()

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
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
            rules = (rulesRes as Resource.Success).data
            addresses = (destsRes as? Resource.Success)?.data ?: emptyList()
            renderAll()
        }
    }

    private fun renderAll() {
        showList()
        adapter.update(settings, rules, addresses)
    }

    // ==================== 总开关 ====================

    private fun toggleEnabled() {
        val account = account ?: return
        val current = settings?.isEnabled == true
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = emailRepo.setEnabled(account, zoneId, !current)) {
                is Resource.Success -> { toast(if (!current) "已启用" else "已关闭"); load(account) }
                is Resource.Error -> toast("操作失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 添加规则 ====================

    private fun showAddRuleDialog() {
        val b = DialogEmailAddRuleBinding.inflate(LayoutInflater.from(requireContext()))
        val verifiedEmails = addresses.filter { it.isVerified }.map { it.email }
        if (verifiedEmails.isNotEmpty()) {
            b.destinationHint.text = "已验证的目的地址：${verifiedEmails.joinToString(", ")}"
            b.destinationHint.visibility = View.VISIBLE
        }
        if (zoneName.isNotBlank()) {
            b.matchInputLayout.hint = "匹配地址（如 alice@$zoneName）"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setPositiveButton("添加") { _, _ ->
                val match = b.matchInput.text.toString().trim()
                val dest = b.destinationInput.text.toString().trim()
                val name = b.nameInput.text.toString().trim().ifBlank { null }
                if (match.isEmpty() || dest.isEmpty()) {
                    toast("匹配地址和转发地址不能为空"); return@setPositiveButton
                }
                account?.let { createRule(it, match, dest, name) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createRule(account: Account, match: String, dest: String, name: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val input = EmailRoutingRuleInput(
                name = name,
                enabled = true,
                matchers = listOf(EmailRoutingMatcher(type = "literal", field = "to", value = match)),
                actions = listOf(EmailRoutingAction(type = "forward", value = listOf(dest))),
            )
            when (val r = emailRepo.createRule(account, zoneId, input)) {
                is Resource.Success -> { toast("已添加"); load(account) }
                is Resource.Error -> toast("添加失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 添加目的地址 ====================

    private fun showAddAddressDialog() {
        val b = DialogEmailAddAddressBinding.inflate(LayoutInflater.from(requireContext()))

        MaterialAlertDialogBuilder(requireContext())
            .setView(b.root)
            .setPositiveButton("添加") { _, _ ->
                val email = b.emailInput.text.toString().trim()
                if (email.isEmpty()) {
                    toast("邮箱不能为空"); return@setPositiveButton
                }
                account?.let { addAddress(it, email) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addAddress(account: Account, email: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = emailRepo.createAddress(account, email)) {
                is Resource.Success -> { toast("已添加，请查收验证邮件"); load(account) }
                is Resource.Error -> toast("添加失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 删除 ====================

    private fun confirmDeleteRule(rule: EmailRoutingRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除此路由规则？")
            .setMessage("删除后邮件将不再按此规则转发。")
            .setPositiveButton("删除") { _, _ ->
                account?.let { acct ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        when (val r = emailRepo.deleteRule(acct, zoneId, rule.id)) {
                            is Resource.Success -> { toast("已删除"); load(acct) }
                            is Resource.Error -> toast("删除失败: ${r.message}")
                            is Resource.Loading -> {}
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteAddress(addr: EmailDestinationAddress) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除此目的地址？")
            .setMessage(addr.email)
            .setPositiveButton("删除") { _, _ ->
                account?.let { acct ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        when (val r = emailRepo.deleteAddress(acct, addr.id)) {
                            is Resource.Success -> { toast("已删除"); load(acct) }
                            is Resource.Error -> toast("删除失败: ${r.message}")
                            is Resource.Loading -> {}
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 多视图类型适配器 ====================

    private sealed class EmailItem {
        data class SettingsItem(val settings: EmailRoutingSettings?) : EmailItem()
        data class SectionHeader(val title: String) : EmailItem()
        data class RuleItem(val rule: EmailRoutingRule) : EmailItem()
        data class AddressItem(val address: EmailDestinationAddress) : EmailItem()
        data class AddButton(val label: String, val isRule: Boolean) : EmailItem()
        data class EmptyHint(val text: String) : EmailItem()
    }

    private class EmailRoutingAdapter(
        private val onToggleEnabled: () -> Unit,
        private val onAddRule: () -> Unit,
        private val onAddAddress: () -> Unit,
        private val onDeleteRule: (EmailRoutingRule) -> Unit,
        private val onDeleteAddress: (EmailDestinationAddress) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<EmailItem>()

        companion object {
            private const val TYPE_SETTINGS = 1
            private const val TYPE_HEADER = 2
            private const val TYPE_RULE = 3
            private const val TYPE_ADDRESS = 4
            private const val TYPE_ADD = 5
            private const val TYPE_HINT = 6
        }

        fun update(
            settings: EmailRoutingSettings?,
            rules: List<EmailRoutingRule>,
            addresses: List<EmailDestinationAddress>,
        ) {
            items.clear()
            items += EmailItem.SettingsItem(settings)
            items += EmailItem.SectionHeader("路由规则")
            if (rules.isEmpty()) {
                items += EmailItem.EmptyHint("暂无路由规则")
            } else {
                items += rules.map { EmailItem.RuleItem(it) }
            }
            items += EmailItem.AddButton("添加规则", isRule = true)
            items += EmailItem.SectionHeader("目的地址")
            if (addresses.isEmpty()) {
                items += EmailItem.EmptyHint("暂无目的地址")
            } else {
                items += addresses.map { EmailItem.AddressItem(it) }
            }
            items += EmailItem.AddButton("添加目的地址", isRule = false)
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is EmailItem.SettingsItem -> TYPE_SETTINGS
            is EmailItem.SectionHeader -> TYPE_HEADER
            is EmailItem.RuleItem -> TYPE_RULE
            is EmailItem.AddressItem -> TYPE_ADDRESS
            is EmailItem.AddButton -> TYPE_ADD
            is EmailItem.EmptyHint -> TYPE_HINT
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_SETTINGS -> SettingsVH(ItemEmailSettingsBinding.inflate(inflater, parent, false))
                TYPE_HEADER -> HeaderVH(makeTextView(parent, 14, true))
                TYPE_RULE -> RuleVH(ItemEmailRuleBinding.inflate(inflater, parent, false))
                TYPE_ADDRESS -> AddressVH(ItemEmailAddressBinding.inflate(inflater, parent, false))
                TYPE_ADD -> AddVH(makeAddButton(parent))
                TYPE_HINT -> HintVH(makeTextView(parent, 13, false))
                else -> throw IllegalArgumentException("unknown type $viewType")
            }
        }

        private fun makeTextView(parent: ViewGroup, sizeSp: Int, semiBold: Boolean): TextView {
            return TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                textSize = sizeSp.toFloat()
                if (semiBold) {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(dp(parent, 12), dp(parent, 12), 0, dp(parent, 2))
                } else {
                    setPadding(dp(parent, 12), 0, 0, 0)
                    setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            parent.context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0xFF888888.toInt(),
                        )
                    )
                }
            }
        }

        private fun makeAddButton(parent: ViewGroup): MaterialButton {
            return MaterialButton(parent.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                gravity = Gravity.CENTER
                icon = androidx.core.content.ContextCompat.getDrawable(
                    parent.context, android.R.drawable.ic_input_add
                )
                iconTint = androidx.core.content.ContextCompat.getColorStateList(parent.context, com.muort.upworker.R.color.md_theme_tertiary)
                setTextColor(androidx.core.content.ContextCompat.getColorStateList(parent.context, com.muort.upworker.R.color.md_theme_tertiary))
                strokeWidth = 0
                elevation = 0f
            }
        }

        private fun dp(parent: ViewGroup, v: Int): Int =
            (v * parent.resources.displayMetrics.density).toInt()

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is EmailItem.SettingsItem -> (holder as SettingsVH).bind(item.settings, onToggleEnabled)
                is EmailItem.SectionHeader -> (holder as HeaderVH).bind(item.title)
                is EmailItem.RuleItem -> (holder as RuleVH).bind(item.rule, onDeleteRule)
                is EmailItem.AddressItem -> (holder as AddressVH).bind(item.address, onDeleteAddress)
                is EmailItem.AddButton -> (holder as AddVH).bind(item, onAddRule, onAddAddress)
                is EmailItem.EmptyHint -> (holder as HintVH).bind(item.text)
            }
        }

        // --- ViewHolders ---

        class SettingsVH(private val b: ItemEmailSettingsBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(settings: EmailRoutingSettings?, onToggle: () -> Unit) {
                b.statusText.text = "状态：${settings?.status ?: "未知"}"
                b.toggleSwitch.isChecked = settings?.isEnabled == true
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.setOnCheckedChangeListener { _, _ -> onToggle() }
            }
        }

        class HeaderVH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(title: String) { tv.text = title }
        }

        class HintVH(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
            fun bind(text: String) { tv.text = text }
        }

        class RuleVH(private val b: ItemEmailRuleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(rule: EmailRoutingRule, onDelete: (EmailRoutingRule) -> Unit) {
                val matchAddr = rule.matchAddress
                val displayName = rule.name?.takeIf { it.isNotBlank() }
                    ?: matchAddr
                    ?: "全部邮件"
                b.nameText.text = displayName

                val dest = rule.actions.firstOrNull()?.value?.joinToString(", ").orEmpty()
                b.forwardText.text = "${matchAddr ?: "全部邮件"} → $dest"

                b.deleteButton.setOnClickListener { onDelete(rule) }
            }
        }

        class AddressVH(private val b: ItemEmailAddressBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(addr: EmailDestinationAddress, onDelete: (EmailDestinationAddress) -> Unit) {
                b.emailText.text = addr.email
                if (addr.isVerified) {
                    b.verifiedText.text = "已验证"
                    b.verifiedText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            itemView.context,
                            com.google.android.material.R.attr.colorPrimary,
                            0xFF4CAF50.toInt(),
                        )
                    )
                } else {
                    b.verifiedText.text = "待验证"
                    b.verifiedText.setTextColor(
                        com.google.android.material.color.MaterialColors.getColor(
                            itemView.context,
                            com.google.android.material.R.attr.colorOnSurfaceVariant,
                            0xFF888888.toInt(),
                        )
                    )
                }
                b.deleteButton.setOnClickListener { onDelete(addr) }
            }
        }

        class AddVH(private val btn: MaterialButton) : RecyclerView.ViewHolder(btn.rootView) {
            fun bind(item: EmailItem.AddButton, onAddRule: () -> Unit, onAddAddress: () -> Unit) {
                btn.text = item.label
                btn.setOnClickListener { if (item.isRule) onAddRule() else onAddAddress() }
            }
        }
    }
}
