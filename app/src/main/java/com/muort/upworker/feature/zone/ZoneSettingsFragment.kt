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
 * 设置页：列出常用 Zone 设置（ssl / always_use_https / min_tls_version / cache_level /
 * browser_cache_ttl / ssl_recommender / 0rtt / http2 / http3 等），点击切换。
 * 只读列表 + 单项修改对话框，避免一次性复杂表单。
 */
@AndroidEntryPoint
class ZoneSettingsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: ZoneRuleAdapter

    /** 简化的设置定义：key → 显示名 + 可选值列表。 */
    private data class SettingDef(val key: String, val label: String, val options: List<String>?)

    private val settings: List<SettingDef> = listOf(
        SettingDef("ssl", "SSL/TLS 加密模式", listOf("off", "flexible", "full", "strict")),
        SettingDef("always_use_https", "始终使用 HTTPS", listOf("on", "off")),
        SettingDef("min_tls_version", "最低 TLS 版本", listOf("1.0", "1.1", "1.2", "1.3")),
        SettingDef("cache_level", "缓存级别", listOf("0", "1", "2")),
        SettingDef("browser_cache_ttl", "浏览器缓存 TTL (秒)", null),
        SettingDef("edge_cache_ttl", "边缘缓存 TTL (秒)", null),
        SettingDef("ipv6", "IPv6 兼容", listOf("on", "off")),
        SettingDef("http2", "HTTP/2", listOf("on", "off")),
        SettingDef("http3", "HTTP/3 (QUIC)", listOf("on", "off")),
        SettingDef("0rtt", "0-RTT 连接恢复", listOf("on", "off")),
        SettingDef("websockets", "WebSockets", listOf("on", "off")),
        SettingDef("minify", "压缩 JS/CSS/HTML", null),
        SettingDef("brotli", "Brotli 压缩", listOf("on", "off")),
    )

    override val emptyText: String = "加载中…"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onItemClick = { position, _ ->
                val def = settings.getOrNull(position) ?: return@ZoneRuleAdapter
                account?.let { onSettingClicked(it, def) }
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
            val items = mutableListOf<ZoneRuleItem>()
            // 串行加载每项设置（Zone Settings API 每次只能查一个）
            for (def in settings) {
                val key = def.key.trim()
                when (val r = settingsRepo.getSetting(account, zoneId, key)) {
                    is Resource.Success -> {
                        items += ZoneRuleItem(
                            id = key,
                            title = def.label,
                            subtitle = "${key} = ${r.data}",
                            meta = if (def.options != null) "点击切换" else "点击输入新值",
                            enabled = null,
                            canDelete = false,
                        )
                    }
                    is Resource.Error -> {
                        items += ZoneRuleItem(
                            id = key,
                            title = def.label,
                            subtitle = "${key} = 加载失败",
                            meta = r.message.take(80),
                            enabled = null,
                            canDelete = false,
                        )
                    }
                    is Resource.Loading -> {}
                }
            }
            showList()
            adapter.submitList(items)
        }
    }

    private fun onSettingClicked(account: Account, def: SettingDef) {
        val key = def.key.trim()
        if (def.options != null) {
            showOptionsPicker(account, key, def.label, def.options)
        } else {
            showValueInputDialog(account, key, def.label)
        }
    }

    private fun showOptionsPicker(account: Account, key: String, label: String, options: List<String>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择 $label")
            .setItems(options.toTypedArray()) { _, which ->
                setSetting(account, key, options[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showValueInputDialog(account: Account, key: String, label: String) {
        val edit = EditText(requireContext()).apply {
            hint = "$label 的新值"
            setSingleLine()
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("修改 $label")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val v = edit.text.toString().trim()
                if (v.isEmpty()) { toast("值不能为空"); return@setPositiveButton }
                setSetting(account, key, v)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setSetting(account: Account, key: String, value: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, key, value)) {
                is Resource.Success -> { toast("已更新"); load(account) }
                is Resource.Error -> toast("更新失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }
}
