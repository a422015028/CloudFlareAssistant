package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.ZoneSettingsRepository
import com.muort.upworker.databinding.ItemSslSelectorBinding
import com.muort.upworker.databinding.ItemSslToggleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSL/TLS 加密设置页。对齐 orange-cloud ZoneSslSettingsScreen：
 * 1. SSL/TLS 加密模式（off / flexible / full / strict）— 选择器
 * 2. 始终使用 HTTPS — 开关
 * 3. 自动 HTTPS 重写 — 开关
 * 4. 最低 TLS 版本（1.0 / 1.1 / 1.2 / 1.3）— 选择器
 * 5. TLS 1.3 — 开关
 */
@AndroidEntryPoint
class SslFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: SslSettingsAdapter

    // 当前值
    private var sslMode: String = "full"
    private var alwaysUseHttps: Boolean = false
    private var autoHttpsRewrites: Boolean = false
    private var minTls: String = "1.0"
    private var tls13: Boolean = false
    // 正在写入的 key（临时禁用对应行）
    private val updating = mutableSetOf<String>()

    override val emptyTextResId: Int = R.string.ssl_empty_loading
    override val showAddFab: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SslSettingsAdapter(
            onSelectorClick = { key -> onSelectorClick(key) },
            onToggle = { key, on -> onToggle(key, on) },
            isUpdating = { key -> key in updating },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    // ==================== 加载 ====================

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            // 各项独立读取，单项失败不影响其它
            val ssl = async { runCatching { settingsRepo.getSetting(account, zoneId, "ssl") }.getOrNull() }
            val ah = async { runCatching { settingsRepo.getSetting(account, zoneId, "always_use_https") }.getOrNull() }
            val ar = async { runCatching { settingsRepo.getSetting(account, zoneId, "automatic_https_rewrites") }.getOrNull() }
            val tls = async { runCatching { settingsRepo.getSetting(account, zoneId, "min_tls_version") }.getOrNull() }
            val t13 = async { runCatching { settingsRepo.getSetting(account, zoneId, "tls_1_3") }.getOrNull() }

            ssl.await()?.let { if (it is Resource.Success) sslMode = it.data }
            ah.await()?.let { if (it is Resource.Success) alwaysUseHttps = it.data == "on" }
            ar.await()?.let { if (it is Resource.Success) autoHttpsRewrites = it.data == "on" }
            tls.await()?.let { if (it is Resource.Success) minTls = it.data }
            t13.await()?.let { if (it is Resource.Success) tls13 = it.data == "on" || it.data == "zrt" }

            showList()
            renderAll()
        }
    }

    private fun renderAll() {
        adapter.update(
            ctx = requireContext(),
            sslMode = sslMode,
            alwaysUseHttps = alwaysUseHttps,
            autoHttpsRewrites = autoHttpsRewrites,
            minTls = minTls,
            tls13 = tls13,
        )
    }

    // ==================== 选择器点击 ====================

    private fun onSelectorClick(key: String) {
        when (key) {
            "ssl" -> showSslModePicker()
            "min_tls_version" -> showMinTlsPicker()
        }
    }

    private fun showSslModePicker() {
        val ctx = requireContext()
        val modes = listOf("off", "flexible", "full", "strict")
        val labels = modes.map { ctx.getString(R.string.ssl_mode_label_format, it, modeLabel(ctx, it)) }.toTypedArray()
        val checked = modes.indexOf(sslMode)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ssl_encryption_mode_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val newMode = modes[which]
                dialog.dismiss()
                updateSetting("ssl", newMode) { sslMode = newMode }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showMinTlsPicker() {
        val ctx = requireContext()
        val versions = listOf("1.0", "1.1", "1.2", "1.3")
        val labels = versions.map { ctx.getString(R.string.ssl_min_tls_label_format, it) }.toTypedArray()
        val checked = versions.indexOf(minTls)
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ssl_min_tls_version_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val v = versions[which]
                dialog.dismiss()
                updateSetting("min_tls_version", v) { minTls = v }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== 开关切换 ====================

    private fun onToggle(key: String, on: Boolean) {
        when (key) {
            "always_use_https" -> updateSetting("always_use_https", if (on) "on" else "off") { alwaysUseHttps = on }
            "automatic_https_rewrites" -> updateSetting("automatic_https_rewrites", if (on) "on" else "off") { autoHttpsRewrites = on }
            "tls_1_3" -> updateSetting("tls_1_3", if (on) "on" else "off") { tls13 = on }
        }
    }

    // ==================== 写设置 ====================

    private fun updateSetting(key: String, value: String, apply: () -> Unit) {
        val account = account ?: return
        updating.add(key)
        apply()  // 乐观更新：立即应用本地状态，避免 renderAll 用旧值导致开关回弹
        binding.recyclerView.post { renderAll() }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, key, value)) {
                is Resource.Success -> toast(getString(R.string.msg_updated))
                is Resource.Error -> toast(getString(R.string.msg_update_failed, r.message))
                is Resource.Loading -> {}
            }
            updating.remove(key)
            binding.recyclerView.post { renderAll() }
        }
    }

    private fun modeLabel(ctx: android.content.Context, mode: String): String = when (mode) {
        "off" -> ctx.getString(R.string.ssl_mode_off)
        "flexible" -> ctx.getString(R.string.ssl_mode_flexible)
        "full" -> ctx.getString(R.string.ssl_mode_full)
        "strict" -> ctx.getString(R.string.ssl_mode_strict)
        else -> mode
    }

    // ==================== 多视图类型适配器 ====================

    private sealed class SslItem {
        data class Selector(
            val key: String,
            val title: String,
            val subtitle: String?,
            val value: String,
        ) : SslItem()

        data class Toggle(
            val key: String,
            val title: String,
            val subtitle: String?,
            val checked: Boolean,
        ) : SslItem()
    }

    private class SslSettingsAdapter(
        private val onSelectorClick: (String) -> Unit,
        private val onToggle: (String, Boolean) -> Unit,
        private val isUpdating: (String) -> Boolean,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<SslItem>()

        companion object {
            private const val TYPE_SELECTOR = 1
            private const val TYPE_TOGGLE = 2
        }

        private fun modeLabel(ctx: android.content.Context, mode: String): String = when (mode) {
            "off" -> ctx.getString(R.string.ssl_mode_off)
            "flexible" -> ctx.getString(R.string.ssl_mode_flexible)
            "full" -> ctx.getString(R.string.ssl_mode_full)
            "strict" -> ctx.getString(R.string.ssl_mode_strict)
            else -> mode
        }

        fun update(
            ctx: android.content.Context,
            sslMode: String,
            alwaysUseHttps: Boolean,
            autoHttpsRewrites: Boolean,
            minTls: String,
            tls13: Boolean,
        ) {
            items.clear()
            items += SslItem.Selector(
                key = "ssl",
                title = ctx.getString(R.string.ssl_encryption_mode_title),
                subtitle = ctx.getString(R.string.ssl_encryption_mode_subtitle),
                value = modeLabel(ctx, sslMode),
            )
            items += SslItem.Toggle(
                key = "always_use_https",
                title = ctx.getString(R.string.ssl_always_https_title),
                subtitle = ctx.getString(R.string.ssl_always_https_subtitle),
                checked = alwaysUseHttps,
            )
            items += SslItem.Toggle(
                key = "automatic_https_rewrites",
                title = ctx.getString(R.string.ssl_automatic_https_rewrites_title),
                subtitle = ctx.getString(R.string.ssl_automatic_https_rewrites_subtitle),
                checked = autoHttpsRewrites,
            )
            items += SslItem.Selector(
                key = "min_tls_version",
                title = ctx.getString(R.string.ssl_min_tls_version_title),
                subtitle = null,
                value = ctx.getString(R.string.ssl_min_tls_label_format, minTls),
            )
            items += SslItem.Toggle(
                key = "tls_1_3",
                title = ctx.getString(R.string.ssl_tls_1_3_title),
                subtitle = ctx.getString(R.string.ssl_tls_1_3_subtitle),
                checked = tls13,
            )
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is SslItem.Selector -> TYPE_SELECTOR
            is SslItem.Toggle -> TYPE_TOGGLE
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_SELECTOR -> SelectorVH(ItemSslSelectorBinding.inflate(inflater, parent, false))
                TYPE_TOGGLE -> ToggleVH(ItemSslToggleBinding.inflate(inflater, parent, false))
                else -> throw IllegalArgumentException("unknown type $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val isBusy = isUpdating(getKey(position))
            when (val item = items[position]) {
                is SslItem.Selector -> (holder as SelectorVH).bind(item, isBusy, onSelectorClick)
                is SslItem.Toggle -> (holder as ToggleVH).bind(item, isBusy, onToggle)
            }
        }

        private fun getKey(position: Int): String = when (val item = items[position]) {
            is SslItem.Selector -> item.key
            is SslItem.Toggle -> item.key
        }

        class SelectorVH(private val b: ItemSslSelectorBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: SslItem.Selector, isBusy: Boolean, onClick: (String) -> Unit) {
                b.titleText.text = item.title
                if (item.subtitle.isNullOrEmpty()) {
                    b.subtitleText.visibility = View.GONE
                } else {
                    b.subtitleText.text = item.subtitle
                    b.subtitleText.visibility = View.VISIBLE
                }
                b.valueText.text = item.value
                b.root.alpha = if (isBusy) 0.5f else 1f
                b.root.isEnabled = !isBusy
                b.root.setOnClickListener { onClick(item.key) }
            }
        }

        class ToggleVH(private val b: ItemSslToggleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: SslItem.Toggle, isBusy: Boolean, onToggle: (String, Boolean) -> Unit) {
                b.titleText.text = item.title
                if (item.subtitle.isNullOrEmpty()) {
                    b.subtitleText.visibility = View.GONE
                } else {
                    b.subtitleText.text = item.subtitle
                    b.subtitleText.visibility = View.VISIBLE
                }
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.isChecked = item.checked
                b.toggleSwitch.isEnabled = !isBusy
                b.toggleSwitch.setOnCheckedChangeListener { _, checked -> onToggle(item.key, checked) }
                b.root.alpha = if (isBusy) 0.5f else 1f
            }
        }
    }
}
