package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TrafficDataPoint
import com.muort.upworker.core.repository.ZoneTrafficRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * 流量分析页：展示最近 7 天的 HTTP 请求 / 字节 / 威胁 / 访客 / 缓存请求数。
 * 复用 item_zone_rule.xml，每行一天。
 */
@AndroidEntryPoint
class AnalyticsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var trafficRepo: ZoneTrafficRepository

    private lateinit var adapter: ZoneRuleAdapter

    override val emptyText: String = "暂无流量数据"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ZoneRuleAdapter()
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            when (val r = trafficRepo.getZoneTraffic(account, zoneId, dayCount = 7)) {
                is Resource.Success -> {
                    val items = r.data.map { it.toZoneRuleItem() }
                    if (items.isEmpty()) showEmpty() else {
                        showList(); adapter.submitList(items)
                    }
                }
                is Resource.Error -> showError(r.message)
                is Resource.Loading -> {}
            }
        }
    }

    private fun TrafficDataPoint.toZoneRuleItem(): ZoneRuleItem {
        val dateDisplay = try {
            SimpleDateFormat("MM-dd EEE", Locale.CHINA)
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!)
        } catch (e: Exception) { date }
        val mb = bytes / (1024.0 * 1024.0)
        val mbStr = if (mb >= 1024) "%.2f GB".format(mb / 1024) else "%.2f MB".format(mb)
        return ZoneRuleItem(
            id = date,
            title = dateDisplay,
            subtitle = "请求 $requests · 缓存 $cachedRequests · 威胁 $threats",
            meta = "流量 $mbStr · 访客 $uniques · PV $pageViews",
            enabled = null,
            canDelete = false,
        )
    }
}
