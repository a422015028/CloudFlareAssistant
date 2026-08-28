package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.muort.upworker.R
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

    override val emptyTextResId: Int = R.string.lb_empty
    override val showAddFab: Boolean = false

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
                    val ctx = requireContext()
                    val items = r.data.map { it.toZoneRuleItem(ctx) }
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
                is Resource.Success -> {
                    toast(if (enabled) getString(R.string.msg_enabled) else getString(R.string.msg_deactivated))
                    load(account)
                }
                is Resource.Error -> toast(getString(R.string.msg_toggle_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    private fun delete(account: Account, lbId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = lbRepo.deleteLoadBalancer(account, zoneId, lbId)) {
                is Resource.Success -> { toast(getString(R.string.msg_deleted)); load(account) }
                is Resource.Error -> toast(getString(R.string.msg_delete_failed, r.message))
                is Resource.Loading -> {}
            }
        }
    }

    private fun LoadBalancer.toZoneRuleItem(ctx: android.content.Context): ZoneRuleItem = ZoneRuleItem(
        id = id,
        title = name ?: id,
        subtitle = run {
            val ttlText = (ttl ?: "-").toString()
            if (proxied == true)
                ctx.getString(R.string.lb_ttl_proxied, ttlText)
            else
                ctx.getString(R.string.lb_ttl_dns_only, ttlText)
        },
        meta = run {
            val policy = steeringPolicy ?: "-"
            val poolCount = defaultPools?.size ?: 0
            if (policy == "-")
                ctx.getString(R.string.lb_policy_pools_default, poolCount)
            else
                ctx.getString(R.string.lb_policy_pools_format, policy, poolCount)
        },
        enabled = enabled,
        canDelete = true,
    )
}
