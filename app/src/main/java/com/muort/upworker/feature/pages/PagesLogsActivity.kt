package com.muort.upworker.feature.pages

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.muort.upworker.R
import com.muort.upworker.core.model.TailTraceItem
import com.muort.upworker.core.util.DisplaySizeHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pages 部署实时日志（Functions console.log / exceptions）
 * 与 Cloudflare 官网 Logs 一致的展示方式：
 * 每个请求一张卡片（状态圆点 / 触发器方法+URL / 时间），点击查看完整 trace JSON 详情
 */
class PagesLogsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(newBase))
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var connectionStatusDot: View
    private lateinit var connectionStatusText: TextView
    private lateinit var pauseBtn: MaterialButton
    private lateinit var clearBtn: MaterialButton
    private lateinit var refreshBtn: MaterialButton
    private lateinit var selectAllBtn: MaterialButton
    private lateinit var copyBtn: MaterialButton
    private lateinit var closeBtn: MaterialButton
    private lateinit var waitingText: TextView
    private lateinit var logsContainer: LinearLayout
    private lateinit var logsScrollView: ScrollView

    private val eventCards = mutableListOf<Pair<View, String>>()
    private var webSocket: WebSocket? = null
    private var isPaused = false
    private var isConnected = false
    private var currentWssUrl: String = ""
    private var reconnectHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val EXTRA_PROJECT_NAME = "project_name"
        private const val EXTRA_WSS_URL = "wss_url"
        private const val MAX_EVENTS = 200

        fun start(context: Context, projectName: String, wssUrl: String) {
            val intent = Intent(context, PagesLogsActivity::class.java).apply {
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_WSS_URL, wssUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pages_logs)

        toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        connectionStatusDot = findViewById<View>(R.id.connectionStatusDot)
        connectionStatusText = findViewById<TextView>(R.id.connectionStatusText)
        pauseBtn = findViewById<MaterialButton>(R.id.pauseBtn)
        clearBtn = findViewById<MaterialButton>(R.id.clearBtn)
        refreshBtn = findViewById<MaterialButton>(R.id.refreshBtn)
        selectAllBtn = findViewById<MaterialButton>(R.id.selectAllBtn)
        copyBtn = findViewById<MaterialButton>(R.id.copyBtn)
        closeBtn = findViewById<MaterialButton>(R.id.closeBtn)
        waitingText = findViewById<TextView>(R.id.waitingText)
        logsContainer = findViewById<LinearLayout>(R.id.logsContainer)
        logsScrollView = findViewById<ScrollView>(R.id.logsScrollView)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_PROJECT_NAME)

        applyStatusBarStyle()

        pauseBtn.setOnClickListener { togglePause() }
        clearBtn.setOnClickListener { clearLogs() }
        refreshBtn.setOnClickListener { refreshConnection() }
        selectAllBtn.setOnClickListener { selectAllLogs() }
        copyBtn.setOnClickListener { copyLogs() }
        closeBtn.setOnClickListener { finish() }

        val wssUrl = intent.getStringExtra(EXTRA_WSS_URL)
        if (wssUrl.isNullOrEmpty()) {
            Log.e("PagesLogs", "WSS URL is empty")
            showToast("WSS URL为空")
            return
        }
        currentWssUrl = wssUrl
        connectWebSocket(currentWssUrl)
    }

    private fun applyStatusBarStyle() {
        val isDarkMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.black, theme)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                @Suppress("DEPRECATION")
                ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
                    controller.isAppearanceLightStatusBars = false
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.white, theme)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
                    controller.isAppearanceLightStatusBars = true
                }
            }
        }
    }

    private fun connectWebSocket(url: String) {
        runOnUiThread {
            isConnected = false
            connectionStatusDot.background = getDrawable(R.drawable.circle_yellow)
            connectionStatusText.text = "连接中..."
        }

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "trace-v1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("PagesLogs", "WebSocket opened, response code: ${response.code}")
                webSocket.send("{\"filters\":[],\"debug\":false}")
                runOnUiThread {
                    isConnected = true
                    connectionStatusDot.background = getDrawable(R.drawable.circle_green)
                    connectionStatusText.text = "已连接"
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                processMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                processMessage(bytes.utf8())
            }

            private fun processMessage(text: String) {
                if (!isPaused) {
                    try {
                        val traceItem = Gson().fromJson(text, TailTraceItem::class.java)
                        mainHandler.post { appendEventCard(traceItem, text) }
                    } catch (e: Exception) {
                        Log.e("PagesLogs", "Failed to parse log message: ${e.message}")
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    isConnected = false
                    connectionStatusDot.background = getDrawable(R.drawable.circle_red)
                    connectionStatusText.text = "已断开"
                }
                webSocket.close(code, reason)
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    isConnected = false
                    connectionStatusDot.background = getDrawable(R.drawable.circle_red)
                    connectionStatusText.text = "连接失败: ${t.message}"
                }
                Log.e("PagesLogs", "WebSocket failure: ${t.message}", t)
                scheduleReconnect()
            }
        })
    }

    // ==================== 事件卡片（官网风格） ====================

    private fun appendEventCard(item: TailTraceItem, rawJson: String) {
        waitingText.visibility = View.GONE

        val card = buildEventCard(item, rawJson)
        eventCards.add(card)
        if (eventCards.size > MAX_EVENTS) {
            val oldest = eventCards.removeAt(0)
            logsContainer.removeView(oldest.first)
        }
        logsContainer.addView(card.first)
        scrollToBottom()
    }

    /**
     * 构建单张事件卡片：第一行 [状态圆点][Ok][方法]，第二行 URL，第三行时间；下方面板展示日志内容
     */
    private fun buildEventCard(item: TailTraceItem, rawJson: String): Pair<View, String> {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val outcome = item.outcome ?: "unknown"
        val isOk = outcome == "ok"
        val statusColor = when {
            isOk -> Color.parseColor("#22c55e")   // 绿
            outcome == "canceled" || outcome == "exceededCpu" -> Color.parseColor("#f59e0b") // 黄
            else -> Color.parseColor("#ef4444")   // 红
        }

        val request = item.event?.request
        val method = request?.method ?: item.event?.cron?.let { "CRON" } ?: ""
        val url = request?.url ?: item.event?.cron ?: ""

        val sdf = SimpleDateFormat("yyyy/M/d HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(item.eventTimestamp ?: System.currentTimeMillis()))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_list_item_border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        // 第一行：状态圆点 + Ok + 方法
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply { marginEnd = dp(6) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(statusColor)
            }
        }
        headerRow.addView(dot)

        val outcomeTv = TextView(this).apply {
            text = outcome
            textSize = 13f
            setTextColor(statusColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
        }
        headerRow.addView(outcomeTv)

        if (method.isNotEmpty()) {
            val methodTv = TextView(this).apply {
                text = method
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#3b82f6"))
            }
            headerRow.addView(methodTv)
        }
        card.addView(headerRow)

        // 第二行：URL（可选中复制）
        if (url.isNotEmpty()) {
            val urlTv = TextView(this).apply {
                text = url
                textSize = 13f
                setTextColor(Color.parseColor("#2563eb"))
                setTextIsSelectable(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            card.addView(urlTv)
        }

        // 第三行：时间
        val timeTv = TextView(this).apply {
            text = timeStr
            textSize = 11f
            setTextColor(Color.parseColor("#9ca3af"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        card.addView(timeTv)

        // 日志内容预览（console.log 等，最多显示前几条）
        val contentLines = buildContentLines(item)
        if (contentLines.isNotEmpty()) {
            val contentTv = TextView(this).apply {
                val preview = contentLines.joinToString("\n")
                text = preview
                textSize = 12f
                setTypeface(Typeface.MONOSPACE)
                setTextColor(Color.parseColor("#6b7280"))
                maxLines = 3
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            }
            card.addView(contentTv)
        }

        // 点击卡片弹出详情对话框（与官网一致）
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { showEventDetailDialog(outcome, method, url, timeStr, rawJson) }

        return Pair(card, rawJson)
    }

    /**
     * 从 trace 中提取内容行：logs / exceptions
     */
    private fun buildContentLines(item: TailTraceItem): List<String> {
        val lines = mutableListOf<String>()
        item.exceptions?.forEach { ex ->
            lines.add("[EXCEPTION] ${ex.name ?: ""}: ${ex.message ?: ""}".trim())
        }
        item.logs?.forEach { log ->
            val msg = log.message?.joinToString(" ") { it.toString() } ?: ""
            lines.add(msg)
        }
        return lines
    }

    /**
     * 详情弹窗：标题为摘要信息，正文为格式化 JSON（与官网点击事件后的展示一致）
     */
    private fun showEventDetailDialog(
        outcome: String,
        method: String,
        url: String,
        timeStr: String,
        rawJson: String
    ) {
        val prettyJson = try {
            GsonBuilder().setPrettyPrinting().create()
                .toJson(JsonParser.parseString(rawJson))
        } catch (e: Exception) {
            rawJson
        }

        val summary = buildString {
            append("$outcome  $method\n$url\n$timeStr")
        }

        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            text = prettyJson
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle(summary.trim())
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制 JSON") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pages log detail", prettyJson))
                showToast("JSON 已复制")
            }
            .show()
    }

    // ==================== 连接管理 ====================

    private fun scheduleReconnect() {
        reconnectHandler = Handler(Looper.getMainLooper())
        reconnectHandler?.postDelayed({
            if (!isConnected && !isPaused) {
                connectWebSocket(currentWssUrl)
            }
        }, 5000)
    }

    private fun cancelReconnect() {
        reconnectHandler?.removeCallbacksAndMessages(null)
        reconnectHandler = null
    }

    private fun togglePause() {
        isPaused = !isPaused
        if (isPaused) {
            pauseBtn.setIconResource(R.drawable.ic_play)
            connectionStatusText.text = "已暂停"
        } else {
            pauseBtn.setIconResource(R.drawable.ic_pause)
            connectionStatusText.text = if (isConnected) "已连接" else "未连接"
        }
    }

    private fun clearLogs() {
        eventCards.clear()
        logsContainer.removeAllViews()
        waitingText.visibility = View.VISIBLE
        logsContainer.addView(waitingText)
    }

    private fun selectAllLogs() {
        // 卡片模式下全选无实际操作对象，保留按钮占位
        showToast("点击卡片可查看和复制详情")
    }

    private fun copyLogs() {
        if (eventCards.isEmpty()) {
            showToast("没有日志可复制")
            return
        }
        val sb = StringBuilder()
        for ((card, _) in eventCards) {
            val texts = collectTexts(card)
            sb.append(texts.joinToString("\n")).append("\n\n")
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Pages logs", sb.toString()))
        showToast("日志已复制")
    }

    private fun collectTexts(view: View): List<String> {
        if (view is TextView) return listOf(view.text.toString())
        if (view is ViewGroup) {
            val out = mutableListOf<String>()
            for (i in 0 until view.childCount) {
                out.addAll(collectTexts(view.getChildAt(i)))
            }
            return out
        }
        return emptyList()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun refreshConnection() {
        cancelReconnect()
        webSocket?.close(1000, "Manual refresh")
        webSocket = null
        isConnected = false
        connectionStatusDot.background = getDrawable(R.drawable.circle_yellow)
        connectionStatusText.text = "连接中..."
        connectWebSocket(currentWssUrl)
    }

    private fun scrollToBottom() {
        logsScrollView.post {
            logsScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        cancelReconnect()
        webSocket?.close(1000, "Activity destroyed")
        webSocket = null
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
