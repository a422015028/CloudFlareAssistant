package com.muort.upworker.feature.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.model.TemplateItem
import com.muort.upworker.core.repository.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 模板商店 ViewModel
 * 管理模板列表、筛选、搜索、收藏、刷新等状态
 */
@HiltViewModel
class StoreViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    // ========== 筛选状态 ==========

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow<String?>(null) // null = all
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    private val _sortBy = MutableStateFlow("name") // name / version
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()

    private val _favOnly = MutableStateFlow(false)
    val favOnly: StateFlow<Boolean> = _favOnly.asStateFlow()

    // ========== 数据源 ==========

    val sources: StateFlow<List<CatalogSource>> =
        catalogRepository.observeSources()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== 模板列表（带筛选） ==========

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<TemplateItem>> = combine(
        _searchQuery,
        _selectedType,
        _selectedTags,
        _sortBy,
        _favOnly
    ) { query, type, tags, sort, favOnly ->
        FilterParams(query, type, tags, sort, favOnly)
    }.flatMapLatest { params ->
        if (params.query.isBlank()) {
            catalogRepository.observeAllTemplates()
        } else {
            catalogRepository.searchTemplates(params.query)
        }.map { list ->
            applyFilters(list, params)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== 所有标签（用于标签云） ==========

    val allTags: StateFlow<List<String>> =
        catalogRepository.observeAllTemplates()
            .map { list ->
                list.flatMap { item ->
                    item.template.tags?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?: emptyList()
                }.distinct().sorted()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ========== 各类型计数 ==========

    val typeCounts: StateFlow<Map<String, Int>> =
        catalogRepository.observeAllTemplates()
            .map { list ->
                mapOf(
                    "worker" to list.count { it.template.type == "worker" },
                    "pages" to list.count { it.template.type == "pages" },
                    "hybrid" to list.count { it.template.type == "hybrid" }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ========== 刷新状态 ==========

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // ========== 初始化 ==========

    init {
        viewModelScope.launch {
            catalogRepository.ensureDefaultSource()
            // 初始化后自动刷新一次
            refreshTemplates()
        }
    }

    // ========== 筛选操作 ==========

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedType(type: String?) {
        _selectedType.value = type
    }

    fun toggleTag(tag: String) {
        val current = _selectedTags.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTags.value = current
    }

    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }

    fun setFavOnly(enabled: Boolean) {
        _favOnly.value = enabled
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _selectedType.value = null
        _selectedTags.value = emptySet()
        _favOnly.value = false
    }

    // ========== 刷新 ==========

    fun refreshTemplates() {
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            _isRefreshing.value = true
            try {
                val results = catalogRepository.refreshAllSources()
                val failed = results.count { it.lastStatus == "error" }
                if (failed > 0) {
                    Timber.w("[Store] 刷新完成，$failed 个源失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[Store] 刷新失败")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ========== 收藏 ==========

    fun toggleFavorite(templateId: String) {
        viewModelScope.launch {
            catalogRepository.toggleFavorite(templateId)
        }
    }

    // ========== 内部方法 ==========

    /**
     * 应用筛选条件到模板列表
     */
    private fun applyFilters(
        list: List<TemplateItem>,
        params: FilterParams
    ): List<TemplateItem> {
        var result = list

        // 类型筛选
        if (params.type != null) {
            result = result.filter { it.template.type == params.type }
        }

        // 标签筛选
        if (params.tags.isNotEmpty()) {
            result = result.filter { item ->
                val itemTags = item.template.tags?.split(",")?.toSet() ?: emptySet()
                params.tags.all { itemTags.contains(it) }
            }
        }

        // 仅收藏
        if (params.favOnly) {
            result = result.filter { it.isFavorite }
        }

        // 排序
        result = when (params.sort) {
            "version" -> result.sortedByDescending { it.template.version }
            else -> result.sortedBy { it.template.name.lowercase() }
        }

        // 收藏的排前面
        result = result.sortedByDescending { it.isFavorite }

        return result
    }

    /**
     * 筛选参数
     */
    private data class FilterParams(
        val query: String,
        val type: String?,
        val tags: Set<String>,
        val sort: String,
        val favOnly: Boolean
    )
}
