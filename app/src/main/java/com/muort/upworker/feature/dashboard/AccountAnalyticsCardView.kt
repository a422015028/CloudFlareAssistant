package com.muort.upworker.feature.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.muort.upworker.R
import com.muort.upworker.core.model.AccountAnalyticsOverview
import com.muort.upworker.core.model.RegionStatItem
import com.muort.upworker.core.model.TimeSeriesPoint
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.databinding.CardAccountAnalyticsBinding
import com.muort.upworker.databinding.ItemNetworkStatBinding
import com.muort.upworker.databinding.ItemRegionStatBinding
import timber.log.Timber

/**
 * 账户分析概览卡片（对应官网 /analytics 页面）
 * 聚合账户下所有 Zone 的 GraphQL 分析数据
 */
class AccountAnalyticsCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: CardAccountAnalyticsBinding

    var onRefreshClick: (() -> Unit)? = null
    var onTimeRangeChanged: ((TimeRange) -> Unit)? = null
    var onAnalyticsEnabledChanged: ((Boolean) -> Unit)? = null

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("account_analytics_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_ANALYTICS_ENABLED = "account_analytics_enabled"
        private const val REGION_PAGE_SIZE = 10

        // Cloudflare 分析中的非 ISO 国家码
        private val specialRegionCodes = setOf("T1", "XX")
    }

    private fun specialRegionName(code: String): String {
        return when (code) {
            "T1" -> context.getString(R.string.analytics_region_tor)
            "XX" -> context.getString(R.string.analytics_region_unknown)
            else -> code
        }
    }

    private var regionStats: List<RegionStatItem> = emptyList()
    private var regionPage = 0

    init {
        binding = CardAccountAnalyticsBinding.inflate(LayoutInflater.from(context), this, true)
        setupListeners()
        setupTimeRangeChips()
        setupSwitch()
        setupCharts()
    }

    /**
     * 迷你趋势图基础样式：无坐标轴、无图例、不可交互
     */
    private fun setupCharts() {
        listOf(
            binding.analyticsRequestsChart,
            binding.analyticsBandwidthChart,
            binding.analyticsVisitorsChart,
            binding.analyticsPageViewsChart,
            binding.analyticsEncryptedRequestsChart,
            binding.analyticsEncryptedRequestRateChart,
            binding.analyticsEncryptedBytesChart,
            binding.analyticsEncryptedBytesRateChart,
            binding.analyticsCachedRequestsChart,
            binding.analyticsCachedRequestRateChart,
            binding.analyticsCachedBytesChart,
            binding.analyticsCachedBytesRateChart,
            binding.analyticsError4xxChart,
            binding.analyticsError4xxRateChart,
            binding.analyticsError5xxChart,
            binding.analyticsError5xxRateChart
        ).forEach { chart ->
            applySparkChartStyle(chart)
        }
    }

    /**
     * 迷你趋势图基础样式：无坐标轴、无图例、不可交互
     * minOffset=0：默认 15dp 边距会把低高度图表的内容区压成负数，导致曲线渲染出界
     * axisMinimum=0：强制零线贴底，波动整体挂在基线上方（与官网一致）
     */
    private fun applySparkChartStyle(chart: LineChart) {
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            minOffset = 0f
            axisLeft.axisMinimum = 0f
            axisRight.axisMinimum = 0f
            xAxis.isEnabled = false
            axisLeft.isEnabled = false
            axisRight.isEnabled = false
            legend.isEnabled = false
            setHighlightPerDragEnabled(false)
            setHighlightPerTapEnabled(false)
            setDrawMarkers(false)
        }
    }

    private fun setupListeners() {
        binding.analyticsRefreshButton.setOnClickListener {
            onRefreshClick?.invoke()
        }

        binding.analyticsRegionPrevButton.setOnClickListener {
            if (regionPage > 0) {
                regionPage--
                renderRegionPage()
            }
        }

        binding.analyticsRegionNextButton.setOnClickListener {
            val totalPages = (regionStats.size + REGION_PAGE_SIZE - 1) / REGION_PAGE_SIZE
            if (regionPage < totalPages - 1) {
                regionPage++
                renderRegionPage()
            }
        }
    }

    private fun setupSwitch() {
        val isEnabled = prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
        binding.analyticsSwitch.isChecked = isEnabled
        updateVisibility(isEnabled)

        binding.analyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ANALYTICS_ENABLED, isChecked).apply()
            updateVisibility(isChecked)
            onAnalyticsEnabledChanged?.invoke(isChecked)
        }
    }

    private fun updateVisibility(isEnabled: Boolean) {
        binding.analyticsMainContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.analyticsStatusIndicator.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.analyticsStatusText.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.analyticsRefreshButton.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    fun isAnalyticsEnabled(): Boolean {
        return binding.analyticsSwitch.isChecked
    }

    private fun setupTimeRangeChips() {
        binding.analyticsTimeRangeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val timeRange = when (checkedIds[0]) {
                R.id.analyticsChip1Day -> TimeRange.ONE_DAY
                R.id.analyticsChip7Days -> TimeRange.SEVEN_DAYS
                R.id.analyticsChip30Days -> TimeRange.THIRTY_DAYS
                else -> TimeRange.ONE_DAY
            }

            updateTimeRangeHint(timeRange)
            onTimeRangeChanged?.invoke(timeRange)
        }

        updateTimeRangeHint(TimeRange.ONE_DAY)
    }

    private fun updateTimeRangeHint(timeRange: TimeRange) {
        binding.analyticsTimeRangeHintText.text = context.getString(R.string.dash_past_data, timeRange.displayName(context))
    }

    fun showLoading() {
        binding.analyticsLoadingContainer.visibility = View.VISIBLE
        binding.analyticsContentContainer.visibility = View.GONE
        binding.analyticsErrorContainer.visibility = View.GONE
        setStatus(context.getString(R.string.analytics_loading))
    }

    fun showError(message: String) {
        binding.analyticsLoadingContainer.visibility = View.GONE
        binding.analyticsContentContainer.visibility = View.GONE
        binding.analyticsErrorContainer.visibility = View.VISIBLE
        binding.analyticsErrorText.text = message
        setStatus(context.getString(R.string.analytics_error), R.color.md_theme_error)
    }

    fun showData(overview: AccountAnalyticsOverview) {
        binding.analyticsLoadingContainer.visibility = View.GONE
        binding.analyticsContentContainer.visibility = View.VISIBLE
        binding.analyticsErrorContainer.visibility = View.GONE

        updateMetrics(overview)
        updateCharts(overview)
        updateStatus(overview)
        updateNetworkStats(overview)
        updateRegionStats(overview)
        updateStorageStats(overview)
    }

    private fun updateMetrics(overview: AccountAnalyticsOverview) {
        // 核心指标
        binding.analyticsRequestsText.text = formatNumber(overview.requests)
        applyDelta(binding.analyticsRequestsDelta, overview.requestsDelta)
        binding.analyticsBandwidthText.text = formatBytes(overview.bandwidthBytes)
        applyDelta(binding.analyticsBandwidthDelta, overview.bandwidthDelta)
        binding.analyticsVisitorsText.text = formatNumber(overview.uniqueVisitors)
        applyDelta(binding.analyticsVisitorsDelta, overview.visitorsDelta)
        binding.analyticsPageViewsText.text = formatNumber(overview.pageViews)
        applyDelta(binding.analyticsPageViewsDelta, overview.pageViewsDelta)

        // 安全性
        binding.analyticsEncryptedRequestsText.text = formatNumber(overview.encryptedRequests)
        applyDelta(binding.analyticsEncryptedRequestsDelta, overview.encryptedRequestsDelta)
        binding.analyticsEncryptedRequestRateText.text = "${formatPercentage(overview.encryptedRequestRate)}%"
        applyDelta(binding.analyticsEncryptedRequestRateDelta, overview.encryptedRequestRateDelta)
        binding.analyticsEncryptedBytesText.text = formatBytes(overview.encryptedBytes)
        applyDelta(binding.analyticsEncryptedBytesDelta, overview.encryptedBytesDelta)
        binding.analyticsEncryptedBytesRateText.text = "${formatPercentage(overview.encryptedBytesRate)}%"
        applyDelta(binding.analyticsEncryptedBytesRateDelta, overview.encryptedBytesRateDelta)

        // 缓存
        binding.analyticsCachedRequestsText.text = formatNumber(overview.cachedRequests)
        applyDelta(binding.analyticsCachedRequestsDelta, overview.cachedRequestsDelta)
        binding.analyticsCachedRequestRateText.text = "${formatPercentage(overview.cachedRequestRate)}%"
        applyDelta(binding.analyticsCachedRequestRateDelta, overview.cachedRequestRateDelta)
        binding.analyticsCachedBytesText.text = formatBytes(overview.cachedBytes)
        applyDelta(binding.analyticsCachedBytesDelta, overview.cachedBytesDelta)
        binding.analyticsCachedBytesRateText.text = "${formatPercentage(overview.cachedBytesRate)}%"
        applyDelta(binding.analyticsCachedBytesRateDelta, overview.cachedBytesRateDelta)

        // 错误
        binding.analyticsError4xxText.text = formatNumber(overview.error4xxRequests)
        applyDelta(binding.analyticsError4xxDelta, overview.error4xxDelta)
        binding.analyticsError4xxRateText.text = "${formatPercentage(overview.error4xxRate)}%"
        applyDelta(binding.analyticsError4xxRateDelta, overview.error4xxRateDelta)
        binding.analyticsError5xxText.text = formatNumber(overview.error5xxRequests)
        applyDelta(binding.analyticsError5xxDelta, overview.error5xxDelta)
        binding.analyticsError5xxRateText.text = "${formatPercentage(overview.error5xxRate)}%"
        applyDelta(binding.analyticsError5xxRateDelta, overview.error5xxRateDelta)
    }

    /**
     * 设置环比标签（官网风格：↗ +12% 绿色 / ↘ -8% 红色 / → 0% 中性）
     */
    private fun applyDelta(deltaView: android.widget.TextView, delta: Double?) {
        if (delta == null) {
            deltaView.visibility = View.GONE
            return
        }

        val (arrow, color) = when {
            delta > 0 -> "↗" to context.getColor(android.R.color.holo_green_dark)
            delta < 0 -> "↘" to context.getColor(R.color.md_theme_error)
            else -> "→" to com.google.android.material.color.MaterialColors.getColor(
                context, com.google.android.material.R.attr.colorOnSurfaceVariant, android.graphics.Color.GRAY
            )
        }

        val absValue = kotlin.math.abs(delta)
        val valueText = String.format("%.2f", absValue)

        deltaView.visibility = View.VISIBLE
        deltaView.text = "$arrow ${if (delta < 0) "-" else "+"}$valueText%"
        deltaView.setTextColor(color)
    }

    private fun updateCharts(overview: AccountAnalyticsOverview) {
        // 核心指标
        updateSparkChart(
            binding.analyticsRequestsChart,
            overview.requestsTimeSeries,
            context.getColor(R.color.purple_700)
        )
        updateSparkChart(
            binding.analyticsBandwidthChart,
            overview.bandwidthTimeSeries,
            context.getColor(android.R.color.holo_blue_dark)
        )
        updateSparkChart(
            binding.analyticsVisitorsChart,
            overview.visitorsTimeSeries,
            context.getColor(android.R.color.holo_green_dark)
        )
        updateSparkChart(
            binding.analyticsPageViewsChart,
            overview.pageViewsTimeSeries,
            context.getColor(R.color.md_theme_tertiary)
        )
        // 安全性（数值与比率同色）
        updateSparkChart(
            binding.analyticsEncryptedRequestsChart,
            overview.encryptedRequestsTimeSeries,
            context.getColor(android.R.color.holo_blue_dark)
        )
        updateSparkChart(
            binding.analyticsEncryptedRequestRateChart,
            overview.encryptedRequestRateTimeSeries,
            context.getColor(android.R.color.holo_blue_dark)
        )
        updateSparkChart(
            binding.analyticsEncryptedBytesChart,
            overview.encryptedBytesTimeSeries,
            context.getColor(android.R.color.holo_blue_bright)
        )
        updateSparkChart(
            binding.analyticsEncryptedBytesRateChart,
            overview.encryptedBytesRateTimeSeries,
            context.getColor(android.R.color.holo_blue_bright)
        )
        // 缓存
        updateSparkChart(
            binding.analyticsCachedRequestsChart,
            overview.cachedRequestsTimeSeries,
            context.getColor(android.R.color.holo_green_dark)
        )
        updateSparkChart(
            binding.analyticsCachedRequestRateChart,
            overview.cachedRequestRateTimeSeries,
            context.getColor(android.R.color.holo_green_dark)
        )
        updateSparkChart(
            binding.analyticsCachedBytesChart,
            overview.cachedBytesTimeSeries,
            context.getColor(android.R.color.holo_green_light)
        )
        updateSparkChart(
            binding.analyticsCachedBytesRateChart,
            overview.cachedBytesRateTimeSeries,
            context.getColor(android.R.color.holo_green_light)
        )
        // 错误
        updateSparkChart(
            binding.analyticsError4xxChart,
            overview.error4xxTimeSeries,
            context.getColor(android.R.color.holo_orange_dark)
        )
        updateSparkChart(
            binding.analyticsError4xxRateChart,
            overview.error4xxRateTimeSeries,
            context.getColor(android.R.color.holo_orange_dark)
        )
        updateSparkChart(
            binding.analyticsError5xxChart,
            overview.error5xxTimeSeries,
            context.getColor(R.color.md_theme_error)
        )
        updateSparkChart(
            binding.analyticsError5xxRateChart,
            overview.error5xxRateTimeSeries,
            context.getColor(R.color.md_theme_error)
        )
    }

    /**
     * 迷你趋势图（官网风格的 sparkline）
     */
    private fun updateSparkChart(chart: LineChart, series: List<TimeSeriesPoint>, color: Int) {
        if (series.isEmpty()) {
            chart.clear()
            chart.invalidate()
            return
        }

        try {
            val entries = series.mapIndexed { index, point ->
                Entry(index.toFloat(), point.value.toFloat())
            }
            val dataSet = LineDataSet(entries, "").apply {
                this.color = color
                setCircleColor(color)
                lineWidth = 1.5f
                // 单点无法画折线，放大圆点保证可见
                circleRadius = if (entries.size == 1) 3f else 1.5f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
                setDrawFilled(entries.size > 1)
                fillColor = color
                fillAlpha = 60
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update spark chart")
            chart.clear()
        }
    }

    /**
     * 根据错误率推导健康状态（与仪表盘卡片一致的指示器样式）
     */
    private fun updateStatus(overview: AccountAnalyticsOverview) {
        when {
            overview.error5xxRate > 5.0 -> setStatus(context.getString(R.string.analytics_status_critical), R.color.md_theme_error)
            overview.error5xxRate > 1.0 || overview.error4xxRate > 20.0 -> setStatus(context.getString(R.string.analytics_status_warning), R.color.md_theme_tertiary)
            else -> setStatus(context.getString(R.string.analytics_status_normal), android.R.color.holo_green_dark)
        }
    }

    private fun setStatus(text: String, colorResId: Int? = null) {
        binding.analyticsStatusText.text = text
        binding.analyticsStatusIndicator.backgroundTintList = if (colorResId != null) {
            ColorStateList.valueOf(context.getColor(colorResId))
        } else {
            ColorStateList.valueOf(
                com.google.android.material.color.MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorPrimary,
                    android.graphics.Color.GRAY
                )
            )
        }
    }

    /**
     * 渲染网络区块（HTTP 版本 / SSL 协议 / 内容类型），官网风格：
     * 名称左对齐 + 请求数右对齐 + 下方按最大值等比的细条形图
     */
    private fun updateNetworkStats(overview: AccountAnalyticsOverview) {
        val hasNetworkData = overview.httpVersionStats.isNotEmpty() ||
            overview.sslProtocolStats.isNotEmpty() ||
            overview.contentTypeStats.isNotEmpty()
        binding.analyticsNetworkSectionTitle.visibility = if (hasNetworkData) View.VISIBLE else View.GONE

        renderNetworkList(
            binding.analyticsHttpVersionSubtitle,
            binding.analyticsHttpVersionContainer,
            overview.httpVersionStats,
            context.getColor(android.R.color.holo_blue_dark)
        )
        renderNetworkList(
            binding.analyticsSslSubtitle,
            binding.analyticsSslProtocolContainer,
            overview.sslProtocolStats,
            context.getColor(R.color.purple_700)
        )
        renderNetworkList(
            binding.analyticsContentTypeSubtitle,
            binding.analyticsContentTypeContainer,
            overview.contentTypeStats,
            context.getColor(android.R.color.holo_green_dark)
        )
    }

    private fun renderNetworkList(
        subtitle: android.widget.TextView,
        container: android.widget.LinearLayout,
        stats: List<com.muort.upworker.core.model.NetworkStatItem>,
        barColor: Int
    ) {
        val visible = stats.isNotEmpty()
        subtitle.visibility = if (visible) View.VISIBLE else View.GONE
        container.visibility = if (visible) View.VISIBLE else View.GONE
        container.removeAllViews()
        if (!visible) return

        val inflater = LayoutInflater.from(context)
        val maxRequests = stats.maxOf { it.requests }.toFloat()

        stats.forEach { item ->
            val row = ItemNetworkStatBinding.inflate(inflater, container, false)
            row.networkStatName.text = item.name
            row.networkStatValue.text = formatNumber(item.requests)

            val track = row.networkStatBarFilled.parent as android.view.ViewGroup
            track.post {
                val ratio = (item.requests / maxRequests).coerceIn(0.02f, 1f)
                row.networkStatBarFilled.layoutParams = android.widget.FrameLayout.LayoutParams(
                    (track.width * ratio).toInt().coerceAtLeast(6),
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            row.networkStatBarFilled.backgroundTintList = ColorStateList.valueOf(barColor)
            container.addView(row.root)
        }
    }

    /**
     * 渲染地区分布区块：国家 | 请求数+迷你图 | 带宽+迷你图，按请求数降序分页展示
     */
    private fun updateRegionStats(overview: AccountAnalyticsOverview) {
        regionStats = overview.regionStats
        regionPage = 0

        val visible = regionStats.isNotEmpty()
        binding.analyticsRegionSectionTitle.visibility = if (visible) View.VISIBLE else View.GONE
        binding.analyticsRegionHeaderRow.visibility = if (visible) View.VISIBLE else View.GONE
        binding.analyticsRegionContainer.visibility = if (visible) View.VISIBLE else View.GONE
        binding.analyticsRegionPagination.visibility =
            if (visible && regionStats.size > REGION_PAGE_SIZE) View.VISIBLE else View.GONE

        if (visible) renderRegionPage()
    }

    private fun renderRegionPage() {
        val totalPages = (regionStats.size + REGION_PAGE_SIZE - 1) / REGION_PAGE_SIZE
        if (regionPage >= totalPages) regionPage = totalPages - 1
        if (regionPage < 0) regionPage = 0

        val container = binding.analyticsRegionContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(context)
        val sparkColor = context.getColor(android.R.color.holo_blue_dark)

        val start = regionPage * REGION_PAGE_SIZE
        val end = minOf(start + REGION_PAGE_SIZE, regionStats.size)
        for (index in start until end) {
            val item = regionStats[index]
            val row = ItemRegionStatBinding.inflate(inflater, container, false)
            row.regionStatName.text = countryDisplayName(item.name)
            row.regionRequestsValue.text = formatNumber(item.requests)
            row.regionBandwidthValue.text = formatBytes(item.bytes)

            applySparkChartStyle(row.regionRequestsChart)
            applySparkChartStyle(row.regionBandwidthChart)
            updateSparkChart(row.regionRequestsChart, item.requestsTimeSeries, sparkColor)
            updateSparkChart(row.regionBandwidthChart, item.bytesTimeSeries, sparkColor)

            container.addView(row.root)
        }

        binding.analyticsRegionPageInfo.text = context.getString(R.string.dash_region_page_info, start + 1, end, regionStats.size)
        binding.analyticsRegionPrevButton.isEnabled = regionPage > 0
        binding.analyticsRegionNextButton.isEnabled = regionPage < totalPages - 1
    }

    /**
     * ISO 3166-1 alpha-2 国家码转本地化显示名；Cloudflare 特殊码单独映射
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

    /**
     * 更新 D1 / R2 / KV 存储监控指标
     */
    private fun updateStorageStats(overview: AccountAnalyticsOverview) {
        // D1 数据库
        binding.analyticsD1ReadsText.text = formatNumber(overview.d1ReadRows)
        binding.analyticsD1WritesText.text = formatNumber(overview.d1WriteRows)
        binding.analyticsD1StorageText.text = formatBytes(overview.d1StorageBytes)
        binding.analyticsD1CountText.text = formatNumber(overview.d1DatabaseCount.toLong())

        // R2 对象存储
        binding.analyticsR2ClassAText.text = formatNumber(overview.r2ClassAOperations)
        binding.analyticsR2ClassBText.text = formatNumber(overview.r2ClassBOperations)
        binding.analyticsR2StorageText.text = formatBytes(overview.r2StorageBytes)
        binding.analyticsR2CountText.text = formatNumber(overview.r2BucketCount.toLong())

        // KV 命名空间
        binding.analyticsKvReadsText.text = formatNumber(overview.kvReads)
        binding.analyticsKvWritesText.text = formatNumber(overview.kvWrites)
        binding.analyticsKvStorageText.text = formatBytes(overview.kvStorageBytes)
        binding.analyticsKvCountText.text = formatNumber(overview.kvNamespaceCount.toLong())
    }

    private fun formatNumber(value: Long): String {
        return when {
            value >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_099_511_627_776L -> String.format("%.2f TB", bytes / 1_099_511_627_776.0)
            bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatPercentage(value: Double): String {
        return when {
            value >= 99.995 -> "100"
            else -> String.format("%.2f", value)
        }
    }
}
