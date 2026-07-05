package com.muort.upworker.feature.worker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.muort.upworker.R
import com.muort.upworker.core.model.TailException
import com.muort.upworker.core.model.TailLog
import com.muort.upworker.core.model.TailTraceItem
import okhttp3.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class WorkerLogsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var connectionStatusDot: View
    private lateinit var connectionStatusText: TextView
    private lateinit var pauseBtn: MaterialButton
    private lateinit var clearBtn: MaterialButton
    private lateinit var refreshBtn: MaterialButton
    private lateinit var waitingText: TextView
    private lateinit var logsText: TextView

    private var webSocket: WebSocket? = null
    private var isPaused = false
    private var isConnected = false
    private var currentWssUrl: String = ""
    private var reconnectHandler: Handler? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val EXTRA_SCRIPT_NAME = "script_name"
        private const val EXTRA_WSS_URL = "wss_url"

        fun start(context: Context, scriptName: String, wssUrl: String) {
            val intent = Intent(context, WorkerLogsActivity::class.java).apply {
                putExtra(EXTRA_SCRIPT_NAME, scriptName)
                putExtra(EXTRA_WSS_URL, wssUrl)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_logs)

        toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        connectionStatusDot = findViewById<View>(R.id.connectionStatusDot)
        connectionStatusText = findViewById<TextView>(R.id.connectionStatusText)
        pauseBtn = findViewById<MaterialButton>(R.id.pauseBtn)
        clearBtn = findViewById<MaterialButton>(R.id.clearBtn)
        refreshBtn = findViewById<MaterialButton>(R.id.refreshBtn)
        waitingText = findViewById<TextView>(R.id.waitingText)
        logsText = findViewById<TextView>(R.id.logsText)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_SCRIPT_NAME)

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

        pauseBtn.setOnClickListener { togglePause() }
        clearBtn.setOnClickListener { clearLogs() }
        refreshBtn.setOnClickListener { refreshConnection() }

        val wssUrl = intent.getStringExtra(EXTRA_WSS_URL)
        if (wssUrl.isNullOrEmpty()) {
            Log.e("WorkerLogs", "WSS URL is empty")
            showToast("WSS URL为空")
            return
        }
        currentWssUrl = wssUrl
        Log.d("WorkerLogs", "Connecting to WSS URL: $currentWssUrl")
        connectWebSocket(currentWssUrl)
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
        
        Log.d("WorkerLogs", "WebSocket request URL: $url")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WorkerLogs", "WebSocket opened, response code: ${response.code}")
                Log.d("WorkerLogs", "Sending filters: {\"filters\":[],\"debug\":false}")
                webSocket.send("{\"filters\":[],\"debug\":false}")
                runOnUiThread {
                    isConnected = true
                    connectionStatusDot.background = getDrawable(R.drawable.circle_green)
                    connectionStatusText.text = "已连接"
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WorkerLogs", "Received raw message: $text")
                if (!isPaused) {
                    try {
                        val traceItem = Gson().fromJson(text, TailTraceItem::class.java)
                        Log.d("WorkerLogs", "Parsed outcome: ${traceItem.outcome}")
                        Log.d("WorkerLogs", "Parsed logs count: ${traceItem.logs?.size ?: 0}")
                        Log.d("WorkerLogs", "Parsed exceptions count: ${traceItem.exceptions?.size ?: 0}")
                        Log.d("WorkerLogs", "Parsed event type: ${traceItem.event?.cron ?: traceItem.event?.request?.method}")
                        val logLines = formatTraceItem(traceItem)
                        Log.d("WorkerLogs", "Formatted ${logLines.size} lines")
                        runOnUiThread {
                            waitingText.visibility = View.GONE
                            logsText.visibility = View.VISIBLE
                            logLines.forEach { logsText.append(it + "\n") }
                            scrollToBottom()
                        }
                    } catch (e: Exception) {
                        Log.e("WorkerLogs", "Failed to parse log message: ${e.message}")
                        runOnUiThread {
                            waitingText.visibility = View.GONE
                            logsText.visibility = View.VISIBLE
                            logsText.append("[ERROR] Failed to parse: $text\n")
                            scrollToBottom()
                        }
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
                Log.e("WorkerLogs", "WebSocket failure: ${t.message}", t)
                Log.e("WorkerLogs", "WebSocket failure URL: $url")
                if (response != null) {
                    Log.e("WorkerLogs", "Response code: ${response.code}")
                    Log.e("WorkerLogs", "Response message: ${response.message}")
                    Log.e("WorkerLogs", "Response headers: ${response.headers}")
                }
                scheduleReconnect()
            }

            })
    }

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
        logsText.text = ""
        waitingText.visibility = View.VISIBLE
        logsText.visibility = View.GONE
    }

    private fun isColorLight(color: Int): Boolean {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance > 0.5
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
        val scrollView = logsText.parent.parent as? android.widget.ScrollView
        scrollView?.post {
            scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun formatTraceItem(item: TailTraceItem): List<String> {
        val lines = mutableListOf<String>()
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        
        val timestamp = item.eventTimestamp ?: System.currentTimeMillis()
        val timeStr = sdf.format(Date(timestamp))
        
        if (item.event?.cron != null) {
            lines.add("$timeStr [CRON] ${item.event.cron}")
        }
        
        if (item.event?.request != null) {
            val req = item.event.request
            lines.add("$timeStr [REQUEST] ${req.method ?: "GET"} ${req.url ?: ""}")
        }
        
        item.logs?.forEach { log ->
            val level = log.level.uppercase()
            val msg = log.message?.joinToString(" ") { it.toString() } ?: ""
            lines.add("$timeStr [$level] $msg")
        }
        
        item.exceptions?.forEach { ex ->
            lines.add("$timeStr [EXCEPTION] ${ex.name ?: ""}: ${ex.message ?: ""}")
        }
        
        if (item.outcome != null) {
            lines.add("$timeStr [OUTCOME] ${item.outcome}")
        }
        
        return lines
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