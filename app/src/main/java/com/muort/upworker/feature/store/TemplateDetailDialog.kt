package com.muort.upworker.feature.store

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.muort.upworker.R
import com.muort.upworker.core.model.CatalogBinding
import com.muort.upworker.core.model.TemplateItem
import com.muort.upworker.core.repository.CatalogRepository
import com.muort.upworker.core.util.MarkdownRenderer
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogTemplateDetailBinding
import com.muort.upworker.databinding.ItemBindingRowBinding
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 模板详情对话框
 * 展示模板的详细信息、README、绑定配置等
 */
@AndroidEntryPoint
class TemplateDetailDialog : BottomSheetDialogFragment() {

    private var _binding: DialogTemplateDetailBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var catalogRepository: CatalogRepository

    private lateinit var templateItem: TemplateItem
    private var onDeployClick: (() -> Unit)? = null
    private var onFavoriteChanged: ((Boolean) -> Unit)? = null

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val ARG_TEMPLATE = "template_item"

        fun newInstance(
            templateItem: TemplateItem,
            onDeployClick: (() -> Unit)? = null,
            onFavoriteChanged: ((Boolean) -> Unit)? = null
        ): TemplateDetailDialog {
            return TemplateDetailDialog().apply {
                this.templateItem = templateItem
                this.onDeployClick = onDeployClick
                this.onFavoriteChanged = onFavoriteChanged
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTemplateDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTemplateInfo()
        setupBindings()
        setupReadme()
        setupButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== 初始化 ==========

    private fun setupTemplateInfo() {
        val template = templateItem.template

        // 图标
        val icon = template.icon
        if (!icon.isNullOrBlank() && icon.length <= 4) {
            binding.iconText.text = icon
        } else {
            binding.iconText.text = "📦"
        }

        // 名称
        binding.nameText.text = template.name

        // 版本
        binding.versionText.text = getString(R.string.store_version_prefix, template.version)

        // 类型
        val typeText = when (template.type) {
            "worker" -> getString(R.string.store_worker)
            "pages" -> getString(R.string.store_pages)
            "hybrid" -> getString(R.string.store_hybrid)
            else -> template.type
        }
        binding.typeChip.text = typeText

        // 作者
        val authorName = template.authorName
        if (authorName.isNullOrBlank()) {
            binding.authorText.visibility = View.GONE
        } else {
            binding.authorText.visibility = View.VISIBLE
            binding.authorText.text = getString(R.string.store_by_author, authorName)
        }

        // 描述
        if (template.description.isNullOrBlank()) {
            binding.descriptionText.visibility = View.GONE
        } else {
            binding.descriptionText.visibility = View.VISIBLE
            binding.descriptionText.text = template.description
        }

        // 来源
        val sourceCount = templateItem.sourceCount
        if (sourceCount > 1) {
            binding.sourceText.text = getString(
                R.string.store_source_multi,
                templateItem.template.sourceName,
                sourceCount
            )
        } else {
            binding.sourceText.text = getString(R.string.store_source_single, templateItem.template.sourceName)
        }

        // 收藏状态
        updateFavoriteButton(templateItem.isFavorite)
    }

    private fun setupBindings() {
        val bindings = catalogRepository.parseBindings(templateItem.template.bindingsJson)

        if (bindings.isEmpty()) {
            binding.bindingsSectionTitle.visibility = View.GONE
            binding.bindingsContainer.visibility = View.GONE
            return
        }

        binding.bindingsSectionTitle.visibility = View.VISIBLE
        binding.bindingsContainer.visibility = View.VISIBLE
        binding.bindingsContainer.removeAllViews()

        for (b in bindings) {
            val itemBinding = ItemBindingRowBinding.inflate(layoutInflater)
            bindBindingItem(itemBinding, b)
            binding.bindingsContainer.addView(itemBinding.root)
        }
    }

    private fun bindBindingItem(itemBinding: ItemBindingRowBinding, binding: CatalogBinding) {
        // 图标和类型名称
        val (icon, typeName) = getBindingTypeInfo(binding.type)
        itemBinding.bindingIconText.text = icon
        itemBinding.bindingTypeText.text = typeName

        // 绑定名称（显示 title 或 name）
        val displayName = binding.title ?: binding.name
        itemBinding.bindingNameText.text = displayName

        // 如果有 title，显示实际绑定名作为描述
        if (!binding.title.isNullOrBlank() && binding.name != binding.title) {
            itemBinding.bindingDescText.visibility = View.VISIBLE
            itemBinding.bindingDescText.text = binding.name
        } else {
            itemBinding.bindingDescText.visibility = View.GONE
        }

        // 必填标识
        if (binding.required) {
            itemBinding.requiredChip.visibility = View.VISIBLE
        } else {
            itemBinding.requiredChip.visibility = View.GONE
        }
    }

    private fun getBindingTypeInfo(type: String): Pair<String, String> {
        return when (type) {
            "kv" -> "🗄️" to getString(R.string.store_binding_type_kv)
            "d1" -> "🗃️" to getString(R.string.store_binding_type_d1)
            "r2" -> "📦" to getString(R.string.store_binding_type_r2)
            "ai" -> "🤖" to getString(R.string.store_binding_type_ai)
            "var" -> "🔑" to getString(R.string.store_binding_type_var)
            "durable_object" -> "📌" to getString(R.string.store_binding_type_durable_object)
            "service" -> "🔗" to getString(R.string.store_binding_type_service)
            "queue" -> "📬" to getString(R.string.store_binding_type_queue)
            else -> "📎" to type
        }
    }

    private fun setupReadme() {
        // 配置 WebView
        setupWebView()

        val readmeUrl = templateItem.template.readmeUrl
        if (readmeUrl.isNullOrBlank()) {
            // 没有 README，显示模板描述
            binding.readmeLoadingText.visibility = View.GONE
            val desc = templateItem.template.description ?: getString(R.string.store_no_readme)
            MarkdownRenderer.renderToWebView(binding.readmeWebView, "# ${templateItem.template.name}\n\n$desc")
            return
        }

        // 加载 README
        binding.readmeLoadingText.visibility = View.VISIBLE
        loadReadme(readmeUrl)
    }

    /**
     * 配置 WebView：智能高度 + 独立滚动 + 滑动冲突处理
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupWebView() {
        val webView = binding.readmeWebView
        val container = binding.readmeContainer

        // 基础设置
        webView.settings.javaScriptEnabled = false
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.setSupportZoom(false)
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

        // 背景透明，让 WebView 跟随外层容器的主题色
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        // 设置 WebView 客户端，监听页面加载完成
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.readmeLoadingText.visibility = View.GONE
                // 页面加载完成后调整高度
                adjustWebViewHeight()
            }
        }

        // 滑动冲突处理：当在 WebView 内垂直滑动时，禁止父容器拦截触摸事件
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下时，先禁止父容器拦截
                    v.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    // 移动时判断是否需要父容器拦截
                    val wv = v as WebView
                    val scrollY = wv.scrollY
                    val density = wv.resources.displayMetrics.density
                    val contentHeight = (wv.contentHeight * density).toInt()
                    val webViewHeight = wv.height

                    // 检查是否在顶部且继续下拉，或在底部且继续上拉
                    val isAtTop = scrollY <= 0
                    val isAtBottom = scrollY + webViewHeight >= contentHeight - 1

                    if (isAtTop || isAtBottom) {
                        // 到顶或到底了，让父容器处理
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    } else {
                        // 还在 WebView 内部滚动，禁止父容器拦截
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 抬起或取消时，恢复父容器拦截
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

        // 监听容器布局变化，计算最大高度
        container.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                adjustWebViewHeight()
            }
        })
    }

    /**
     * 智能调整 WebView 高度：
     * - 内容少：wrap_content 自适应
     * - 内容多：限制最大高度为屏幕的 60%，内部可滚动
     */
    private fun adjustWebViewHeight() {
        val webView = binding.readmeWebView

        // 获取屏幕可用高度的 60% 作为最大高度
        val displayMetrics = resources.displayMetrics
        val maxHeight = (displayMetrics.heightPixels * 0.6f).toInt()

        // 获取 WebView 内容高度（contentHeight 返回的是 CSS 像素，乘以 density 转换为实际像素）
        val contentHeight = (webView.contentHeight * displayMetrics.density).toInt()

        val layoutParams = webView.layoutParams
        if (contentHeight <= 0) {
            // 内容高度未知，先设为 wrap_content
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        } else if (contentHeight < maxHeight) {
            // 内容较少，自适应高度
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            // 内容较多，限制最大高度，内部滚动
            layoutParams.height = maxHeight
        }
        webView.layoutParams = layoutParams
    }

    private fun loadReadme(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        if (isAdded && _binding != null) {
                            showReadmeError("HTTP ${response.code}")
                        }
                    }
                    return@launch
                }

                val content = response.body?.string()
                if (content.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        if (isAdded && _binding != null) {
                            showReadmeError("Empty content")
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        binding.readmeLoadingText.visibility = View.GONE
                        MarkdownRenderer.renderToWebView(binding.readmeWebView, content, url)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[TemplateDetail] 加载 README 失败")
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        showReadmeError(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    private fun showReadmeError(error: String) {
        binding.readmeLoadingText.text = getString(R.string.store_readme_failed, error)
        binding.readmeLoadingText.visibility = View.VISIBLE
    }

    private fun setupButtons() {
        // 收藏按钮
        binding.favoriteBtn.setOnClickListener {
            val templateId = templateItem.template.templateId
            lifecycleScope.launch(Dispatchers.IO) {
                val newState = catalogRepository.toggleFavorite(templateId)
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        templateItem = templateItem.copy(isFavorite = newState)
                        updateFavoriteButton(newState)
                        onFavoriteChanged?.invoke(newState)
                    }
                }
            }
        }

        // 部署按钮
        binding.deployBtn.setOnClickListener {
            onDeployClick?.invoke()
            dismiss()
        }
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        if (isFavorite) {
            binding.favoriteBtn.setIconResource(R.drawable.ic_favorite_24)
            binding.favoriteBtn.contentDescription = getString(R.string.store_remove_favorite)
        } else {
            binding.favoriteBtn.setIconResource(R.drawable.ic_favorite_border_24)
            binding.favoriteBtn.contentDescription = getString(R.string.store_add_favorite)
        }
    }
}
