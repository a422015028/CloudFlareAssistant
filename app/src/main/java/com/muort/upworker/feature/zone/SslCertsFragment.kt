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

    override val emptyTextResId: Int = R.string.ssl_certs_empty
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
        adapter.update(
            ctx = requireContext(),
            universalEnabled = universalEnabled,
            universalLoaded = universalLoaded,
            isTogglingUniversal = isTogglingUniversal,
            packs = packs,
        )
    }

    // ==================== 通用证书 SSL 开关 ====================

    private fun toggleUniversal(on: Boolean) {
        val account = account ?: return
        isTogglingUniversal = true
        universalEnabled = on  // 乐观更新
        binding.recyclerView.post { renderAll() }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = sslRepo.setUniversalEnabled(account, zoneId, on)) {
                is Resource.Success -> toast(if (on) getString(R.string.msg_enabled) else getString(R.string.msg_disabled))
                is Resource.Error -> {
                    universalEnabled = !on  // 回退
                    toast(getString(R.string.msg_operation_failed, r.message))
                }
                is Resource.Loading -> {}
            }
            isTogglingUniversal = false
            binding.recyclerView.post { renderAll() }
        }
    }

    // ==================== 删除证书包 ====================

    private fun confirmDeletePack(pack: SslCertificatePack) {
        val ctx = requireContext()
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ssl_certs_delete_title)
            .setMessage(ctx.getString(R.string.ssl_certs_delete_message, certTypeLabel(ctx, pack.type)))
            .setPositiveButton(R.string.delete) { _, _ ->
                account?.let { deletePack(it, pack.id) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deletePack(account: Account, packId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = sslRepo.deletePack(account, zoneId, packId)) {
                is Resource.Success -> { toast(getString(R.string.msg_deleted)); load(account) }
                is Resource.Error -> toast(getString(R.string.msg_delete_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 标签 ====================

    private fun certTypeLabel(ctx: android.content.Context, type: String?): String = when (type) {
        "universal" -> ctx.getString(R.string.ssl_certs_type_universal)
        "advanced" -> ctx.getString(R.string.ssl_certs_type_advanced)
        "sni_custom", "legacy_custom", "mh_custom", "keyless" -> ctx.getString(R.string.ssl_certs_type_custom)
        "total_tls" -> ctx.getString(R.string.ssl_certs_type_total_tls)
        else -> type ?: "—"
    }

    private fun certStatusLabel(ctx: android.content.Context, status: String?): String = when (status) {
        "active" -> ctx.getString(R.string.ssl_certs_status_active)
        "pending_validation" -> ctx.getString(R.string.ssl_certs_status_pending_validation)
        "initializing" -> ctx.getString(R.string.ssl_certs_status_initializing)
        "expired" -> ctx.getString(R.string.ssl_certs_status_expired)
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
            ctx: android.content.Context,
            universalEnabled: Boolean,
            universalLoaded: Boolean,
            isTogglingUniversal: Boolean,
            packs: List<SslCertificatePack>,
        ) {
            this.cachedCtx = ctx
            items.clear()
            if (universalLoaded) {
                items += CertItem.UniversalToggle(universalEnabled, isTogglingUniversal)
            }
            items += packs.map { CertItem.CertPack(it) }
            notifyDataSetChanged()
        }

        private var cachedCtx: android.content.Context? = null

        private fun certTypeLabel(type: String?): String {
            val ctx = cachedCtx ?: return type ?: "—"
            return when (type) {
                "universal" -> ctx.getString(R.string.ssl_certs_type_universal)
                "advanced" -> ctx.getString(R.string.ssl_certs_type_advanced)
                "sni_custom", "legacy_custom", "mh_custom", "keyless" -> ctx.getString(R.string.ssl_certs_type_custom)
                "total_tls" -> ctx.getString(R.string.ssl_certs_type_total_tls)
                else -> type ?: "—"
            }
        }

        private fun certStatusLabel(status: String?): String {
            val ctx = cachedCtx ?: return status ?: "—"
            return when (status) {
                "active" -> ctx.getString(R.string.ssl_certs_status_active)
                "pending_validation" -> ctx.getString(R.string.ssl_certs_status_pending_validation)
                "initializing" -> ctx.getString(R.string.ssl_certs_status_initializing)
                "expired" -> ctx.getString(R.string.ssl_certs_status_expired)
                else -> status ?: "—"
            }
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
                is CertItem.CertPack -> (holder as CertVH).bind(item.pack, onDeletePack, cachedCtx ?: holder.itemView.context)
            }
        }

        class UniversalVH(private val b: ItemSslToggleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: CertItem.UniversalToggle, onToggle: (Boolean) -> Unit) {
                b.titleText.setText(R.string.ssl_certs_universal_title)
                b.subtitleText.setText(R.string.ssl_certs_universal_subtitle)
                b.subtitleText.visibility = View.VISIBLE
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.isChecked = item.enabled
                b.toggleSwitch.isEnabled = !item.isBusy
                b.toggleSwitch.setOnCheckedChangeListener { _, checked -> onToggle(checked) }
                b.root.alpha = if (item.isBusy) 0.5f else 1f
            }
        }

        class CertVH(private val b: ItemSslCertBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(pack: SslCertificatePack, onDelete: (SslCertificatePack) -> Unit, ctx: android.content.Context) {
                b.titleText.text = certTypeLabel(ctx, pack.type)
                val sub = buildString {
                    append(certStatusLabel(ctx, pack.status))
                    pack.expiresOnDay?.let { append(ctx.getString(R.string.ssl_certs_expires_format, it)) }
                }
                b.subtitleText.text = sub
                b.hostsText.text = pack.hosts?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: ""
                b.hostsText.visibility = if (b.hostsText.text.isBlank()) View.GONE else View.VISIBLE
                b.deleteButton.visibility = if (!pack.isUniversal) View.VISIBLE else View.GONE
                b.deleteButton.setOnClickListener { onDelete(pack) }
            }

            private fun certTypeLabel(ctx: android.content.Context, type: String?): String = when (type) {
                "universal" -> ctx.getString(R.string.ssl_certs_type_universal)
                "advanced" -> ctx.getString(R.string.ssl_certs_type_advanced)
                "sni_custom", "legacy_custom", "mh_custom", "keyless" -> ctx.getString(R.string.ssl_certs_type_custom)
                "total_tls" -> ctx.getString(R.string.ssl_certs_type_total_tls)
                else -> type ?: "—"
            }

            private fun certStatusLabel(ctx: android.content.Context, status: String?): String = when (status) {
                "active" -> ctx.getString(R.string.ssl_certs_status_active)
                "pending_validation" -> ctx.getString(R.string.ssl_certs_status_pending_validation)
                "initializing" -> ctx.getString(R.string.ssl_certs_status_initializing)
                "expired" -> ctx.getString(R.string.ssl_certs_status_expired)
                else -> status ?: "—"
            }
        }
    }
}
