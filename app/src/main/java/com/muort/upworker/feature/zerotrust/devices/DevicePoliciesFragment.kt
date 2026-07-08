package com.muort.upworker.feature.zerotrust.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.View.VISIBLE
import android.view.View.GONE
import android.widget.LinearLayout
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
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.DevicePolicyUpdate
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

    companion object {
        private const val DEFAULT_SPLIT_TUNNEL_EXCLUDE = """10.0.0.0/8
100.64.0.0/10
169.254.0.0/16
172.16.0.0/12
192.0.0.0/24
192.168.0.0/16
224.0.0.0/24
240.0.0.0/4
255.255.255.255/32
fe80::/10
fd00::/8
ff01::/16
ff02::/16
ff03::/16
ff04::/16
ff05::/16"""
    }

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
    
    private fun isDefaultPolicy(policy: DeviceSettingsPolicy): Boolean {
        return policy.isDefault == true
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
        
        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.policyNameLayout)
        val descriptionInputLayout = dialogView.findViewById<TextInputLayout>(R.id.policyDescriptionLayout)
        val matchInputLayout = dialogView.findViewById<TextInputLayout>(R.id.policyMatchLayout)
        val matchBuilderLayout = dialogView.findViewById<LinearLayout>(R.id.matchBuilderLayout)
        val matchSelectorSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.matchSelectorSpinner)
        val matchOperatorSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.matchOperatorSpinner)
        val matchValueInput = dialogView.findViewById<TextInputEditText>(R.id.matchValueInput)
        val matchValueLayout = dialogView.findViewById<TextInputLayout>(R.id.matchValueLayout)
        val matchAdvancedToggle = dialogView.findViewById<android.widget.TextView>(R.id.matchAdvancedToggle)
        val precedenceInputLayout = dialogView.findViewById<TextInputLayout>(R.id.policyPrecedenceLayout)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.policyNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.policyDescriptionInput)
        val matchInput = dialogView.findViewById<TextInputEditText>(R.id.policyMatchInput)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.policyPrecedenceInput)
        val enabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.policyEnabledSwitch)
        
        val autoConnectSwitch = dialogView.findViewById<SwitchMaterial>(R.id.autoConnectSwitch)
        val autoConnectMinutesSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.autoConnectMinutesSpinner)
        val allowModeSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowModeSwitch)
        val switchLockedSwitch = dialogView.findViewById<SwitchMaterial>(R.id.switchLockedSwitch)
        val excludeOfficeIpsSwitch = dialogView.findViewById<SwitchMaterial>(R.id.excludeOfficeIpsSwitch)
        val allowedToLeaveSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowedToLeaveSwitch)
        val captivePortalSwitch = dialogView.findViewById<SwitchMaterial>(R.id.captivePortalSwitch)
        val captivePortalMinutesSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.captivePortalMinutesSpinner)
        val gatewayUniqueIdInput = dialogView.findViewById<TextInputEditText>(R.id.gatewayUniqueIdInput)
        
        val protocolSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.protocolSpinner)
        val allowUpdatesSwitch = dialogView.findViewById<SwitchMaterial>(R.id.allowUpdatesSwitch)
        val serviceModeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.serviceModeSpinner)
        val ipDnsRegistrationSwitch = dialogView.findViewById<SwitchMaterial>(R.id.ipDnsRegistrationSwitch)
        val sccmVpnBoundarySupportSwitch = dialogView.findViewById<SwitchMaterial>(R.id.sccmVpnBoundarySupportSwitch)
        val netbtEnabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.netbtEnabledSwitch)
        val localNetworkExcludeSwitch = dialogView.findViewById<SwitchMaterial>(R.id.localNetworkExcludeSwitch)
        val localNetworkExcludeMinutesSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.localNetworkExcludeMinutesSpinner)
        val splitTunnelExcludeInput = dialogView.findViewById<TextInputEditText>(R.id.splitTunnelExcludeInput)
        val splitTunnelIncludeInput = dialogView.findViewById<TextInputEditText>(R.id.splitTunnelIncludeInput)
        val splitTunnelExcludeLayout = dialogView.findViewById<TextInputLayout>(R.id.splitTunnelExcludeLayout)
        val splitTunnelIncludeLayout = dialogView.findViewById<TextInputLayout>(R.id.splitTunnelIncludeLayout)
        val splitTunnelModeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.splitTunnelModeSpinner)
        
        // Setup Spinner adapters
        val protocolOptions = arrayOf("WireGuard", "MASQUE")
        val protocolAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, protocolOptions)
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        protocolSpinner.adapter = protocolAdapter
        
        val osSelectorLayout = dialogView.findViewById<LinearLayout>(R.id.osSelectorLayout)
        val osWindows = dialogView.findViewById<android.widget.CheckBox>(R.id.osWindows)
        val osMacos = dialogView.findViewById<android.widget.CheckBox>(R.id.osMacos)
        val osLinux = dialogView.findViewById<android.widget.CheckBox>(R.id.osLinux)
        val osIos = dialogView.findViewById<android.widget.CheckBox>(R.id.osIos)
        val osAndroid = dialogView.findViewById<android.widget.CheckBox>(R.id.osAndroid)
        val osChromeOs = dialogView.findViewById<android.widget.CheckBox>(R.id.osChromeOs)
        
        // Match expression selectors and operators
        val matchSelectorOptions = arrayOf(
            "用户电子邮件",
            "操作系统"
        )
        val matchSelectorAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item_left, matchSelectorOptions)
        matchSelectorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        matchSelectorSpinner.adapter = matchSelectorAdapter
        
        val emailOperatorOptions = arrayOf("是", "在", "不在", "匹配")
        val osOperatorOptions = arrayOf("是", "在", "不在")
        
        val matchOperatorAdapter = android.widget.ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            arrayListOf(*emailOperatorOptions)
        )
        matchOperatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        matchOperatorSpinner.adapter = matchOperatorAdapter
        
        fun updateOperatorOptions(selectorIndex: Int, preserveSelection: Boolean = true) {
            val prevOperatorIdx = matchOperatorSpinner.selectedItemPosition
            val newOptions = if (selectorIndex == 0) {
                emailOperatorOptions
            } else {
                osOperatorOptions
            }
            matchOperatorAdapter.clear()
            matchOperatorAdapter.addAll(*newOptions)
            matchOperatorAdapter.notifyDataSetChanged()
            if (preserveSelection && prevOperatorIdx < newOptions.size) {
                matchOperatorSpinner.setSelection(prevOperatorIdx)
            } else {
                matchOperatorSpinner.setSelection(0)
            }
        }
        
        // Toggle OS selector based on selector
        matchSelectorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                updateOperatorOptions(position)
                if (position == 1) {
                    osSelectorLayout.visibility = View.VISIBLE
                    matchValueLayout.visibility = View.GONE
                } else {
                    osSelectorLayout.visibility = View.GONE
                    matchValueLayout.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        
        // Advanced mode toggle
        var isAdvancedMode = false
        
        fun getSelectedOsValuesLocal(): String {
            val osList = mutableListOf<String>()
            if (osWindows.isChecked) osList.add("windows")
            if (osMacos.isChecked) osList.add("macos")
            if (osLinux.isChecked) osList.add("linux")
            if (osIos.isChecked) osList.add("ios")
            if (osAndroid.isChecked) osList.add("android")
            if (osChromeOs.isChecked) osList.add("chrome")
            return osList.joinToString(", ")
        }
        
        fun setOsCheckboxesFromValues(values: String) {
            val osList = values.split(",").map { it.trim().lowercase() }
            osWindows.isChecked = osList.contains("windows")
            osMacos.isChecked = osList.contains("macos")
            osLinux.isChecked = osList.contains("linux")
            osIos.isChecked = osList.contains("ios")
            osAndroid.isChecked = osList.contains("android")
            osChromeOs.isChecked = osList.contains("chrome")
        }
        
        matchAdvancedToggle.setOnClickListener {
            isAdvancedMode = !isAdvancedMode
            if (isAdvancedMode) {
                matchBuilderLayout.visibility = View.GONE
                matchInputLayout.visibility = View.VISIBLE
                matchAdvancedToggle.text = "简化模式"
                val value = if (matchSelectorSpinner.selectedItemPosition == 1) {
                    getSelectedOsValuesLocal()
                } else {
                    matchValueInput.text?.toString()
                }
                matchInput.setText(buildMatchExpressionFromBuilder(
                    matchSelectorSpinner.selectedItemPosition,
                    matchOperatorSpinner.selectedItemPosition,
                    value
                ))
            } else {
                matchBuilderLayout.visibility = View.VISIBLE
                matchInputLayout.visibility = View.GONE
                matchAdvancedToggle.text = "高级：直接编辑表达式"
                parseMatchExpressionToBuilder(matchInput.text?.toString())?.let { (selectorIdx, operatorIdx, value) ->
                    matchSelectorSpinner.setSelection(selectorIdx)
                    matchOperatorSpinner.setSelection(operatorIdx)
                    if (selectorIdx == 1) {
                        setOsCheckboxesFromValues(value)
                    } else {
                        matchValueInput.setText(value)
                    }
                }
            }
        }
        
        val serviceModeOptions = arrayOf("流量和 DNS 模式", "纯 DNS 模式", "本地代理模式", "纯态势模式")
        val serviceModeAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, serviceModeOptions)
        serviceModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serviceModeSpinner.adapter = serviceModeAdapter
        
        // 强制网络门户检测: 仅支持 3、5、10 分钟
        val captivePortalOptions = arrayOf("3 分钟", "5 分钟", "10 分钟")
        val captivePortalAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, captivePortalOptions)
        captivePortalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        captivePortalMinutesSpinner.adapter = captivePortalAdapter
        
        // 自动连接: 1-1440 分钟，常用选项
        val autoConnectOptions = arrayOf("3 分钟", "5 分钟", "10 分钟", "15 分钟", "30 分钟", "60 分钟", "120 分钟", "240 分钟", "480 分钟", "720 分钟", "1440 分钟")
        val autoConnectAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, autoConnectOptions)
        autoConnectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoConnectMinutesSpinner.adapter = autoConnectAdapter
        
        // 本地网络排除: 1-120 分钟，常用选项
        val localNetworkExcludeOptions = arrayOf("5 分钟", "10 分钟", "15 分钟", "30 分钟", "60 分钟", "90 分钟", "120 分钟")
        val localNetworkExcludeAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, localNetworkExcludeOptions)
        localNetworkExcludeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        localNetworkExcludeMinutesSpinner.adapter = localNetworkExcludeAdapter
        
        val splitTunnelModeOptions = arrayOf("排除模式", "包含模式")
        val splitTunnelModeAdapter = android.widget.ArrayAdapter(requireContext(), R.layout.spinner_item, splitTunnelModeOptions)
        splitTunnelModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        splitTunnelModeSpinner.adapter = splitTunnelModeAdapter
        
        splitTunnelModeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        splitTunnelExcludeLayout.visibility = View.VISIBLE
                        splitTunnelIncludeLayout.visibility = View.GONE
                        if (splitTunnelExcludeInput.text.isNullOrBlank()) {
                            splitTunnelExcludeInput.setText(DEFAULT_SPLIT_TUNNEL_EXCLUDE)
                            com.google.android.material.snackbar.Snackbar.make(
                                dialogView,
                                "已自动填入默认排除列表，请检查后点击保存",
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    1 -> {
                        splitTunnelExcludeLayout.visibility = View.GONE
                        splitTunnelIncludeLayout.visibility = View.VISIBLE
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        
        val isDefault = existingPolicy != null && isDefaultPolicy(existingPolicy)
        
        if (isDefault) {
            nameInputLayout.visibility = View.GONE
            descriptionInputLayout.visibility = View.GONE
            matchInputLayout.visibility = View.GONE
            matchBuilderLayout.visibility = View.GONE
            matchAdvancedToggle.visibility = View.GONE
            precedenceInputLayout.visibility = View.GONE
            enabledSwitch.visibility = View.GONE
        }
        
        existingPolicy?.let { policy ->
            if (!isDefault) {
                nameInput.setText(policy.name ?: "")
                descriptionInput.setText(policy.description ?: "")
                matchInput.setText(policy.match ?: "")
                precedenceInput.setText(policy.precedence?.toString() ?: "100")
                enabledSwitch.isChecked = policy.enabled ?: true
                
                parseMatchExpressionToBuilder(policy.match)?.let { (selectorIdx, operatorIdx, value) ->
                    matchSelectorSpinner.setSelection(selectorIdx)
                    matchOperatorSpinner.setSelection(operatorIdx)
                    if (selectorIdx == 1) {
                        val osList = value.split(",").map { it.trim().lowercase() }
                        osWindows.isChecked = osList.contains("windows")
                        osMacos.isChecked = osList.contains("macos")
                        osLinux.isChecked = osList.contains("linux")
                        osIos.isChecked = osList.contains("ios")
                        osAndroid.isChecked = osList.contains("android")
                        osChromeOs.isChecked = osList.contains("chrome")
                    } else {
                        matchValueInput.setText(value)
                    }
                }
            }
            
            captivePortalSwitch.isChecked = (policy.captivePortal ?: 0) > 0
            captivePortalMinutesSpinner.setSelection(getCaptivePortalIndex(policy.captivePortal))
            allowModeSwitch.isChecked = policy.allowModeSwitch ?: true
            protocolSpinner.setSelection(getProtocolIndex(policy.tunnelProtocol))
            switchLockedSwitch.isChecked = policy.switchLocked ?: false
            allowedToLeaveSwitch.isChecked = policy.allowedToLeave ?: true
            allowUpdatesSwitch.isChecked = policy.allowUpdates ?: true
            autoConnectSwitch.isChecked = (policy.autoConnect ?: 0) > 0
            autoConnectMinutesSpinner.setSelection(getAutoConnectIndex(policy.autoConnect))
            serviceModeSpinner.setSelection(getServiceModeIndex(policy.serviceModeV2?.mode))
            
            excludeOfficeIpsSwitch.isChecked = policy.excludeOfficeIps ?: false
            localNetworkExcludeSwitch.isChecked = (policy.lanAllowMinutes ?: 0) > 0
            localNetworkExcludeMinutesSpinner.setSelection(getLocalNetworkExcludeIndex(policy.lanAllowMinutes))
            ipDnsRegistrationSwitch.isChecked = policy.registerInterfaceIpWithDns ?: false
            sccmVpnBoundarySupportSwitch.isChecked = policy.sccmVpnBoundarySupport ?: false
            netbtEnabledSwitch.isChecked = policy.netbtEnabled ?: false
            gatewayUniqueIdInput.setText(policy.gatewayUniqueId ?: "")
            
            splitTunnelExcludeInput.setText(formatSplitTunnelAddresses(policy.exclude))
            splitTunnelIncludeInput.setText(formatSplitTunnelAddresses(policy.include))
            
            val hasInclude = policy.include?.isNotEmpty() == true
            if (hasInclude) {
                splitTunnelModeSpinner.setSelection(1)
            } else {
                splitTunnelModeSpinner.setSelection(0)
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingPolicy == null) "创建配置文件" else "编辑配置文件")
            .setView(dialogView)
            .setPositiveButton(if (existingPolicy == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                
                val captivePortalMinutes = getCaptivePortalMinutes(captivePortalMinutesSpinner.selectedItemPosition)
                val captivePortal = if (captivePortalSwitch.isChecked) captivePortalMinutes * 60 else 0
                val allowModeSwitchValue = allowModeSwitch.isChecked
                val tunnelProtocol = getProtocolValue(protocolSpinner.selectedItem?.toString())
                val switchLocked = switchLockedSwitch.isChecked
                val allowedToLeave = allowedToLeaveSwitch.isChecked
                val allowUpdates = allowUpdatesSwitch.isChecked
                val autoConnectMinutes = getAutoConnectMinutes(autoConnectMinutesSpinner.selectedItemPosition)
                val autoConnect = if (autoConnectSwitch.isChecked) autoConnectMinutes * 60 else 0
                val serviceMode = getServiceModeValue(serviceModeSpinner.selectedItem?.toString())
                
                val excludeOfficeIps = excludeOfficeIpsSwitch.isChecked
                val registerInterfaceIpWithDns = ipDnsRegistrationSwitch.isChecked
                val sccmVpnBoundarySupport = sccmVpnBoundarySupportSwitch.isChecked
                val netbtEnabled = netbtEnabledSwitch.isChecked
                val lanAllowMinutes = if (localNetworkExcludeSwitch.isChecked) {
                    getLocalNetworkExcludeMinutes(localNetworkExcludeMinutesSpinner.selectedItemPosition)
                } else {
                    0
                }
                
                val splitTunnelMode = splitTunnelModeSpinner.selectedItemPosition
                val splitTunnelExclude = if (splitTunnelMode == 0) parseSplitTunnelAddresses(splitTunnelExcludeInput.text?.toString()) else null
                val splitTunnelInclude = if (splitTunnelMode == 1) parseSplitTunnelAddresses(splitTunnelIncludeInput.text?.toString()) else null
                
                val serviceModeV2 = if (serviceMode != null) ServiceModeV2(mode = serviceMode) else null
                
                fun getSelectedOsValues(): String {
                    val osList = mutableListOf<String>()
                    if (osWindows.isChecked) osList.add("windows")
                    if (osMacos.isChecked) osList.add("macos")
                    if (osLinux.isChecked) osList.add("linux")
                    if (osIos.isChecked) osList.add("ios")
                    if (osAndroid.isChecked) osList.add("android")
                    if (osChromeOs.isChecked) osList.add("chrome")
                    return osList.joinToString(", ")
                }
                
                fun getMatchExpression(): String? {
                    return if (isAdvancedMode) {
                        buildMatchExpression(matchInput.text?.toString())
                    } else {
                        val selectorIdx = matchSelectorSpinner.selectedItemPosition
                        val operatorIdx = matchOperatorSpinner.selectedItemPosition
                        val value = if (selectorIdx == 1) {
                            getSelectedOsValues()
                        } else {
                            matchValueInput.text?.toString()
                        }
                        val expr = buildMatchExpressionFromBuilder(selectorIdx, operatorIdx, value)
                        expr.ifBlank { null }
                    }
                }
                
                if (existingPolicy == null) {
                    val name = nameInput.text?.toString()
                    if (name.isNullOrBlank()) {
                        Snackbar.make(binding.root, "配置文件名称不能为空", Snackbar.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    val description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }
                    val match = getMatchExpression()
                    val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 100
                    val enabled = enabledSwitch.isChecked
                    
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
                        serviceModeV2 = serviceModeV2,
                        excludeOfficeIps = excludeOfficeIps,
                        registerInterfaceIpWithDns = registerInterfaceIpWithDns,
                        sccmVpnBoundarySupport = sccmVpnBoundarySupport,
                        netbtEnabled = netbtEnabled,
                        lanAllowMinutes = lanAllowMinutes
                    )
                    
                    viewModel.createPolicy(account, request)
                    
                    viewModel.setSplitTunnel(account, null, splitTunnelExclude, splitTunnelInclude)
                } else {
                    if (isDefaultPolicy(existingPolicy)) {
                        val update = DevicePolicyUpdate(
                            allowModeSwitch = allowModeSwitchValue,
                            allowUpdates = allowUpdates,
                            allowedToLeave = allowedToLeave,
                            autoConnect = autoConnect,
                            captivePortal = captivePortal,
                            excludeOfficeIps = excludeOfficeIps,
                            registerInterfaceIpWithDns = registerInterfaceIpWithDns,
                            sccmVpnBoundarySupport = sccmVpnBoundarySupport,
                            serviceModeV2 = serviceModeV2,
                            switchLocked = switchLocked,
                            tunnelProtocol = tunnelProtocol,
                            lanAllowMinutes = lanAllowMinutes,
                            netbtEnabled = netbtEnabled
                        )
                        viewModel.updateDefaultPolicy(account, update)
                        viewModel.setSplitTunnel(account, null, splitTunnelExclude, splitTunnelInclude)
                    } else {
                        val name = nameInput.text?.toString()
                        if (name.isNullOrBlank()) {
                            Snackbar.make(binding.root, "配置文件名称不能为空", Snackbar.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        
                        val description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }
                        val match = getMatchExpression()
                        val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 100
                        val enabled = enabledSwitch.isChecked
                        
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
                        serviceModeV2 = serviceModeV2,
                        excludeOfficeIps = excludeOfficeIps,
                        registerInterfaceIpWithDns = registerInterfaceIpWithDns,
                        sccmVpnBoundarySupport = sccmVpnBoundarySupport,
                        netbtEnabled = netbtEnabled,
                        lanAllowMinutes = lanAllowMinutes
                    )
                    
                    existingPolicy.policyId?.let { policyId ->
                        viewModel.updatePolicy(account, policyId, request)
                        viewModel.setSplitTunnel(account, policyId, splitTunnelExclude, splitTunnelInclude)
                    }
                }
                }
            }
            .setNegativeButton("取消", null)
            .show()
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

    // captive_portal: API单位是秒，UI显示分钟
    private fun getCaptivePortalMinutes(position: Int): Int {
        return when (position) {
            0 -> 3
            1 -> 5
            2 -> 10
            else -> 3
        }
    }

    private fun getCaptivePortalIndex(captivePortal: Int?): Int {
        val minutes = (captivePortal ?: 0) / 60
        return when (minutes) {
            3 -> 0
            5 -> 1
            10 -> 2
            else -> 0
        }
    }

    // auto_connect: API单位是秒，UI显示分钟
    private fun getAutoConnectMinutes(position: Int): Int {
        return when (position) {
            0 -> 3
            1 -> 5
            2 -> 10
            3 -> 15
            4 -> 30
            5 -> 60
            6 -> 120
            7 -> 240
            8 -> 480
            9 -> 720
            10 -> 1440
            else -> 3
        }
    }

    private fun getAutoConnectIndex(autoConnect: Int?): Int {
        val minutes = (autoConnect ?: 0) / 60
        return when (minutes) {
            3 -> 0
            5 -> 1
            10 -> 2
            15 -> 3
            30 -> 4
            60 -> 5
            120 -> 6
            240 -> 7
            480 -> 8
            720 -> 9
            1440 -> 10
            else -> 0
        }
    }

    // lan_allow_minutes: API单位是分钟
    private fun getLocalNetworkExcludeMinutes(position: Int): Int {
        return when (position) {
            0 -> 5
            1 -> 10
            2 -> 15
            3 -> 30
            4 -> 60
            5 -> 90
            6 -> 120
            else -> 60
        }
    }

    private fun getLocalNetworkExcludeIndex(lanAllowMinutes: Int?): Int {
        val minutes = lanAllowMinutes ?: 0
        return when (minutes) {
            5 -> 0
            10 -> 1
            15 -> 2
            30 -> 3
            60 -> 4
            90 -> 5
            120 -> 6
            else -> 4
        }
    }
    
    private fun getServiceModeLabel(serviceMode: String?): String {
        return when (serviceMode) {
            "warp" -> "流量和 DNS 模式"
            "1dot1" -> "纯 DNS 模式"
            "proxy" -> "本地代理模式"
            "posture_only" -> "纯态势模式"
            else -> "流量和 DNS 模式"
        }
    }
    
    private fun getServiceModeIndex(serviceMode: String?): Int {
        return when (serviceMode) {
            "warp" -> 0
            "1dot1" -> 1
            "proxy" -> 2
            "posture_only" -> 3
            else -> 0
        }
    }
    
    private fun getServiceModeValue(label: String?): String? {
        return when (label) {
            "流量和 DNS 模式" -> "warp"
            "纯 DNS 模式" -> "1dot1"
            "本地代理模式" -> "proxy"
            "纯态势模式" -> "posture_only"
            else -> "warp"
        }
    }

    private fun updatePolicyEnabled(policy: DeviceSettingsPolicy, enabled: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        if (isDefaultPolicy(policy)) {
            val update = DevicePolicyUpdate(
                allowModeSwitch = policy.allowModeSwitch,
                allowUpdates = policy.allowUpdates,
                allowedToLeave = policy.allowedToLeave,
                autoConnect = policy.autoConnect,
                captivePortal = policy.captivePortal,
                excludeOfficeIps = policy.excludeOfficeIps,
                registerInterfaceIpWithDns = policy.registerInterfaceIpWithDns,
                sccmVpnBoundarySupport = policy.sccmVpnBoundarySupport,
                serviceModeV2 = policy.serviceModeV2,
                switchLocked = policy.switchLocked,
                tunnelProtocol = policy.tunnelProtocol,
                lanAllowMinutes = policy.lanAllowMinutes,
                netbtEnabled = policy.netbtEnabled
            )
            viewModel.updateDefaultPolicy(account, update)
        } else {
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
                    captivePortal = policy.captivePortal,
                    disableAutoFallback = policy.disableAutoFallback,
                    tunnelProtocol = policy.tunnelProtocol,
                    allowUpdates = policy.allowUpdates,
                    serviceModeV2 = policy.serviceModeV2,
                    registerInterfaceIpWithDns = policy.registerInterfaceIpWithDns,
                    sccmVpnBoundarySupport = policy.sccmVpnBoundarySupport,
                    netbtEnabled = policy.netbtEnabled,
                    lanAllowMinutes = policy.lanAllowMinutes,
                    exclude = policy.exclude,
                    include = policy.include
                )
                viewModel.updatePolicy(account, policyId, request)
            }
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
        return match
    }
    
    private fun buildMatchExpression(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return input.trim()
    }
    
    private fun getMatchSelectorField(selectorIndex: Int): String {
        return when (selectorIndex) {
            0 -> "identity.email"
            1 -> "os.name"
            else -> "identity.email"
        }
    }
    
    private fun getMatchOperatorSymbol(selectorIndex: Int, operatorIndex: Int): String {
        return when (selectorIndex) {
            0 -> when (operatorIndex) {
                0 -> "=="
                1 -> "in"
                2 -> "not in"
                3 -> "matches"
                else -> "=="
            }
            1 -> when (operatorIndex) {
                0 -> "=="
                1 -> "in"
                2 -> "not in"
                else -> "=="
            }
            else -> "=="
        }
    }
    
    private fun getSelectorIndexFromField(field: String): Int {
        return when (field) {
            "identity.email" -> 0
            "os.name" -> 1
            else -> -1
        }
    }
    
    private fun getOperatorIndexFromSymbol(selectorIndex: Int, operator: String): Int {
        return when (selectorIndex) {
            0 -> when (operator) {
                "==" -> 0
                "in" -> 1
                "not in" -> 2
                "matches" -> 3
                else -> -1
            }
            1 -> when (operator) {
                "==" -> 0
                "in" -> 1
                "not in" -> 2
                else -> -1
            }
            else -> -1
        }
    }
    
    private fun buildMatchExpressionFromBuilder(selectorIndex: Int, operatorIndex: Int, value: String?): String {
        if (value.isNullOrBlank()) return ""
        val field = getMatchSelectorField(selectorIndex)
        val operator = getMatchOperatorSymbol(selectorIndex, operatorIndex)
        
        if (operator == "in" || operator == "not in") {
            val values = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (values.isEmpty()) return ""
            val quotedValues = values.joinToString(", ") { "\"$it\"" }
            return "$field $operator { $quotedValues }"
        } else {
            return "$field $operator \"${value.trim()}\""
        }
    }
    
    private fun parseMatchExpressionToBuilder(expression: String?): Triple<Int, Int, String>? {
        if (expression.isNullOrBlank()) return null
        
        val trimmed = expression.trim()
        
        val inPattern = Regex("^(\\S+(?:\\.\\S+)*)\\s+(in|not in)\\s+\\{(.+)\\}$", RegexOption.IGNORE_CASE)
        val inMatch = inPattern.find(trimmed)
        if (inMatch != null) {
            val field = inMatch.groupValues[1]
            val operator = inMatch.groupValues[2].lowercase()
            val valuesStr = inMatch.groupValues[3]
            val values = valuesStr.split(",").map { 
                it.trim().removeSurrounding("\"") 
            }.filter { it.isNotBlank() }
            
            val selectorIdx = getSelectorIndexFromField(field)
            val operatorIdx = getOperatorIndexFromSymbol(selectorIdx, operator)
            
            if (selectorIdx >= 0 && operatorIdx >= 0) {
                return Triple(selectorIdx, operatorIdx, values.joinToString(", "))
            }
        }
        
        val eqPattern = Regex("^(\\S+(?:\\.\\S+)*)\\s+(==|!=|matches)\\s+\"(.+)\"$", RegexOption.IGNORE_CASE)
        val eqMatch = eqPattern.find(trimmed)
        if (eqMatch != null) {
            val field = eqMatch.groupValues[1]
            val operator = eqMatch.groupValues[2].lowercase()
            val value = eqMatch.groupValues[3]
            
            val selectorIdx = getSelectorIndexFromField(field)
            val operatorIdx = getOperatorIndexFromSymbol(selectorIdx, operator)
            
            if (selectorIdx >= 0 && operatorIdx >= 0) {
                return Triple(selectorIdx, operatorIdx, value)
            }
        }
        
        return null
    }

    private fun parseSplitTunnelAddresses(input: String?): List<SplitTunnel>? {
        if (input.isNullOrBlank()) return null
        return input.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { item ->
                if (isValidIpAddress(item)) {
                    SplitTunnel(address = item)
                } else {
                    SplitTunnel(host = item)
                }
            }
    }

    private fun formatSplitTunnelAddresses(items: List<SplitTunnel>?): String {
        if (items.isNullOrEmpty()) return ""
        return items.joinToString("\n") { item ->
            item.address ?: item.host ?: ""
        }
    }

    private fun isValidIpAddress(input: String): Boolean {
        val ipv4Regex = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?:/[0-9]{1,2})?$"
        val ipv6Regex = "^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}(?:/[0-9]{1,3})?$"
        return input.matches(ipv4Regex.toRegex()) || input.matches(ipv6Regex.toRegex())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
