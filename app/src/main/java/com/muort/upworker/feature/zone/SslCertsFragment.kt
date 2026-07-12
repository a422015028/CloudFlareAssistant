package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SslCertificatePack
import com.muort.upworker.core.repository.SslRepository
import com.muort.upworker.databinding.ItemSslCertBinding
import com.muort.upworker.databinding.ItemSslToggleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSL 证书页。对齐 orange-cloud ZoneSslCertsScreen：
 * - 通用证书 SSL 开关（可切换）
 * - 证书包列表（类型标签 + 状态/到期 + 域名 + 删除非通用证书）
 */
@AndroidEntryPoint
class SslCertsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var sslRepo: SslRepository

    private lateinit var adapter: SslCertsAdapter

    private var universalEnabled: Boolean = true
    private var universalLoaded: Boolean = false
    private var packs: List<SslCertificatePack> = emptyList()
    private var isTogglingUniversal: Boolean = false

    override val emptyText: String = "暂无 SSL 证书"
    override val showAddFab: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SslCertsAdapter(
            onToggleUniversal = { on -> toggleUniversal(on) },
            onDeletePack = { pack -> confirmDeletePack(pack) },
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
            val packsRes = sslRepo.listCertificatePacks(account, zoneId)
            val universalRes = sslRepo.getUniversalEnabled(account, zoneId)
            if (packsRes is Resource.Error) {
                showError(packsRes.message); return@launch
            }
            packs = (packsRes as Resource.Success).data
            universalLoaded = universalRes is Resource.Success
            universalEnabled = (universalRes as? Resource.Success)?.data == true
            renderAll()
        }
    }

    private fun renderAll() {
        if (packs.isEmpty() && !universalLoaded) {
            showEmpty(); return
        }
        showList()
        adapter.update(universalEnabled, universalLoaded, isTogglingUniversal, packs)
    }

    // ==================== 通用证书 SSL 开关 ====================

    private fun toggleUniversal(on: Boolean) {
        val account = account ?: return
        isTogglingUniversal = true
        universalEnabled = on  // 乐观更新
        binding.recyclerView.post { renderAll() }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = sslRepo.setUniversalEnabled(account, zoneId, on)) {
                is Resource.Success -> toast(if (on) "已启用" else "已关闭")
                is Resource.Error -> {
                    universalEnabled = !on  // 回退
                    toast("操作失败: ${r.message}")
                }
                is Resource.Loading -> {}
            }
            isTogglingUniversal = false
            binding.recyclerView.post { renderAll() }
        }
    }

    // ==================== 删除证书包 ====================

    private fun confirmDeletePack(pack: SslCertificatePack) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除此证书？")
            .setMessage("证书类型：${certTypeLabel(pack.type)}\n删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                account?.let { deletePack(it, pack.id) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deletePack(account: Account, packId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = sslRepo.deletePack(account, zoneId, packId)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 标签 ====================

    private fun certTypeLabel(type: String?): String = when (type) {
        "universal" -> "通用证书"
        "advanced" -> "高级证书"
        "sni_custom", "legacy_custom", "mh_custom", "keyless" -> "自定义证书"
        "total_tls" -> "Total SSL"
        else -> type ?: "—"
    }

    private fun certStatusLabel(status: String?): String = when (status) {
        "active" -> "已激活"
        "pending_validation" -> "等待验证"
        "initializing" -> "初始化中"
        "expired" -> "已过期"
        else -> status ?: "—"
    }

    // ==================== 多视图类型适配器 ====================

    private sealed class CertItem {
        data class UniversalToggle(val enabled: Boolean, val isBusy: Boolean) : CertItem()
        data class CertPack(val pack: SslCertificatePack) : CertItem()
    }

    private class SslCertsAdapter(
        private val onToggleUniversal: (Boolean) -> Unit,
        private val onDeletePack: (SslCertificatePack) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<CertItem>()

        companion object {
            private const val TYPE_TOGGLE = 1
            private const val TYPE_CERT = 2
        }

        fun update(
            universalEnabled: Boolean,
            universalLoaded: Boolean,
            isTogglingUniversal: Boolean,
            packs: List<SslCertificatePack>,
        ) {
            items.clear()
            if (universalLoaded) {
                items += CertItem.UniversalToggle(universalEnabled, isTogglingUniversal)
            }
            items += packs.map { CertItem.CertPack(it) }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is CertItem.UniversalToggle -> TYPE_TOGGLE
            is CertItem.CertPack -> TYPE_CERT
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_TOGGLE -> UniversalVH(ItemSslToggleBinding.inflate(inflater, parent, false))
                TYPE_CERT -> CertVH(ItemSslCertBinding.inflate(inflater, parent, false))
                else -> throw IllegalArgumentException("unknown type $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is CertItem.UniversalToggle -> (holder as UniversalVH).bind(item, onToggleUniversal)
                is CertItem.CertPack -> (holder as CertVH).bind(item.pack, onDeletePack)
            }
        }

        class UniversalVH(private val b: ItemSslToggleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: CertItem.UniversalToggle, onToggle: (Boolean) -> Unit) {
                b.titleText.text = "通用证书 SSL"
                b.subtitleText.text = "Cloudflare 提供的免费通用 SSL 证书"
                b.subtitleText.visibility = View.VISIBLE
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.isChecked = item.enabled
                b.toggleSwitch.isEnabled = !item.isBusy
                b.toggleSwitch.setOnCheckedChangeListener { _, checked -> onToggle(checked) }
                b.root.alpha = if (item.isBusy) 0.5f else 1f
            }
        }

        class CertVH(private val b: ItemSslCertBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(pack: SslCertificatePack, onDelete: (SslCertificatePack) -> Unit) {
                b.titleText.text = certTypeLabel(pack.type)
                val sub = buildString {
                    append(certStatusLabel(pack.status))
                    pack.expiresOnDay?.let { append(" · 到期 $it") }
                }
                b.subtitleText.text = sub
                b.hostsText.text = pack.hosts?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: ""
                b.hostsText.visibility = if (b.hostsText.text.isBlank()) View.GONE else View.VISIBLE
                b.deleteButton.visibility = if (!pack.isUniversal) View.VISIBLE else View.GONE
                b.deleteButton.setOnClickListener { onDelete(pack) }
            }

            private fun certTypeLabel(type: String?): String = when (type) {
                "universal" -> "通用证书"
                "advanced" -> "高级证书"
                "sni_custom", "legacy_custom", "mh_custom", "keyless" -> "自定义证书"
                "total_tls" -> "Total SSL"
                else -> type ?: "—"
            }

            private fun certStatusLabel(status: String?): String = when (status) {
                "active" -> "已激活"
                "pending_validation" -> "等待验证"
                "initializing" -> "初始化中"
                "expired" -> "已过期"
                else -> status ?: "—"
            }
        }
    }
}
