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
                account?.let { toggleRule(it, item, enabled) }
            },
            onDelete = { _, item ->
                account?.let { deleteRule(it, item) }
            },
            onItemClick = { _, item ->
                account?.let { showEditRuleDialog(it, item) }
            },
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

    private fun showAddRuleDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        val expressionEdit = EditText(context).apply {
            hint = "表达式 (例: http.request.uri.path == \"/api/*\")"
            setSingleLine()
        }
        val descriptionEdit = EditText(context).apply {
            hint = "规则描述 (可选)"
            setSingleLine()
        }
        val enabledSwitch = SwitchMaterial(context).apply {
            text = "启用规则"
            isChecked = true
        }

        layout.addView(expressionEdit)
        layout.addView(descriptionEdit)
        layout.addView(enabledSwitch)

        MaterialAlertDialogBuilder(context)
            .setTitle("添加触发规则")
            .setView(layout)
            .setPositiveButton("创建") { _, _ ->
                val expression = expressionEdit.text.toString().trim()
                if (expression.isEmpty()) {
                    toast("表达式不能为空")
                    return@setPositiveButton
                }
                val description = descriptionEdit.text.toString().trim().ifEmpty { null }
                account?.let { createRule(it, expression, description, enabledSwitch.isChecked) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditRuleDialog(account: Account, rule: SnippetRule) {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        val expressionEdit = EditText(context).apply {
            setText(rule.expression)
            setSingleLine()
        }
        val descriptionEdit = EditText(context).apply {
            setText(rule.description ?: "")
            setSingleLine()
        }
        val enabledSwitch = SwitchMaterial(context).apply {
            text = "启用规则"
            isChecked = rule.enabled ?: true
        }

        layout.addView(expressionEdit)
        layout.addView(descriptionEdit)
        layout.addView(enabledSwitch)

        MaterialAlertDialogBuilder(context)
            .setTitle("编辑触发规则")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val expression = expressionEdit.text.toString().trim()
                if (expression.isEmpty()) {
                    toast("表达式不能为空")
                    return@setPositiveButton
                }
                val description = descriptionEdit.text.toString().trim().ifEmpty { null }
                val ruleId = rule.id ?: return@setPositiveButton
                updateRule(account, ruleId, expression, description, enabledSwitch.isChecked)
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除规则") { _, _ ->
                deleteRule(account, rule)
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
