package com.muort.upworker.feature.log

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.muort.upworker.R
import com.muort.upworker.core.log.LogRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {
    // 保证状态栏样式与主界面一致
    override fun getTheme(): android.content.res.Resources.Theme {
        val theme = super.getTheme()
        theme.applyStyle(R.style.AppTheme, true)
        return theme
    }
    private val scope = MainScope()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        
        // Configure system bars like main activity
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
        
        val logTextView = findViewById<TextView>(R.id.logTextView)
        logTextView.setTextIsSelectable(true)
        // 彩色高亮日志（关键字、时间戳、JSON高亮）
        fun colorizeLog(raw: String): CharSequence {
            if (raw.isBlank()) return "暂无日志"
            val spannable = android.text.SpannableStringBuilder()
            val keywordColor = 0xFF1976D2.toInt() // 蓝色
            val timeColor = 0xFF388E3C.toInt()   // 绿色
            val jsonKeyColor = 0xFFD84315.toInt() // 橙色
            val jsonStringColor = 0xFF6A1B9A.toInt() // 紫色
            val jsonNumberColor = 0xFF00897B.toInt() // 青色
            val jsonBoolColor = 0xFFEF6C00.toInt() // 深橙
            val jsonNullColor = 0xFF757575.toInt() // 灰色
            val lines = raw.split("\n")
            val timeRegex = Regex("""\d{4} \d{2}:\d{2}:\d{2} [A-Za-z0-9:+]+""")
            val jsonKeyRegex = Regex(""""([^"]+)"(?=:)\s*:""")
            val jsonStringRegex = Regex(""":\s*"(.*?)"""")
            val jsonNumberRegex = Regex(""":\s*(-?\d+(?:\.\d+)?)""")
            val jsonBoolRegex = Regex(""":\s*(true|false)""")
            val jsonNullRegex = Regex(""":\s*(null)""")
            for (line in lines) {
                val start = spannable.length
                spannable.append(line)
                val end = spannable.length
                // 关键字高亮
                if (line.startsWith("--- 请求")) {
                    spannable.setSpan(android.text.style.ForegroundColorSpan(keywordColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (line.startsWith("--- 响应")) {
                    spannable.setSpan(android.text.style.ForegroundColorSpan(keywordColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // 时间戳高亮
                timeRegex.findAll(line).forEach {
                    spannable.setSpan(android.text.style.ForegroundColorSpan(timeColor), start+it.range.first, start+it.range.last+1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // JSON key高亮
                jsonKeyRegex.findAll(line).forEach {
                    spannable.setSpan(android.text.style.ForegroundColorSpan(jsonKeyColor), start+it.range.first, start+it.range.last+1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // JSON string高亮
                jsonStringRegex.findAll(line).forEach {
                    val g = it.groups[1]
                    if (g != null) {
                        val vStart = line.indexOf(g.value, it.range.first)
                        if (vStart >= 0) {
                            spannable.setSpan(android.text.style.ForegroundColorSpan(jsonStringColor), start+vStart, start+vStart+g.value.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                // JSON number高亮
                jsonNumberRegex.findAll(line).forEach {
                    val g = it.groups[1]
                    if (g != null) {
                        val vStart = line.indexOf(g.value, it.range.first)
                        if (vStart >= 0) {
                            spannable.setSpan(android.text.style.ForegroundColorSpan(jsonNumberColor), start+vStart, start+vStart+g.value.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                // JSON bool高亮
                jsonBoolRegex.findAll(line).forEach {
                    val g = it.groups[1]
                    if (g != null) {
                        val vStart = line.indexOf(g.value, it.range.first)
                        if (vStart >= 0) {
                            spannable.setSpan(android.text.style.ForegroundColorSpan(jsonBoolColor), start+vStart, start+vStart+g.value.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                // JSON null高亮
                jsonNullRegex.findAll(line).forEach {
                    val g = it.groups[1]
                    if (g != null) {
                        val vStart = line.indexOf(g.value, it.range.first)
                        if (vStart >= 0) {
                            spannable.setSpan(android.text.style.ForegroundColorSpan(jsonNullColor), start+vStart, start+vStart+g.value.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
                spannable.append("\n")
            }
            return spannable
        }
        scope.launch {
            LogRepository.getLogFlow().collectLatest {
                logTextView.text = colorizeLog(it)
            }
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.logCloseBtn).setOnClickListener { finish() }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.logClearBtn).setOnClickListener {
            LogRepository.clearLog()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.logCopyBtn).setOnClickListener {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = logTextView.text.toString()
            val clip = android.content.ClipData.newPlainText("log", text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "日志已复制", android.widget.Toast.LENGTH_SHORT).show()
        }
        val logSwitch = findViewById<com.google.android.material.button.MaterialButton>(R.id.logSwitch)
        var isLoggingEnabled = true
        logSwitch.text = if (isLoggingEnabled) "开" else "关"
        // 同步开关状态
        scope.launch {
            LogRepository.getEnableFlow().collectLatest { enable ->
                isLoggingEnabled = enable
                logSwitch.text = if (enable) "开" else "关"
            }
        }
        logSwitch.setOnClickListener {
            isLoggingEnabled = !isLoggingEnabled
            LogRepository.setEnable(isLoggingEnabled)
            logSwitch.text = if (isLoggingEnabled) "开" else "关"
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
