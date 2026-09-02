package com.muort.upworker.feature.store

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.repository.CatalogRepository
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogSourcesManagerBinding
import com.muort.upworker.databinding.ItemSourceRowBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 数据源管理对话框
 * 支持添加、删除、启用/禁用、刷新数据源
 */
@AndroidEntryPoint
class SourcesManagerDialog : BottomSheetDialogFragment() {

    private var _binding: DialogSourcesManagerBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var catalogRepository: CatalogRepository

    private lateinit var adapter: SourceListAdapter
    private var onSourcesChanged: (() -> Unit)? = null

    companion object {
        fun newInstance(onSourcesChanged: (() -> Unit)? = null): SourcesManagerDialog {
            return SourcesManagerDialog().apply {
                this.onSourcesChanged = onSourcesChanged
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
        _binding = DialogSourcesManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAddButton()
        loadSources()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== 初始化 ==========

    private fun setupRecyclerView() {
        adapter = SourceListAdapter(
            onToggleEnabled = { source, enabled ->
                toggleSourceEnabled(source, enabled)
            },
            onRefresh = { source ->
                refreshSource(source)
            },
            onDelete = { source ->
                deleteSource(source)
            }
        )

        binding.sourcesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SourcesManagerDialog.adapter
        }
    }

    private fun setupAddButton() {
        binding.addSourceBtn.setOnClickListener {
            showAddSourceDialog()
        }
    }

    private fun loadSources() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                catalogRepository.observeSources().collect { sources ->
                    adapter.submitList(sources)
                    binding.emptyText.visibility =
                        if (sources.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ========== 操作 ==========

    private fun toggleSourceEnabled(source: CatalogSource, enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            catalogRepository.updateSourceEnabled(source.id, enabled)
            onSourcesChanged?.invoke()
        }
    }

    private fun refreshSource(source: CatalogSource) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    catalogRepository.refreshSource(source)
                }
            }
            result.onSuccess {
                showToast(getString(R.string.store_refresh))
                onSourcesChanged?.invoke()
            }.onFailure {
                showToast("${getString(R.string.store_source_error)}: ${it.message}")
            }
        }
    }

    private fun deleteSource(source: CatalogSource) {
        if (source.isDefault) {
            showToast("默认源不可删除")
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.store_delete_source))
            .setMessage(getString(R.string.store_delete_source_confirm, source.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    catalogRepository.deleteSource(source.id)
                    onSourcesChanged?.invoke()
                    showToast(getString(R.string.store_source_deleted))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddSourceDialog() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val nameInputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = getString(R.string.store_source_name)
            isHintEnabled = true
        }

        val nameEditText = TextInputEditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }

        val urlInputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 16 }
            hint = getString(R.string.store_source_url)
            isHintEnabled = true
        }

        val urlEditText = TextInputEditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            maxLines = 1
        }

        nameInputLayout.addView(nameEditText)
        urlInputLayout.addView(urlEditText)
        container.addView(nameInputLayout)
        container.addView(urlInputLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.store_add_source))
            .setView(container)
            .setPositiveButton(getString(R.string.store_add_source)) { _, _ ->
                val name = nameEditText.text?.toString()?.trim()
                val url = urlEditText.text?.toString()?.trim()

                if (name.isNullOrBlank()) {
                    showToast("请输入源名称")
                    return@setPositiveButton
                }
                if (url.isNullOrBlank() || !url.startsWith("https://")) {
                    showToast("请输入有效的 HTTPS URL")
                    return@setPositiveButton
                }

                addSource(name, url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addSource(name: String, url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    catalogRepository.addSource(url, name)
                }
            }
            result.onSuccess { source ->
                if (source != null) {
                    showToast(getString(R.string.store_source_added))
                    onSourcesChanged?.invoke()
                    // 刷新新添加的源
                    launch {
                        catalogRepository.refreshSource(source)
                    }
                } else {
                    showToast("添加失败")
                }
            }.onFailure {
                showToast("添加失败: ${it.message}")
            }
        }
    }

    // ==================== Adapter ====================

    inner class SourceListAdapter(
        private val onToggleEnabled: (CatalogSource, Boolean) -> Unit,
        private val onRefresh: (CatalogSource) -> Unit,
        private val onDelete: (CatalogSource) -> Unit
    ) : RecyclerView.Adapter<SourceListAdapter.SourceViewHolder>() {

        private var items: List<CatalogSource> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun submitList(list: List<CatalogSource>) {
            items = list
            notifyDataSetChanged()
        }

        inner class SourceViewHolder(val binding: ItemSourceRowBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
            val binding = ItemSourceRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return SourceViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
            val source = items[position]
            val binding = holder.binding

            // 图标
            binding.sourceIconText.text = if (source.isDefault) "⭐" else "📦"

            // 名称
            binding.sourceNameText.text = source.name

            // URL
            binding.sourceUrlText.text = source.url

            // 开关（先移除监听器避免触发）
            binding.enableSwitch.setOnCheckedChangeListener(null)
            binding.enableSwitch.isChecked = source.enabled
            binding.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(source, isChecked)
            }

            // 状态
            val statusRes = when (source.lastStatus) {
                "ok" -> R.string.store_source_ok
                "error" -> R.string.store_source_error
                else -> R.string.store_source_loading
            }
            binding.statusText.text = binding.root.context.getString(statusRes)

            // 模板数量 - 隐藏（数据源不直接存储模板数量）
            binding.templateCountText.visibility = View.GONE

            // 更多操作
            binding.moreBtn.setOnClickListener {
                showSourceMenu(source)
            }
        }

        private fun showSourceMenu(source: CatalogSource) {
            val context = binding.root.context
            val options = mutableListOf<String>()
            options.add(context.getString(R.string.store_refresh))

            if (!source.isDefault) {
                options.add(context.getString(R.string.store_delete_source))
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(source.name)
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        context.getString(R.string.store_refresh) -> onRefresh(source)
                        context.getString(R.string.store_delete_source) -> onDelete(source)
                    }
                }
                .show()
        }

        override fun getItemCount(): Int = items.size
    }
}
