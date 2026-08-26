package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SnippetRule
import com.muort.upworker.core.model.SnippetRuleCreate
import com.muort.upworker.core.repository.SnippetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Snippet 规则列表页面：管理某个代码片段的触发规则。
 * 规则决定什么请求会执行该 Snippet（基于 expression 表达式）。
 */
@AndroidEntryPoint
class SnippetRulesFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    private val args: SnippetRulesFragmentArgs by navArgs()

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<SnippetRule> = emptyList()

    override val emptyText: String = "暂无触发规则\n添加规则以定义何时执行此代码片段"
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        adapter = ZoneRuleAdapter(
            onToggle = { _, item, enabled ->
                account?.let { acct ->
                    loaded.firstOrNull { it.id == item.id }?.let { rule ->
                        toggleRule(acct, rule, enabled)
                    }
                }
            },
            onDelete = { _, item ->
                account?.let { acct ->
                    loaded.firstOrNull { it.id == item.id }?.let { rule ->
                        deleteRule(acct, rule)
                    }
                }
            },
            onItemClick = { _, item ->
                account?.let { acct ->
                    loaded.firstOrNull { it.id == item.id }?.let { rule ->
                        showRuleDialog(acct, rule)
                    }
                }
            },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() {
        account?.let { showRuleDialog(it) }
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = snippetRepo.listSnippetRules(account, zoneId)) {
                is Resource.Success -> {
                    loaded = r.data.filter { it.snippetName == args.snippetName }
                    val items = loaded.map { it.toZoneRuleItem() }
                    if (items.isEmpty()) showEmpty() else {
                        showList()
                        adapter.submitList(items)
                    }
                }
                is Resource.Error -> showError(r.message)
                is Resource.Loading -> {}
            }
        }
    }

    /**
     * 官方化规则编辑器：对齐 Cloudflare Dashboard 的"匹配传入请求"体验。
     * 简易模式：绑定域名(支持通配符) + 可选路径前缀 → 自动生成规则表达式；
     * 高级模式：直接编辑完整 expression。
     */
    private fun showRuleDialog(account: Account, existing: SnippetRule? = null) {
        val context = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }

        // —— 绑定域名 ——
        val hostEdit = EditText(context).apply {
            hint = "绑定域名（如 example.com，子域可用 *.example.com）"
            setSingleLine()
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(hostEdit)

        // —— 可选路径前缀 ——
        val pathEdit = EditText(context).apply {
            hint = "路径前缀（可选，如 /api 仅匹配该前缀）"
            setSingleLine()
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        layout.addView(pathEdit)

        // —— 表达式预览 / 高级编辑 ——
        var userEditedExpression = false
        var updatingPreview = false
        val exprLabel = android.widget.TextView(context).apply {
            text = "规则表达式"
            setPadding(0, pad / 2, 0, pad / 4)
        }
        val exprEdit = EditText(context).apply {
            minLines = 2
            maxLines = 4
            gravity = android.view.Gravity.TOP
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 13f
            hint = "(http.host eq \"example.com\")"
        }
        fun buildExpression(): String {
            val host = hostEdit.text.toString().trim().trim('"')
            val path = pathEdit.text.toString().trim().trim('"')
            val parts = mutableListOf<String>()
            if (host.isNotEmpty()) {
                parts.add(
                    if (host.contains("*")) "(http.host wildcard \"$host\")"
                    else "(http.host eq \"$host\")"
                )
            }
            if (path.isNotEmpty()) parts.add("(starts_with(http.request.uri.path, \"$path\"))")
            return parts.joinToString(" and ")
        }
        val sync = {
            if (!userEditedExpression) {
                updatingPreview = true
                exprEdit.setText(buildExpression())
                updatingPreview = false
            }
            Unit
        }
        hostEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = sync()
        })
        pathEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) = sync()
        })
        exprEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!updatingPreview) userEditedExpression = true
            }
        })

        // 编辑模式：从已有表达式反向提取域名/路径预填
        existing?.let { rule ->
            val hostMatch = Regex("http\\.host\\s+(eq|wildcard)\\s+\"([^\"]+)\"").find(rule.expression)
            val pathMatch = Regex("starts_with\\(http\\.request\\.uri\\.path,\\s*\"([^\"]+)\"\\)").find(rule.expression)
            hostMatch?.groupValues?.getOrNull(2)?.let { hostEdit.setText(it) }
            pathMatch?.groupValues?.getOrNull(1)?.let { pathEdit.setText(it) }
            if (hostMatch != null || pathMatch != null) {
                // 能解析的部分已回填；剩余部分视为手工表达式
                userEditedExpression = false
                sync()
            } else {
                exprEdit.setText(rule.expression)
                userEditedExpression = true
            }
        }

        layout.addView(exprLabel)
        layout.addView(exprEdit)

        val descriptionEdit = EditText(context).apply {
            hint = "规则描述（可选）"
            setSingleLine()
        }
        layout.addView(descriptionEdit)

        val enabledSwitch = SwitchMaterial(context).apply {
            text = "启用规则"
            isChecked = existing?.enabled ?: true
        }
        layout.addView(enabledSwitch)

        MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) "设置触发规则" else "编辑触发规则")
            .setView(layout)
            .setPositiveButton(if (existing == null) "创建并绑定" else "保存") { _, _ ->
                val expression = exprEdit.text.toString().trim()
                if (expression.isEmpty()) {
                    toast("请先填写域名或路径以生成规则")
                    return@setPositiveButton
                }
                val description = descriptionEdit.text.toString().trim().ifEmpty { null }
                if (existing == null) {
                    createRule(account, expression, description, enabledSwitch.isChecked)
                } else {
                    val ruleId = existing.id ?: return@setPositiveButton
                    updateRule(account, ruleId, expression, description, enabledSwitch.isChecked)
                }
            }
            .setNegativeButton("取消", null)
            .apply {
                if (existing != null) setNeutralButton("删除规则") { _, _ -> deleteRule(account, existing) }
            }
            .show()
    }

    private fun createRule(account: Account, expression: String, description: String?, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            val create = SnippetRuleCreate(
                snippetName = args.snippetName,
                expression = expression,
                description = description,
                enabled = enabled,
            )
            when (val r = snippetRepo.createSnippetRule(account, zoneId, create)) {
                is Resource.Success -> {
                    toast("规则已创建")
                    load(account)
                }
                is Resource.Error -> {
                    toast("创建失败: ${r.message}")
                    load(account)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun updateRule(account: Account, ruleId: String, expression: String, description: String?, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            val create = SnippetRuleCreate(
                snippetName = args.snippetName,
                expression = expression,
                description = description,
                enabled = enabled,
            )
            when (val r = snippetRepo.updateSnippetRule(account, zoneId, ruleId, create)) {
                is Resource.Success -> {
                    toast("规则已更新")
                    load(account)
                }
                is Resource.Error -> {
                    toast("更新失败: ${r.message}")
                    load(account)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun toggleRule(account: Account, rule: SnippetRule, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ruleId = rule.id ?: return@launch
            val create = SnippetRuleCreate(
                snippetName = rule.snippetName,
                expression = rule.expression,
                description = rule.description,
                enabled = enabled,
            )
            when (val r = snippetRepo.updateSnippetRule(account, zoneId, ruleId, create)) {
                is Resource.Success -> load(account)
                is Resource.Error -> {
                    toast("操作失败: ${r.message}")
                    load(account)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun deleteRule(account: Account, rule: SnippetRule) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ruleId = rule.id ?: return@launch
            when (val r = snippetRepo.deleteSnippetRule(account, zoneId, ruleId)) {
                is Resource.Success -> {
                    toast("规则已删除")
                    load(account)
                }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun SnippetRule.toZoneRuleItem(): ZoneRuleItem = ZoneRuleItem(
        id = id ?: "",
        title = expression,
        subtitle = description,
        meta = if (enabled == true) "已启用" else "已禁用",
        enabled = enabled,
        canDelete = true,
    )
}
