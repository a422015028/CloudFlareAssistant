package com.muort.upworker.feature.scripteditor

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.R
import com.muort.upworker.core.model.ScriptVersion
import com.muort.upworker.databinding.FragmentScriptEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            
            addJavascriptInterface(JavaScriptBridge(), "AndroidBridge")
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Set theme based on current app theme
                    setEditorTheme()
                }
            }
            
            loadUrl("file:///android_asset/script_editor.html")
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
    
    private fun setEditorTheme() {
        val isDarkMode = when (resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        executeJavaScript("setTheme($isDarkMode)")
    }
    
    private fun setEditorContent(content: String) {
        val escapedContent = content
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        executeJavaScript("setContent('$escapedContent')")
    }
    
    private fun getEditorContent(callback: (String) -> Unit) {
        binding.webView.evaluateJavascript("getContent()") { result ->
            // Remove quotes from JSON string
            val content = result?.trim('"')?.let { unescapeJavaScript(it) } ?: ""
            callback(content)
        }
    }
    
    private fun unescapeJavaScript(text: String): String {
        var result = text
            .replace("\\\\", "\\")
            .replace("\\'", "'")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
        
        // 处理 Unicode 转义序列 (如 \u003C)
        val unicodePattern = Regex("""\\u([0-9a-fA-F]{4})""")
        result = unicodePattern.replace(result) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            hexCode.toInt(16).toChar().toString()
        }
        
        return result
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
            .setPositiveButton("恢复此版本") { _, _ ->
                setEditorContent(version.content)
                hasUnsavedChanges = true
                Toast.makeText(requireContext(), "已恢复到此版本", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
        
        // 如果是Cloudflare版本，添加删除按钮
        if (version.description == "从Cloudflare加载") {
            dialog.setNeutralButton("删除") { _, _ ->
                showDeleteVersionConfirmDialog(version)
            }
        }
        
        dialog.show()
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    /**
     * JavaScript Bridge for communication with CodeMirror
     */
    inner class JavaScriptBridge {
        
        @JavascriptInterface
        fun onEditorReady() {
            lifecycleScope.launch {
                isEditorReady = true
                Timber.d("Editor ready")
                
                // Load content if available
                viewModel.scriptContent.value?.let { content ->
                    setEditorContent(content)
                }
            }
        }
        
        @JavascriptInterface
        fun onContentChanged() {
            viewLifecycleOwner.lifecycleScope.launch {
                hasUnsavedChanges = true
            }
        }
        
        @JavascriptInterface
        fun onSaveRequested(content: String) {
            viewLifecycleOwner.lifecycleScope.launch {
                Timber.d("Manual save requested")
                saveVersion(content, isAutoSave = false)
            }
        }
        
        @JavascriptInterface
        fun onCopyRequested(content: String) {
            lifecycleScope.launch {
                copyToClipboard(content)
            }
        }
        
        @JavascriptInterface
        fun onShowSearch() {
            viewLifecycleOwner.lifecycleScope.launch {
                requireActivity().runOnUiThread {
                    showSearchBar()
                }
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
