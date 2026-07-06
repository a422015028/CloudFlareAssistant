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

        val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
        val httpDomainListSpinner = dialogView.findViewById<Spinner>(R.id.httpDomainListSpinner)
        val httpUrlListSpinner = dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)

        val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
        val l4ListSpinner = dialogView.findViewById<Spinner>(R.id.l4ListSpinner)

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
            "isolate" to "隔离",
            "noscan" to "不扫描"
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

        dnsMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.dnsMatchSingle) {
                dnsHostLayout.visibility = View.VISIBLE
                dnsListSpinner.visibility = View.GONE
            } else {
                dnsHostLayout.visibility = View.GONE
                dnsListSpinner.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        httpMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val httpHostLayout = dialogView.findViewById<View>(R.id.httpHostLayout)
            val httpPathLayout = dialogView.findViewById<View>(R.id.httpPathLayout)
            when (checkedId) {
                R.id.httpMatchSingle -> {
                    httpHostLayout?.visibility = View.VISIBLE
                    httpPathLayout?.visibility = View.VISIBLE
                    httpDomainListSpinner?.visibility = View.GONE
                    httpUrlListSpinner?.visibility = View.GONE
                }
                R.id.httpMatchDomainList -> {
                    httpHostLayout?.visibility = View.GONE
                    httpPathLayout?.visibility = View.GONE
                    httpDomainListSpinner?.visibility = View.VISIBLE
                    httpUrlListSpinner?.visibility = View.GONE
                }
                R.id.httpMatchUrlList -> {
                    httpHostLayout?.visibility = View.GONE
                    httpPathLayout?.visibility = View.GONE
                    httpDomainListSpinner?.visibility = View.GONE
                    httpUrlListSpinner?.visibility = View.VISIBLE
                }
            }
            updateTrafficPreview()
        }

        l4MatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            val l4SourceIpLayout = dialogView.findViewById<View>(R.id.l4SourceIpLayout)
            val l4DestPortLayout = dialogView.findViewById<View>(R.id.l4DestPortLayout)
            if (checkedId == R.id.l4MatchSingle) {
                l4SourceIpLayout?.visibility = View.VISIBLE
                l4DestPortLayout?.visibility = View.VISIBLE
                l4ListSpinner.visibility = View.GONE
            } else {
                l4SourceIpLayout?.visibility = View.GONE
                l4DestPortLayout?.visibility = View.GONE
                l4ListSpinner.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                dnsFields.visibility = if (position == 0) View.VISIBLE else View.GONE
                httpFields.visibility = if (position == 1) View.VISIBLE else View.GONE
                l4Fields.visibility = if (position == 2) View.VISIBLE else View.GONE
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

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
                            } else {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchSingle)
                            }
                        } else {
                            val domain = extractDomainFromTraffic(traffic)
                            dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.setText(domain)
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
                            } else {
                                val editUrlLists = viewModel.lists.value.filter { it.type == "URL" }
                                val urlListIndex = editUrlLists.indexOfFirst { it.id == listId }
                                if (urlListIndex >= 0) {
                                    dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchUrlList)
                                    dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)?.setSelection(urlListIndex)
                                } else {
                                    dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchSingle)
                                }
                            }
                        } else {
                            val host = extractHostFromTraffic(traffic)
                            val path = extractPathFromTraffic(traffic)
                            dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.setText(host)
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
                            } else {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchSingle)
                            }
                        } else {
                            val ip = extractIpFromTraffic(traffic)
                            val port = extractPortFromTraffic(traffic)
                            dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.setText(ip)
                            dialogView.findViewById<TextInputEditText>(R.id.l4DestPortInput)?.setText(port ?: "")
                        }
                    }
                }
            }
            
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
            
            val actionIndex = actions.indexOfFirst { it.first == rule.action }
            if (actionIndex >= 0) actionSpinner.setSelection(actionIndex)
            
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
                
                val request = GatewayRuleRequest(
                    name = name,
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
                val dnsMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)
                if (dnsMatchTypeGroup?.checkedRadioButtonId == R.id.dnsMatchList) {
                    val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                    val dnsListSpinner = dialogView.findViewById<Spinner>(R.id.dnsListSpinner)
                    val selectedIndex = dnsListSpinner?.selectedItemPosition ?: -1
                    if (selectedIndex >= 0 && selectedIndex < domainLists.size) {
                        val listId = domainLists[selectedIndex].id
                        "dns.fqdn in $${listId.trim()}"
                    } else null
                } else {
                    val host = dialogView.findViewById<TextInputEditText>(R.id.dnsHostInput)?.text?.toString()
                    if (!host.isNullOrBlank()) {
                        "dns.fqdn == \"$host\" or dns.fqdn matches \".*\\.$host\""
                    } else null
                }
            }
            "http" -> {
                val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
                when (httpMatchTypeGroup?.checkedRadioButtonId) {
                    R.id.httpMatchDomainList -> {
                        val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                        val httpDomainListSpinner = dialogView.findViewById<Spinner>(R.id.httpDomainListSpinner)
                        val selectedIndex = httpDomainListSpinner?.selectedItemPosition ?: -1
                        if (selectedIndex >= 0 && selectedIndex < domainLists.size) {
                            val listId = domainLists[selectedIndex].id
                            "http.request.host in $${listId.trim()}"
                        } else null
                    }
                    R.id.httpMatchUrlList -> {
                        val urlLists = viewModel.lists.value.filter { it.type == "URL" }
                        val httpUrlListSpinner = dialogView.findViewById<Spinner>(R.id.httpUrlListSpinner)
                        val selectedIndex = httpUrlListSpinner?.selectedItemPosition ?: -1
                        if (selectedIndex >= 0 && selectedIndex < urlLists.size) {
                            val listId = urlLists[selectedIndex].id
                            "http.request.uri in $${listId.trim()}"
                        } else null
                    }
                    else -> {
                        val host = dialogView.findViewById<TextInputEditText>(R.id.httpHostInput)?.text?.toString()
                        val path = dialogView.findViewById<TextInputEditText>(R.id.httpPathInput)?.text?.toString()
                        if (!host.isNullOrBlank()) {
                            val hostPart = "http.request.host == \"$host\" or http.request.host matches \".*\\.$host\""
                            if (!path.isNullOrBlank()) {
                                "$hostPart and http.request.uri.path matches \"$path\""
                            } else {
                                hostPart
                            }
                        } else null
                    }
                }
            }
            "l4" -> {
                val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
                if (l4MatchTypeGroup?.checkedRadioButtonId == R.id.l4MatchList) {
                    val ipLists = viewModel.lists.value.filter { it.type == "IP" }
                    val l4ListSpinner = dialogView.findViewById<Spinner>(R.id.l4ListSpinner)
                    val selectedIndex = l4ListSpinner?.selectedItemPosition ?: -1
                    if (selectedIndex >= 0 && selectedIndex < ipLists.size) {
                        val listId = ipLists[selectedIndex].id
                        "net.dst.ip in $${listId.trim()}"
                    } else null
                } else {
                    val sourceIp = dialogView.findViewById<TextInputEditText>(R.id.l4SourceIpInput)?.text?.toString()
                    val port = dialogView.findViewById<TextInputEditText>(R.id.l4DestPortInput)?.text?.toString()
                    if (!sourceIp.isNullOrBlank()) {
                        val ipPart = "net.src.ip == $sourceIp"
                        if (!port.isNullOrBlank()) {
                            "$ipPart and net.dst.port == $port"
                        } else {
                            ipPart
                        }
                    } else null
                }
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

private fun extractDomainFromTraffic(traffic: String): String {
    val regex = Regex("dns\\.fqdn\\s*(==|matches)\\s*\"([^\"]+)\"")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2) ?: ""
}

private fun extractHostFromTraffic(traffic: String): String {
    val regex = Regex("http\\.request\\.host\\s*(==|matches)\\s*\"([^\"]+)\"")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2) ?: ""
}

private fun extractPathFromTraffic(traffic: String): String? {
    val regex = Regex("http\\.request\\.uri\\.path\\s*(==|matches)\\s*\"([^\"]+)\"")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2)
}

private fun extractIpFromTraffic(traffic: String): String {
    val regex = Regex("net\\.src\\.ip\\s*==\\s*([\\d./]+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(1) ?: ""
}

private fun extractPortFromTraffic(traffic: String): String? {
    val regex = Regex("net\\.dst\\.port\\s*==\\s*(\\d+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(1)
}

private fun extractListIdFromTraffic(traffic: String): String? {
    val regex = Regex("\\b(in)\\s+\\$(\\S+)")
    val match = regex.find(traffic)
    return match?.groupValues?.get(2)
}