package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.ZoneSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSL/TLS 页：展示并切换当前域名的 SSL 加密模式（off / flexible / full / strict）。
 * 通过 zone setting `ssl` 读写。
 */
@AndroidEntryPoint
class SslFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var currentMode: String = ""

    override val emptyText: String = "加载中…"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { _, item ->
                if (item.id == "ssl_mode") showModePicker()
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
            when (val r = settingsRepo.getSetting(account, zoneId, "ssl")) {
                is Resource.Success -> {
                    currentMode = r.data
                    showList()
                    adapter.submitList(listOf(
                        ZoneRuleItem(
                            id = "ssl_mode",
                            title = "SSL/TLS 加密模式",
                            subtitle = "当前：${modeLabel(currentMode)}",
                            meta = "点击切换",
                            enabled = null,
                            canDelete = false,
                        ),
                        ZoneRuleItem(
                            id = "ssl_help",
                            title = "推荐：Full (strict)",
                            subtitle = "off: 不加密 · flexible: 浏览器→CF 加密 · full: 端到端自签 · strict: 端到端可信证书",
                            meta = null,
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

    private fun showModePicker() {
        val modes = listOf("off", "flexible", "full", "strict")
        val labels = modes.map { "$it — ${modeLabel(it)}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择 SSL/TLS 模式")
            .setItems(labels) { _, which ->
                val newMode = modes[which]
                account?.let { setMode(it, newMode) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setMode(account: Account, mode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, "ssl", mode)) {
                is Resource.Success -> { toast("已切换为 $mode"); load(account) }
                is Resource.Error -> toast("切换失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "off" -> "关闭"
        "flexible" -> "灵活"
        "full" -> "完全"
        "strict" -> "完全(严格)"
        else -> mode
    }
}
