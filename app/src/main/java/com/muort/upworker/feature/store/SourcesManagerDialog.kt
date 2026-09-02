package com.muort.upworker.feature.store

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.repository.CatalogRepository
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogAddSourceBinding
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

    companion object {
        fun newInstance(): SourcesManagerDialog {
            return SourcesManagerDialog()
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
            onItemClick = { source ->
                showEditSourceDialog(source)
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
        catalogRepository.updateSourceEnabled(source.id, enabled)
    }

    private fun showAddSourceDialog() {
        showSourceEditorDialog(null)
    }

    private fun showEditSourceDialog(source: CatalogSource) {
        showSourceEditorDialog(source)
    }

    private fun showSourceEditorDialog(editingSource: CatalogSource?) {
        val dialogBinding = DialogAddSourceBinding.inflate(layoutInflater)
        val isEditing = editingSource != null

        if (isEditing) {
            dialogBinding.titleText.text = getString(R.string.store_edit_source)
            dialogBinding.nameEditText.setText(editingSource!!.name)
            dialogBinding.urlEditText.setText(editingSource.url)
            dialogBinding.saveBtn.setText(R.string.store_save)
            // 非默认源显示删除按钮
            if (!editingSource.isDefault) {
                dialogBinding.deleteBtn.visibility = View.VISIBLE
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        fun setButtonsEnabled(enabled: Boolean) {
            dialogBinding.saveBtn.isEnabled = enabled
            dialogBinding.cancelBtn.isEnabled = enabled
            dialogBinding.deleteBtn.isEnabled = enabled
        }

        // 保存/添加按钮
        dialogBinding.saveBtn.setOnClickListener {
            val name = dialogBinding.nameEditText.text?.toString()?.trim()
            val url = dialogBinding.urlEditText.text?.toString()?.trim()

            dialogBinding.errorText.visibility = View.GONE

            if (name.isNullOrBlank()) {
                dialogBinding.errorText.text = getString(R.string.store_source_name_hint)
                dialogBinding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (url.isNullOrBlank() || !url.startsWith("https://")) {
                dialogBinding.errorText.text = getString(R.string.store_source_url_invalid)
                dialogBinding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            dialogBinding.savingProgress.visibility = View.VISIBLE
            setButtonsEnabled(false)

            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (isEditing && editingSource != null) {
                            catalogRepository.updateSource(editingSource.id, name, url)
                            true
                        } else {
                            val newSource = catalogRepository.addSource(url, name)
                            newSource?.let { catalogRepository.refreshSource(it) }
                            newSource != null
                        }
                    }
                }
                result.onSuccess { success ->
                    if (success) {
                        showToast(
                            if (isEditing) getString(R.string.store_source_updated)
                            else getString(R.string.store_source_added)
                        )
                        dialog.dismiss()
                    } else {
                        dialogBinding.savingProgress.visibility = View.GONE
                        setButtonsEnabled(true)
                        dialogBinding.errorText.text = getString(R.string.store_source_operation_failed)
                        dialogBinding.errorText.visibility = View.VISIBLE
                    }
                }.onFailure {
                    dialogBinding.savingProgress.visibility = View.GONE
                    setButtonsEnabled(true)
                    dialogBinding.errorText.text = getString(R.string.store_source_operation_failed_with_msg, it.message ?: "unknown")
                    dialogBinding.errorText.visibility = View.VISIBLE
                }
            }
        }

        // 取消按钮
        dialogBinding.cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        // 删除按钮（编辑非默认源时）
        if (isEditing && editingSource != null && !editingSource.isDefault) {
            dialogBinding.deleteBtn.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    catalogRepository.deleteSource(editingSource.id)
                    showToast(getString(R.string.store_source_deleted))
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // ==================== Adapter ====================

    inner class SourceListAdapter(
        private val onToggleEnabled: (CatalogSource, Boolean) -> Unit,
        private val onItemClick: (CatalogSource) -> Unit
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
                "loading" -> R.string.store_source_loading
                else -> R.string.store_source_idle
            }
            binding.statusText.text = binding.root.context.getString(statusRes)

            // 模板数量 - 隐藏（数据源不直接存储模板数量）
            binding.templateCountText.visibility = View.GONE

            // 点击整行弹出编辑对话框
            binding.root.setOnClickListener {
                onItemClick(source)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
