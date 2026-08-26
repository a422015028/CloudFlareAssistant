package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Snippet
import com.muort.upworker.core.repository.SnippetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SnippetsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<Snippet> = emptyList()

    override val emptyText: String = "暂无代码片段"
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { _, item ->
                navigateToEditor(item.id, isNew = false)
            },
            onDelete = { _, item ->
                account?.let { deleteSnippet(it, item.id) }
            },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    override fun onAddClicked() = showAddNameDialog()

    private fun showAddNameDialog() {
        val nameEdit = EditText(requireContext()).apply {
            hint = "代码片段名称（如 redirect_old）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建代码片段")
            .setView(nameEdit)
            .setPositiveButton("确定") { _, _ ->
                val name = nameEdit.text.toString().trim()
                if (name.isEmpty()) {
                    toast("名称不能为空")
                    return@setPositiveButton
                }
                if (!name.matches(Regex("^[a-z0-9_]+$"))) {
                    toast("名称仅支持小写字母、数字和下划线")
                    return@setPositiveButton
                }
                navigateToEditor(name, isNew = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun navigateToEditor(snippetName: String, isNew: Boolean) {
        val action = SnippetsFragmentDirections.actionSnippetsToEditor(
            zoneId = zoneId,
            snippetName = snippetName,
            isNew = isNew,
        )
        findNavController().navigate(action)
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            val snippets = when (val r = snippetRepo.listSnippets(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> { showError(r.message); return@launch }
                is Resource.Loading -> return@launch
            }
            // 规则读取失败不影响片段列表，仅不展示规则状态
            val rules = when (val r = snippetRepo.listSnippetRules(account, zoneId)) {
                is Resource.Success -> r.data
                is Resource.Error -> emptyList()
                is Resource.Loading -> emptyList()
            }
            loaded = snippets
            val items = snippets.map { s ->
                s.toZoneRuleItem(rules.firstOrNull { it.snippetName == s.snippetName })
            }
            if (items.isEmpty()) showEmpty() else { showList(); adapter.submitList(items) }
        }
    }

    private fun deleteSnippet(account: Account, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.deleteSnippet(account, zoneId, name)) {
                is Resource.Success -> {
                    // 尽力清理该片段的规则，避免遗留孤儿规则
                    snippetRepo.deleteSnippetRule(account, zoneId, name)
                    toast("已删除"); load(account)
                }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun Snippet.toZoneRuleItem(rule: com.muort.upworker.core.model.SnippetRule?): ZoneRuleItem = ZoneRuleItem(
        id = snippetName,
        title = snippetName,
        subtitle = "创建 ${createdOn?.take(10) ?: "-"} · 修改 ${modifiedOn?.take(10) ?: "-"}",
        meta = when {
            rule == null -> "未关联规则（不会执行）"
            rule.enabled == false -> "规则已禁用：${ruleSummary(rule)}"
            else -> "规则：${ruleSummary(rule)}"
        },
        enabled = null,
        canDelete = true,
    )

    private fun ruleSummary(rule: com.muort.upworker.core.model.SnippetRule): String =
        if (rule.expression.trim() == "true") "所有格式化请求"
        else rule.expression.let { if (it.length > 60) it.take(60) + "…" else it }
}