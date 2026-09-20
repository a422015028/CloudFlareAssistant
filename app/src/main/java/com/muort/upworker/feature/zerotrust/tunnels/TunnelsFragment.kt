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
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.databinding.FragmentTunnelsBinding
import com.muort.upworker.databinding.ItemTunnelConnectionBinding
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
                showTunnelConfigDialog(tunnel, readOnly = false)
            },
            onRunCommandClick = { tunnel ->
                showRunCommandDialog(tunnel)
            },
            onViewRoutesClick = { tunnel ->
                showTunnelConfigDialog(tunnel, readOnly = true)
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
                        android.widget.Toast.makeText(requireContext(), message.asString(requireContext()), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        android.widget.Toast.makeText(requireContext(), error.asString(requireContext()), android.widget.Toast.LENGTH_LONG).show()
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
    
    override fun onResume() {
        super.onResume()
        loadTunnels()
    }

    private fun showCreateTunnelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_tunnel, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.nameInput)
        val configSrcSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.configSrcSpinner)
        
        // Config source options
        val configSources = listOf(
            "local" to getString(R.string.zt_tunnel_config_src_local),
            "cloudflare" to getString(R.string.zt_tunnel_config_src_cloudflare)
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            configSources.map { it.second }
        )
        configSrcSpinner.setAdapter(adapter)
        configSrcSpinner.setText(configSources[0].second, false)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tunnel_create)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_tunnel_name_empty), android.widget.Toast.LENGTH_SHORT).show()
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
            .setNegativeButton(R.string.cancel, null)
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
        configSourceChip.text = if (tunnel.remoteConfig == true) getString(R.string.zt_tunnel_config_remote) else getString(R.string.zt_tunnel_config_local)
        
        // Tunnel ID
        dialogView.findViewById<TextView>(R.id.tunnelIdText).text = tunnel.id
        
        // Connection Count
        val connectionCount = tunnel.connections?.size ?: 0
        dialogView.findViewById<TextView>(R.id.connectionCountText).text =
            resources.getQuantityString(R.plurals.zt_tunnel_active_conns, connectionCount, connectionCount)
        
        // Connections
        val connectionsContainer = dialogView.findViewById<LinearLayout>(R.id.connectionsContainer)
        val noConnectionsText = dialogView.findViewById<TextView>(R.id.noConnectionsText)

        val connections = tunnel.connections ?: emptyList()
        if (connections.isNotEmpty()) {
            connectionsContainer.removeAllViews()
            for (connection in connections) {
                val itemBinding = ItemTunnelConnectionBinding.inflate(layoutInflater, connectionsContainer, false)
                bindConnectionItem(itemBinding, connection)
                connectionsContainer.addView(itemBinding.root)
            }
            connectionsContainer.visibility = View.VISIBLE
            noConnectionsText.visibility = View.GONE
        } else {
            connectionsContainer.visibility = View.GONE
            noConnectionsText.visibility = View.VISIBLE
        }
        
        // Time Info
        dialogView.findViewById<TextView>(R.id.createdAtText).text =
            getString(R.string.token_detail_created_time, formatDateTime(tunnel.createdAt))
        dialogView.findViewById<TextView>(R.id.activeAtText).text =
            getString(R.string.zt_device_last_seen_label, formatDateTime(tunnel.connsActiveAt))
        
        // Inactive time
        val inactiveAtText = dialogView.findViewById<TextView>(R.id.inactiveAtText)
        if (tunnel.connsInactiveAt != null) {
            inactiveAtText.text = getString(R.string.zt_tunnel_inactive_at, formatDateTime(tunnel.connsInactiveAt))
            inactiveAtText.visibility = View.VISIBLE
        } else {
            inactiveAtText.visibility = View.GONE
        }
        
        // Deleted time
        val deletedAtText = dialogView.findViewById<TextView>(R.id.deletedAtText)
        if (tunnel.deletedAt != null) {
            deletedAtText.text = getString(R.string.zt_device_deleted_label, formatDateTime(tunnel.deletedAt))
            deletedAtText.visibility = View.VISIBLE
        } else {
            deletedAtText.visibility = View.GONE
        }
        
        // Token section
        val tokenText = dialogView.findViewById<TextView>(R.id.tunnelTokenText)
        val hideTokenButton = dialogView.findViewById<android.widget.Button>(R.id.hideTokenButton)
        val copyCommandButton = dialogView.findViewById<android.widget.Button>(R.id.copyCommandButton)
        val refreshTokenButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.refreshTokenButton)
        val tokenModeChipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.tokenModeChipGroup)
        
        // Hide mode switcher for remote config tunnels - only show token mode
        if (tunnel.remoteConfig == true) {
            tokenModeChipGroup.visibility = View.GONE
        }
        
        if (tunnel.deletedAt == null) {
            val account = accountViewModel.defaultAccount.value
            account?.let { acc ->
                viewModel.getTunnelToken(acc, tunnel.id) { token ->
                    if (token != null) {
                        var currentToken: String = token
                        val isLocalConfig = tunnel.remoteConfig == false
                        var isTokenHidden = true
                        
                        // Display mode: 0=Token, 1=JSON, 2=YAML, 3=RunCommand
                        var currentMode = 0
                        
                        fun getDisplayContent(tokenValue: String, mode: Int): String {
                            return when (mode) {
                                1 -> decodeTunnelTokenToJson(tokenValue) ?: tokenValue
                                2 -> {
                                    val tunnelId = tunnel.id
                                    "tunnel: $tunnelId\ncredentials-file: /data/local/tmp/cloudflared/credentials.json\n\ningress:\n  - hostname: gitlab.widgetcorp.tech\n    service: http://localhost:80\n  - hostname: gitlab-ssh.widgetcorp.tech\n    service: ssh://localhost:22\n  - service: http_status:404"
                                }
                                3 -> "cloudflared tunnel --config /data/local/tmp/cloudflared/config.yml run ${tunnel.name}"
                                else -> "cloudflared service install $tokenValue"
                            }
                        }
                        
                        fun getMaskedContent(mode: Int): String {
                            return when (mode) {
                                1 -> "{\"AccountTag\":\"●●●●●●●●●●●●\",\"TunnelSecret\":\"●●●●●●●●●●●●●●●●●●●●●●●●●●●●\",\"TunnelID\":\"●●●●●●●●-●●●●-●●●●-●●●●-●●●●●●●●●●●●\",\"Endpoint\":\"\"}"
                                2 -> "tunnel: ●●●●●●●●-●●●●-●●●●-●●●●-●●●●●●●●●●●●\ncredentials-file: /data/local/tmp/cloudflared/credentials.json\n\ningress:\n  - hostname: ●●●●●●●●●●●●●●●●●●\n    service: http://localhost:80\n  - hostname: ●●●●●●●●●●●●●●●●●●\n    service: ssh://localhost:22\n  - service: http_status:404"
                                3 -> "cloudflared tunnel --config /data/local/tmp/cloudflared/config.yml run ●●●●●●●●●●●●"
                                else -> "cloudflared service install ●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●"
                            }
                        }
                        
                        fun getCopyButtonText(mode: Int): Int {
                            return when (mode) {
                                1 -> R.string.zt_tunnel_copy_json
                                else -> R.string.tunnel_copy_command
                            }
                        }
                        
                        fun getClipLabel(mode: Int): String {
                            return when (mode) {
                                1 -> "Tunnel Credentials JSON"
                                2 -> "Cloudflared YAML Config"
                                3 -> "Cloudflared Tunnel Run Command"
                                else -> "Cloudflared Service Command"
                            }
                        }
                        
                        fun updateDisplay() {
                            tokenText.text = if (isTokenHidden) getMaskedContent(currentMode) else getDisplayContent(currentToken, currentMode)
                            hideTokenButton.setText(if (isTokenHidden) R.string.zt_tunnel_show_token else R.string.tunnel_hide_token)
                            copyCommandButton.setText(getCopyButtonText(currentMode))
                        }
                        
                        updateDisplay()
                        
                        tokenModeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
                            currentMode = when (group.checkedChipId) {
                                R.id.chipModeJson -> 1
                                R.id.chipModeYaml -> 2
                                R.id.chipModeRunCmd -> 3
                                else -> 0
                            }
                            updateDisplay()
                        }
                        
                        hideTokenButton.setOnClickListener {
                            isTokenHidden = !isTokenHidden
                            updateDisplay()
                        }
                        
                        copyCommandButton.setOnClickListener {
                            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(getClipLabel(currentMode), getDisplayContent(currentToken, currentMode))
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(requireContext(), getString(R.string.zt_tunnel_command_copied), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        
                        refreshTokenButton.setOnClickListener {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.zt_tunnel_refresh_token_title)
                                .setMessage(R.string.zt_tunnel_refresh_token_confirm)
                                .setPositiveButton(R.string.zt_tunnel_refresh_token) { _, _ ->
                                    refreshTokenButton.isEnabled = false
                                    viewModel.refreshTunnelToken(acc, tunnel.id) { newToken ->
                                        refreshTokenButton.isEnabled = true
                                        if (newToken != null) {
                                            currentToken = newToken
                                            isTokenHidden = true
                                            updateDisplay()
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                        }
                    } else {
                        tokenText.text = getString(R.string.zt_tunnel_token_fetch_failed)
                        hideTokenButton.visibility = View.GONE
                        copyCommandButton.visibility = View.GONE
                        refreshTokenButton.visibility = View.GONE
                    }
                }
            } ?: run {
                tokenText.text = getString(R.string.msg_please_select_account_first)
                hideTokenButton.visibility = View.GONE
                copyCommandButton.visibility = View.GONE
                refreshTokenButton.visibility = View.GONE
            }
        } else {
            tokenText.visibility = View.GONE
            hideTokenButton.visibility = View.GONE
            copyCommandButton.visibility = View.GONE
            refreshTokenButton.visibility = View.GONE
        }
        
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_tunnel_detail_title)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_close, null)
        
        // Add config button for remote config tunnels
        if (tunnel.remoteConfig == true && tunnel.deletedAt == null) {
            builder.setPositiveButton(R.string.zt_tunnel_configure_button) { _, _ ->
                showTunnelConfigDialog(tunnel, readOnly = false)
            }
        }

        // Add view routes button for local config tunnels
        if (tunnel.remoteConfig == false && tunnel.deletedAt == null) {
            builder.setPositiveButton(R.string.zt_tunnel_view_routes_button) { _, _ ->
                showTunnelConfigDialog(tunnel, readOnly = true)
            }
        }
        
        // Add delete button if not deleted
        if (tunnel.deletedAt == null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                confirmDeleteTunnel(tunnel.id, tunnel.name)
            }
        }
        
        builder.show()
    }

    private fun showTunnelConfigDialog(tunnel: CloudflareTunnel, readOnly: Boolean = false) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        // Load current configuration
        viewModel.loadTunnelConfiguration(account, tunnel.id)
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_tunnel_config, null)
        val ingressContainer = dialogView.findViewById<LinearLayout>(R.id.ingressRulesContainer)
        val addRuleButton = dialogView.findViewById<View>(R.id.addIngressRuleButton)
        val warpRoutingSwitch = dialogView.findViewById<SwitchMaterial>(R.id.warpRoutingSwitch)

        // Read-only mode notice
        val readonlyNotice = dialogView.findViewById<TextView>(R.id.readonlyNoticeText)
        if (readOnly) {
            readonlyNotice?.visibility = View.VISIBLE
        } else {
            readonlyNotice?.visibility = View.GONE
        }
        
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

            // Apply read-only mode
            if (readOnly) {
                hostnameInput.isFocusable = false
                hostnameInput.isCursorVisible = false
                hostnameInput.keyListener = null
                pathInput.isFocusable = false
                pathInput.isCursorVisible = false
                pathInput.keyListener = null
                serviceInput.isFocusable = false
                serviceInput.isCursorVisible = false
                serviceInput.keyListener = null
                removeButton.visibility = View.GONE
            }
            
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
                    // Disable switch in read-only mode
                    if (readOnly) {
                        warpRoutingSwitch.isEnabled = false
                    }
                }
            }
        }
        
        // Add rule button - hidden in read-only mode
        if (readOnly) {
            addRuleButton.visibility = View.GONE
        } else {
            addRuleButton.setOnClickListener {
                addIngressRuleView()
            }
        }
        
        // Add a catch-all rule if no rules exist (only for editable mode)
        if (!readOnly && ingressRules.isEmpty()) {
            addIngressRuleView(service = "http_status:404")
        }
        
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (readOnly) getString(R.string.zt_tunnel_view_routes_button)
                else getString(R.string.zt_tunnel_config_title, tunnel.name)
            )
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)

        // Save button only for editable mode
        if (!readOnly) {
            builder.setPositiveButton(R.string.save) { _, _ ->
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
        }
        
        builder.show()
    }

    private fun confirmDeleteTunnel(tunnelId: String, tunnelName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.tunnel_delete)
            .setMessage(getString(R.string.zt_tunnel_delete_confirm, tunnelName))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.deleteTunnel(account, tunnelId)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showRunCommandDialog(tunnel: CloudflareTunnel) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        viewModel.getTunnelToken(account, tunnel.id) { token ->
            if (token == null) return@getTunnelToken
            
            val dialogView = layoutInflater.inflate(R.layout.dialog_tunnel_run_command, null)
            
            val tokenTextView = dialogView.findViewById<TextView>(R.id.tokenTextView)
            val copyCommandButton = dialogView.findViewById<android.widget.Button>(R.id.copyCommandButton)
            val hideTokenButton = dialogView.findViewById<android.widget.Button>(R.id.hideTokenButton)
            val refreshTokenButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.refreshTokenButton)
            val tokenModeChipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.tokenModeChipGroup)
            
            // Hide mode switcher for remote config tunnels - only show token mode
            if (tunnel.remoteConfig == true) {
                tokenModeChipGroup.visibility = View.GONE
            }
            
            var currentToken: String = token
            val isLocalConfig = tunnel.remoteConfig == false
            var isTokenHidden = true
            
            // Display mode: 0=Token, 1=JSON, 2=YAML, 3=RunCommand
            var currentMode = 0
            
            fun getDisplayContent(tokenValue: String, mode: Int): String {
                return when (mode) {
                    1 -> decodeTunnelTokenToJson(tokenValue) ?: tokenValue
                    2 -> {
                        val tunnelId = tunnel.id
                        "tunnel: $tunnelId\ncredentials-file: /data/local/tmp/cloudflared/credentials.json\n\ningress:\n  - hostname: gitlab.widgetcorp.tech\n    service: http://localhost:80\n  - hostname: gitlab-ssh.widgetcorp.tech\n    service: ssh://localhost:22\n  - service: http_status:404"
                    }
                    3 -> "cloudflared tunnel --config /data/local/tmp/cloudflared/config.yml run ${tunnel.name}"
                    else -> "cloudflared tunnel run --token $tokenValue"
                }
            }
            
            fun getMaskedContent(mode: Int): String {
                return when (mode) {
                    1 -> "{\"AccountTag\":\"●●●●●●●●●●●●\",\"TunnelSecret\":\"●●●●●●●●●●●●●●●●●●●●●●●●●●●●\",\"TunnelID\":\"●●●●●●●●-●●●●-●●●●-●●●●-●●●●●●●●●●●●\",\"Endpoint\":\"\"}"
                    2 -> "tunnel: ●●●●●●●●-●●●●-●●●●-●●●●-●●●●●●●●●●●●\ncredentials-file: /data/local/tmp/cloudflared/credentials.json\n\ningress:\n  - hostname: ●●●●●●●●●●●●●●●●●●\n    service: http://localhost:80\n  - hostname: ●●●●●●●●●●●●●●●●●●\n    service: ssh://localhost:22\n  - service: http_status:404"
                    3 -> "cloudflared tunnel --config /data/local/tmp/cloudflared/config.yml run ●●●●●●●●●●●●"
                    else -> "cloudflared tunnel run ●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●●"
                }
            }
            
            fun getCopyButtonText(mode: Int): Int {
                return when (mode) {
                    1 -> R.string.zt_tunnel_copy_json
                    else -> R.string.tunnel_copy_command
                }
            }
            
            fun getClipLabel(mode: Int): String {
                return when (mode) {
                    1 -> "Tunnel Credentials JSON"
                    2 -> "Cloudflared YAML Config"
                    3 -> "Cloudflared Tunnel Run Command"
                    else -> "Cloudflared Tunnel Command"
                }
            }
            
            fun updateDisplay() {
                tokenTextView.text = if (isTokenHidden) getMaskedContent(currentMode) else getDisplayContent(currentToken, currentMode)
                hideTokenButton.setText(if (isTokenHidden) R.string.zt_tunnel_show_token else R.string.tunnel_hide_token)
                copyCommandButton.setText(getCopyButtonText(currentMode))
            }
            
            updateDisplay()
            
            tokenModeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
                currentMode = when (group.checkedChipId) {
                    R.id.chipModeJson -> 1
                    R.id.chipModeYaml -> 2
                    R.id.chipModeRunCmd -> 3
                    else -> 0
                }
                updateDisplay()
            }
            
            hideTokenButton.setOnClickListener {
                isTokenHidden = !isTokenHidden
                updateDisplay()
            }
            
            copyCommandButton.setText(getCopyButtonText(currentMode))
            copyCommandButton.setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText(getClipLabel(currentMode), getDisplayContent(currentToken, currentMode))
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(requireContext(), getString(R.string.zt_tunnel_command_copied), android.widget.Toast.LENGTH_SHORT).show()
            }
            
            refreshTokenButton.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.zt_tunnel_refresh_token_title)
                    .setMessage(R.string.zt_tunnel_refresh_token_confirm)
                    .setPositiveButton(R.string.zt_tunnel_refresh_token) { _, _ ->
                        refreshTokenButton.isEnabled = false
                        viewModel.refreshTunnelToken(account, tunnel.id) { newToken ->
                            refreshTokenButton.isEnabled = true
                            if (newToken != null) {
                                currentToken = newToken
                                isTokenHidden = true
                                updateDisplay()
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.tunnel_run_command)
                .setView(dialogView)
                .setPositiveButton(R.string.dialog_close, null)
                .show()
        }
    }

    private fun getStatusLabel(status: String): String {
        return when (status.lowercase()) {
            "active" -> getString(R.string.r2_status_active)
            "inactive" -> getString(R.string.zt_tunnel_status_inactive)
            "degraded" -> getString(R.string.zt_tunnel_status_degraded)
            "down" -> getString(R.string.zt_tunnel_status_down)
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

    private fun bindConnectionItem(binding: ItemTunnelConnectionBinding, connection: TunnelConnection) {
        val ctx = requireContext()
        binding.coloNameText.text = connection.coloName ?: "Unknown Colo"

        val isPendingReconnect = connection.isPendingReconnect == true
        binding.connectionStatusChip.text = if (isPendingReconnect) getString(R.string.zt_tunnel_connection_reconnecting) else getString(R.string.status_connected)
        binding.connectionStatusChip.setChipBackgroundColorResource(
            if (isPendingReconnect) android.R.color.holo_orange_light else android.R.color.holo_green_light
        )

        val clientVersion = connection.clientVersion ?: "Unknown"
        binding.clientInfoText.text = getString(R.string.zt_tunnel_client_version, clientVersion)

        val originIp = connection.originIp
        if (!originIp.isNullOrBlank()) {
            binding.originIpText.text = getString(R.string.zt_tunnel_origin_ip, originIp)
        } else {
            binding.originIpText.text = getString(R.string.zt_tunnel_origin_ip, ctx.getString(R.string.status_unknown))
        }

        binding.openedAtText.text = getString(R.string.zt_tunnel_connection_time, formatConnectionDateTime(connection.openedAt))
    }

    private fun formatConnectionDateTime(dateString: String?): String {
        if (dateString == null) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
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

    /**
     * Decode a Cloudflare tunnel token (base64-encoded JSON) into credentials JSON format.
     * Token format: base64({"a":"account_id","t":"tunnel_id","s":"tunnel_secret"})
     * Output format: {"AccountTag":"...","TunnelSecret":"...","TunnelID":"...","Endpoint":""}
     */
    private fun decodeTunnelTokenToJson(token: String): String? {
        return try {
            val cleanToken = token.trim()
            val padded = cleanToken + "=".repeat((4 - cleanToken.length % 4) % 4)
            val decoded = android.util.Base64.decode(
                padded.replace('-', '+').replace('_', '/'),
                android.util.Base64.DEFAULT
            )
            val jsonStr = String(decoded, Charsets.UTF_8)
            val jsonObj = org.json.JSONObject(jsonStr)
            val accountTag = jsonObj.optString("a", "")
            val tunnelId = jsonObj.optString("t", "")
            val tunnelSecret = jsonObj.optString("s", "")

            val result = org.json.JSONObject()
            result.put("AccountTag", accountTag)
            result.put("TunnelSecret", tunnelSecret)
            result.put("TunnelID", tunnelId)
            result.put("Endpoint", "")
            result.toString()
        } catch (e: Exception) {
            null
        }
    }
}
