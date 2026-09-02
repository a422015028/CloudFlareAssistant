package com.muort.upworker.feature.store

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.muort.upworker.R
import com.muort.upworker.core.model.TemplateItem
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentStoreBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 模板商店 Fragment
 * 展示模板列表，支持搜索、筛选、收藏、部署等功能
 */
@AndroidEntryPoint
class StoreFragment : Fragment() {

    private var _binding: FragmentStoreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StoreViewModel by viewModels()

    private lateinit var adapter: StoreCardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupTypeFilter()
        setupToolbar()
        setupSwipeRefresh()
        observeData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== 初始化 ==========

    private fun setupRecyclerView() {
        adapter = StoreCardAdapter(
            onItemClick = { item ->
                showTemplateDetail(item)
            },
            onDeployClick = { item ->
                showDeployDialog(item)
            },
            onFavoriteClick = { item ->
                viewModel.toggleFavorite(item.template.templateId)
            }
        )

        // 网格布局：根据屏幕宽度自动调整列数
        val spanCount = resources.getInteger(R.integer.store_grid_span_count)
        val layoutManager = GridLayoutManager(requireContext(), spanCount)

        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@StoreFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearch() {
        // 文本变化实时搜索
        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })

        // 刷新按钮
        binding.refreshBtn.setOnClickListener {
            viewModel.refreshTemplates()
        }

