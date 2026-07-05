package com.muort.upworker.feature.scripteditor

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.R
import com.muort.upworker.core.model.ScriptVersion
import com.muort.upworker.databinding.FragmentScriptEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ScriptEditorFragment : Fragment() {
    
    private var _binding: FragmentScriptEditorBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ScriptEditorViewModel by viewModels()
    private val args: ScriptEditorFragmentArgs by navArgs()
    
    private var isEditorReady = false
    private var hasUnsavedChanges = false
    private var originalContent: String = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScriptEditorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupBackPressHandler()
        setupWebView()
        setupButtons()
        observeViewModel()
        setupLayoutListener()

        // Load script content
        viewModel.loadScript(args.accountEmail, args.scriptName)
    }
    
    private fun setupToolbar() {
        binding.toolbar.apply {
            title = args.scriptName
            setNavigationOnClickListener {
                handleBackNavigation()
            }
        }
    }
    
    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }
    
    private fun handleBackNavigation() {
        if (hasUnsavedChanges) {
            showUnsavedChangesDialog()
        } else {
            findNavController().navigateUp()
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun setupWebView() {
        binding.webView.apply {
            // 软件渲染：避免Fragment生命周期变化时GPU渲染表面被回收导致闪屏
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }
            
            addJavascriptInterface(JavaScriptBridge(), "AndroidBridge")
            
            setOnLongClickListener {
                false
            }
            
            setOnTouchListener { _, event ->
                handleTouchEvent(event)
                false
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // CodeMirror在window.load时自动初始化，无需在此设置主题
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    return true
                }
            }
            
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                    result?.cancel()
                    return true
                }
                
                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                    result?.cancel()
                    return true
                }
            }
            
            val isDarkMode = when (resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
                else -> false
            }
            
            val themeScript = "window._editorTheme = '${if (isDarkMode) "dracula" else "default"}';"
            evaluateJavascript(themeScript, null)
            
            loadUrl("file:///android_asset/code_editor.html")
        }
    }
    
    private fun setupButtons() {
        binding.btnCopy.setOnClickListener {
            getEditorContent { content ->
                copyToClipboard(content)
            }
        }
        
        binding.btnManualSave.setOnClickListener {
            getEditorContent { content ->
                saveVersion(content, isAutoSave = false)
            }
        }
        
        binding.btnUpload.setOnClickListener {
            getEditorContent { content ->
                uploadScript(content)
            }
        }
        
        binding.btnSearch.setOnClickListener {
            toggleSearchBar()
        }
        
        binding.webViewContainer.setOnClickListener {
            if (binding.searchBar.visibility == View.VISIBLE) {
                hideSearchBar()
            }
        }
        
        binding.btnVersionHistory.setOnClickListener {
            showVersionHistoryDialog()
        }
        
        binding.btnSelectAll.setOnClickListener {
            executeJavaScript("selectAll()")
        }
        
        binding.btnUndo.setOnClickListener {
            undoToLastSavedVersion()
        }
        
        // Search bar controls
        binding.btnCloseSearch.setOnClickListener {
            hideSearchBar()
        }
        
        binding.btnNextMatch.setOnClickListener {
            executeJavaScript("findNext()")
        }
        
        binding.btnPrevMatch.setOnClickListener {
            executeJavaScript("findPrev()")
        }
        
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                performSearch()
            }
        })
    }
    
    private fun toggleSearchBar() {
        if (binding.searchBar.visibility == View.VISIBLE) {
            hideSearchBar()
        } else {
            showSearchBar()
        }
    }
    
    private fun showSearchBar() {
        binding.searchBar.visibility = View.VISIBLE
        binding.searchInput.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun hideSearchBar() {
        binding.searchBar.visibility = View.GONE
        executeJavaScript("clearSearchHighlights()")
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }
    
    private fun performSearch() {
        val query = binding.searchInput.text.toString()
        if (query.isNotEmpty()) {
            // Escape special characters for JavaScript
            val escapedQuery = query
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            executeJavaScript("customSearch('$escapedQuery', false)")
        } else {
            executeJavaScript("clearSearchHighlights()")
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scriptContent.collect { content ->
                if (content != null) {
                    originalContent = content
                    if (isEditorReady) {
                        setEditorContent(content)
                    }
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uploadSuccess.collect { success ->
                if (success) {
                    Snackbar.make(binding.root, "脚本上传成功", Snackbar.LENGTH_SHORT).show()
                    hasUnsavedChanges = false
                    viewModel.clearUploadSuccess()
                }
            }
        }
    }
    
    private var lastAppliedDarkMode: Boolean? = null

    private fun setEditorTheme() {
        val isDarkMode = when (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        // 只在主题真正变化时才操作WebView，避免不必要的重绘导致闪屏
        if (lastAppliedDarkMode == isDarkMode) return
        lastAppliedDarkMode = isDarkMode

        val bgColor = if (isDarkMode) 0xFF282a36.toInt() else 0xFFFFFFFF.toInt()
        binding.webView.setBackgroundColor(bgColor)
        executeJavaScript("setTheme($isDarkMode)")
    }
    
    private fun setEditorContent(content: String) {
        Timber.d("setEditorContent: Setting ${content.length} chars")
        // 使用JSON.stringify确保所有特殊字符正确转义
        val jsonContent = org.json.JSONObject().apply {
            put("c", content)
        }.toString()
        executeJavaScript("setContentFromJSON($jsonContent)")
    }
    
    private fun getEditorContent(callback: (String) -> Unit) {
        binding.webView.evaluateJavascript("getContent()") { result ->
            val content = if (!result.isNullOrEmpty() && result != "null") {
                try {
                    // evaluateJavascript返回JSON编码的字符串，包装为对象解析以正确反转义
                    org.json.JSONObject("{\"v\":$result}").getString("v")
                } catch (e: Exception) {
                    result.removeSurrounding("\"")
                }
            } else ""
            callback(content)
        }
    }
    
    private fun executeJavaScript(script: String) {
        binding.webView.evaluateJavascript(script, null)
    }
    
    private fun copyToClipboard(content: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Worker Script", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
    
    private fun saveVersion(content: String, isAutoSave: Boolean, description: String? = null) {
        viewModel.saveVersion(
            accountEmail = args.accountEmail,
            scriptName = args.scriptName,
            content = content,
            isAutoSave = isAutoSave,
            description = description
        )
        
        hasUnsavedChanges = false
        originalContent = content
        
        if (!isAutoSave) {
            Toast.makeText(requireContext(), "手动保存成功", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun undoToLastSavedVersion() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 获取版本历史
                val versions = viewModel.getVersionHistory(args.accountEmail, args.scriptName)
                
                // 找到最后一个手动保存或Cloudflare同步的版本
                val lastSavedVersion = versions.firstOrNull { version ->
                    !version.isAutoSave || version.description == "从Cloudflare加载"
                }
                
                if (lastSavedVersion != null) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("撤销确认")
                        .setMessage("确定要撤销到以下版本吗？\n\n${lastSavedVersion.description ?: "手动保存"}\n${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastSavedVersion.timestamp))}")
                        .setPositiveButton("撤销") { _, _ ->
                            setEditorContent(lastSavedVersion.content)
                            hasUnsavedChanges = true
                            Toast.makeText(requireContext(), "已撤销到上一版本", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "没有可撤销的版本", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to undo")
                Toast.makeText(requireContext(), "撤销失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun uploadScript(content: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("上传脚本")
            .setMessage("确定要上传脚本到Cloudflare Workers吗?这将替换现有脚本。")
            .setPositiveButton("上传") { _, _ ->
                viewModel.uploadScript(args.accountEmail, args.scriptName, content)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showVersionHistoryDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val versions = viewModel.getVersionHistory(args.accountEmail, args.scriptName)
            
            if (versions.isEmpty()) {
                Toast.makeText(requireContext(), "暂无历史版本", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val items = versions.map { version ->
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(version.timestamp))
                val type = when {
                    version.description == "从Cloudflare加载" -> "同步"
                    version.isAutoSave -> "自动"
                    else -> "手动"
                }
                val desc = version.description?.let { " - $it" } ?: ""
                "$date [$type]$desc"
            }.toTypedArray()
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("版本历史 (共${versions.size}个版本)")
                .setItems(items) { _, which ->
                    val selectedVersion = versions[which]
                    showVersionDetailDialog(selectedVersion)
                }
                .setNeutralButton("清除") { _, _ ->
                    showClearVersionsConfirmDialog()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun showVersionDetailDialog(version: ScriptVersion) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(version.timestamp))
        val type = when {
            version.description == "从Cloudflare加载" -> "同步"
            version.isAutoSave -> "自动保存"
            else -> "手动保存"
        }
        val desc = version.description?.let { "\n描述: $it" } ?: ""
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("版本详情")
            .setMessage("时间: $date\n类型: $type$desc")
            .setPositiveButton("恢复到编辑器") { _, _ ->
                setEditorContent(version.content)
                hasUnsavedChanges = true
                Toast.makeText(requireContext(), "已恢复到此版本", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
        
        dialog.setNeutralButton("回滚并上传") { _, _ ->
            showRollbackConfirmDialog(version)
        }
        
        if (version.description == "从Cloudflare加载") {
            dialog.setNegativeButton("删除") { _, _ ->
                showDeleteVersionConfirmDialog(version)
            }
        }
        
        dialog.show()
    }
    
    private fun showRollbackConfirmDialog(version: ScriptVersion) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(version.timestamp))
        val type = when {
            version.description == "从Cloudflare加载" -> "同步"
            version.isAutoSave -> "自动保存"
            else -> "手动保存"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认回滚")
            .setMessage("确定要回滚到此版本并上传到Cloudflare吗？\n\n时间: $date\n类型: $type\n\n此操作将替换当前部署的脚本。")
            .setPositiveButton("回滚") { _, _ ->
                viewModel.rollbackScript(args.accountEmail, args.scriptName, version)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认离开")
            .setMessage("确定要离开编辑器吗？\n\n未保存的修改将丢失。")
            .setPositiveButton("离开") { _, _ ->
                findNavController().navigateUp()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private var lastTouchY = 0f
    private var isScrolling = false
    
    private fun handleTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isScrolling = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = Math.abs(event.y - lastTouchY)
                if (deltaY > 10) {
                    isScrolling = true
                    hideKeyboard()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isScrolling) {
                    showKeyboard()
                }
            }
        }
    }
    
    private fun showKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        binding.webView.requestFocus()
        imm.showSoftInput(binding.webView, InputMethodManager.SHOW_IMPLICIT)
        executeJavaScript("focusEditor()")
    }
    
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.webView.windowToken, 0)
    }
    
    private var lastLayoutWidth = 0
    private var lastLayoutHeight = 0

    private fun setupLayoutListener() {
        binding.webViewContainer.viewTreeObserver.addOnGlobalLayoutListener {
            val b = _binding ?: return@addOnGlobalLayoutListener
            val width = b.webViewContainer.width
            val height = b.webViewContainer.height
            if (width > 0 && height > 0 && (width != lastLayoutWidth || height != lastLayoutHeight)) {
                lastLayoutWidth = width
                lastLayoutHeight = height
                if (isEditorReady) {
                    b.webView.evaluateJavascript("doLayout()", null)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setEditorTheme()
    }

    override fun onDestroyView() {
        binding.webView.apply {
            stopLoading()
            removeJavascriptInterface("AndroidBridge")
            webViewClient = WebViewClient()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }
    
    /**
     * JavaScript Bridge for communication with CodeMirror editor
     */
    inner class JavaScriptBridge {
        
        @JavascriptInterface
        fun onEditorReady() {
            requireActivity().runOnUiThread {
                isEditorReady = true
                Timber.d("Editor ready")
                viewModel.scriptContent.value?.let { content ->
                    setEditorContent(content)
                }
            }
        }
        
        @JavascriptInterface
        fun onContentChanged() {
            requireActivity().runOnUiThread {
                hasUnsavedChanges = true
            }
        }
        
        @JavascriptInterface
        fun onSaveRequested(content: String) {
            requireActivity().runOnUiThread {
                Timber.d("Manual save requested")
                saveVersion(content, isAutoSave = false)
            }
        }
        
        @JavascriptInterface
        fun onCopyRequested(content: String) {
            requireActivity().runOnUiThread {
                copyToClipboard(content)
            }
        }
        
        @JavascriptInterface
        fun onShowSearch() {
            requireActivity().runOnUiThread {
                showSearchBar()
            }
        }
    }
    
    private fun showClearVersionsConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清除历史版本")
            .setMessage("确定要清除所有自动保存和手动保存的版本吗？\n\n只会保留从Cloudflare加载的版本。")
            .setPositiveButton("清除") { _, _ ->
                viewModel.clearNonCloudflareVersions(args.accountEmail, args.scriptName)
                Toast.makeText(requireContext(), "已清除历史版本", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteVersionConfirmDialog(version: ScriptVersion) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(version.timestamp))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除版本")
            .setMessage("确定要删除这个Cloudflare同步版本吗？\n\n时间: $date")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteVersion(version)
                Toast.makeText(requireContext(), "已删除版本", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
