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
import com.muort.upworker.core.model.GatewayRedirect
import com.muort.upworker.core.model.L4Override
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
        val dnsExpressionLayout = dialogView.findViewById<View>(R.id.dnsExpressionLayout)
        val dnsListSelector = dialogView.findViewById<TextView>(R.id.dnsListSelector)
        val dnsListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)
        val selectedDnsListIds = mutableSetOf<String>()
        dnsListSelector.tag = selectedDnsListIds

        val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
        val httpExpressionLayout = dialogView.findViewById<View>(R.id.httpExpressionLayout)
        val httpDomainListSelector = dialogView.findViewById<TextView>(R.id.httpDomainListSelector)
        val httpUrlListSelector = dialogView.findViewById<TextView>(R.id.httpUrlListSelector)
        val httpDomainListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)
        val httpUrlListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)
        val selectedHttpDomainListIds = mutableSetOf<String>()
        httpDomainListSelector.tag = selectedHttpDomainListIds
        val selectedHttpUrlListIds = mutableSetOf<String>()
        httpUrlListSelector.tag = selectedHttpUrlListIds

        val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
        val l4ExpressionLayout = dialogView.findViewById<View>(R.id.l4ExpressionLayout)
        val l4ListSelector = dialogView.findViewById<TextView>(R.id.l4ListSelector)
        val l4ListNotInCheckbox = dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)
        val selectedL4ListIds = mutableSetOf<String>()
        l4ListSelector.tag = selectedL4ListIds

        val overrideFields = dialogView.findViewById<View>(R.id.overrideFields)
        val overrideTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.overrideTypeGroup)
        val overrideIpsLayout = dialogView.findViewById<View>(R.id.overrideIpsLayout)
        val overrideHostLayout = dialogView.findViewById<View>(R.id.overrideHostLayout)
        val overrideIpsInput = dialogView.findViewById<TextInputEditText>(R.id.overrideIpsInput)
        val overrideHostInput = dialogView.findViewById<TextInputEditText>(R.id.overrideHostInput)

        val redirectUrlLayout = dialogView.findViewById<View>(R.id.redirectUrlLayout)
        val redirectUrlInput = dialogView.findViewById<TextInputEditText>(R.id.redirectUrlInput)

        val l4OverrideIpLayout = dialogView.findViewById<View>(R.id.l4OverrideIpLayout)
        val l4OverrideIpInput = dialogView.findViewById<TextInputEditText>(R.id.l4OverrideIpInput)
        val l4OverridePortLayout = dialogView.findViewById<View>(R.id.l4OverridePortLayout)
        val l4OverridePortInput = dialogView.findViewById<TextInputEditText>(R.id.l4OverridePortInput)

        val templateBlockBtn = dialogView.findViewById<Button>(R.id.templateBlockBtn)
        val templateAllowBtn = dialogView.findViewById<Button>(R.id.templateAllowBtn)
        val templateSafeBtn = dialogView.findViewById<Button>(R.id.templateSafeBtn)

        val types = listOf("dns" to "DNS", "http" to "HTTP", "l4" to "网络")
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        val dnsActions = listOf(
            "allow" to "允许",
            "block" to "阻止",
            "safesearch" to "安全搜索",
            "ytrestricted" to "YouTube限制",
            "override" to "覆盖"
        )
        val httpActions = listOf(
            "allow" to "允许",
            "redirect" to "重定向",
            "block" to "阻止",
            "off" to "不检查",
            "noscan" to "不扫描"
        )
        val l4Actions = listOf(
            "allow" to "允许",
            "block" to "阻止",
            "l4_override" to "覆盖"
        )

        fun currentActions(): List<Pair<String, String>> = when (types[typeSpinner.selectedItemPosition].first) {
            "http" -> httpActions
            "l4" -> l4Actions
            else -> dnsActions
        }

        // 云端动作 → 中文标签映射（涵盖 Cloudflare Gateway 所有可能的动作值）
        fun actionLabel(action: String): String = when (action) {
            "allow" -> "允许"
            "block" -> "阻止"
            "safesearch" -> "安全搜索"
            "ytrestricted" -> "YouTube限制"
            "override" -> "覆盖"
            "redirect" -> "重定向"
            "off" -> "不检查"
            "noscan" -> "不扫描"
            "on" -> "开启"
            "scan" -> "扫描"
            "isolate" -> "隔离"
            "noisolate" -> "不隔离"
            "l4_override" -> "L4覆盖"
            "egress" -> "出口"
            "audit_ssh" -> "SSH审计"
            else -> action
        }

        // 编辑模式下，若云端动作不在当前类型的支持列表中，追加一项用于显示并保留原值
        fun effectiveActions(): List<Pair<String, String>> {
            val base = currentActions().toMutableList()
            existingRule?.let { rule ->
                if (base.none { it.first == rule.action }) {
                    base.add(rule.action to "${actionLabel(rule.action)} (云端)")
                }
            }
            return base
        }

        val actionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            effectiveActions().map { it.second }
        )
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        actionSpinner.adapter = actionAdapter

        fun updateActionSpinner() {
            val newActions = effectiveActions()
            actionAdapter.clear()
            actionAdapter.addAll(newActions.map { it.second })
            actionAdapter.notifyDataSetChanged()
        }

        fun updateTrafficPreview() {
            val ruleType = types[typeSpinner.selectedItemPosition].first
            val isSingleMode = when (ruleType) {
                "dns" -> dnsMatchTypeGroup.checkedRadioButtonId == R.id.dnsMatchSingle
                "http" -> httpMatchTypeGroup.checkedRadioButtonId == R.id.httpMatchSingle
                "l4" -> l4MatchTypeGroup.checkedRadioButtonId == R.id.l4MatchSingle
                else -> false
            }
            if (isSingleMode) {
                trafficPreview.visibility = View.GONE
                return
            }
            val traffic = buildTrafficExpression(ruleType, dialogView)
            if (!traffic.isNullOrBlank()) {
                trafficExpressionText.text = traffic
                trafficPreview.visibility = View.VISIBLE
            } else {
                trafficPreview.visibility = View.GONE
            }
        }

        val domainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
        val httpDomainLists = domainLists
        val httpUrlLists = viewModel.lists.value.filter { it.type == "URL" }
        val ipLists = viewModel.lists.value.filter { it.type == "IP" }

        fun updateListSelectorText(selector: TextView, selectedIds: Set<String>, lists: List<com.muort.upworker.core.model.GatewayList>) {
            val names = lists.filter { selectedIds.contains(it.id) }.map { it.name }
            selector.text = if (names.isEmpty()) "点击选择列表" else names.joinToString(", ")
        }

        fun showMultiListDialog(
            title: String,
            lists: List<com.muort.upworker.core.model.GatewayList>,
            selectedIds: MutableSet<String>,
            selector: TextView,
            onUpdate: () -> Unit
        ) {
            val items = lists.map { it.name }.toTypedArray()
            val checkedItems = BooleanArray(lists.size) { i -> selectedIds.contains(lists[i].id) }
            android.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                    if (isChecked) selectedIds.add(lists[which].id)
                    else selectedIds.remove(lists[which].id)
                }
                .setPositiveButton("确定") { _, _ ->
                    updateListSelectorText(selector, selectedIds, lists)
                    onUpdate()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dnsListSelector.setOnClickListener {
            showMultiListDialog("选择域名列表", domainLists, selectedDnsListIds, dnsListSelector) { updateTrafficPreview() }
        }
        httpDomainListSelector.setOnClickListener {
            showMultiListDialog("选择域名列表", httpDomainLists, selectedHttpDomainListIds, httpDomainListSelector) { updateTrafficPreview() }
        }
        httpUrlListSelector.setOnClickListener {
            showMultiListDialog("选择URL列表", httpUrlLists, selectedHttpUrlListIds, httpUrlListSelector) { updateTrafficPreview() }
        }
        l4ListSelector.setOnClickListener {
            showMultiListDialog("选择IP列表", ipLists, selectedL4ListIds, l4ListSelector) { updateTrafficPreview() }
        }

        dnsMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.dnsMatchSingle) {
                dnsExpressionLayout.visibility = View.VISIBLE
                dnsListSelector.visibility = View.GONE
                dnsListNotInCheckbox.visibility = View.GONE
            } else {
                dnsExpressionLayout.visibility = View.GONE
                dnsListSelector.visibility = View.VISIBLE
                dnsListNotInCheckbox.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        httpMatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.httpMatchSingle -> {
                    httpExpressionLayout.visibility = View.VISIBLE
                    httpDomainListSelector.visibility = View.GONE
                    httpUrlListSelector.visibility = View.GONE
                    httpDomainListNotInCheckbox.visibility = View.GONE
                    httpUrlListNotInCheckbox.visibility = View.GONE
                }
                R.id.httpMatchDomainList -> {
                    httpExpressionLayout.visibility = View.GONE
                    httpDomainListSelector.visibility = View.VISIBLE
                    httpUrlListSelector.visibility = View.GONE
                    httpDomainListNotInCheckbox.visibility = View.VISIBLE
                    httpUrlListNotInCheckbox.visibility = View.GONE
                }
                R.id.httpMatchUrlList -> {
                    httpExpressionLayout.visibility = View.GONE
                    httpDomainListSelector.visibility = View.GONE
                    httpUrlListSelector.visibility = View.VISIBLE
                    httpDomainListNotInCheckbox.visibility = View.GONE
                    httpUrlListNotInCheckbox.visibility = View.VISIBLE
                }
            }
            updateTrafficPreview()
        }

        l4MatchTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.l4MatchSingle) {
                l4ExpressionLayout.visibility = View.VISIBLE
                l4ListSelector.visibility = View.GONE
                l4ListNotInCheckbox.visibility = View.GONE
            } else {
                l4ExpressionLayout.visibility = View.GONE
                l4ListSelector.visibility = View.VISIBLE
                l4ListNotInCheckbox.visibility = View.VISIBLE
            }
            updateTrafficPreview()
        }

        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                dnsFields.visibility = if (position == 0) View.VISIBLE else View.GONE
                httpFields.visibility = if (position == 1) View.VISIBLE else View.GONE
                l4Fields.visibility = if (position == 2) View.VISIBLE else View.GONE
                updateActionSpinner()
                // 编辑模式下保留云端动作选中项；新建模式默认选第一项
                existingRule?.let { rule ->
                    val idx = effectiveActions().indexOfFirst { it.first == rule.action }
                    actionSpinner.setSelection(if (idx >= 0) idx else 0)
                } ?: actionSpinner.setSelection(0)
                updateOverrideVisibility(typeSpinner, actionSpinner, overrideFields, redirectUrlLayout, l4OverrideIpLayout, l4OverridePortLayout)
                updateTrafficPreview()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        actionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateOverrideVisibility(typeSpinner, actionSpinner, overrideFields, redirectUrlLayout, l4OverrideIpLayout, l4OverridePortLayout)
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

        dialogView.findViewById<TextInputEditText>(R.id.dnsExpressionInput)?.setOnTextChangedListener {
            updateTrafficPreview()
        }
        dialogView.findViewById<TextInputEditText>(R.id.httpExpressionInput)?.setOnTextChangedListener {
            updateTrafficPreview()
        }
        dialogView.findViewById<TextInputEditText>(R.id.l4ExpressionInput)?.setOnTextChangedListener {
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
                        val listIds = extractAllListIds(traffic)
                        if (listIds.isNotEmpty()) {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchList)
                            val editDomainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                            selectedDnsListIds.clear()
                            selectedDnsListIds.addAll(listIds)
                            updateListSelectorText(dnsListSelector, selectedDnsListIds, editDomainLists)
                            dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)?.isChecked = isNotInList(traffic)
                            dnsListNotInCheckbox.visibility = View.VISIBLE
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)?.check(R.id.dnsMatchSingle)
                            dialogView.findViewById<TextInputEditText>(R.id.dnsExpressionInput)?.setText(traffic)
                        }
                    }
                }
                "http" -> {
                    httpFields.visibility = View.VISIBLE
                    rule.traffic?.let { traffic ->
                        val listIds = extractAllListIds(traffic)
                        if (listIds.isNotEmpty()) {
                            val isUrlList = traffic.contains("http.request.uri")
                            if (isUrlList) {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchUrlList)
                                val editUrlLists = viewModel.lists.value.filter { it.type == "URL" }
                                selectedHttpUrlListIds.clear()
                                selectedHttpUrlListIds.addAll(listIds)
                                updateListSelectorText(httpUrlListSelector, selectedHttpUrlListIds, editUrlLists)
                                dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                httpUrlListNotInCheckbox.visibility = View.VISIBLE
                            } else {
                                dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchDomainList)
                                val editDomainLists = viewModel.lists.value.filter { it.type == "DOMAIN" }
                                selectedHttpDomainListIds.clear()
                                selectedHttpDomainListIds.addAll(listIds)
                                updateListSelectorText(httpDomainListSelector, selectedHttpDomainListIds, editDomainLists)
                                dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)?.isChecked = isNotInList(traffic)
                                httpDomainListNotInCheckbox.visibility = View.VISIBLE
                            }
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)?.check(R.id.httpMatchSingle)
                            dialogView.findViewById<TextInputEditText>(R.id.httpExpressionInput)?.setText(traffic)
                        }
                    }
                }
                "l4" -> {
                    l4Fields.visibility = View.VISIBLE
                    rule.traffic?.let { traffic ->
                        val listIds = extractAllListIds(traffic)
                        if (listIds.isNotEmpty()) {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchList)
                            val editIpLists = viewModel.lists.value.filter { it.type == "IP" }
                            selectedL4ListIds.clear()
                            selectedL4ListIds.addAll(listIds)
                            updateListSelectorText(l4ListSelector, selectedL4ListIds, editIpLists)
                            dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)?.isChecked = isNotInList(traffic)
                            l4ListNotInCheckbox.visibility = View.VISIBLE
                        } else {
                            dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)?.check(R.id.l4MatchSingle)
                            dialogView.findViewById<TextInputEditText>(R.id.l4ExpressionInput)?.setText(traffic)
                        }
                    }
                }
            }
            
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
            
            val actionIndex = effectiveActions().indexOfFirst { it.first == rule.action }
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

            // Populate redirect URL if action is redirect
            if (rule.action == "redirect" && ruleType == "http") {
                rule.ruleSettings?.redirect?.targetUri?.let { uri ->
                    redirectUrlLayout.visibility = View.VISIBLE
                    redirectUrlInput.setText(uri)
                }
            }

            // Populate l4 override IP if action is l4_override
            if (rule.action == "l4_override" && ruleType == "l4") {
                rule.ruleSettings?.l4Override?.let { l4 ->
                    l4OverrideIpLayout.visibility = View.VISIBLE
                    l4OverridePortLayout.visibility = View.VISIBLE
                    l4.ip?.let { l4OverrideIpInput.setText(it) }
                    l4.port?.let { l4OverridePortInput.setText(it.toString()) }
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
                val action = effectiveActions()[actionSpinner.selectedItemPosition].first
                val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 0
                val enabled = enabledSwitch.isChecked
                
                val traffic = buildTrafficExpression(ruleType, dialogView)

                val ruleSettings = when {
                    action == "override" && ruleType == "dns" -> buildOverrideSettings(dialogView)
                    action == "redirect" && ruleType == "http" -> {
                        val uri = redirectUrlInput.text?.toString()?.trim()
                        if (!uri.isNullOrEmpty()) {
                            GatewayRuleSettings(redirect = GatewayRedirect(targetUri = uri))
                        } else {
                            existingRule?.ruleSettings
                        }
                    }
                    action == "l4_override" && ruleType == "l4" -> {
                        val ip = l4OverrideIpInput.text?.toString()?.trim()
                        val port = l4OverridePortInput.text?.toString()?.trim()?.toIntOrNull()
                        if (!ip.isNullOrEmpty() || port != null) {
                            GatewayRuleSettings(l4Override = L4Override(ip = ip?.takeIf { it.isNotEmpty() }, port = port))
                        } else {
                            existingRule?.ruleSettings
                        }
                    }
                    else -> existingRule?.ruleSettings
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

    private fun buildListExpression(field: String, selectedIds: Set<String>, notIn: Boolean): String? {
        if (selectedIds.isEmpty()) return null
        val expr = selectedIds.joinToString(" or ") { "$field in $${it.trim()}" }
        return if (notIn) "not($expr)" else expr
    }

    private fun getSelectedIds(dialogView: View, selectorId: Int): Set<String> {
        val selector = dialogView.findViewById<TextView>(selectorId)
        return (selector?.tag as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
    }

    private fun buildTrafficExpression(ruleType: String, dialogView: View): String? {
        return when (ruleType) {
            "dns" -> {
                val dnsMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.dnsMatchTypeGroup)
                if (dnsMatchTypeGroup?.checkedRadioButtonId == R.id.dnsMatchList) {
                    val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.dnsListNotInCheckbox)?.isChecked == true
                    buildListExpression("dns.fqdn", getSelectedIds(dialogView, R.id.dnsListSelector), notIn)
                } else {
                    val expr = dialogView.findViewById<TextInputEditText>(R.id.dnsExpressionInput)?.text?.toString()
                    expr?.takeIf { it.isNotBlank() }
                }
            }
            "http" -> {
                val httpMatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.httpMatchTypeGroup)
                when (httpMatchTypeGroup?.checkedRadioButtonId) {
                    R.id.httpMatchDomainList -> {
                        val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.httpDomainListNotInCheckbox)?.isChecked == true
                        buildListExpression("http.request.host", getSelectedIds(dialogView, R.id.httpDomainListSelector), notIn)
                    }
                    R.id.httpMatchUrlList -> {
                        val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.httpUrlListNotInCheckbox)?.isChecked == true
                        buildListExpression("http.request.uri", getSelectedIds(dialogView, R.id.httpUrlListSelector), notIn)
                    }
                    else -> {
                        val expr = dialogView.findViewById<TextInputEditText>(R.id.httpExpressionInput)?.text?.toString()
                        expr?.takeIf { it.isNotBlank() }
                    }
                }
            }
            "l4" -> {
                val l4MatchTypeGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.l4MatchTypeGroup)
                if (l4MatchTypeGroup?.checkedRadioButtonId == R.id.l4MatchList) {
                    val notIn = dialogView.findViewById<android.widget.CheckBox>(R.id.l4ListNotInCheckbox)?.isChecked == true
                    buildListExpression("net.dst.ip", getSelectedIds(dialogView, R.id.l4ListSelector), notIn)
                } else {
                    val expr = dialogView.findViewById<TextInputEditText>(R.id.l4ExpressionInput)?.text?.toString()
                    expr?.takeIf { it.isNotBlank() }
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
        overrideFields: View,
        redirectUrlLayout: View? = null,
        l4OverrideIpLayout: View? = null,
        l4OverridePortLayout: View? = null
    ) {
        val types = listOf("dns", "http", "l4")
        val ruleType = types.getOrNull(typeSpinner.selectedItemPosition) ?: "dns"
        val actions = when (ruleType) {
            "http" -> listOf("allow", "redirect", "block", "off", "noscan")
            "l4" -> listOf("allow", "block", "l4_override")
            else -> listOf("allow", "block", "safesearch", "ytrestricted", "override")
        }
        val action = actions.getOrNull(actionSpinner.selectedItemPosition) ?: "allow"
        overrideFields.visibility = if (action == "override" && ruleType == "dns") View.VISIBLE else View.GONE
        redirectUrlLayout?.visibility = if (action == "redirect" && ruleType == "http") View.VISIBLE else View.GONE
        val showL4Override = action == "l4_override" && ruleType == "l4"
        l4OverrideIpLayout?.visibility = if (showL4Override) View.VISIBLE else View.GONE
        l4OverridePortLayout?.visibility = if (showL4Override) View.VISIBLE else View.GONE
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

private fun extractAllListIds(traffic: String): List<String> {
    val regex = Regex("in\\s+\\$([a-fA-F0-9-]+)")
    return regex.findAll(traffic).map { it.groupValues[1] }.distinct().toList()
}

private fun isNotInList(traffic: String): Boolean {
    val trimmed = traffic.trimStart()
    return trimmed.startsWith("not") && trimmed.contains(" in $")
}