package com.muort.upworker.feature.zerotrust.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.muort.upworker.core.model.GatewayRuleSettings
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
    
    private var allRules = listOf<GatewayRule>()
    private var currentFilter = "all"

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
        setupFilters()
        observeViewModel()
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

    private fun setupFilters() {
        binding.filterAllBtn.setOnClickListener {
            currentFilter = "all"
            updateFilterButtons()
            applyFilter()
        }
        
        binding.filterDnsBtn.setOnClickListener {
            currentFilter = "dns"
            updateFilterButtons()
            applyFilter()
        }
        
        binding.filterHttpBtn.setOnClickListener {
            currentFilter = "http"
            updateFilterButtons()
            applyFilter()
        }
        
        binding.filterL4Btn.setOnClickListener {
            currentFilter = "l4"
            updateFilterButtons()
            applyFilter()
        }
    }
    
    private fun updateFilterButtons() {
        binding.filterAllBtn.isSelected = currentFilter == "all"
        binding.filterDnsBtn.isSelected = currentFilter == "dns"
        binding.filterHttpBtn.isSelected = currentFilter == "http"
        binding.filterL4Btn.isSelected = currentFilter == "l4"
        
        binding.filterAllBtn.setTextColor(if (currentFilter == "all") 
            requireContext().getColor(R.color.white) else requireContext().getColor(R.color.black))
        binding.filterDnsBtn.setTextColor(if (currentFilter == "dns") 
            requireContext().getColor(R.color.white) else requireContext().getColor(R.color.black))
        binding.filterHttpBtn.setTextColor(if (currentFilter == "http") 
            requireContext().getColor(R.color.white) else requireContext().getColor(R.color.black))
        binding.filterL4Btn.setTextColor(if (currentFilter == "l4") 
            requireContext().getColor(R.color.white) else requireContext().getColor(R.color.black))
            
        binding.filterAllBtn.setBackgroundColor(if (currentFilter == "all") 
            requireContext().getColor(R.color.blue) else requireContext().getColor(R.color.transparent))
        binding.filterDnsBtn.setBackgroundColor(if (currentFilter == "dns") 
            requireContext().getColor(R.color.blue) else requireContext().getColor(R.color.transparent))
        binding.filterHttpBtn.setBackgroundColor(if (currentFilter == "http") 
            requireContext().getColor(R.color.blue) else requireContext().getColor(R.color.transparent))
        binding.filterL4Btn.setBackgroundColor(if (currentFilter == "l4") 
            requireContext().getColor(R.color.blue) else requireContext().getColor(R.color.transparent))
    }
    
    private fun applyFilter() {
        val filtered = if (currentFilter == "all") {
            allRules
        } else {
            allRules.filter { it.filters.firstOrNull() == currentFilter }
        }
        
        ruleAdapter.submitList(filtered)
        binding.emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.rules.collect { rules ->
                        allRules = rules
                        updateStats(rules)
                        applyFilter()
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        viewModel.error.collect { error ->
                            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun updateStats(rules: List<GatewayRule>) {
        binding.totalCountText.text = rules.size.toString()
        binding.enabledCountText.text = rules.count { it.enabled }.toString()
        binding.disabledCountText.text = rules.count { !it.enabled }.toString()
    }

    private fun loadRules() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            return
        }
        viewModel.loadRules(account)
        viewModel.loadLists(account)
    }
    
    override fun onResume() {
        super.onResume()
        loadRules()
    }

    private fun showCreateRuleDialog(existingRule: GatewayRule? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_gateway_rule, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.ruleNameInput)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.ruleTypeSpinner)
        val actionSpinner = dialogView.findViewById<Spinner>(R.id.actionSpinner)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.precedenceInput)
        val enabledSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.enabledSwitch)
        
        val dnsFields = dialogView.findViewById<View>(R.id.dnsFields)
        val httpFields = dialogView.findViewById<View>(R.id.httpFields)
        val l4Fields = dialogView.findViewById<View>(R.id.l4Fields)
        val trafficPreview = dialogView.findViewById<View>(R.id.trafficPreview)
        val trafficExpressionText = dialogView.findViewById<TextView>(R.id.trafficExpressionText)

        val dnsMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)
        val dnsHostLayout = dialogView.findViewById<View>(R.id.dnsHostLayout)
        val dnsListSpinner = dialogView.findViewById<Spinner>(R.id.dnsListSpinner)
        val dnsListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)
        val dnsOperatorSpinner = dialogView.findViewById<Spinner>(R.id.dnsOperatorSpinner)

        val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
        val httpHostLayout = dialogView.findViewById<View>(R.id.httpHostLayout)
        val httpDomainListSpinner = dialogView.findViewById<Spinner>(R.id.httpDomainListSpinner)
        val httpUrlListSpinner = dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)
        val httpDomainListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)
        val httpUrlListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)
        val httpOperatorSpinner = dialogView.findViewById<Spinner>(R.id.httpOperatorSpinner)

        val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
        val l4SourceIpLayout = dialogView.findViewById<View>(R.id.l4SourceIpLayout)
        val l4ListSpinner = dialogView.findViewById<Spinner>(R.id.l4ListSpinner)
        val l4ListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)
        val l4OperatorSpinner = dialogView.findViewById<Spinner>(R.id.l4OperatorSpinner)

        val overrideFields = dialogView.findViewById<View>(R.id.overrideFields)
        val overrideTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.overrideTypeGroup)
        val overrideIpsLayout = dialogView.findViewById<View>(R.id.overrideIpsLayout)
        val overrideHostLayout = dialogView.findViewById<View>(R.id.overrideHostLayout)
        val overrideIpsInput = dialogView.findViewById<TextInputEditText>(R.id.overrideIpsInput)
        val overrideHostInput = dialogView.findViewById<TextInputEditText>(R.id.overrideHostInput)

        val templateBlockBtn = dialogView.findViewById<Button>(R.id.templateBlockBtn)
        val templateAllowBtn = dialogView.findViewById<Button>(R.id.templateAllowBtn)
        val templateSafeBtn = dialogView.findViewById<Button>(R.id.templateSafeBtn)

        val types = listOf("dns" to "域名规则", "http" to "网站规则", "l4" to "IP规则")
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        val actions = listOf(
            "allow" to "允许",
            "block" to "阻止",
            "safesearch" to "安全搜索",
            "ytrestricted" to "YouTube限制",
            "override" to "覆盖"
        )
        val actionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            actions.map { it.second }
        )
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        actionSpinner.adapter = actionAdapter

        fun updateTrafficPreview() {
            val ruleType = types[typeSpinner.selectedItemPosition].first
            val traffic = buildTrafficExpression(ruleType, dialogView)
            if (!traffic.isNullOrBlank()) {
                trafficExpressionText.text = traffic
                trafficPreview.visibility = View.VISIBLE
            } else {
                trafficPreview.visibility = View.GONE
            }
        }

        fun updateValueHint(layout: View?, operator: String, baseName: String) {
            val isIp = baseName.contains("IP", ignoreCase = true)
            val singleExample = if (isIp) "192.168.1.0/24" else "example.com"
            val multiExample = if (isIp) "192.168.1.1, 10.0.0.0/8" else "example.com, test.com"
            val regexExample = if (isIp) "192\\\\.168\\\\..*" else ".*\\\\.example\\\\.com"
            val hint = when (operator) {
                "in", "not_in" -> "多个值，逗号分隔 (如: $multiExample)"
                "matches", "not_matches" -> "正则表达式 (如: $regexExample)"
                else -> "$baseName (如: $singleExample)"
            }
            (layout as? com.google.android.material.textfield.TextInputLayout)?.hint = hint
        }

        val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
        val dnsListAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            domainLists.map { it.name }
        )
        dnsListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dnsListSpinner.adapter = dnsListAdapter

        val httpDomainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
        val httpDomainListAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            httpDomainLists.map { it.name }
        )
        httpDomainListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        httpDomainListSpinner?.adapter = httpDomainListAdapter

        val httpUrlLists = viewModel.lists.value.filter { it.type == "URL" }
        val httpUrlListAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            httpUrlLists.map { it.name }
        )
        httpUrlListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        httpUrlListSpinner?.adapter = httpUrlListAdapter

        val ipLists = viewModel.lists.value.filter { it.type == "IP" }
        val l4ListAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            ipLists.map { it.name }
        )
        l4ListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        l4ListSpinner.adapter = l4ListAdapter

        val dnsOperators = listOf(
            "default" to "默认(域名+子域名)",
            "is" to "is (等于)",
            "is_not" to "is not (不等于)",
            "in" to "in (在集合中)",
            "not_in" to "not in (不在集合中)",
            "matches" to "matches (匹配正则)",
            "not_matches" to "does not match (不匹配正则)"
        )
        val dnsOperatorAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            dnsOperators.map { it.second }
        )
        dnsOperatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dnsOperatorSpinner.adapter = dnsOperatorAdapter
        dnsOperatorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateValueHint(dnsHostLayout, dnsOperators[position].first, "域名")
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val httpOperators = listOf(
            "default" to "默认(主机+子域名)",
            "is" to "is (等于)",
            "is_not" to "is not (不等于)",
            "in" to "in (在集合中)",
            "not_in" to "not in (不在集合中)",
            "matches" to "matches (匹配正则)",
            "not_matches" to "does not match (不匹配正则)"
        )
        val httpOperatorAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            httpOperators.map { it.second }
        )
        httpOperatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        httpOperatorSpinner.adapter = httpOperatorAdapter
        httpOperatorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateValueHint(httpHostLayout, httpOperators[position].first, "主机名")
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val l4Operators = listOf(
            "is" to "is (等于)",
            "is_not" to "is not (不等于)",
            "in" to "in (在集合中)",
            "not_in" to "not in (不在集合中)"
        )
        val l4OperatorAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            l4Operators.map { it.second }
        )
        l4OperatorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        l4OperatorSpinner.adapter = l4OperatorAdapter
        l4OperatorSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateValueHint(l4SourceIpLayout, l4Operators[position].first, "IP/CIDR")
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        dnsMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.dnsMatchSingle) {
                dnsOperatorSpinner.visibility = View.VISIBLE
                dnsHostLayout.visibility = View.VISIBLE
                dnsListSpinner.visibility = View.GONE
                dnsListNotInCheckbox.visibility = View.GONE
            } else {
                dnsOperatorSpinner.visibility = View.GONE
                dnsHostLayout.visibility = View.GONE
                dnsListSpinner.visibility = View.VISIBLE
                dnsListNotInCheckbox.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        httpMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val httpPathLayout = dialogView.findViewById<View>(R.id.httpPathLayout)
            when (checkedId) {
                R.id.httpMatchSingle -> {
                    httpOperatorSpinner.visibility = View.VISIBLE
                    httpHostLayout?.visibility = View.VISIBLE
                    httpPathLayout?.visibility = View.VISIBLE
                    httpDomainListSpinner?.visibility = View.GONE
                    httpUrlListSpinner?.visibility = View.GONE
                    httpDomainListNotInCheckbox.visibility = View.GONE
                    httpUrlListNotInCheckbox.visibility = View.GONE
                }
                R.id.httpMatchDomainList -> {
                    httpOperatorSpinner.visibility = View.GONE
                    httpHostLayout?.visibility = View.GONE
                    httpPathLayout?.visibility = View.GONE
                    httpDomainListSpinner?.visibility = View.VISIBLE
                    httpUrlListSpinner?.visibility = View.GONE
                    httpDomainListNotInCheckbox.visibility = View.VISIBLE
                    httpUrlListNotInCheckbox.visibility = View.GONE
                }
                R.id.httpMatchUrlList -> {
                    httpOperatorSpinner.visibility = View.GONE
                    httpHostLayout?.visibility = View.GONE
                    httpPathLayout?.visibility = View.GONE
                    httpDomainListSpinner?.visibility = View.GONE
                    httpUrlListSpinner?.visibility = View.VISIBLE
                    httpDomainListNotInCheckbox.visibility = View.GONE
                    httpUrlListNotInCheckbox.visibility = View.VISIBLE
                }
            }
            updateTrafficPreview()
        }

        l4MatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val l4DestPortLayout = dialogView.findViewById<View>(R.id.l4DestPortLayout)
            if (checkedId == R.id.l4MatchSingle) {
                l4OperatorSpinner.visibility = View.VISIBLE
                l4SourceIpLayout?.visibility = View.VISIBLE
                l4DestPortLayout?.visibility = View.VISIBLE
                l4ListSpinner.visibility = View.GONE
                l4ListNotInCheckbox.visibility = View.GONE
            } else {
                l4OperatorSpinner.visibility = View.GONE
                l4SourceIpLayout?.visibility = View.GONE
                l4DestPortLayout?.visibility = View.GONE
                l4ListSpinner.visibility = View.VISIBLE
                l4ListNotInCheckbox.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                dnsFields.visibility = if (position == 0) View.VISIBLE else View.GONE
                httpFields.visibility = if (position == 1) View.VISIBLE else View.GONE
                l4Fields.visibility = if (position == 2) View.VISIBLE else View.GONE
                updateOverrideVisibility(typeSpinner, actionSpinner, overrideFields)
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        actionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateOverrideVisibility(typeSpinner, actionSpinner, overrideFields)
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        overrideTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.overrideIpList) {
                overrideIpsLayout.visibility = View.VISIBLE
                overrideHostLayout.visibility = View.GONE
            } else {
                overrideIpsLayout.visibility = View.GONE
                overrideHostLayout.visibility = View.VISIBLE
            }
        }

        dnsListNotInCheckbox.setOnCheckedChangeListener { _, _ -> updateTrafficPreview() }
        httpDomainListNotInCheckbox.setOnCheckedChangeListener { _, _ -> updateTrafficPreview() }
        httpUrlListNotInCheckbox.setOnCheckedChangeListener { _, _ -> updateTrafficPreview() }
        l4ListNotInCheckbox.setOnCheckedChangeListener { _, _ -> updateTrafficPreview() }

        dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.setOnTextChangedListener {
            updateTrafficPreview()
        }
        dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.setOnTextChangedListener {
            updateTrafficPreview()
        }
        dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.setOnTextChangedListener {
            updateTrafficPreview()
        }

        trafficExpressionText.setOnClickListener {
            val text = trafficExpressionText.text?.toString()
            if (!text.isNullOrBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("规则表达式", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }

        templateBlockBtn.setOnClickListener {
            typeSpinner.setSelection(0)
            actionSpinner.setSelection(1)
            nameInput.setText("阻止域名")
        }

        templateAllowBtn.setOnClickListener {
            typeSpinner.setSelection(0)
            actionSpinner.setSelection(0)
            nameInput.setText("允许域名")
        }

        templateSafeBtn.setOnClickListener {
            typeSpinner.setSelection(0)
            actionSpinner.setSelection(2)
            nameInput.setText("安全搜索")
        }

        existingRule?.let { rule ->
            nameInput.setText(rule.name)
            precedenceInput.setText(rule.precedence?.toString() ?: "0")
            enabledSwitch.isChecked = rule.enabled
            typeSpinner.isEnabled = false
            
            val ruleType = rule.filters.firstOrNull() ?: "dns"
            val typeIndex = types.indexOfFirst { it.first == ruleType }
            
            dnsFields.visibility = View.GONE
            httpFields.visibility = View.GONE
            l4Fields.visibility = View.GONE
            
            when (ruleType) {
                "dns" -> {
                    dnsFields.visibility = View.VISIBLE
                    rule.traffic?.let { traffic ->
                        val listId = extractListIdFromTraffic(traffic)
                        if (listId != null) {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchList)
                            val editDomainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                            val listIndex = editDomainLists.indexOfFirst { it.id == listId }
                            if (listIndex >= 0) {
                                dialogView.findViewById<Spinner>(R.id.dnsListSpinner)?.setSelection(listIndex)
                                dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                dnsListNotInCheckbox.visibility = View.VISIBLE
                            } else {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchSingle)
                            }
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchSingle)
                            val op = detectOperator(traffic)
                            val dnsOps = listOf("default", "is", "is_not", "in", "not_in", "matches", "not_matches")
                            val opIndex = dnsOps.indexOf(op).coerceAtLeast(0)
                            dnsOperatorSpinner.setSelection(opIndex)
                            val value = when (op) {
                                "in", "not_in" -> extractInlineValues(traffic)
                                else -> extractSingleValue(traffic, "dns.fqdn")
                            }
                            dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.setText(value)
                        }
                    }
                }
                "http" -> {
                    httpFields.visibility = View.VISIBLE
                    rule.traffic?.let { traffic ->
                        val listId = extractListIdFromTraffic(traffic)
                        if (listId != null) {
                            val editDomainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                            val domainListIndex = editDomainLists.indexOfFirst { it.id == listId }
                            if (domainListIndex >= 0) {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchDomainList)
                                dialogView.findViewById<Spinner>(R.id.httpDomainListSpinner)?.setSelection(domainListIndex)
                                dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                httpDomainListNotInCheckbox.visibility = View.VISIBLE
                            } else {
                                val editUrlLists = viewModel.lists.value.filter { it.type == "URL" }
                                val urlListIndex = editUrlLists.indexOfFirst { it.id == listId }
                                if (urlListIndex >= 0) {
                                    dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchUrlList)
                                    dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)?.setSelection(urlListIndex)
                                    dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                    httpUrlListNotInCheckbox.visibility = View.VISIBLE
                                } else {
                                    dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchSingle)
                                }
                            }
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchSingle)
                            val op = detectOperator(traffic)
                            val httpOps = listOf("default", "is", "is_not", "in", "not_in", "matches", "not_matches")
                            val opIndex = httpOps.indexOf(op).coerceAtLeast(0)
                            httpOperatorSpinner.setSelection(opIndex)
                            val value = when (op) {
                                "in", "not_in" -> extractInlineValues(traffic)
                                else -> extractSingleValue(traffic, "http.request.host")
                            }
                            dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.setText(value)
                            val path = extractPathFromTraffic(traffic)
                            dialogView.findViewById<TextInputEditText>(R.id.httpPathInput)?.setText(path ?: "")
                        }
                    }
                }
                "l4" -> {
                    l4Fields.visibility = View.VISIBLE
                    rule.traffic?.let { traffic ->
                        val listId = extractListIdFromTraffic(traffic)
                        if (listId != null) {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchList)
                            val editIpLists = viewModel.lists.value.filter { it.type == "IP" }
                            val listIndex = editIpLists.indexOfFirst { it.id == listId }
                            if (listIndex >= 0) {
                                dialogView.findViewById<Spinner>(R.id.l4ListSpinner)?.setSelection(listIndex)
                                dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                l4ListNotInCheckbox.visibility = View.VISIBLE
                            } else {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchSingle)
                            }
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchSingle)
                            val op = detectOperator(traffic)
                            val l4Ops = listOf("is", "is_not", "in", "not_in")
                            val opIndex = l4Ops.indexOf(op).coerceAtLeast(0)
                            l4OperatorSpinner.setSelection(opIndex)
                            val value = when (op) {
                                "in", "not_in" -> extractInlineValues(traffic)
                                else -> extractIpFromTraffic(traffic)
                            }
                            dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.setText(value)
                            val port = extractPortFromTraffic(traffic)
                            dialogView.findViewById<TextInputEditText>(R.id.l4DestPortInput)?.setText(port ?: "")
                        }
                    }
                }
            }
            
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
            
            val actionIndex = actions.indexOfFirst { it.first == rule.action }
            if (actionIndex >= 0) actionSpinner.setSelection(actionIndex)

            // Populate override fields if action is override
            if (rule.action == "override" && ruleType == "dns") {
                rule.ruleSettings?.let { settings ->
                    overrideFields.visibility = View.VISIBLE
                    if (!settings.overrideIps.isNullOrEmpty()) {
                        overrideTypeGroup.check(R.id.overrideIpList)
                        overrideIpsInput.setText(settings.overrideIps.joinToString("\n"))
                    } else if (!settings.overrideHost.isNullOrBlank()) {
                        overrideTypeGroup.check(R.id.overrideHostname)
                        overrideHostInput.setText(settings.overrideHost)
                    }
                }
            }
            
            updateTrafficPreview()
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
                val enabled = enabledSwitch.isChecked
                
                val traffic = buildTrafficExpression(ruleType, dialogView)

                val ruleSettings = if (action == "override" && ruleType == "dns") {
                    buildOverrideSettings(dialogView)
                } else {
                    existingRule?.ruleSettings
                }

                val request = GatewayRuleRequest(
                    name = name,
                    action = action,
                    enabled = enabled,
                    filters = listOf(ruleType),
                    traffic = traffic,
                    precedence = precedence,
                    ruleSettings = ruleSettings
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
    
    private fun formatInlineSet(input: String): String {
        return input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ") { "\"$it\"" }
    }

    private fun buildTrafficExpression(ruleType: String, dialogView: View): String? {
        return when (ruleType) {
            "dns" -> {
                val dnsMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)
                if (dnsMatchTypeGroup?.checkedRadioButtonId == R.id.dnsMatchList) {
                    val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                    val dnsListSpinner = dialogView.findViewById<Spinner>(R.id.dnsListSpinner)
                    val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)?.isChecked == true
                    val selectedIndex = dnsListSpinner?.selectedItemPosition ?: -1
                    if (selectedIndex >= 0 && selectedIndex < domainLists.size) {
                        val listId = domainLists[selectedIndex].id
                        val expr = "dns.fqdn in $${listId.trim()}"
                        if (notIn) "not($expr)" else expr
                    } else null
                } else {
                    val operators = listOf("default", "is", "is_not", "in", "not_in", "matches", "not_matches")
                    val operatorSpinner = dialogView.findViewById<Spinner>(R.id.dnsOperatorSpinner)
                    val operator = operators.getOrNull(operatorSpinner?.selectedItemPosition ?: 0) ?: "default"
                    val value = dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.text?.toString()
                    if (value.isNullOrBlank()) null
                    else when (operator) {
                        "default" -> "dns.fqdn == \"$value\" or dns.fqdn matches \".*\\.$value\""
                        "is" -> "dns.fqdn == \"$value\""
                        "is_not" -> "not(dns.fqdn == \"$value\")"
                        "in" -> "dns.fqdn in {${formatInlineSet(value)}}"
                        "not_in" -> "not(dns.fqdn in {${formatInlineSet(value)}})"
                        "matches" -> "dns.fqdn matches \"$value\""
                        "not_matches" -> "not(dns.fqdn matches \"$value\")"
                        else -> null
                    }
                }
            }
            "http" -> {
                val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
                when (httpMatchTypeGroup?.checkedRadioButtonId) {
                    R.id.httpMatchDomainList -> {
                        val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                        val httpDomainListSpinner = dialogView.findViewById<Spinner>(R.id.httpDomainListSpinner)
                        val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)?.isChecked == true
                        val selectedIndex = httpDomainListSpinner?.selectedItemPosition ?: -1
                        if (selectedIndex >= 0 && selectedIndex < domainLists.size) {
                            val listId = domainLists[selectedIndex].id
                            val expr = "http.request.host in $${listId.trim()}"
                            if (notIn) "not($expr)" else expr
                        } else null
                    }
                    R.id.httpMatchUrlList -> {
                        val urlLists = viewModel.lists.value.filter { it.type == "URL" }
                        val httpUrlListSpinner = dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)
                        val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)?.isChecked == true
                        val selectedIndex = httpUrlListSpinner?.selectedItemPosition ?: -1
                        if (selectedIndex >= 0 && selectedIndex < urlLists.size) {
                            val listId = urlLists[selectedIndex].id
                            val expr = "http.request.uri in $${listId.trim()}"
                            if (notIn) "not($expr)" else expr
                        } else null
                    }
                    else -> {
                        val operators = listOf("default", "is", "is_not", "in", "not_in", "matches", "not_matches")
                        val operatorSpinner = dialogView.findViewById<Spinner>(R.id.httpOperatorSpinner)
                        val operator = operators.getOrNull(operatorSpinner?.selectedItemPosition ?: 0) ?: "default"
                        val host = dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.text?.toString()
                        val path = dialogView.findViewById<TextInputEditText>(R.id.httpPathInput)?.text?.toString()
                        if (host.isNullOrBlank()) null
                        else {
                            val hostPart = when (operator) {
                                "default" -> "http.request.host == \"$host\" or http.request.host matches \".*\\.$host\""
                                "is" -> "http.request.host == \"$host\""
                                "is_not" -> "not(http.request.host == \"$host\")"
                                "in" -> "http.request.host in {${formatInlineSet(host)}}"
                                "not_in" -> "not(http.request.host in {${formatInlineSet(host)}})"
                                "matches" -> "http.request.host matches \"$host\""
                                "not_matches" -> "not(http.request.host matches \"$host\")"
                                else -> null
                            }
                            if (hostPart != null && !path.isNullOrBlank()) {
                                "$hostPart and http.request.uri.path matches \"$path\""
                            } else {
                                hostPart
                            }
                        }
                    }
                }
            }
            "l4" -> {
                val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
                if (l4MatchTypeGroup?.checkedRadioButtonId == R.id.l4MatchList) {
                    val ipLists = viewModel.lists.value.filter { it.type == "IP" }
                    val l4ListSpinner = dialogView.findViewById<Spinner>(R.id.l4ListSpinner)
                    val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)?.isChecked == true
                    val selectedIndex = l4ListSpinner?.selectedItemPosition ?: -1
                    if (selectedIndex >= 0 && selectedIndex < ipLists.size) {
                        val listId = ipLists[selectedIndex].id
                        val expr = "net.dst.ip in $${listId.trim()}"
                        if (notIn) "not($expr)" else expr
                    } else null
                } else {
                    val operators = listOf("is", "is_not", "in", "not_in")
                    val operatorSpinner = dialogView.findViewById<Spinner>(R.id.l4OperatorSpinner)
                    val operator = operators.getOrNull(operatorSpinner?.selectedItemPosition ?: 0) ?: "is"
                    val sourceIp = dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.text?.toString()
                    val port = dialogView.findViewById<TextInputEditText>(R.id.l4DestPortInput)?.text?.toString()
                    if (sourceIp.isNullOrBlank()) null
                    else {
                        val ipPart = when (operator) {
                            "is" -> "net.src.ip == $sourceIp"
                            "is_not" -> "not(net.src.ip == $sourceIp)"
                            "in" -> "net.src.ip in {${formatInlineSet(sourceIp)}}"
                            "not_in" -> "not(net.src.ip in {${formatInlineSet(sourceIp)}})"
                            else -> null
                        }
                        if (ipPart != null && !port.isNullOrBlank()) {
                            "$ipPart and net.dst.port == $port"
                        } else {
                            ipPart
                        }
                    }
                }
            }
            else -> null
        }
    }

    private fun buildOverrideSettings(dialogView: View): GatewayRuleSettings {
        val overrideTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.overrideTypeGroup)
        return if (overrideTypeGroup?.checkedRadioButtonId == R.id.overrideIpList) {
            val ipsText = dialogView.findViewById<TextInputEditText>(R.id.overrideIpsInput)?.text?.toString() ?: ""
            val ips = ipsText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            GatewayRuleSettings(overrideIps = ips)
        } else {
            val host = dialogView.findViewById<TextInputEditText>(R.id.overrideHostInput)?.text?.toString()?.trim()
            GatewayRuleSettings(overrideHost = host?.takeIf { it.isNotEmpty() })
        }
    }

    private fun updateOverrideVisibility(
        typeSpinner: Spinner,
        actionSpinner: Spinner,
        overrideFields: View
    ) {
        val types = listOf("dns", "http", "l4")
        val actions = listOf("allow", "block", "safesearch", "ytrestricted", "override")
        val ruleType = types.getOrNull(typeSpinner.selectedItemPosition) ?: "dns"
        val action = actions.getOrNull(actionSpinner.selectedItemPosition) ?: "allow"
        overrideFields.visibility = if (action == "override" && ruleType == "dns") View.VISIBLE else View.GONE
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

fun TextInputEditText.setOnTextChangedListener(callback: () -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            callback()
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}

private fun extractPathFromTraffic(traffic: String): String? {
    val regex = Regex("http\\.request\\.uri\\.path\\s*(==|matches)\\s*\"([^\"]+)\"")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2)
}

