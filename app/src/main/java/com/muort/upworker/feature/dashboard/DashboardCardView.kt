package com.muort.upworker.feature.dashboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.muort.upworker.R
import com.muort.upworker.core.model.DashboardMetrics
import com.muort.upworker.core.model.HealthStatus
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.databinding.CardDashboardBinding
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 仪表盘自定义卡片视图
 * 展示 Analytics 指标和图表
 */
class DashboardCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding: CardDashboardBinding
    
    var onRefreshClick: (() -> Unit)? = null
    var onTimeRangeChanged: ((TimeRange) -> Unit)? = null

    init {
        binding = CardDashboardBinding.inflate(LayoutInflater.from(context), this, true)
        setupListeners()
        setupChart()
        setupPieChart()
        setupBarChart()
        setupTimeRangeChips()
    }

    private fun setupListeners() {
        binding.refreshButton.setOnClickListener {
            onRefreshClick?.invoke()
        }
    }
    
    private fun setupTimeRangeChips() {
        binding.timeRangeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val timeRange = when (checkedIds[0]) {
                R.id.chip1Day -> TimeRange.ONE_DAY
                R.id.chip7Days -> TimeRange.SEVEN_DAYS
                R.id.chip30Days -> TimeRange.THIRTY_DAYS
                else -> TimeRange.ONE_DAY
            }
            
            onTimeRangeChanged?.invoke(timeRange)
        }
    }

    /**
     * 设置图表样式
     */
    private fun setupChart() {
        binding.chartView.apply {
            // 基本设置
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            setDrawGridBackground(false)
            
            // X 轴设置
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = context.getColor(android.R.color.darker_gray)
                valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        return dateFormat.format(Date(value.toLong()))
                    }
                }
            }
            
            // 左侧 Y 轴设置
            axisLeft.apply {
                setDrawGridLines(true)
                textColor = context.getColor(android.R.color.darker_gray)
                gridColor = context.getColor(android.R.color.darker_gray)
                gridLineWidth = 0.5f
            }
            
            // 右侧 Y 轴禁用
            axisRight.isEnabled = false
            
            // 图例设置
            legend.apply {
                isEnabled = true
                textColor = context.getColor(android.R.color.darker_gray)
            }
        }
    }

    /**
     * 更新图表
     */
    private fun updateChart(metrics: DashboardMetrics) {
        if (metrics.requestsTimeSeries.isEmpty()) {
            binding.chartView.visibility = View.GONE
            Timber.w("No time series data available for chart")
            return
        }
        
        binding.chartView.visibility = View.VISIBLE
        
        try {
            val dataSets = mutableListOf<LineDataSet>()
            
            // 1. 请求数时间序列 (主线 - 紫色)
            if (metrics.requestsTimeSeries.isNotEmpty()) {
                val requestsEntries = metrics.requestsTimeSeries.map { point ->
                    Entry(point.timestamp.toFloat(), point.value.toFloat())
                }
                dataSets.add(LineDataSet(requestsEntries, "请求数").apply {
                    color = context.getColor(R.color.purple_700)
                    setCircleColor(context.getColor(R.color.purple_700))
                    lineWidth = 2.5f
                    circleRadius = 3f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                    setDrawFilled(false)
                })
            }
            
            // 2. 带宽时间序列 (蓝色)
            if (metrics.bandwidthTimeSeries.isNotEmpty()) {
                val bandwidthEntries = metrics.bandwidthTimeSeries.map { point ->
                    Entry(point.timestamp.toFloat(), point.value.toFloat())
                }
                dataSets.add(LineDataSet(bandwidthEntries, "带宽 (MB)").apply {
                    color = context.getColor(android.R.color.holo_blue_dark)
                    setCircleColor(context.getColor(android.R.color.holo_blue_dark))
                    lineWidth = 2f
                    circleRadius = 2.5f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                })
            }
            
            // 3. 威胁拦截时间序列 (红色)
            if (metrics.threatsTimeSeries.isNotEmpty()) {
                val threatsEntries = metrics.threatsTimeSeries.map { point ->
                    Entry(point.timestamp.toFloat(), point.value.toFloat())
                }
                dataSets.add(LineDataSet(threatsEntries, "威胁").apply {
                    color = context.getColor(R.color.md_theme_error)
                    setCircleColor(context.getColor(R.color.md_theme_error))
                    lineWidth = 2f
                    circleRadius = 2.5f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                })
            }
            
            // 4. 缓存字节数时间序列 (绿色)
            if (metrics.cachedBytesTimeSeries.isNotEmpty()) {
                val cachedEntries = metrics.cachedBytesTimeSeries.map { point ->
                    Entry(point.timestamp.toFloat(), point.value.toFloat())
                }
                dataSets.add(LineDataSet(cachedEntries, "缓存 (MB)").apply {
                    color = context.getColor(android.R.color.holo_green_dark)
                    setCircleColor(context.getColor(android.R.color.holo_green_dark))
                    lineWidth = 2f
                    circleRadius = 2.5f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                })
            }
            
            // 5. 页面浏览量时间序列 (橙色)
            if (metrics.pageViewsTimeSeries.isNotEmpty()) {
                val pageViewsEntries = metrics.pageViewsTimeSeries.map { point ->
                    Entry(point.timestamp.toFloat(), point.value.toFloat())
                }
                dataSets.add(LineDataSet(pageViewsEntries, "页面浏览").apply {
                    color = context.getColor(R.color.md_theme_tertiary)
                    setCircleColor(context.getColor(R.color.md_theme_tertiary))
                    lineWidth = 2f
                    circleRadius = 2.5f
                    setDrawCircleHole(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f
                })
            }
            
            // 应用数据到图表
            val lineData = LineData(dataSets.toList())
            binding.chartView.data = lineData
            binding.chartView.invalidate()
            
            Timber.d("Chart updated with ${dataSets.size} datasets")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update chart")
            binding.chartView.visibility = View.GONE
        }
    }

    /**
     * 初始化饼图
     */
    private fun setupPieChart() {
        with(binding.pieChart) {
            description.isEnabled = false
            setDrawHoleEnabled(true)
            setHoleColor(Color.TRANSPARENT)
            holeRadius = 58f
            transparentCircleRadius = 61f
            setDrawCenterText(true)
            setCenterTextSize(12f)
            setDrawEntryLabels(true)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(10f)
            legend.isEnabled = true
            legend.textSize = 10f
            legend.textColor = Color.BLACK
            setTouchEnabled(true)
            animateY(1000)
        }
    }

    /**
     * 初始化柱状图
     */
    private fun setupBarChart() {
        with(binding.barChart) {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setMaxVisibleValueCount(60)
            setPinchZoom(false)
            setDrawGridBackground(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                labelCount = 5
                textSize = 9f
                textColor = Color.BLACK
                valueFormatter = object : ValueFormatter() {
                    private val labels = listOf("请求", "带宽", "缓存", "威胁", "访客")
                    override fun getFormattedValue(value: Float): String {
                        return labels.getOrNull(value.toInt()) ?: ""
                    }
                }
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.LTGRAY
                textSize = 9f
                textColor = Color.BLACK
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            animateY(1000)
        }
    }

    /**
     * 更新饼图数据
     */
    private fun updatePieChart(metrics: DashboardMetrics) {
        val entries = ArrayList<PieEntry>()
        
        // 缓存命中率分布
        val cacheHitRate = metrics.cacheHitRate.toFloat()
        val cacheMissRate = 100f - cacheHitRate
        
        if (cacheHitRate > 0) {
            entries.add(PieEntry(cacheHitRate, "缓存命中"))
        }
        if (cacheMissRate > 0) {
            entries.add(PieEntry(cacheMissRate, "缓存未命中"))
        }
        
        if (entries.isEmpty()) {
            binding.pieChart.clear()
            binding.pieChart.centerText = "暂无数据"
            return
        }
        
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"), // 绿色 - 缓存命中
                Color.parseColor("#FFC107")  // 橙色 - 缓存未命中
            )
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            sliceSpace = 3f
            selectionShift = 5f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}%"
                }
            }
        }
        
        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.centerText = "缓存分布"
        binding.pieChart.invalidate()
    }

    /**
     * 更新柱状图数据
     */
    private fun updateBarChart(metrics: DashboardMetrics) {
        val entries = ArrayList<BarEntry>()
        
        // 使用对数缩放处理不同量级的数据
        // 将带宽和缓存从字节转换为MB，使量级更接近
        val requestsValue = metrics.totalRequests.toFloat()
        val bandwidthMB = (metrics.bandwidthBytes / 1_048_576.0).toFloat() // 转换为MB
        val cachedMB = (metrics.dataSaved / 1_048_576.0).toFloat() // 转换为MB
        val threatsValue = metrics.threatsBlocked.toFloat()
        val visitorsValue = metrics.uniqueVisitors.toFloat()
        
        // 使用对数函数平滑数据，使各指标都可见
        // log(1 + x) 确保0值不会出错，且小值也有合理显示
        val requestsLog = kotlin.math.log10(1f + requestsValue) * 10f
        val bandwidthLog = kotlin.math.log10(1f + bandwidthMB) * 10f
        val cachedLog = kotlin.math.log10(1f + cachedMB) * 10f
        val threatsLog = kotlin.math.log10(1f + threatsValue) * 10f
        val visitorsLog = kotlin.math.log10(1f + visitorsValue) * 10f
        
        if (requestsLog == 0f && bandwidthLog == 0f && cachedLog == 0f && 
            threatsLog == 0f && visitorsLog == 0f) {
            binding.barChart.clear()
            return
        }
        
        entries.add(BarEntry(0f, requestsLog))
        entries.add(BarEntry(1f, bandwidthLog))
        entries.add(BarEntry(2f, cachedLog))
        entries.add(BarEntry(3f, threatsLog))
        entries.add(BarEntry(4f, visitorsLog))
        
        val dataSet = BarDataSet(entries, "关键指标对比(对数尺度)").apply {
            colors = listOf(
                Color.parseColor("#9C27B0"), // 紫色 - 请求
                Color.parseColor("#2196F3"), // 蓝色 - 带宽
                Color.parseColor("#4CAF50"), // 绿色 - 缓存
                Color.parseColor("#F44336"), // 红色 - 威胁
                Color.parseColor("#FF9800")  // 橙色 - 访客
            )
            valueTextSize = 9f
            valueTextColor = Color.BLACK
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // 显示对数值
                    return String.format("%.1f", value)
                }
            }
        }
        
        val data = BarData(dataSet)
        data.barWidth = 0.8f
        binding.barChart.data = data
        binding.barChart.invalidate()
    }

    /**
     * 显示加载状态
     */
    fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.contentContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    /**
     * 显示错误状态
     */
    fun showError(message: String) {
        binding.loadingContainer.visibility = View.GONE
        binding.contentContainer.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    /**
     * 显示数据
     */
    fun showData(metrics: DashboardMetrics) {
        binding.loadingContainer.visibility = View.GONE
        binding.contentContainer.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE

        // 更新指标
        updateMetrics(metrics)
        
        // 更新健康状态
        updateHealthStatus(metrics.status)
        
        // 更新图表
        updateChart(metrics)
        updatePieChart(metrics)
        updateBarChart(metrics)
    }

    /**
     * 更新指标显示
     */
    private fun updateMetrics(metrics: DashboardMetrics) {
        // 总请求数
        binding.totalRequestsText.text = formatNumber(metrics.totalRequests)
        
        // 缓存命中率
        binding.cacheHitRateText.text = "${formatPercentage(metrics.cacheHitRate)}%"
        
        // 带宽使用
        binding.bandwidthText.text = formatBytes(metrics.bandwidthBytes)
        
        // Workers 调用
        binding.workersInvocationsText.text = formatNumber(metrics.workersInvocations)
        
        // 子请求数
        binding.cpuTimeText.text = formatNumber(metrics.workersSubrequests)
        
        // 错误率
        val errorRateText = "${formatPercentage(metrics.workersErrorRate)}%"
        binding.errorRateText.text = errorRateText
        
        // 根据错误率设置颜色
        val errorRateColor = when {
            metrics.workersErrorRate > 10.0 -> context.getColor(R.color.md_theme_error)
            metrics.workersErrorRate > 5.0 -> context.getColor(R.color.md_theme_tertiary)
            else -> context.getColor(android.R.color.holo_green_dark)
        }
        binding.errorRateText.setTextColor(errorRateColor)
        
        // 威胁拦截数
        binding.threatsBlockedText.text = formatNumber(metrics.threatsBlocked)
        
        // 页面浏览量
        binding.pageViewsText.text = formatNumber(metrics.pageViews)
        
        // 独立访客
        binding.uniqueVisitorsText.text = formatNumber(metrics.uniqueVisitors)
        
        // 已节省流量 (绿色高亮)
        binding.dataSavedText.text = formatBytes(metrics.dataSaved)
        
        // HTTPS 加密请求占比
        val encryptedRateText = "${formatPercentage(metrics.encryptedRequestRate)}%"
        binding.encryptedRateText.text = encryptedRateText
        
        // 根据加密占比设置颜色（低于100%时显示警告）
        val encryptedColor = when {
            metrics.encryptedRequestRate >= 99.0 -> context.getColor(android.R.color.holo_green_dark)
            metrics.encryptedRequestRate >= 95.0 -> context.getColor(R.color.md_theme_tertiary)
            else -> context.getColor(R.color.md_theme_error)
        }
        binding.encryptedRateText.setTextColor(encryptedColor)
        

        // === 衡生指标（基于现有数据计算）===
        
        // 源站承担流量 (这是服务器真实负载)
        binding.originBandwidthText.text = formatBytes(metrics.originBandwidth)
        
        // 人均页面浏览量 (衡量用户粘性)
        binding.pagesPerVisitText.text = formatDecimal(metrics.pagesPerVisit)
        
        // 平均请求体积 (性能监控)
        binding.avgRequestSizeText.text = "${formatDecimal(metrics.avgRequestSize)} KB"
        
        // 未加密请求数 (应该为0，否则显示警告)
        binding.unencryptedRequestsText.text = formatNumber(metrics.unencryptedRequests)
        val unencryptedColor = if (metrics.unencryptedRequests > 0) {
            context.getColor(R.color.md_theme_error)
        } else {
            context.getColor(android.R.color.holo_green_dark)
        }
        binding.unencryptedRequestsText.setTextColor(unencryptedColor)
        
        // === D1 数据库监控 ===
        binding.d1ReadsText.text = formatNumber(metrics.d1ReadRows)
        binding.d1WritesText.text = formatNumber(metrics.d1WriteRows)
        binding.d1StorageText.text = formatBytes(metrics.d1StorageBytes)
        binding.d1DatabaseCountText.text = metrics.d1DatabaseCount.toString()
        
        // === R2 存储监控 ===
        binding.r2ClassAOperationsText.text = formatNumber(metrics.r2ClassAOperations)
        binding.r2ClassBOperationsText.text = formatNumber(metrics.r2ClassBOperations)
        binding.r2StorageText.text = formatBytes(metrics.r2StorageBytes)
        binding.r2BucketCountText.text = metrics.r2BucketCount.toString()
    }

    /**
     * 更新健康状态指示器
     */
    private fun updateHealthStatus(status: HealthStatus) {
        val (statusText, statusColor) = when (status) {
            HealthStatus.HEALTHY -> "正常" to context.getColor(android.R.color.holo_green_dark)
            HealthStatus.WARNING -> "警告" to context.getColor(R.color.md_theme_tertiary)
            HealthStatus.CRITICAL -> "严重" to context.getColor(R.color.md_theme_error)
        }
        
        binding.statusText.text = statusText
        binding.statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(statusColor)
    }

    /**
     * 格式化数字
     */
    private fun formatNumber(value: Long): String {
        return when {
            value >= 1_000_000_000 -> String.format("%.1fB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }

    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_099_511_627_776L -> String.format("%.2f TB", bytes / 1_099_511_627_776.0)
            bytes >= 1_073_741_824L -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576L -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * 格式化字节-月 (用于 D1 计费存储)
     */
    private fun formatByteMonths(byteMonths: Long): String {
        return when {
            byteMonths >= 1_099_511_627_776L -> String.format("%.2f TB-mo", byteMonths / 1_099_511_627_776.0)
            byteMonths >= 1_073_741_824L -> String.format("%.2f GB-mo", byteMonths / 1_073_741_824.0)
            byteMonths >= 1_048_576L -> String.format("%.2f MB-mo", byteMonths / 1_048_576.0)
            byteMonths >= 1024L -> String.format("%.2f KB-mo", byteMonths / 1024.0)
            else -> "$byteMonths B-mo"
        }
    }

    /**
     * 格式化百分比
     */
    private fun formatPercentage(value: Double): String {
        return when {
            value >= 100.0 -> "100"
            value >= 10.0 -> value.roundToInt().toString()
            value >= 1.0 -> String.format("%.1f", value)
            else -> String.format("%.2f", value)
        }
    }

    /**
     * 格式化小数
     */
    private fun formatDecimal(value: Double): String {
        return when {
            value >= 1000.0 -> String.format("%.0f", value)
            value >= 100.0 -> String.format("%.1f", value)
            value >= 10.0 -> String.format("%.2f", value)
            else -> String.format("%.3f", value)
        }
    }
}
