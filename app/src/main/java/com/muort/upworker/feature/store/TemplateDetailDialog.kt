package com.muort.upworker.feature.store

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        binding.versionText.text = "v${template.version}"

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
            "kv" -> "🗄️" to "KV 命名空间"
            "d1" -> "🗃️" to "D1 数据库"
            "r2" -> "📦" to "R2 存储桶"
            "ai" -> "🤖" to "AI 绑定"
            "var" -> "🔑" to "环境变量"
            "durable_object" -> "📌" to "Durable Object"
            "service" -> "🔗" to "Service 绑定"
            "queue" -> "📬" to "队列"
            else -> "📎" to type
        }
    }

    private fun setupReadme() {
        val readmeUrl = templateItem.template.readmeUrl
        if (readmeUrl.isNullOrBlank()) {
            // 没有 README，显示模板描述
            binding.readmeLoadingText.visibility = View.GONE
            val desc = templateItem.template.description ?: getString(R.string.store_no_readme)
            MarkdownRenderer.renderToWebView(binding.readmeWebView, "# ${templateItem.template.name}\n\n$desc")
            return
        }

        // 设置 WebView
        binding.readmeWebView.settings.javaScriptEnabled = false
        binding.readmeWebView.settings.loadWithOverviewMode = true
        binding.readmeWebView.settings.useWideViewPort = true

        // 加载 README
        binding.readmeLoadingText.visibility = View.VISIBLE
        loadReadme(readmeUrl)
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
