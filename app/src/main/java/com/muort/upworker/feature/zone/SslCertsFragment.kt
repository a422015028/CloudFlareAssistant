package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SslCertificatePack
import com.muort.upworker.core.repository.SslRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SSL 证书页：列出该 Zone 的边缘证书包，支持删除非通用证书。
 * 同时展示 Universal SSL 开关状态（只读，调整需 Cloudflare 控制台）。
 */
@AndroidEntryPoint
class SslCertsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var sslRepo: SslRepository

    private lateinit var adapter: ZoneRuleAdapter

    override val emptyText: String = "暂无 SSL 证书"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onDelete = { _, item ->
                if (item.id.startsWith("PACK:")) {
                    account?.let { deletePack(it, item.id.removePrefix("PACK:")) }
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
            val packsRes = sslRepo.listCertificatePacks(account, zoneId)
            val universalRes = sslRepo.getUniversalEnabled(account, zoneId)
            if (packsRes is Resource.Error) {
                showError(packsRes.message); return@launch
            }
            val packs = (packsRes as Resource.Success).data
            val universalOn = (universalRes as? Resource.Success)?.data == true
            val items = mutableListOf<ZoneRuleItem>()
            items += ZoneRuleItem(
                id = "UNIVERSAL",
                title = "Universal SSL",
                subtitle = "通用 SSL 证书",
                meta = if (universalOn) "已启用" else "已关闭",
                enabled = universalOn,
                canDelete = false,
            )
            packs.forEach { items += it.toZoneRuleItem() }
            if (items.isEmpty()) showEmpty() else { showList(); adapter.submitList(items) }
        }
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

    private fun SslCertificatePack.toZoneRuleItem(): ZoneRuleItem = ZoneRuleItem(
        id = "PACK:$id",
        title = type ?: id,
        subtitle = (hosts?.joinToString(", ")?.take(80) ?: "") + " · CA: ${certificateAuthority ?: "-"}",
        meta = "状态 ${status ?: "-"} · 到期 ${expiresOnDay ?: "-"}",
        enabled = null,
        canDelete = !isUniversal,
    )
}
