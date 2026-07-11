package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.ZoneSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 性能已缓存页：展示当前域名的缓存级别（cache_level）+ 提供全量清理 / 按 URL 清理操作。
 */
@AndroidEntryPoint
class PerformanceFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: ZoneRuleAdapter

    override val emptyText: String = "加载中…"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { _, item ->
                when (item.id) {
                    "ACTION_PURGE_ALL" -> confirmPurgeAll()
                    "ACTION_PURGE_URL" -> showPurgeUrlDialog()
                }
            },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = settingsRepo.getSetting(account, zoneId, "cache_level")) {
                is Resource.Success -> {
                    val level = r.data
                    showList()
                    adapter.submitList(listOf(
                        ZoneRuleItem(
                            id = "CACHE_LEVEL",
                            title = "缓存级别",
                            subtitle = "当前：${cacheLevelLabel(level)}（$level）",
                            meta = "aggressive: 全缓存 · basic: 静态资源 · simplified: 仅默认扩展名",
                            enabled = null,
                            canDelete = false,
                        ),
                        ZoneRuleItem(
                            id = "ACTION_PURGE_ALL",
                            title = "清理所有缓存",
                            subtitle = "立即从 Cloudflare 边缘移除全部缓存资源",
                            meta = "点击执行",
                            enabled = null,
                            canDelete = false,
                        ),
                        ZoneRuleItem(
                            id = "ACTION_PURGE_URL",
                            title = "按 URL 清理缓存",
                            subtitle = "仅清理指定 URL 的缓存",
                            meta = "点击输入 URL",
                            enabled = null,
                            canDelete = false,
                        ),
                    ))
                }
                is Resource.Error -> showError(r.message)
                is Resource.Loading -> {}
            }
        }
    }

    private fun confirmPurgeAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("确认清理该域名所有缓存？此操作不可撤销。")
            .setPositiveButton("清理全部") { _, _ -> account?.let { purgeAll(it) } }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun purgeAll(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.purgeAllCache(account, zoneId)) {
                is Resource.Success -> toast("已清理全部缓存")
                is Resource.Error -> toast("清理失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun showPurgeUrlDialog() {
        val edit = EditText(requireContext()).apply {
            hint = "https://${zoneName.ifBlank { "example.com" }}/path"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("按 URL 清理缓存")
            .setView(edit)
            .setPositiveButton("清理") { _, _ ->
                val url = edit.text.toString().trim()
                if (url.isEmpty()) { toast("URL 不能为空"); return@setPositiveButton }
                account?.let { purgeUrls(it, listOf(url)) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun purgeUrls(account: Account, urls: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.purgeFiles(account, zoneId, urls)) {
                is Resource.Success -> toast("已清理 ${urls.size} 条 URL")
                is Resource.Error -> toast("清理失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun cacheLevelLabel(level: String): String = when (level) {
        "0" -> "简化"
        "1" -> "基础"
        "2" -> "激进（推荐）"
        else -> level
    }
}
