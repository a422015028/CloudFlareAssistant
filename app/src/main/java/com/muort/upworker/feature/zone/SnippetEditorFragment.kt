package com.muort.upworker.feature.zone

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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.SnippetRepository
import com.muort.upworker.databinding.FragmentSnippetEditorBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SnippetEditorFragment : Fragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    private var _binding: FragmentSnippetEditorBinding? = null
    private val binding get() = _binding!!

    private val args: SnippetEditorFragmentArgs by navArgs()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private val account: Account?
        get() = accountViewModel.defaultAccount.value

    private var isEditorReady = false
    private var hasUnsavedChanges = false
    private var originalContent: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnippetEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupBackPressHandler()
        setupWebView()
        setupButtons()
        setupLayoutListener()

        loadSnippetContent()
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            title = args.snippetName
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
        findNavController().navigateUp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun setupWebView() {
        binding.webView.apply {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            }

            addJavascriptInterface(JavaScriptBridge(), "AndroidBridge")

            setOnLongClickListener { false }

            setOnTouchListener { _, event ->
                handleTouchEvent(event)
                false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
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

            loadUrl("file:///android_asset/code_editor.html")
        }
    }

    private fun setupButtons() {
        binding.btnCopy.setOnClickListener {
            getEditorContent { content ->
                copyToClipboard(content)
            }
        }

        binding.btnSave.setOnClickListener {
            getEditorContent { content ->
                saveSnippet(content)
            }
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }

        binding.btnSearch.setOnClickListener {
            toggleSearchBar()
        }

        binding.webViewContainer.setOnClickListener {
            if (binding.searchBar.visibility == View.VISIBLE) {
                hideSearchBar()
            }
        }

        binding.btnSelectAll.setOnClickListener {
            executeJavaScript("selectAll()")
        }

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

    private fun loadSnippetContent() {
        binding.progressBar.visibility = View.VISIBLE

        if (account == null) {
            Snackbar.make(binding.root, "账号未就绪", Snackbar.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            return
        }

        if (args.isNew) {
            binding.progressBar.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.getSnippetContent(account!!, args.zoneId, args.snippetName)) {
                is Resource.Success -> {
                    originalContent = r.data
                    if (isEditorReady) {
                        setEditorContent(r.data)
                    }
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, "读取失败: ${r.message}", Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> {}
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun saveSnippet(content: String) {
        binding.progressBar.visibility = View.VISIBLE

        if (account == null) {
            Snackbar.make(binding.root, "账号未就绪", Snackbar.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.putSnippet(account!!, args.zoneId, args.snippetName, content)) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, "保存成功", Snackbar.LENGTH_SHORT).show()
                    hasUnsavedChanges = false
                    originalContent = content
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, "保存失败: ${r.message}", Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> {}
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun showDeleteConfirmDialog() {
        if (args.isNew) {
            findNavController().navigateUp()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除代码片段")
            .setMessage("确定要删除「${args.snippetName}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                deleteSnippet()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteSnippet() {
        binding.progressBar.visibility = View.VISIBLE

        if (account == null) {
            Snackbar.make(binding.root, "账号未就绪", Snackbar.LENGTH_LONG).show()
            binding.progressBar.visibility = View.GONE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = snippetRepo.deleteSnippet(account!!, args.zoneId, args.snippetName)) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, "已删除", Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, "删除失败: ${r.message}", Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> {}
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setEditorContent(content: String) {
        val jsonContent = org.json.JSONObject().apply {
            put("c", content)
        }.toString()
        executeJavaScript("setContentFromJSON($jsonContent)")
    }

    private fun getEditorContent(callback: (String) -> Unit) {
        binding.webView.evaluateJavascript("getContent()") { result ->
            val content = if (!result.isNullOrEmpty() && result != "null") {
                try {
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
        val clip = ClipData.newPlainText("Snippet Code", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
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

    private var lastAppliedDarkMode: Boolean? = null

    private fun setEditorTheme() {
        val isDarkMode = when (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        if (lastAppliedDarkMode == isDarkMode) return
        lastAppliedDarkMode = isDarkMode

        val bgColor = if (isDarkMode) 0xFF282a36.toInt() else 0xFFFFFFFF.toInt()
        binding.webView.setBackgroundColor(bgColor)
        executeJavaScript("setTheme($isDarkMode)")
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

    inner class JavaScriptBridge {

        @JavascriptInterface
        fun onEditorReady() {
            requireActivity().runOnUiThread {
                isEditorReady = true
                if (originalContent.isNotEmpty()) {
                    setEditorContent(originalContent)
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
                saveSnippet(content)
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
}