        // 数据源按钮
        binding.sourcesBtn.setOnClickListener {
            showSourcesManager()
        }
    }

    private fun setupTypeFilter() {
        binding.typeChipGroup.setOnCheckedStateChangeListener { group, _ ->
            val type = when (group.checkedChipId) {
                R.id.chipWorker -> "worker"
                R.id.chipPages -> "pages"
                R.id.chipHybrid -> "hybrid"
                else -> null // all
            }
            viewModel.setSelectedType(type)
        }
    }

    private fun setupToolbar() {
        // 排序按钮
        binding.sortBtn.setOnClickListener {
            val current = viewModel.sortBy.value
            val next = if (current == "name") "version" else "name"
            viewModel.setSortBy(next)
            updateSortButton(next)
        }

        // 仅收藏按钮
        binding.favFilterBtn.setOnClickListener {
            val enabled = !viewModel.favOnly.value
            viewModel.setFavOnly(enabled)
            updateFavFilterButton(enabled)
        }
    }

    private fun setupSwipeRefresh() {
        binding.emptyRefreshBtn.setOnClickListener {
            viewModel.refreshTemplates()
        }
    }

    // ========== 数据观察 ==========

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.templates.collect { templates ->
                        adapter.submitList(templates)
                        updateResultCount(templates.size)
                        updateEmptyState(templates.isEmpty())
                    }
                }

                launch {
                    viewModel.isRefreshing.collect { _ ->
                        // 刷新状态通过空状态视图展示
                    }
                }

                launch {
                    viewModel.typeCounts.collect { counts ->
                        updateTypeChipCounts(counts)
                    }
                }

                launch {
                    viewModel.sources.collect { sources ->
                        updateSourceStatus(sources)
                    }
                }

                launch {
                    viewModel.refreshEvent.collect { result ->
                        if (result.success) {
                            if (result.failedCount > 0) {
                                showToast(getString(R.string.store_refresh_partial, result.successCount, result.failedCount))
                            } else {
                                showToast(getString(R.string.store_refresh_success))
                            }
                        } else {
                            val msg = result.message
                            if (msg != null) {
                                showToast("${getString(R.string.store_refresh_failed)}: $msg")
                            } else {
                                showToast(getString(R.string.store_refresh_failed))
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== UI 更新 ==========

    private fun updateSourceStatus(sources: List<com.muort.upworker.core.model.CatalogSource>) {
        val updated = sources.count { it.lastStatus == "ok" }
        binding.sourceStatusText.text = getString(R.string.store_source_status, updated)
    }

    private fun updateResultCount(count: Int) {
        binding.resultCountText.text = resources.getQuantityString(
            R.plurals.store_template_count,
            count,
            count
        )
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE

            val isRefreshing = viewModel.isRefreshing.value
            val hasQuery = viewModel.searchQuery.value.isNotBlank()
            val hasFilters = viewModel.selectedType.value != null ||
                    viewModel.selectedTags.value.isNotEmpty() ||
                    viewModel.favOnly.value

            when {
                isRefreshing -> {
                    binding.emptySubtitleText.text = getString(R.string.store_loading)
                    binding.emptyRefreshBtn.visibility = View.GONE
                }
                hasQuery -> {
                    binding.emptySubtitleText.text = getString(R.string.store_no_search_results)
                    binding.emptyRefreshBtn.visibility = View.GONE
                }
                hasFilters -> {
                    binding.emptySubtitleText.text = getString(R.string.store_no_filter_results)
                    binding.emptyRefreshBtn.visibility = View.VISIBLE
                    binding.emptyRefreshBtn.text = getString(R.string.store_clear_filters)
                    binding.emptyRefreshBtn.setOnClickListener {
                        viewModel.clearFilters()
                        resetTypeFilter()
                    }
                }
                else -> {
                    binding.emptySubtitleText.text = getString(R.string.store_pull_to_refresh)
                    binding.emptyRefreshBtn.visibility = View.VISIBLE
                    binding.emptyRefreshBtn.text = getString(R.string.store_refresh)
                    binding.emptyRefreshBtn.setOnClickListener {
                        viewModel.refreshTemplates()
                    }
                }
            }
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun updateSortButton(sort: String) {
        when (sort) {
            "version" -> {
                binding.sortBtn.text = getString(R.string.store_sort_version)
            }
            else -> {
                binding.sortBtn.text = getString(R.string.store_sort_name)
            }
        }
    }

    private fun updateFavFilterButton(enabled: Boolean) {
        if (enabled) {
            binding.favFilterBtn.setIconResource(R.drawable.ic_favorite_24)
        } else {
            binding.favFilterBtn.setIconResource(R.drawable.ic_favorite_border_24)
        }
    }

    private fun updateTypeChipCounts(counts: Map<String, Int>) {
        val total = counts.values.sum()
        (binding.typeChipGroup.getChildAt(0) as? Chip)?.text =
            getString(R.string.store_all_with_count, total)
        (binding.typeChipGroup.getChildAt(1) as? Chip)?.text =
            getString(R.string.store_worker_with_count, counts["worker"] ?: 0)
        (binding.typeChipGroup.getChildAt(2) as? Chip)?.text =
            getString(R.string.store_pages_with_count, counts["pages"] ?: 0)
        (binding.typeChipGroup.getChildAt(3) as? Chip)?.text =
            getString(R.string.store_hybrid_with_count, counts["hybrid"] ?: 0)
    }

    private fun resetTypeFilter() {
        binding.typeChipGroup.check(R.id.chipAll)
    }

    // ========== 数据源管理对话框 ==========

    private fun showSourcesManager() {
        val dialog = SourcesManagerDialog.newInstance()
        dialog.show(childFragmentManager, "SourcesManagerDialog")
    }

    // ========== 详情对话框 ==========

    private fun showTemplateDetail(item: TemplateItem) {
        val dialog = TemplateDetailDialog.newInstance(
            templateItem = item,
            onDeployClick = {
                showDeployDialog(item)
            },
            onFavoriteChanged = { _ ->
                // 收藏状态变化后刷新列表（通过 Flow 自动更新）
            }
        )
        dialog.show(childFragmentManager, "TemplateDetailDialog")
    }

    // ========== 部署对话框 ==========

    private fun showDeployDialog(item: TemplateItem) {
        val dialog = DeployTemplateDialog.newInstance(
            template = item.template,
            onDeploySuccess = {
                showToast(getString(R.string.store_deploy_success))
            }
        )
        dialog.show(childFragmentManager, "DeployTemplateDialog")
    }
}
