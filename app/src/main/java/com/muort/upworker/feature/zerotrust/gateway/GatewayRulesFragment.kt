package com.muort.upworker.feature.zerotrust.gateway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.GatewayRule
import com.muort.upworker.core.model.GatewayRuleRequest
import com.muort.upworker.databinding.FragmentGatewayRulesBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GatewayRulesFragment : Fragment() {

    private var _binding: FragmentGatewayRulesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var ruleAdapter: GatewayRuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGatewayRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadRules()
    }

    private fun setupRecyclerView() {
        ruleAdapter = GatewayRuleAdapter(
            onEditClick = { rule ->
                showCreateRuleDialog(rule)
            },
            onDeleteClick = { rule ->
                confirmDeleteRule(rule.id, rule.name)
            },
            onEnabledChange = { rule, enabled ->
                updateRuleEnabled(rule, enabled)
            }
        )

        binding.rulesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ruleAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddRule.setOnClickListener {
            showCreateRuleDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rules.collect { rules ->
                        ruleAdapter.submitList(rules)
                        binding.emptyText.visibility = 
                            if (rules.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.loadingState.collect { _ ->
                        // Loading state handled byViewModel
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

    private fun loadRules() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewModel.loadRules(account)
    }

    private fun showCreateRuleDialog(existingRule: GatewayRule? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_gateway_rule, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.ruleNameInput)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.ruleTypeSpinner)
        val actionSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.actionSpinner)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.precedenceInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val enabledSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.enabledSwitch)
        
        val dnsFields = dialogView.findViewById<View>(R.id.dnsFields)
        val httpFields = dialogView.findViewById<View>(R.id.httpFields)
        val l4Fields = dialogView.findViewById<View>(R.id.l4Fields)

        // Setup type spinner
        val types = listOf("dns" to "DNS", "http" to "HTTP", "l4" to "网络 (L4)")
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter
        
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                dnsFields.visibility = if (position == 0) View.VISIBLE else View.GONE
                httpFields.visibility = if (position == 1) View.VISIBLE else View.GONE
                l4Fields.visibility = if (position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Setup action spinner
        val actions = listOf("allow" to "允许", "block" to "阻止", "safe_search" to "安全搜索")
        val actionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            actions.map { it.second }
        )
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        actionSpinner.adapter = actionAdapter

        // Populate existing rule
        existingRule?.let { rule ->
            nameInput.setText(rule.name)
            precedenceInput.setText(rule.precedence?.toString() ?: "0")
            descriptionInput.setText(rule.description ?: "")
            enabledSwitch.isChecked = rule.enabled
            
            val ruleType = rule.filters.firstOrNull() ?: "dns"
            val typeIndex = types.indexOfFirst { it.first == ruleType }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
            
            val actionIndex = actions.indexOfFirst { it.first == rule.action }
            if (actionIndex >= 0) actionSpinner.setSelection(actionIndex)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingRule == null) "创建规则" else "编辑规则")
            .setView(dialogView)
            .setPositiveButton(if (existingRule == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "规则名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val ruleType = types[typeSpinner.selectedItemPosition].first
                val action = actions[actionSpinner.selectedItemPosition].first
                val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 0
                val description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }
                val enabled = enabledSwitch.isChecked
                
                // Build traffic expression based on rule type
                val traffic = buildTrafficExpression(ruleType, dialogView)
                
                val request = GatewayRuleRequest(
                    name = name,
                    description = description,
                    action = action,
                    enabled = enabled,
                    filters = listOf(ruleType),
                    traffic = traffic,
                    precedence = precedence
                )
                
                if (existingRule == null) {
                    viewModel.createRule(account, request)
                } else {
                    viewModel.updateRule(account, existingRule.id, request)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun buildTrafficExpression(ruleType: String, dialogView: View): String? {
        return when (ruleType) {
            "dns" -> {
                val host = dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.text?.toString()
                if (!host.isNullOrBlank()) {
                    "dns.fqdn == \"$host\" or dns.fqdn =~ \".*\\.$host\""
                } else null
            }
            "http" -> {
                val host = dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.text?.toString()
                if (!host.isNullOrBlank()) {
                    "http.host == \"$host\" or http.host =~ \".*\\.$host\""
                } else null
            }
            "l4" -> {
                val sourceIp = dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.text?.toString()
                if (!sourceIp.isNullOrBlank()) {
                    "net.src.ip == $sourceIp"
                } else null
            }
            else -> null
        }
    }

    private fun confirmDeleteRule(ruleId: String, ruleName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除规则")
            .setMessage("确定要删除规则 \"$ruleName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteRule(ruleId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteRule(ruleId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        viewModel.deleteRule(account, ruleId)
    }

    private fun updateRuleEnabled(rule: GatewayRule, enabled: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        val request = GatewayRuleRequest(
            name = rule.name,
            description = rule.description,
            action = rule.action,
            enabled = enabled,
            filters = rule.filters,
            traffic = rule.traffic,
            precedence = rule.precedence,
            ruleSettings = rule.ruleSettings
        )
        viewModel.updateRule(account, rule.id, request)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
