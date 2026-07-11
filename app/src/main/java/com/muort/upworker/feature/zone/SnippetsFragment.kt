package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Snippet
import com.muort.upworker.core.repository.SnippetRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Snippets 页：列出该 Zone 的边缘 Snippet，点击查看正文，
 * FAB 支持新建（输入名称 + JS 代码），支持删除。
 */
@AndroidEntryPoint
class SnippetsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<Snippet> = emptyList()

    override val emptyText: String = "暂无 Snippet"
    override val showAddFab: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { _, item ->
                account?.let { viewSnippet(it, item.id) }
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

    override fun onAddClicked() = showAddDialog()

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = snippetRepo.listSnippets(account, zoneId)) {
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

    private fun viewSnippet(account: Account, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.getSnippetContent(account, zoneId, name)) {
                is Resource.Success -> showCodeDialog(name, r.data)
                is Resource.Error -> toast("读取失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun showCodeDialog(name: String, code: String) {
        val edit = EditText(requireContext()).apply {
            setText(code)
            setSingleLine(false)
            isFocusable = false
            isClickable = false
            setPadding(48, 32, 48, 32)
        }
        // 让超长内容可滚动
        edit.setOnTouchListener { v, _ ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Snippet: $name")
            .setView(edit)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun deleteSnippet(account: Account, name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.deleteSnippet(account, zoneId, name)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun showAddDialog() {
        val nameEdit = EditText(requireContext()).apply {
            hint = "snippet 名称（如 redirect-old）"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        val codeEdit = EditText(requireContext()).apply {
            hint = "JS 代码"
            setSingleLine(false)
            minLines = 6
            setPadding(48, 32, 48, 32)
        }
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(nameEdit); addView(codeEdit)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加 Snippet")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val code = codeEdit.text.toString()
                if (name.isEmpty() || code.isEmpty()) {
                    toast("名称和代码不能为空"); return@setPositiveButton
                }
                account?.let { create(it, name, code) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun create(account: Account, name: String, code: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.putSnippet(account, zoneId, name, code)) {
                is Resource.Success -> { toast("已保存"); load(account) }
                is Resource.Error -> toast("保存失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun Snippet.toZoneRuleItem(): ZoneRuleItem = ZoneRuleItem(
        id = snippetName,
        title = snippetName,
        subtitle = "创建 ${createdOn?.take(10) ?: "-"} · 修改 ${modifiedOn?.take(10) ?: "-"}",
        meta = "点击查看代码",
        enabled = null,
        canDelete = true,
    )
}
