package com.muort.upworker.feature.zerotrust.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.DeviceSettingsPolicy
import com.muort.upworker.core.model.DeviceSettingsPolicyRequest
import com.muort.upworker.core.model.ServiceModeV2
import com.muort.upworker.core.model.SplitTunnel
import com.muort.upworker.databinding.FragmentDevicePoliciesBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DevicePoliciesFragment : Fragment() {

    private var _binding: FragmentDevicePoliciesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var policyAdapter: DevicePolicyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicePoliciesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        policyAdapter = DevicePolicyAdapter(
            onEditClick = { policy ->
                showPolicyDialog(policy)
            },
            onDeleteClick = { policy ->
                policy.policyId?.let { id ->
                    confirmDeletePolicy(id, policy.name ?: "配置文件")
                }
            },
            onEnabledChange = { policy, enabled ->
                updatePolicyEnabled(policy, enabled)
            }
        )
        
        binding.policiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = policyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddPolicy.setOnClickListener {
            showPolicyDialog(null)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.policies.collect { policies ->
                        policyAdapter.submitList(policies)
                        binding.emptyText.visibility = 
                            if (policies.isEmpty()) View.VISIBLE else View.GONE
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

    private fun loadPolicies() {
        accountViewModel.defaultAccount.value?.let { account ->
            viewModel.loadPolicies(account)
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadPolicies()
    }

    private fun showPolicyDialog(existingPolicy: DeviceSettingsPolicy?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_device_policy, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.policyNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.policyDescriptionInput)
        val matchInput = dialogView.findViewById<TextInputEditText>(R.id.policyMatchInput)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.policyPrecedenceInput)
        val enabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.policyEnabledSwitch)
        
        val autoConnectSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.autoConnectSpinner)
        val allowModeSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowModeSwitch)
        val switchLockedSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switchLockedSwitch)
        val excludeOfficeIpsSwitch = dialogView.findViewById<SwitchMaterial>(R.id.excludeOfficeIpsSwitch)
        val allowedToLeaveSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowedToLeaveSwitch)
        val supportUrlInput = dialogView.findViewById<TextInputEditText>(R.id.supportUrlInput)
        val captivePortalSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.captivePortalSpinner)
        val gatewayUniqueIdInput = dialogView.findViewById<TextInputEditText>(R.id.gatewayUniqueIdInput)
        
        val protocolSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.protocolSpinner)
        val allowUpdatesSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowUpdatesSwitch)
        val serviceModeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.serviceModeSpinner)
        val ipDnsRegistrationSwitch = dialogView.findViewById<SwitchMaterial>(R.id.ipDnsRegistrationSwitch)
        val sccmVpnBoundarySupportSwitch = dialogView.findViewById<SwitchMaterial>(R.id.sccmVpnBoundarySupportSwitch)
        val netbtEnabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.netbtEnabledSwitch)
        val localNetworkExcludeSwitch = dialogView.findViewById<SwitchMaterial>(R.id.localNetworkExcludeSwitch)
        val splitTunnelExcludeInput = dialogView.findViewById<TextInputEditText>(R.id.splitTunnelExcludeInput)
        val splitTunnelIncludeInput = dialogView.findViewById<TextInputEditText>(R.id.splitTunnelIncludeInput)
        
        // Setup Spinner adapters
        val autoConnectOptions = arrayOf("关闭", "开启", "强制")
        val autoConnectAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, autoConnectOptions)
        autoConnectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoConnectSpinner.adapter = autoConnectAdapter
        
        val captivePortalOptions = arrayOf("关闭", "开启", "强制")
        val captivePortalAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, captivePortalOptions)
        captivePortalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        captivePortalSpinner.adapter = captivePortalAdapter
        
        val protocolOptions = arrayOf("WireGuard", "MASQUE")
        val protocolAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, protocolOptions)
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = protocolAdapter
        
        val serviceModeOptions = arrayOf("流量和 DNS 模式", "纯 DNS 模式", "纯流量模式", "纯态势模式")
        val serviceModeAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, serviceModeOptions)
        serviceModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serviceModeSpinner.adapter = serviceModeAdapter
        
        // Populate existing data
        existingPolicy?.let { policy ->
            nameInput.setText(policy.name ?: "")
            descriptionInput.setText(policy.description ?: "")
            matchInput.setText(simplifyMatchExpression(policy.match))
            precedenceInput.setText(policy.precedence?.toString() ?: "100")
            enabledSwitch.isChecked = policy.enabled ?: true
            
            captivePortalSpinner.setSelection(getCaptivePortalIndex(policy.captivePortal))
            allowModeSwitch.isChecked = policy.allowModeSwitch ?: true
            protocolSpinner.setSelection(getProtocolIndex(policy.tunnelProtocol))
            switchLockedSwitch.isChecked = policy.switchLocked ?: false
            allowedToLeaveSwitch.isChecked = policy.allowedToLeave ?: true
            allowUpdatesSwitch.isChecked = policy.allowUpdates ?: true
            autoConnectSpinner.setSelection(getAutoConnectIndex(policy.autoConnect))
            supportUrlInput.setText(policy.supportUrl ?: "")
            serviceModeSpinner.setSelection(getServiceModeIndex(policy.serviceModeV2?.mode))
            
            excludeOfficeIpsSwitch.isChecked = policy.excludeOfficeIps ?: false
            localNetworkExcludeSwitch.isChecked = policy.allowLocalNetworkExclusion ?: false
            ipDnsRegistrationSwitch.isChecked = policy.registerInterfaceIpWithDns ?: false
            sccmVpnBoundarySupportSwitch.isChecked = policy.sccmVpnBoundarySupport ?: false
            netbtEnabledSwitch.isChecked = policy.netbtEnabled ?: false
            gatewayUniqueIdInput.setText(policy.gatewayUniqueId ?: "")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingPolicy == null) "创建配置文件" else "编辑配置文件")
            .setView(dialogView)
            .setPositiveButton(if (existingPolicy == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "配置文件名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }
                val match = buildMatchExpression(matchInput.text?.toString())
                val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 100
                val enabled = enabledSwitch.isChecked
                
                val captivePortal = getCaptivePortalValue(captivePortalSpinner.selectedItem?.toString())
                val allowModeSwitchValue = allowModeSwitch.isChecked
                val tunnelProtocol = getProtocolValue(protocolSpinner.selectedItem?.toString())
                val switchLocked = switchLockedSwitch.isChecked
                val allowedToLeave = allowedToLeaveSwitch.isChecked
                val allowUpdates = allowUpdatesSwitch.isChecked
                val autoConnect = getAutoConnectValue(autoConnectSpinner.selectedItem?.toString())
                val supportUrl = supportUrlInput.text?.toString()?.takeIf { it.isNotBlank() }
                val serviceMode = getServiceModeValue(serviceModeSpinner.selectedItem?.toString())
                
                val excludeOfficeIps = excludeOfficeIpsSwitch.isChecked
                val localNetworkExclude = localNetworkExcludeSwitch.isChecked
                val registerInterfaceIpWithDns = ipDnsRegistrationSwitch.isChecked
                val sccmVpnBoundarySupport = sccmVpnBoundarySupportSwitch.isChecked
                val netbtEnabled = netbtEnabledSwitch.isChecked
                val gatewayUniqueId = gatewayUniqueIdInput.text?.toString()?.takeIf { it.isNotBlank() }
                
                val splitTunnelExclude = parseSplitTunnelAddresses(splitTunnelExcludeInput.text?.toString())
                val splitTunnelInclude = parseSplitTunnelAddresses(splitTunnelIncludeInput.text?.toString())
                
                val serviceModeV2 = if (serviceMode != null) ServiceModeV2(mode = serviceMode) else null
                
                val request = DeviceSettingsPolicyRequest(
                    name = name,
                    description = description,
                    match = match,
                    precedence = precedence,
                    enabled = enabled,
                    captivePortal = captivePortal,
                    allowModeSwitch = allowModeSwitchValue,
                    tunnelProtocol = tunnelProtocol,
                    switchLocked = switchLocked,
                    allowedToLeave = allowedToLeave,
                    allowUpdates = allowUpdates,
                    autoConnect = autoConnect,
                    supportUrl = supportUrl,
                    serviceModeV2 = serviceModeV2,
                    excludeOfficeIps = excludeOfficeIps,
                    allowLocalNetworkExclusion = localNetworkExclude,
                    registerInterfaceIpWithDns = registerInterfaceIpWithDns,
                    sccmVpnBoundarySupport = sccmVpnBoundarySupport,
                    netbtEnabled = netbtEnabled,
                    gatewayUniqueId = gatewayUniqueId,
                    exclude = splitTunnelExclude,
                    include = splitTunnelInclude
                )
                
                if (existingPolicy == null) {
                    viewModel.createPolicy(account, request)
                } else {
                    existingPolicy.policyId?.let { policyId ->
                        viewModel.updatePolicy(account, policyId, request)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun getAutoConnectLabel(autoConnect: Int?): String {
        return when (autoConnect) {
            0 -> "关闭"
            1 -> "开启"
            2 -> "强制"
            else -> "开启"
        }
    }
    
    private fun getAutoConnectValue(label: String?): Int? {
        return when (label) {
            "关闭" -> 0
            "开启" -> 1
            "强制" -> 2
            else -> 1
        }
    }
    
    private fun getAutoConnectIndex(autoConnect: Int?): Int {
        return when (autoConnect) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> 1
        }
    }
    
    private fun getCaptivePortalLabel(captivePortal: Int?): String {
        return when (captivePortal) {
            0 -> "关闭"
            1 -> "开启"
            2 -> "强制"
            else -> "关闭"
        }
    }
    
    private fun getCaptivePortalValue(label: String?): Int? {
        return when (label) {
            "关闭" -> 0
            "开启" -> 1
            "强制" -> 2
            else -> 0
        }
    }
    
    private fun getCaptivePortalIndex(captivePortal: Int?): Int {
        return when (captivePortal) {
            0 -> 0
            1 -> 1
            2 -> 2
            else -> 0
        }
    }
    
    private fun getProtocolLabel(protocol: String?): String {
        return when (protocol) {
            "wireguard" -> "WireGuard"
            "masque" -> "MASQUE"
            else -> "WireGuard"
        }
    }
    
    private fun getProtocolValue(label: String?): String? {
        return when (label) {
            "WireGuard" -> "wireguard"
            "MASQUE" -> "masque"
            else -> "wireguard"
        }
    }
    
    private fun getProtocolIndex(protocol: String?): Int {
        return when (protocol) {
            "wireguard" -> 0
            "masque" -> 1
            else -> 0
        }
    }
    
    private fun getServiceModeLabel(serviceMode: String?): String {
        return when (serviceMode) {
            "warp" -> "流量和 DNS 模式"
            "dns" -> "纯 DNS 模式"
            "warp_only" -> "纯流量模式"
            "posture_only" -> "纯态势模式"
            else -> "流量和 DNS 模式"
        }
    }
    
    private fun getServiceModeIndex(serviceMode: String?): Int {
        return when (serviceMode) {
            "warp" -> 0
            "dns" -> 1
            "warp_only" -> 2
            "posture_only" -> 3
            else -> 0
        }
    }
    
    private fun getServiceModeValue(label: String?): String? {
        return when (label) {
            "流量和 DNS 模式" -> "warp"
            "纯 DNS 模式" -> "dns"
            "纯流量模式" -> "warp_only"
            "纯态势模式" -> "posture_only"
            else -> "warp"
        }
    }

    private fun updatePolicyEnabled(policy: DeviceSettingsPolicy, enabled: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        policy.policyId?.let { policyId ->
            val request = DeviceSettingsPolicyRequest(
                name = policy.name ?: "",
                description = policy.description,
                match = policy.match,
                precedence = policy.precedence,
                enabled = enabled,
                autoConnect = policy.autoConnect,
                allowModeSwitch = policy.allowModeSwitch,
                switchLocked = policy.switchLocked,
                excludeOfficeIps = policy.excludeOfficeIps,
                allowedToLeave = policy.allowedToLeave,
                supportUrl = policy.supportUrl,
                captivePortal = policy.captivePortal,
                disableAutoFallback = policy.disableAutoFallback,
                gatewayUniqueId = policy.gatewayUniqueId,
                tunnelProtocol = policy.tunnelProtocol,
                allowUpdates = policy.allowUpdates,
                serviceModeV2 = policy.serviceModeV2,
                registerInterfaceIpWithDns = policy.registerInterfaceIpWithDns,
                sccmVpnBoundarySupport = policy.sccmVpnBoundarySupport,
                netbtEnabled = policy.netbtEnabled
            )
            viewModel.updatePolicy(account, policyId, request)
        }
    }

    private fun confirmDeletePolicy(policyId: String, policyName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除配置文件")
            .setMessage("确定要删除配置文件 \"$policyName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.deletePolicy(account, policyId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun simplifyMatchExpression(match: String?): String {
        if (match.isNullOrBlank()) return ""
        val emailRegex = Regex("identity\\.email\\s+(==|in)\\s+\\{?\"?([^\"]+)\"?\\}?")
        val matchResult = emailRegex.find(match)
        if (matchResult != null) {
            return matchResult.groupValues[2]
        }
        return match
    }
    
    private fun buildMatchExpression(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()
        if (trimmed.contains("@")) {
            return "identity.email == \"$trimmed\""
        }
        return trimmed
    }

    private fun parseSplitTunnelAddresses(input: String?): List<SplitTunnel>? {
        if (input.isNullOrBlank()) return null
        return input.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { address ->
                SplitTunnel(address = address)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
