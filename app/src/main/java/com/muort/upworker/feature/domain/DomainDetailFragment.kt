package com.muort.upworker.feature.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.muort.upworker.R
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentDomainDetailBinding
import com.muort.upworker.databinding.ItemZoneToolBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** 域名工具项：标签 + 图标 + 目标 actionId。 */
private data class ZoneToolItem(
    val label: String,
    val iconRes: Int,
    val actionId: Int,
)

@AndroidEntryPoint
class DomainDetailFragment : Fragment() {

    private var _binding: FragmentDomainDetailBinding? = null
    private val binding get() = _binding!!

    private val domainDetailViewModel: DomainDetailViewModel by viewModels()

    private val zoneIdArg: String by lazy {
        arguments?.getString("zoneId") ?: ""
    }
    private val zoneNameArg: String by lazy {
        arguments?.getString("zoneName") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDomainDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        domainDetailViewModel.bind(zoneIdArg)
        binding.zoneIdText.text = "Zone ID · $zoneIdArg"
        setupTools()
        observeZone()
    }

    private fun setupTools() {
        val tools = listOf(
            ZoneToolItem("DNS记录", android.R.drawable.ic_menu_edit, R.id.action_domainDetail_to_dns),
            ZoneToolItem("流量分析", android.R.drawable.ic_menu_sort_by_size, R.id.action_domainDetail_to_analytics),
            ZoneToolItem("WAF规则", android.R.drawable.ic_menu_search, R.id.action_domainDetail_to_waf),
            ZoneToolItem("缓存规则", android.R.drawable.ic_menu_save, R.id.action_domainDetail_to_cache),
            ZoneToolItem("速率限制", android.R.drawable.ic_menu_recent_history, R.id.action_domainDetail_to_rateLimit),
            ZoneToolItem("电子邮件路由", android.R.drawable.ic_menu_send, R.id.action_domainDetail_to_emailRouting),
            ZoneToolItem("负载均衡", android.R.drawable.ic_menu_share, R.id.action_domainDetail_to_loadBalancer),
            ZoneToolItem("SSL/TLS", android.R.drawable.ic_lock_lock, R.id.action_domainDetail_to_ssl),
            ZoneToolItem("SSL证书", android.R.drawable.ic_menu_manage, R.id.action_domainDetail_to_sslCerts),
            ZoneToolItem("TransformRules", android.R.drawable.ic_menu_rotate, R.id.action_domainDetail_to_transform),
            ZoneToolItem("IP访问规则", android.R.drawable.ic_menu_view, R.id.action_domainDetail_to_accessRules),
            ZoneToolItem("性能已缓存", android.R.drawable.ic_menu_compass, R.id.action_domainDetail_to_performance),
            ZoneToolItem("Snippets", android.R.drawable.ic_menu_agenda, R.id.action_domainDetail_to_snippets),
            ZoneToolItem("设置", android.R.drawable.ic_menu_preferences, R.id.action_domainDetail_to_zoneSettings),
        )
        tools.forEach { tool -> binding.toolsContainer.addView(buildToolRow(tool)) }
    }

    private fun buildToolRow(tool: ZoneToolItem): View {
        val row = ItemZoneToolBinding.inflate(layoutInflater, binding.toolsContainer, false)
        row.toolLabel.text = tool.label
        row.toolIcon.setImageResource(tool.iconRes)
        row.root.setOnClickListener {
            val args = Bundle().apply {
                putString("zoneId", zoneIdArg)
                putString("zoneName", zoneNameArg)
            }
            findNavController().navigate(tool.actionId, args)
        }
        return row.root
    }

    private fun observeZone() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                domainDetailViewModel.zone.collect { zone ->
                    if (zone != null) {
                        showNameServers(zone.nameServerList())
                    }
                }
            }
        }
    }

    private fun showNameServers(servers: List<String>) {
        if (servers.isEmpty()) {
            binding.nameServersSection.visibility = View.GONE
            return
        }
        binding.nameServersSection.visibility = View.VISIBLE
        binding.nameServersContainer.removeAllViews()
        servers.forEach { server ->
            val tv = TextView(requireContext()).apply {
                text = server
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, 8, 0, 8)
            }
            binding.nameServersContainer.addView(tv)
        }
        binding.nameServersCard.setOnClickListener {
            copyToClipboard(servers.joinToString("\n"))
            requireContext().showToast("已复制名称服务器")
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("name_servers", text))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
