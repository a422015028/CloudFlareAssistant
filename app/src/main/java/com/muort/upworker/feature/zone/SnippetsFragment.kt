package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Snippet
import com.muort.upworker.core.repository.SnippetRepository
import com.muort.upworker.databinding.DialogSnippetAddBinding
import com.muort.upworker.databinding.DialogSnippetViewBinding
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
        val binding = DialogSnippetViewBinding.inflate(layoutInflater)
        binding.nameText.text = name
        binding.codeInput.setText(code)

        val snippet = loaded.find { it.snippetName == name }
        binding.createdText.text = snippet?.createdOn?.take(10) ?: "-"
        binding.modifiedText.text = snippet?.modifiedOn?.take(10) ?: "-"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(name)
            .setView(binding.root)
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
        val binding = DialogSnippetAddBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加代码片段")
            .setView(binding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = binding.nameInput.text.toString().trim()
                val code = binding.codeInput.text.toString()
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