package com.muort.upworker.feature.zerotrust.tunnels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.databinding.FragmentTunnelsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tunnels Fragment - Cloudflare Tunnel management
 */
@AndroidEntryPoint
class TunnelsFragment : Fragment() {

    private var _binding: FragmentTunnelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TunnelsViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var tunnelAdapter: TunnelAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTunnelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadTunnels()
    }

    private fun setupRecyclerView() {
        tunnelAdapter = TunnelAdapter(
            onDeleteClick = { tunnel ->
                confirmDeleteTunnel(tunnel.id, tunnel.name)
            },
            onItemClick = { tunnel ->
                showTunnelDetailDialog(tunnel)
            },
            onConfigClick = { tunnel ->
                showTunnelConfigDialog(tunnel)
            }
        )
        
        binding.tunnelsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = tunnelAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddTunnel.setOnClickListener {
            showCreateTunnelDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tunnels.collect { tunnels ->
                        tunnelAdapter.submitList(tunnels)
                        binding.emptyText.visibility = 
                            if (tunnels.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadTunnels() {
        accountViewModel.defaultAccount.value?.let { account ->
            viewModel.loadTunnels(account)
        }
    }

    private fun showCreateTunnelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_tunnel, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val configSrcSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.configSrcSpinner)
        
        // Config source options
        val configSources = listOf(
            "local" to "本地 (cloudflared 配置)",
            "cloudflare" to "远程 (Dashboard 配置)"
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            configSources.map { it.second }
        )
        configSrcSpinner.setAdapter(adapter)
        configSrcSpinner.setText(configSources[0].second, false)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建隧道")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "隧道名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedIndex = configSources.indexOfFirst { 
                    it.second == configSrcSpinner.text.toString() 
                }.coerceAtLeast(0)
                val configSrc = configSources[selectedIndex].first
                
                val request = TunnelCreateRequest(
                    name = name,
                    configSrc = configSrc
                )
                
                viewModel.createTunnel(account, request)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTunnelDetailDialog(tunnel: CloudflareTunnel) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tunnel_detail, null)
        
        // Tunnel Name
        dialogView.findViewById<TextView>(R.id.tunnelNameText).text = tunnel.name
        
        // Status Chip
        val status = tunnel.status ?: "unknown"
        val statusChip = dialogView.findViewById<Chip>(R.id.statusChip)
        statusChip.text = getStatusLabel(status)
        statusChip.setChipBackgroundColorResource(getStatusColor(status))
        
        // Tunnel Type Chip
        val tunnelType = tunnel.tunType ?: "cfd_tunnel"
        dialogView.findViewById<Chip>(R.id.tunnelTypeChip).text = getTunnelTypeLabel(tunnelType)
        
        // Config Source Chip
        val configSourceChip = dialogView.findViewById<Chip>(R.id.configSourceChip)
        configSourceChip.text = if (tunnel.remoteConfig == true) "远程配置" else "本地配置"
        
        // Tunnel ID
        dialogView.findViewById<TextView>(R.id.tunnelIdText).text = tunnel.id
        
        // Connection Count
        val connectionCount = tunnel.connections?.size ?: 0
        dialogView.findViewById<TextView>(R.id.connectionCountText).text = 
            "活跃连接: $connectionCount"
        
        // Connections RecyclerView
        val connectionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.connectionsRecyclerView)
        val noConnectionsText = dialogView.findViewById<TextView>(R.id.noConnectionsText)
        
        val connections = tunnel.connections ?: emptyList()
        if (connections.isNotEmpty()) {
            val connectionAdapter = TunnelConnectionAdapter()
            connectionsRecyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = connectionAdapter
            }
            connectionAdapter.submitList(connections)
            connectionsRecyclerView.visibility = View.VISIBLE
            noConnectionsText.visibility = View.GONE
        } else {
            connectionsRecyclerView.visibility = View.GONE
            noConnectionsText.visibility = View.VISIBLE
        }
        
        // Time Info
        dialogView.findViewById<TextView>(R.id.createdAtText).text = 
            "创建时间: ${formatDateTime(tunnel.createdAt)}"
        dialogView.findViewById<TextView>(R.id.activeAtText).text = 
            "最后活跃: ${formatDateTime(tunnel.connsActiveAt)}"
        
        // Inactive time
        val inactiveAtText = dialogView.findViewById<TextView>(R.id.inactiveAtText)
        if (tunnel.connsInactiveAt != null) {
            inactiveAtText.text = "不活跃时间: ${formatDateTime(tunnel.connsInactiveAt)}"
            inactiveAtText.visibility = View.VISIBLE
        } else {
            inactiveAtText.visibility = View.GONE
        }
        
        // Deleted time
        val deletedAtText = dialogView.findViewById<TextView>(R.id.deletedAtText)
        if (tunnel.deletedAt != null) {
            deletedAtText.text = "删除时间: ${formatDateTime(tunnel.deletedAt)}"
            deletedAtText.visibility = View.VISIBLE
        } else {
            deletedAtText.visibility = View.GONE
        }
        
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("隧道详情")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
        
        // Add config button for remote config tunnels
        if (tunnel.remoteConfig == true && tunnel.deletedAt == null) {
            builder.setNeutralButton("配置") { _, _ ->
                showTunnelConfigDialog(tunnel)
            }
        }
        
        // Add delete button if not deleted
        if (tunnel.deletedAt == null) {
            builder.setPositiveButton("删除") { _, _ ->
                confirmDeleteTunnel(tunnel.id, tunnel.name)
            }
        }
        
        builder.show()
    }

    private fun showTunnelConfigDialog(tunnel: CloudflareTunnel) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        // Load current configuration
        viewModel.loadTunnelConfiguration(account, tunnel.id)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_tunnel_config, null)
        val ingressContainer = dialogView.findViewById<LinearLayout>(R.id.ingressRulesContainer)
        val addRuleButton = dialogView.findViewById<View>(R.id.addIngressRuleButton)
        val warpRoutingSwitch = dialogView.findViewById<SwitchMaterial>(R.id.warpRoutingSwitch)
        
        // Mutable list to track ingress rules
        val ingressRules = mutableListOf<IngressRuleViewHolder>()
        
        // Function to add a new ingress rule view
        fun addIngressRuleView(hostname: String? = null, path: String? = null, service: String = "") {
            val ruleView = layoutInflater.inflate(R.layout.item_ingress_rule_edit, ingressContainer, false)
            
            val hostnameInput = ruleView.findViewById<TextInputEditText>(R.id.hostnameInput)
            val pathInput = ruleView.findViewById<TextInputEditText>(R.id.pathInput)
            val serviceInput = ruleView.findViewById<TextInputEditText>(R.id.serviceInput)
            val removeButton = ruleView.findViewById<View>(R.id.removeRuleButton)
            
            hostnameInput.setText(hostname ?: "")
            pathInput.setText(path ?: "")
            serviceInput.setText(service)
            
            val holder = IngressRuleViewHolder(ruleView, hostnameInput, pathInput, serviceInput)
            ingressRules.add(holder)
            
            removeButton.setOnClickListener {
                ingressContainer.removeView(ruleView)
                ingressRules.remove(holder)
            }
            
            ingressContainer.addView(ruleView)
        }
        
        // Observe configuration changes
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.tunnelConfiguration.collect { config ->
                config?.config?.let { tunnelConfig ->
                    // Clear existing rules
                    ingressContainer.removeAllViews()
                    ingressRules.clear()
                    
                    // Add existing ingress rules
                    tunnelConfig.ingress?.forEach { rule ->
                        addIngressRuleView(rule.hostname, rule.path, rule.service)
                    }
                    
                    // Set WARP routing
                    warpRoutingSwitch.isChecked = tunnelConfig.warpRouting?.enabled == true
                }
            }
        }
        
        // Add rule button
        addRuleButton.setOnClickListener {
            addIngressRuleView()
        }
        
        // Add a catch-all rule if no rules exist
        if (ingressRules.isEmpty()) {
            addIngressRuleView(service = "http_status:404")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("隧道配置: ${tunnel.name}")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                // Build ingress rules
                val rules = ingressRules.mapNotNull { holder ->
                    val service = holder.serviceInput.text?.toString()
                    if (service.isNullOrBlank()) return@mapNotNull null
                    
                    IngressRule(
                        hostname = holder.hostnameInput.text?.toString()?.takeIf { it.isNotBlank() },
                        path = holder.pathInput.text?.toString()?.takeIf { it.isNotBlank() },
                        service = service
                    )
                }
                
                // Ensure there's a catch-all rule at the end
                val finalRules = if (rules.none { it.hostname == null }) {
                    rules + IngressRule(service = "http_status:404")
                } else {
                    rules
                }
                
                val tunnelConfig = TunnelConfig(
                    ingress = finalRules,
                    warpRouting = WarpRouting(enabled = warpRoutingSwitch.isChecked)
                )
                
                val request = TunnelConfigurationRequest(config = tunnelConfig)
                viewModel.updateTunnelConfiguration(account, tunnel.id, request)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteTunnel(tunnelId: String, tunnelName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除隧道")
            .setMessage("确定要删除隧道 \"$tunnelName\" 吗？\n\n删除后，所有通过此隧道的连接将中断。")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.deleteTunnel(account, tunnelId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getStatusLabel(status: String): String {
        return when (status.lowercase()) {
            "active" -> "活跃"
            "inactive" -> "未活跃"
            "degraded" -> "降级"
            "down" -> "离线"
            else -> status
        }
    }

    private fun getStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "active" -> android.R.color.holo_green_light
            "inactive" -> android.R.color.darker_gray
            "degraded" -> android.R.color.holo_orange_light
            "down" -> android.R.color.holo_red_light
            else -> android.R.color.darker_gray
        }
    }

    private fun getTunnelTypeLabel(type: String): String {
        return when (type) {
            "cfd_tunnel" -> "cloudflared"
            "warp_connector" -> "WARP Connector"
            else -> type
        }
    }

    private fun formatDateTime(dateString: String?): String {
        if (dateString == null) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString.substringBefore(".").substringBefore("Z"))
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateString.substringBefore("T")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Helper class to hold ingress rule view references
    private data class IngressRuleViewHolder(
        val view: View,
        val hostnameInput: TextInputEditText,
        val pathInput: TextInputEditText,
        val serviceInput: TextInputEditText
    )
}
