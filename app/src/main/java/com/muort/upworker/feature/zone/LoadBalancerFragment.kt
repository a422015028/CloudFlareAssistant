package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.LoadBalancer
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.LoadBalancerRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 负载均衡页：列出该 Zone 的 Load Balancer，支持启停 / 删除。
 * 池 / 监视器只读展示（账号级，暂不在此页展开列表）。
 */
@AndroidEntryPoint
class LoadBalancerFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var lbRepo: LoadBalancerRepository

    private lateinit var adapter: ZoneRuleAdapter
    private var loaded: List<LoadBalancer> = emptyList()

    override val emptyText: String = "暂无负载均衡器"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter(
            onToggle = { _, item, enabled ->
                account?.let { toggle(it, item.id, enabled) }
            },
            onDelete = { _, item ->
                account?.let { delete(it, item.id) }
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
            when (val r = lbRepo.listLoadBalancers(account, zoneId)) {
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

    private fun toggle(account: Account, lbId: String, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = lbRepo.setEnabled(account, zoneId, lbId, enabled)) {
                is Resource.Success -> { toast(if (enabled) "已启用" else "已停用"); load(account) }
                is Resource.Error -> toast("切换失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun delete(account: Account, lbId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = lbRepo.deleteLoadBalancer(account, zoneId, lbId)) {
                is Resource.Success -> { toast("已删除"); load(account) }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    private fun LoadBalancer.toZoneRuleItem(): ZoneRuleItem = ZoneRuleItem(
        id = id,
        title = name ?: id,
        subtitle = "TTL ${ttl ?: "-"}s · " + if (proxied == true) "已代理" else "DNS only",
        meta = "策略 ${steeringPolicy ?: "-"} · 池数 ${defaultPools?.size ?: 0}",
        enabled = enabled,
        canDelete = true,
    )
}
