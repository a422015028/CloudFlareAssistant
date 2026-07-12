package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.muort.upworker.databinding.DialogPurgeUrlBinding
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
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

data class ZoneSettingsState(
    val developmentMode: Boolean = false,
    val underAttack: Boolean = false,
    val isLoading: Boolean = false,
    val isPurging: Boolean = false,
)

@AndroidEntryPoint
class ZoneSettingsFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var settingsRepo: ZoneSettingsRepository

    private lateinit var adapter: SettingsAdapter
    private var state = ZoneSettingsState(isLoading = true)

    override val emptyText: String = "加载中…"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SettingsAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ZoneSettingsFragment.adapter
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
                val devMode = async {
                    runCatching {
                        when (val r = settingsRepo.getSetting(account, zoneId, "development_mode")) {
                            is Resource.Success -> r.data == "on"
                            else -> false
                        }
                    }.getOrDefault(false)
                }
                val securityLevel = async {
                    runCatching {
                        when (val r = settingsRepo.getSetting(account, zoneId, "security_level")) {
                            is Resource.Success -> r.data == "under_attack"
                            else -> false
                        }
                    }.getOrDefault(false)
                }
                state = state.copy(
                    developmentMode = devMode.await(),
                    underAttack = securityLevel.await(),
                    isLoading = false,
                )
                showList()
                adapter.notifyDataSetChanged()
            } finally {
                state = state.copy(isLoading = false)
            }
        }
    }

    private fun setDevelopmentMode(account: Account, on: Boolean) {
        state = state.copy(developmentMode = on)
        adapter.notifyDataSetChanged()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.setSetting(account, zoneId, "development_mode", if (on) "on" else "off")) {
                is Resource.Success -> {
                    state = state.copy(developmentMode = r.data == "on")
                    adapter.notifyDataSetChanged()
                }
                is Resource.Error -> {
                    toast("更新失败: ${r.message}")
                    load(account)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun setUnderAttack(account: Account, on: Boolean) {
        state = state.copy(underAttack = on)
        adapter.notifyDataSetChanged()
        viewLifecycleOwner.lifecycleScope.launch {
            val value = if (on) "under_attack" else "medium"
            when (val r = settingsRepo.setSetting(account, zoneId, "security_level", value)) {
                is Resource.Success -> {
                    state = state.copy(underAttack = r.data == "under_attack")
                    adapter.notifyDataSetChanged()
                }
                is Resource.Error -> {
                    toast("更新失败: ${r.message}")
                    load(account)
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun showPurgeConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清理缓存")
            .setMessage("确认清理该域名所有缓存？此操作不可撤销。")
            .setPositiveButton("清理") { _, _ -> purgeAllCache() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun purgeAllCache() {
        val account = account ?: return
        state = state.copy(isPurging = true)
        adapter.notifyDataSetChanged()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.purgeAllCache(account, zoneId)) {
                is Resource.Success -> {
                    toast("已清理全部缓存")
                }
                is Resource.Error -> {
                    toast("清理失败: ${r.message}")
                }
                is Resource.Loading -> {}
            }
            state = state.copy(isPurging = false)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showPurgeUrlDialog() {
        val binding = DialogPurgeUrlBinding.inflate(layoutInflater)
        binding.hintText.text = "输入 URL，每行一个（最多 ${MAX_PURGE_URLS} 个）\n例如:\nhttps://${zoneName.ifBlank { "example.com" }}/path"
        binding.countText.text = "0 / ${MAX_PURGE_URLS}"

        binding.urlInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val text = s?.toString() ?: ""
                val urls = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                binding.countText.text = "${urls.size} / ${MAX_PURGE_URLS}"
                binding.countText.setTextColor(
                    if (urls.size > MAX_PURGE_URLS) {
                        resources.getColor(android.R.color.holo_red_light, null)
                    } else {
                        resources.getColor(android.R.color.darker_gray, null)
                    }
                )
            }
        })

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("按 URL 清理缓存")
            .setView(binding.root)
            .setPositiveButton("清理") { _, _ ->
                val text = binding.urlInput.text.toString()
                val urls = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (urls.isEmpty()) {
                    toast("请输入 URL")
                    return@setPositiveButton
                }
                if (urls.size > MAX_PURGE_URLS) {
                    toast("最多支持 ${MAX_PURGE_URLS} 个 URL")
                    return@setPositiveButton
                }
                purgeUrls(urls)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun purgeUrls(urls: List<String>) {
        val account = account ?: return
        state = state.copy(isPurging = true)
        adapter.notifyDataSetChanged()
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = settingsRepo.purgeFiles(account, zoneId, urls.take(MAX_PURGE_URLS))) {
                is Resource.Success -> {
                    toast("已清理 ${urls.size} 条 URL")
                }
                is Resource.Error -> {
                    toast("清理失败: ${r.message}")
                }
                is Resource.Loading -> {}
            }
            state = state.copy(isPurging = false)
            adapter.notifyDataSetChanged()
        }
    }

    companion object {
        const val MAX_PURGE_URLS = 30
    }

    inner class SettingsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (position) {
            0 -> VIEW_TYPE_DEV_MODE
            1 -> VIEW_TYPE_UNDER_ATTACK
            2 -> VIEW_TYPE_PURGE_ALL
            3 -> VIEW_TYPE_PURGE_URL
            else -> throw IllegalArgumentException("Unknown position: $position")
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_DEV_MODE, VIEW_TYPE_UNDER_ATTACK -> {
                    val view = inflater.inflate(R.layout.item_settings_toggle, parent, false)
                    ToggleVH(view)
                }
                VIEW_TYPE_PURGE_ALL, VIEW_TYPE_PURGE_URL -> {
                    val view = inflater.inflate(R.layout.item_settings_button, parent, false)
                    ButtonVH(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (position) {
                0 -> (holder as ToggleVH).bind(
                    title = "开发模式",
                    subtitle = "绕过 Cloudflare 缓存，直接访问源站",
                    checked = state.developmentMode,
                    onClick = { account?.let { acc -> setDevelopmentMode(acc, it) } },
                )
                1 -> (holder as ToggleVH).bind(
                    title = "攻击模式",
                    subtitle = "启用后会对所有请求进行更严格的检查",
                    checked = state.underAttack,
                    onClick = { account?.let { acc -> setUnderAttack(acc, it) } },
                )
                2 -> (holder as ButtonVH).bind(
                    text = "清理缓存",
                    enabled = !state.isPurging,
                    onClick = { showPurgeConfirmDialog() },
                )
                3 -> (holder as ButtonVH).bind(
                    text = "按 URL 清理缓存",
                    enabled = !state.isPurging,
                    onClick = { showPurgeUrlDialog() },
                )
            }
        }

        override fun getItemCount(): Int = 4

        private val VIEW_TYPE_DEV_MODE = 0
        private val VIEW_TYPE_UNDER_ATTACK = 1
        private val VIEW_TYPE_PURGE_ALL = 2
        private val VIEW_TYPE_PURGE_URL = 3
    }

    inner class ToggleVH(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText = view.findViewById<TextView>(R.id.titleText)
        private val subtitleText = view.findViewById<TextView>(R.id.subtitleText)
        private val switch = view.findViewById<MaterialSwitch>(R.id.switchWidget)

        fun bind(title: String, subtitle: String, checked: Boolean, onClick: (Boolean) -> Unit) {
            titleText.text = title
            subtitleText.text = subtitle
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = checked
            switch.isEnabled = !state.isLoading
            switch.setOnCheckedChangeListener { _, isChecked ->
                onClick(isChecked)
            }
        }
    }

    inner class ButtonVH(view: View) : RecyclerView.ViewHolder(view) {
        private val button = view.findViewById<MaterialButton>(R.id.actionButton)

        fun bind(text: String, enabled: Boolean, onClick: () -> Unit) {
            button.text = text
            button.isEnabled = enabled
            button.setOnClickListener { onClick() }
        }
    }
}