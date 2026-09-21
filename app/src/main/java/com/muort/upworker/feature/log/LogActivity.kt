package com.muort.upworker.feature.log

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.muort.upworker.R
import com.muort.upworker.core.log.LogRepository
import com.muort.upworker.core.util.DisplaySizeHelper
import com.muort.upworker.core.util.LocaleHelper
import com.muort.upworker.core.util.ThemeHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogActivity : AppCompatActivity() {

    /** 日志过滤类型 */
    private enum class LogFilter { APP, HTTP, ERROR, ALL }

    private var currentFilter = LogFilter.APP

    /**
     * 按过滤类型过滤日志内容。
     * 日志条目分为两类：
     *   - 应用日志：以 [VDIWEA] 开头（如 [D] 2026 ... Tag: message）
     *   - HTTP 日志：以 --- 开头的块（请求、响应、响应异常）
     * 条目的后续行（不以上述标记开头）归属于当前条目。
     */
    private fun filterLog(raw: String, filter: LogFilter): String {
        if (filter == LogFilter.ALL) return raw
        val lines = raw.split("\n")
        val result = StringBuilder()
        var keep = false
        for (line in lines) {
            val isAppLog = line.length >= 3 && line[0] == '[' && line[1] in "VDIWEA" && line[2] == ']'
            val isHttpMarker = line.startsWith("--- ")
            if (isAppLog || isHttpMarker) {
                keep = when (filter) {
                    LogFilter.APP -> isAppLog
                    LogFilter.HTTP -> isHttpMarker
                    LogFilter.ERROR -> (isAppLog && (line[1] == 'E' || line[1] == 'A')) ||
                            line.startsWith("--- 响应异常")
                    LogFilter.ALL -> true
                }
            }
            if (keep) {
                if (result.isNotEmpty()) result.append("\n")
                result.append(line)
            }
        }
        return result.toString()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(LocaleHelper.applyLocale(newBase)))
    }

    private val scope = MainScope()

    /** 保存日志到用户选择的文件 */
    private val saveLogLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val content = filterLog(LogRepository.getLog(), currentFilter)
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            }
            android.widget.Toast.makeText(this, getString(R.string.log_save_success), android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, getString(R.string.log_save_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)
        
        // Configure system bars like main activity
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 使用 WindowInsetsControllerCompat 设置状态栏颜色和模式
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) != android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        // 状态栏颜色设置依然保留，兼容旧设备
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Handle system bar insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPaddingRelative(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isNightMode
        
        val logTextView = findViewById<TextView>(R.id.logTextView)
        logTextView.setTextIsSelectable(true)
        // 彩色高亮日志（关键字、时间戳、JSON高亮）
        fun colorizeLog(raw: String): CharSequence {
            if (raw.isBlank()) return getString(R.string.app_log_empty)
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
                logTextView.text = colorizeLog(filterLog(it, currentFilter))
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
            android.widget.Toast.makeText(this, getString(R.string.msg_logs_copied), android.widget.Toast.LENGTH_SHORT).show()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.logSaveBtn).setOnClickListener {
            val timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            saveLogLauncher.launch("cloudflare_log_$timestamp.txt")
        }
        // 日志过滤标签
        val filterChips = findViewById<com.google.android.material.chip.ChipGroup>(R.id.logFilterChips)
        filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.filterHttpChip -> LogFilter.HTTP
                R.id.filterErrorChip -> LogFilter.ERROR
                R.id.filterAllChip -> LogFilter.ALL
                else -> LogFilter.APP
            }
            logTextView.text = colorizeLog(filterLog(LogRepository.getLog(), currentFilter))
        }
        val logSwitch = findViewById<com.google.android.material.button.MaterialButton>(R.id.logSwitch)
        var isLoggingEnabled = false
        logSwitch.text = getString(if (isLoggingEnabled) R.string.app_log_switch_on else R.string.app_log_switch_off)
        // 同步开关状态
        scope.launch {
            LogRepository.getEnableFlow().collectLatest { enable ->
                isLoggingEnabled = enable
                logSwitch.text = getString(if (enable) R.string.app_log_switch_on else R.string.app_log_switch_off)
            }
        }
        logSwitch.setOnClickListener {
            isLoggingEnabled = !isLoggingEnabled
            LogRepository.setEnable(isLoggingEnabled)
            logSwitch.text = getString(if (isLoggingEnabled) R.string.app_log_switch_on else R.string.app_log_switch_off)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