private fun extractIpFromTraffic(traffic: String): String {
    val regex = Regex("net\\.src\\.ip\\s*(==|!=)\\s*([\\d./a-fA-F:]+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2) ?: ""
}

private fun extractPortFromTraffic(traffic: String): String? {
    val regex = Regex("net\\.dst\\.port\\s*==\\s*(\\d+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(1)
}

private fun extractListIdFromTraffic(traffic: String): String? {
    val regex = Regex("\\b(?:not )?in\\s+\\$([a-fA-F0-9-]+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(1)
}

private fun isNotInList(traffic: String): Boolean {
    val trimmed = traffic.trimStart()
    return trimmed.startsWith("not") && trimmed.contains(" in $")
}

private fun detectOperator(traffic: String): String {
    val trimmed = traffic.trim()
    return when {
        trimmed.contains(" or ") && trimmed.contains("==") && trimmed.contains("matches") -> "default"
        trimmed.startsWith("not") && trimmed.contains(" in {") -> "not_in"
        trimmed.startsWith("not") && trimmed.contains("matches") -> "not_matches"
        trimmed.startsWith("not") && trimmed.contains("==") -> "is_not"
        trimmed.contains("!=") -> "is_not"
        trimmed.contains(" in {") -> "in"
        trimmed.contains("matches") -> "matches"
        trimmed.contains("==") -> "is"
        else -> "default"
    }
}

private fun extractSingleValue(traffic: String, field: String): String {
    val eqRegex = Regex("${Regex.escape(field)}\\s*(==|!=)\\s*\"([^\"]+)\"")
    val eqMatch = eqRegex.find(traffic)
    if (eqMatch != null) return eqMatch.groupValues[2]
    val matchRegex = Regex("${Regex.escape(field)}\\s*matches\\s*\"([^\"]+)\"")
    val matchMatch = matchRegex.find(traffic)
    return matchMatch?.groupValues?.get(1) ?: ""
}

private fun extractInlineValues(traffic: String): String {
    val regex = Regex("\\{([^}]+)\\}")
    val match = regex.find(traffic) ?: return ""
    return match.groupValues[1].split(Regex("\\s+"))
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
}