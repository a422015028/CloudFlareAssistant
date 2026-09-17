package com.muort.upworker.feature.zerotrust.gateway

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.GatewayDnsAnalytics
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.databinding.FragmentGatewayAnalyticsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Gateway DNS 查询分析
 */
@AndroidEntryPoint
class GatewayAnalyticsFragment : Fragment() {

    private var _binding: FragmentGatewayAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    // 热门域名 / 命中策略分组适配器
    private lateinit var domainsAdapter: DnsAnalyticsAdapter
    private lateinit var policiesAdapter: DnsAnalyticsAdapter

    private lateinit var opsAdapter: DnsAnalyticsAdapter
    private lateinit var countriesAdapter: DnsAnalyticsAdapter
    private lateinit var locationsAdapter: DnsAnalyticsAdapter
    private lateinit var blockedUsersAdapter: DnsAnalyticsAdapter
    private lateinit var allowedUsersAdapter: DnsAnalyticsAdapter

    // 每个区块显示的条目数（Top N），默认 5
    private var topN: Int = 5
    // 当前查询时间范围，默认 24 小时
    private var currentTimeRange: TimeRange = TimeRange.ONE_DAY

    private val topNOptions = listOf(5, 10, 15, 25)

    // 时间范围与 Top N 的跨打开持久化
    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var topNPopup: ListPopupWindow? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGatewayAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        restorePreferences()
        setupRecyclerViews()
        setupTimeRangeChips()
        observeViewModel()
    }

    /**
     * 恢复上次选择的时间范围与 Top N；非法/缺失值回退到默认
     */
    private fun restorePreferences() {
        currentTimeRange = runCatching {
            TimeRange.valueOf(prefs.getString(KEY_TIME_RANGE, null) ?: TimeRange.ONE_DAY.name)
        }.getOrDefault(TimeRange.ONE_DAY)

        topN = prefs.getInt(KEY_TOP_N, topN).takeIf { it in topNOptions } ?: 5

        // XML 默认选中 24 小时，这里按记忆值显式勾选
        binding.gatewayDnsTimeRangeChipGroup.check(chipIdFor(currentTimeRange))
    }

    override fun onResume() {
        super.onResume()
        // 每次进入页面/切回该标签都实时拉取云端数据，不使用缓存
        loadData()
    }

    private fun setupRecyclerViews() {
        opsAdapter = DnsAnalyticsAdapter()
        domainsAdapter = DnsAnalyticsAdapter()
        countriesAdapter = DnsAnalyticsAdapter()
        locationsAdapter = DnsAnalyticsAdapter()
        policiesAdapter = DnsAnalyticsAdapter()
        blockedUsersAdapter = DnsAnalyticsAdapter()
        allowedUsersAdapter = DnsAnalyticsAdapter()

        binding.opsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = opsAdapter
        }
        binding.domainsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = domainsAdapter
        }
        binding.countriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = countriesAdapter
        }
        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = locationsAdapter
        }
        binding.policiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = policiesAdapter
        }
        binding.blockedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = blockedUsersAdapter
        }
        binding.allowedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = allowedUsersAdapter
        }

        binding.retryButton.setOnClickListener { loadData() }

        setupTopNDropdown()
    }

    private fun setupTopNDropdown() {
        val optionLabels = topNOptions.map { getString(R.string.zt_gateway_dns_top_count, it) }
        updateTopNLabel()

        // 使用 ListPopupWindow 而非 AutoCompleteTextView：
        // 后者自带文本过滤/焦点处理，选项点击在部分场景下不回调，导致切换无反应
        binding.itemsDropdown.setOnClickListener { anchor ->
            topNPopup?.dismiss()
            val minWidthPx = (96 * resources.displayMetrics.density).toInt()
            val popup = ListPopupWindow(requireContext()).apply {
                setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        optionLabels
                    )
                )
                anchorView = anchor
                isModal = true
                width = anchor.width.coerceAtLeast(minWidthPx)
                setOnItemClickListener { _, _, position, _ ->
                    val newTopN = topNOptions[position]
                    dismiss()
                    if (newTopN != topN) {
                        topN = newTopN
                        updateTopNLabel()
                        prefs.edit().putInt(KEY_TOP_N, topN).apply()
                        // 与时间范围切换一致：带新的 limit 重新请求云端
                        loadData()
                    }
                }
                setOnDismissListener { topNPopup = null }
            }
            topNPopup = popup
            popup.show()
        }
    }

    private fun updateTopNLabel() {
        binding.itemsDropdownText.text = getString(R.string.zt_gateway_dns_top_count, topN)
    }

    private fun setupTimeRangeChips() {
        binding.gatewayDnsTimeRangeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val timeRange = when (checkedIds[0]) {
                R.id.gatewayDnsChip1Day -> TimeRange.ONE_DAY
                R.id.gatewayDnsChip7Days -> TimeRange.SEVEN_DAYS
                R.id.gatewayDnsChip30Days -> TimeRange.THIRTY_DAYS
                else -> TimeRange.ONE_DAY
            }

            if (timeRange != currentTimeRange) {
                currentTimeRange = timeRange
                prefs.edit().putString(KEY_TIME_RANGE, timeRange.name).apply()
                loadData()
            }
        }
    }

    private fun chipIdFor(timeRange: TimeRange): Int = when (timeRange) {
        TimeRange.ONE_DAY -> R.id.gatewayDnsChip1Day
        TimeRange.SEVEN_DAYS -> R.id.gatewayDnsChip7Days
        TimeRange.THIRTY_DAYS -> R.id.gatewayDnsChip30Days
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dnsAnalyticsLoading.collect { loading ->
                        binding.loadingContainer.visibility =
                            if (loading) View.VISIBLE else View.GONE
                        // 实时拉取期间隐藏上一次的数据，避免把旧数据当作当前结果展示
                        if (loading) {
                            binding.contentContainer.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.dnsAnalyticsError.collect { error ->
                        if (error != null) {
                            binding.errorContainer.visibility = View.VISIBLE
                            binding.contentContainer.visibility = View.GONE
                            binding.errorText.text =
                                getString(R.string.store_readme_failed, error)
                        } else {
                            binding.errorContainer.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.dnsAnalytics.collect { analytics ->
                        analytics?.let { renderAnalytics(it) }
                    }
                }
            }
        }
    }

    private fun renderAnalytics(data: GatewayDnsAnalytics) {
        val isEmpty = data.operations.isEmpty() && data.domains.isEmpty() &&
                data.countries.isEmpty() && data.locations.isEmpty() && data.policies.isEmpty()

        binding.contentContainer.visibility = View.VISIBLE
        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE

        if (isEmpty) {
            return
        }

        // 阻止次数最多的用户
        val blockedDecisions = setOf("3", "6", "9")
        val totalBlocked = data.operations
            .filter { it.resolverDecision in blockedDecisions }
            .sumOf { it.count }

        // 允许次数最多的用户
        val allowedDecisions = setOf("4", "5", "7", "8", "10")
        val totalAllowed = data.operations
            .filter { it.resolverDecision in allowedDecisions }
            .sumOf { it.count }

        opsAdapter.submitList(
            data.operations.take(topN)
                .map { DnsAnalyticsAdapter.Item(mapResolverDecision(it.resolverDecision), it.count) }
                .toMutableList()
        )
        domainsAdapter.submitList(
            data.domains.take(topN)
                .map { DnsAnalyticsAdapter.Item(it.queryName, it.count) }
                .toMutableList()
        )
        countriesAdapter.submitList(
            data.countries.take(topN)
                .map { DnsAnalyticsAdapter.Item(countryDisplayName(it.countryCode), it.count) }
                .toMutableList()
        )
        locationsAdapter.submitList(
            data.locations.take(topN)
                .map { DnsAnalyticsAdapter.Item(it.locationName, it.count) }
                .toMutableList()
        )
        policiesAdapter.submitList(
            data.policies.take(topN)
                .map { DnsAnalyticsAdapter.Item(it.policyName, it.count) }
                .toMutableList()
        )
        blockedUsersAdapter.submitList(
            if (totalBlocked > 0) {
                mutableListOf(DnsAnalyticsAdapter.Item(getString(R.string.zt_gateway_dns_no_identity), totalBlocked))
            } else {
                mutableListOf()
            }
        )
        allowedUsersAdapter.submitList(
            if (totalAllowed > 0) {
                mutableListOf(DnsAnalyticsAdapter.Item(getString(R.string.zt_gateway_dns_no_identity), totalAllowed))
            } else {
                mutableListOf()
            }
        )
    }

    /**
     * resolverDecision 维度返回数值（如 5、10），映射为中文描述
     * 3=类别阻止, 4=无匹配位置时允许, 5=无策略匹配时允许,
     * 6=始终阻止的类别, 7=安全搜索覆盖, 8=已应用覆盖,
     * 9=规则阻止, 10=规则允许
     */
    private fun mapResolverDecision(decision: String): String {
        val resId = when (decision) {
            "3" -> R.string.zt_gateway_dns_decision_blocked_category
            "4" -> R.string.zt_gateway_dns_decision_allowed_no_location
            "5" -> R.string.zt_gateway_dns_decision_allowed_no_policy
            "6" -> R.string.zt_gateway_dns_decision_blocked_always_category
            "7" -> R.string.zt_gateway_dns_decision_override_safesearch
            "8" -> R.string.zt_gateway_dns_decision_override_applied
            "9" -> R.string.zt_gateway_dns_decision_blocked_rule
            "10" -> R.string.zt_gateway_dns_decision_allowed_rule
            else -> R.string.status_unknown
        }
        return getString(resId)
    }

    /**
     * ISO 3166-1 alpha-2 国家码转本地化显示名；Cloudflare 特殊码单独映射
     * 与账号分析概览地区分布保持一致的显示逻辑
     */
    private fun countryDisplayName(code: String): String {
        if (code.isBlank()) return code
        if (specialRegionCodes.contains(code)) return specialRegionName(code)
        return try {
            val name = java.util.Locale.Builder()
                .setRegion(code)
                .build()
                .getDisplayCountry(java.util.Locale.getDefault())
            if (name.isBlank() || name == code) code else name
        } catch (e: Exception) {
            code
        }
    }

    private fun specialRegionName(code: String): String {
        return when (code) {
            "T1" -> getString(R.string.analytics_region_tor)
            "XX" -> getString(R.string.analytics_region_unknown)
            else -> code
        }
    }

    private fun loadData() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            binding.errorContainer.visibility = View.VISIBLE
            binding.contentContainer.visibility = View.GONE
            binding.errorText.text = getString(R.string.app_no_account_selected)
            return
        }
        binding.errorContainer.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadDnsAnalytics(account, currentTimeRange, topN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        topNPopup?.dismiss()
        topNPopup = null
        _binding = null
    }

    companion object {
        private const val PREFS_NAME = "gateway_dns_analytics_prefs"
        private const val KEY_TIME_RANGE = "time_range"
        private const val KEY_TOP_N = "top_n"

        // Cloudflare 分析中的非 ISO 国家码
        private val specialRegionCodes = setOf("T1", "XX")
    }
}
