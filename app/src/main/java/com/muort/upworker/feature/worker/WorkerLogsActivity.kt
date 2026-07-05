package com.muort.upworker.feature.worker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.appbar.MaterialToolbar
import com.muort.upworker.R
import okhttp3.*
import java.util.concurrent.TimeUnit

class WorkerLogsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var connectionStatusDot: View
    private lateinit var connectionStatusText: TextView
    private lateinit var pauseBtn: MaterialButton
    private lateinit var clearBtn: MaterialButton
    private lateinit var waitingText: TextView
    private lateinit var logsText: TextView

    private var webSocket: WebSocket? = null
    private var isPaused = false
    private var isConnected = false
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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
        waitingText = findViewById<TextView>(R.id.waitingText)
        logsText = findViewById<TextView>(R.id.logsText)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_SCRIPT_NAME)

        pauseBtn.setOnClickListener { togglePause() }
        clearBtn.setOnClickListener { clearLogs() }

        val wssUrl = intent.getStringExtra(EXTRA_WSS_URL) ?: return
        connectWebSocket(wssUrl)
    }

    private fun connectWebSocket(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread {
                    isConnected = true
                    connectionStatusDot.background = getDrawable(R.drawable.circle_green)
                    connectionStatusText.text = "已连接"
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isPaused) {
                    runOnUiThread {
                        waitingText.visibility = View.GONE
                        logsText.visibility = View.VISIBLE
                        logsText.append(text + "\n")
                        scrollToBottom()
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
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    isConnected = false
                    connectionStatusDot.background = getDrawable(R.drawable.circle_red)
                    connectionStatusText.text = "连接失败"
                }
                Log.e("WorkerLogs", "WebSocket failure", t)
            }
        })
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

    private fun scrollToBottom() {
        val scrollView = logsText.parent.parent as? android.widget.ScrollView
        scrollView?.post {
            scrollView.fullScroll(android.view.View.FOCUS_DOWN)
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
        webSocket?.close(1000, "Activity destroyed")
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}