package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.ZoneSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CacheLevel(val raw: String, val label: String) {
    BASIC("basic", "无查询字符串"),
    SIMPLIFIED("simplified", "忽略查询字符串"),
    AGGRESSIVE("aggressive", "标准");

    companion object {
        fun fromRaw(raw: String?): CacheLevel = entries.firstOrNull { it.raw == raw } ?: AGGRESSIVE
    }
}

data class PerfToggle(val id: String, val label: String)

data class PerfState(
    val values: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val updating: Set<String> = emptySet(),
) {
    fun isOn(id: String): Boolean = values[id] == "on"
    val cacheLevel: CacheLevel get() = CacheLevel.fromRaw(values["cache_level"])
}

@AndroidEntryPoint
class PerformanceFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: PerfAdapter
    private var state = PerfState(isLoading = true)

    override val emptyText: String = "加载中…"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PerfAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PerformanceFragment.adapter
        }
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            state = state.copy(isLoading = true)
            try {
                val ids = NETWORK_TOGGLES.map { it.id } +
                    listOf("cache_level", "always_online", "sort_query_string_for_cache")
                val results = ids.map { id ->
                    id to async {
                        runCatching {
                            when (val r = settingsRepo.getSetting(account, zoneId, id)) {
                                is Resource.Success -> r.data
                                is Resource.Error -> null
                                is Resource.Loading -> null
                            }
                        }.getOrNull()
                    }
                }
                val acc = buildMap {
                    results.forEach { (id, deferred) ->
                        deferred.await()?.let { put(id, it) }
                    }
                }
                state = state.copy(values = acc, isLoading = false)
                showList()
                adapter.submitList(buildItems())
            } finally {
                state = state.copy(isLoading = false)
            }
        }
    }

    private fun buildItems(): List<PerfItem> {
        val items = mutableListOf<PerfItem>()
        items += PerfItem.Section("网络优化")
        NETWORK_TOGGLES.forEach { toggle ->
            items += PerfItem.Toggle(
                id = toggle.id,
                title = toggle.label,
                checked = state.isOn(toggle.id),
                enabled = toggle.id !in state.updating && toggle.id in state.values,
            )
        }
        items += PerfItem.Section("缓存")
        items += PerfItem.Selector(
            id = "cache_level",
            title = "缓存级别",
            value = state.cacheLevel.label,
            enabled = "cache_level" !in state.updating && "cache_level" in state.values,
        )
        items += PerfItem.Toggle(
            id = "always_online",
            title = "Always Online",
            checked = state.isOn("always_online"),
            enabled = "always_online" !in state.updating && "always_online" in state.values,
        )
        items += PerfItem.Toggle(
            id = "sort_query_string_for_cache",
            title = "查询字符串排序",
            checked = state.isOn("sort_query_string_for_cache"),
            enabled = "sort_query_string_for_cache" !in state.updating &&
                "sort_query_string_for_cache" in state.values,
        )
        return items
    }

    private fun onToggleClick(id: String, checked: Boolean) {
        val account = account ?: return
        if (id in state.updating) return
        state = state.copy(updating = state.updating + id)
        adapter.submitList(buildItems())
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, id, if (checked) "on" else "off")) {
                is Resource.Success -> {
                    state = state.copy(values = state.values + (id to r.data))
                }
                is Resource.Error -> {
                    toast("更新失败: ${r.message}")
                }
                is Resource.Loading -> {}
            }
            state = state.copy(updating = state.updating - id)
            adapter.submitList(buildItems())
        }
    }

    private fun onCacheLevelClick() {
        val account = account ?: return
        if ("cache_level" in state.updating) return

        val levels = CacheLevel.entries
        val currentIndex = levels.indexOf(state.cacheLevel)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("缓存级别")
            .setSingleChoiceItems(levels.map { it.label }.toTypedArray(), currentIndex) { dialog, which ->
                val level = levels[which]
                dialog.dismiss()
                setCacheLevel(account, level)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setCacheLevel(account: Account, level: CacheLevel) {
        if ("cache_level" in state.updating) return
        state = state.copy(updating = state.updating + "cache_level")
        adapter.submitList(buildItems())
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, "cache_level", level.raw)) {
                is Resource.Success -> {
                    state = state.copy(values = state.values + ("cache_level" to r.data))
                }
                is Resource.Error -> {
                    toast("更新失败: ${r.message}")
                }
                is Resource.Loading -> {}
            }
            state = state.copy(updating = state.updating - "cache_level")
            adapter.submitList(buildItems())
        }
    }

    companion object {
        val NETWORK_TOGGLES = listOf(
            PerfToggle("brotli", "Brotli 压缩"),
            PerfToggle("http2", "HTTP/2"),
            PerfToggle("http3", "HTTP/3 (QUIC)"),
            PerfToggle("0rtt", "0-RTT 连接恢复"),
            PerfToggle("early_hints", "Early Hints"),
            PerfToggle("websockets", "WebSockets"),
            PerfToggle("ipv6", "IPv6 兼容"),
        )
    }

    inner class PerfAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<PerfItem> = emptyList()

        fun submitList(newItems: List<PerfItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is PerfItem.Section -> VIEW_TYPE_SECTION
            is PerfItem.Toggle -> VIEW_TYPE_TOGGLE
            is PerfItem.Selector -> VIEW_TYPE_SELECTOR
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_SECTION -> {
                    val view = inflater.inflate(R.layout.item_perf_section, parent, false)
                    SectionVH(view)
                }
                VIEW_TYPE_TOGGLE -> {
                    val view = inflater.inflate(R.layout.item_perf_toggle, parent, false)
                    ToggleVH(view)
                }
                VIEW_TYPE_SELECTOR -> {
                    val view = inflater.inflate(R.layout.item_perf_selector, parent, false)
                    SelectorVH(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when {
                holder is SectionVH && item is PerfItem.Section -> holder.bind(item)
                holder is ToggleVH && item is PerfItem.Toggle -> holder.bind(item)
                holder is SelectorVH && item is PerfItem.Selector -> holder.bind(item)
            }
        }

        override fun getItemCount(): Int = items.size

        private val VIEW_TYPE_SECTION = 0
        private val VIEW_TYPE_TOGGLE = 1
        private val VIEW_TYPE_SELECTOR = 2
    }

    inner class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText = view.findViewById<TextView>(R.id.titleText)
        fun bind(item: PerfItem.Section) {
            titleText.text = item.title
        }
    }

    inner class ToggleVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText = view.findViewById<TextView>(R.id.titleText)
        private val switch = view.findViewById<MaterialSwitch>(R.id.switchWidget)

        fun bind(item: PerfItem.Toggle) {
            titleText.text = item.title
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = item.checked
            switch.isEnabled = item.enabled
            itemView.alpha = if (item.enabled) 1f else 0.5f
            switch.setOnCheckedChangeListener { _, checked ->
                onToggleClick(item.id, checked)
            }
        }
    }

    inner class SelectorVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText = view.findViewById<TextView>(R.id.titleText)
        private val valueText = view.findViewById<TextView>(R.id.valueText)

        fun bind(item: PerfItem.Selector) {
            titleText.text = item.title
            valueText.text = item.value
            itemView.alpha = if (item.enabled) 1f else 0.5f
            itemView.setOnClickListener {
                if (item.enabled) onCacheLevelClick()
            }
        }
    }
}

sealed class PerfItem {
    data class Section(val title: String) : PerfItem()
    data class Toggle(
        val id: String,
        val title: String,
        val checked: Boolean,
        val enabled: Boolean,
    ) : PerfItem()
    data class Selector(
        val id: String,
        val title: String,
        val value: String,
        val enabled: Boolean,
    ) : PerfItem()
}