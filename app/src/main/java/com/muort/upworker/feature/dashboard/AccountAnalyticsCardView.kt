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
import com.muort.upworker.core.model.TimeSeriesPoint
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.databinding.CardAccountAnalyticsBinding
import timber.log.Timber
import kotlin.math.roundToInt

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
    }

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
            chart.apply {
                description.isEnabled = false
                setTouchEnabled(false)
                isDragEnabled = false
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                xAxis.isEnabled = false
                axisLeft.isEnabled = false
                axisRight.isEnabled = false
                legend.isEnabled = false
                setHighlightPerDragEnabled(false)
                setHighlightPerTapEnabled(false)
                setDrawMarkers(false)
            }
        }
    }

    private fun setupListeners() {
        binding.analyticsRefreshButton.setOnClickListener {
            onRefreshClick?.invoke()
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
        binding.analyticsTimeRangeHintText.text = "过去 ${timeRange.displayName} 数据"
    }

    fun showLoading() {
        binding.analyticsLoadingContainer.visibility = View.VISIBLE
        binding.analyticsContentContainer.visibility = View.GONE
        binding.analyticsErrorContainer.visibility = View.GONE
        setStatus("加载中")
    }

    fun showError(message: String) {
        binding.analyticsLoadingContainer.visibility = View.GONE
        binding.analyticsContentContainer.visibility = View.GONE
        binding.analyticsErrorContainer.visibility = View.VISIBLE
        binding.analyticsErrorText.text = message
        setStatus("错误", R.color.md_theme_error)
    }

    fun showData(overview: AccountAnalyticsOverview) {
        binding.analyticsLoadingContainer.visibility = View.GONE
        binding.analyticsContentContainer.visibility = View.VISIBLE
        binding.analyticsErrorContainer.visibility = View.GONE

        updateMetrics(overview)
        updateCharts(overview)
        updateStatus(overview)
    }

    private fun updateMetrics(overview: AccountAnalyticsOverview) {
        // 核心指标
        binding.analyticsRequestsText.text = formatNumber(overview.requests)
        binding.analyticsBandwidthText.text = formatBytes(overview.bandwidthBytes)
        binding.analyticsVisitorsText.text = formatNumber(overview.uniqueVisitors)
        binding.analyticsPageViewsText.text = formatNumber(overview.pageViews)

        // 安全性
        binding.analyticsEncryptedRequestsText.text = formatNumber(overview.encryptedRequests)
        binding.analyticsEncryptedRequestRateText.text = "${formatPercentage(overview.encryptedRequestRate)}%"
        binding.analyticsEncryptedBytesText.text = formatBytes(overview.encryptedBytes)
        binding.analyticsEncryptedBytesRateText.text = "${formatPercentage(overview.encryptedBytesRate)}%"

        // 缓存
        binding.analyticsCachedRequestsText.text = formatNumber(overview.cachedRequests)
        binding.analyticsCachedRequestRateText.text = "${formatPercentage(overview.cachedRequestRate)}%"
        binding.analyticsCachedBytesText.text = formatBytes(overview.cachedBytes)
        binding.analyticsCachedBytesRateText.text = "${formatPercentage(overview.cachedBytesRate)}%"

        // 错误
        binding.analyticsError4xxText.text = formatNumber(overview.error4xxRequests)
        binding.analyticsError4xxRateText.text = "${formatPercentage(overview.error4xxRate)}%"
        binding.analyticsError5xxText.text = formatNumber(overview.error5xxRequests)
        binding.analyticsError5xxRateText.text = "${formatPercentage(overview.error5xxRate)}%"
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
                circleRadius = 1.5f
                setDrawCircleHole(false)
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
                setDrawFilled(true)
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
            overview.error5xxRate > 5.0 -> setStatus("严重", R.color.md_theme_error)
            overview.error5xxRate > 1.0 || overview.error4xxRate > 20.0 -> setStatus("警告", R.color.md_theme_tertiary)
            else -> setStatus("正常", android.R.color.holo_green_dark)
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
            value >= 100.0 -> "100"
            value >= 10.0 -> value.roundToInt().toString()
            value >= 1.0 -> String.format("%.1f", value)
            else -> String.format("%.2f", value)
        }
    }
}
