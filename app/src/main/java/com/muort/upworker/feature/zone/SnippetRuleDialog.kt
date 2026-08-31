package com.muort.upworker.feature.zone

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DnsRecord
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SnippetRule
import com.muort.upworker.core.repository.DnsRepository
import com.muort.upworker.core.repository.SnippetRepository
import com.muort.upworker.databinding.DialogSnippetDnsBinding
import com.muort.upworker.databinding.DialogSnippetRuleBinding
import com.muort.upworker.databinding.ItemSnippetConditionBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

/**
 * 代码片段规则编辑对话框：所有请求 / 自定义表达式两种模式，
 * 自定义模式支持可视化构建器（字段/运算符/值 + AND/OR）与表达式编辑器切换，上限 4000 字符。
 */
@AndroidEntryPoint
class SnippetRuleDialog : DialogFragment() {

    @Inject lateinit var snippetRepo: SnippetRepository

    @Inject lateinit var dnsRepo: DnsRepository

    private val accountViewModel: AccountViewModel by activityViewModels()

    private var _binding: DialogSnippetRuleBinding? = null
    private val binding get() = _binding!!

    private val zoneId: String by lazy { requireArguments().getString(ARG_ZONE_ID)!! }
    private val zoneName: String by lazy { requireArguments().getString(ARG_ZONE_NAME) ?: "" }
    private val snippetName: String by lazy { requireArguments().getString(ARG_SNIPPET_NAME)!! }
    private val existingRule: SnippetRule? by lazy {
        requireArguments().getString(ARG_EXPRESSION)?.let { expr ->
            SnippetRule(
                snippetName = snippetName,
                expression = expr,
                description = requireArguments().getString(ARG_DESCRIPTION),
                enabled = requireArguments().getBoolean(ARG_ENABLED, true),
            )
        }
    }

    private val conditions = mutableListOf(SnippetRuleExpression.Condition())
    private var useAnd = true
    private var isBuilderMode = true
    private var saving = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSnippetRuleBinding.inflate(LayoutInflater.from(requireContext()))
        setupViews()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    private fun setupViews() {
        initFromExistingRule()
        setupModeSwitch()
        setupBuilder()
        setupEditor()
        setupButtons()
        refreshModeSections()
    }

    // ==================== 初始化 ====================

    private fun initFromExistingRule() {
        val rule = existingRule
        binding.enabledSwitch.isChecked = rule?.enabled ?: true
        binding.descriptionInput.setText(rule?.description ?: "")
        binding.btnRemoveRule.visibility = if (rule != null) View.VISIBLE else View.GONE

        when {
            rule == null -> binding.radioAllRequests.isChecked = true
            rule.expression.trim() == "true" -> binding.radioAllRequests.isChecked = true
            else -> {
                binding.radioCustomExpression.isChecked = true
                val parsed = SnippetRuleExpression.parse(rule.expression)
                if (parsed != null) {
                    conditions.clear()
                    conditions.addAll(parsed.conditions)
                    useAnd = parsed.useAnd
                    isBuilderMode = true
                } else {
                    isBuilderMode = false
                }
            }
        }
    }

    // ==================== 模式切换 ====================

    private fun setupModeSwitch() {
        binding.modeRadioGroup.setOnCheckedChangeListener { _, _ -> refreshModeSections() }
    }

    private fun refreshModeSections() {
        val custom = binding.radioCustomExpression.isChecked
        binding.builderSection.visibility =
            if (custom && isBuilderMode) View.VISIBLE else View.GONE
        binding.editorSection.visibility =
            if (custom && !isBuilderMode) View.VISIBLE else View.GONE
        if (custom && isBuilderMode) {
            renderConditions()
            updatePreview()
        }
        if (custom && !isBuilderMode) {
            updateEditorCounter()
        }
    }

    // ==================== 构建器 ====================

    private fun setupBuilder() {
        binding.addConditionBtn.setOnClickListener {
            conditions.add(SnippetRuleExpression.Condition())
            renderConditions()
            updatePreview()
        }

        binding.andChip.setOnClickListener {
            useAnd = true
            binding.andChip.isChecked = true
            binding.orChip.isChecked = false
            updatePreview()
        }
        binding.orChip.setOnClickListener {
            useAnd = false
            binding.orChip.isChecked = true
            binding.andChip.isChecked = false
            updatePreview()
        }
        binding.andChip.isChecked = true
    }

    private fun renderConditions() {
        val container = binding.conditionsContainer
        container.removeAllViews()
        conditions.forEachIndexed { index, _ ->
            container.addView(inflateConditionView(index))
        }
        binding.logicSwitchRow.visibility =
            if (conditions.size > 1) View.VISIBLE else View.GONE
        if (conditions.size > 1) {
            binding.andChip.isChecked = useAnd
            binding.orChip.isChecked = !useAnd
        }
    }

    private fun inflateConditionView(index: Int): View {
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)
        val row = ItemSnippetConditionBinding.inflate(inflater, binding.conditionsContainer, false)
        val cond = conditions[index]

        fun syncValueInput() {
            val field = SnippetRuleExpression.FIELDS[cond.fieldIndex]
            val op = SnippetRuleExpression.OPS[cond.opIndex]
            row.headerNameLayout.visibility =
                if (field.needsHeaderName) View.VISIBLE else View.GONE
            row.valueLayout.visibility = if (op.noValue) View.GONE else View.VISIBLE
            val valueHintRes = when {
                op.expr == "in" -> R.string.snippet_value_multiple_values
                field.type == SnippetRuleExpression.ValueType.IP -> R.string.snippet_value_ip_or_cidr
                field.type == SnippetRuleExpression.ValueType.NUMBER -> R.string.snippet_value_number
                field.expr == "ip.src.country" -> R.string.snippet_value_country_code
                else -> R.string.snippet_value_default
            }
            row.valueLayout.hint = ctx.getString(valueHintRes)
        }

        // 字段
        val fieldLabels = SnippetRuleExpression.FIELDS.map { it.label(ctx) }
        row.fieldSpinner.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_list_item_1, fieldLabels)
        )
        row.fieldSpinner.setText(fieldLabels[cond.fieldIndex], false)
        // 初始运算符需与字段类型匹配
        ensureOpValid(cond)
        row.fieldSpinner.setOnItemClickListener { _, _, position, _ ->
            cond.fieldIndex = position
            cond.opIndex = 0
            ensureOpValid(cond)
            syncValueInput()
            renderConditions()
            updatePreview()
        }

        // 运算符（随字段类型过滤）
        fun refreshOps() {
            val field = SnippetRuleExpression.FIELDS[cond.fieldIndex]
            val ops = SnippetRuleExpression.opsFor(field.type)
            row.opSpinner.setAdapter(
                ArrayAdapter(ctx, android.R.layout.simple_list_item_1, ops.map { it.label(ctx) })
            )
            row.opSpinner.setText(ops[cond.opIndex.coerceAtMost(ops.size - 1)].label(ctx), false)
        }
        refreshOps()
        row.opSpinner.setOnItemClickListener { _, _, position, _ ->
            cond.opIndex = position
            syncValueInput()
            updatePreview()
        }

        // 请求头名称
        row.headerNameInput.setText(cond.headerName)
        row.headerNameInput.addTextChangedListener(object : SimpleTextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                cond.headerName = s?.toString() ?: ""
                updatePreview()
            }
        })

        // 值
        row.valueInput.setText(cond.value)
        row.valueInput.addTextChangedListener(object : SimpleTextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                cond.value = s?.toString() ?: ""
                updatePreview()
            }
        })

        // 删除
        if (index == 0) {
            row.removeConditionBtn.visibility = View.GONE
        } else {
            row.removeConditionBtn.setOnClickListener {
                conditions.removeAt(index)
                renderConditions()
                updatePreview()
            }
        }

        syncValueInput()
        return row.root
    }

    private fun ensureOpValid(cond: SnippetRuleExpression.Condition) {
        val field = SnippetRuleExpression.FIELDS[cond.fieldIndex]
        val ops = SnippetRuleExpression.opsFor(field.type)
        if (cond.opIndex >= ops.size) cond.opIndex = 0
    }

    private fun updatePreview() {
        val expr = SnippetRuleExpression.build(conditions, useAnd)
        binding.previewText.text = expr.ifBlank { getString(R.string.snippet_expr_preview_empty) }
        binding.builderCharCount.text = getString(
            R.string.snippet_expr_char_count_format,
            expr.length,
            SnippetRepository.MAX_EXPRESSION_LENGTH,
        )
        binding.builderCharCount.setTextColor(charCountColor(expr.length))
    }

    // ==================== 表达式编辑器 ====================

    private fun setupEditor() {
        binding.editExpressionBtn.setOnClickListener {
            val expr = SnippetRuleExpression.build(conditions, useAnd).ifBlank {
                existingRule?.expression?.takeIf { it.trim() != "true" } ?: ""
            }
            binding.expressionInput.setText(expr)
            isBuilderMode = false
            refreshModeSections()
        }

        binding.useBuilderBtn.setOnClickListener {
            val expr = binding.expressionInput.text.toString().trim()
            val parsed = SnippetRuleExpression.parse(expr)
            if (parsed == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.snippet_expr_advanced_not_supported),
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnClickListener
            }
            conditions.clear()
            conditions.addAll(parsed.conditions)
            useAnd = parsed.useAnd
            isBuilderMode = true
            refreshModeSections()
        }

        binding.expressionInput.addTextChangedListener(object : SimpleTextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateEditorCounter()
            }
        })
    }

    private fun updateEditorCounter() {
        val len = binding.expressionInput.text?.toString()?.length ?: 0
        binding.editorCharCount.text = getString(
            R.string.snippet_expr_char_count_format,
            len,
            SnippetRepository.MAX_EXPRESSION_LENGTH,
        )
        binding.editorCharCount.setTextColor(charCountColor(len))
    }

    private fun charCountColor(length: Int): Int {
        val attr = if (length > SnippetRepository.MAX_EXPRESSION_LENGTH)
            com.google.android.material.R.attr.colorError
        else
            android.R.attr.textColorSecondary
        return com.google.android.material.color.MaterialColors.getColor(binding.root, attr)
    }

    // ==================== 保存 / 移除 ====================

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener { saveRule() }

        binding.btnRemoveRule.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.snippet_remove_rule_message, snippetName))
                .setPositiveButton(R.string.snippet_remove_rule_button) { _, _ -> removeRule() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun buildFinalExpression(): String? {
        if (binding.radioAllRequests.isChecked) return "true"
        return if (isBuilderMode) {
            SnippetRuleExpression.build(conditions, useAnd).ifBlank {
                Toast.makeText(requireContext(), getString(R.string.msg_condition_required), Toast.LENGTH_SHORT).show()
                null
            }
        } else {
            binding.expressionInput.text.toString().trim().ifBlank {
                Toast.makeText(requireContext(), getString(R.string.msg_expression_empty), Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    private fun saveRule() {
        if (saving) return
        val account = accountViewModel.defaultAccount.value ?: run {
            Toast.makeText(requireContext(), getString(R.string.msg_account_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        val expr = buildFinalExpression() ?: return
        if (expr.length > SnippetRepository.MAX_EXPRESSION_LENGTH) {
            Toast.makeText(
                requireContext(),
                getString(
                    R.string.msg_expr_too_long,
                    expr.length,
                    SnippetRepository.MAX_EXPRESSION_LENGTH,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val rule = SnippetRule(
            snippetName = snippetName,
            expression = expr,
            description = binding.descriptionInput.text?.toString()?.trim()?.ifEmpty { null },
            enabled = binding.enabledSwitch.isChecked,
        )

        saving = true
        binding.btnSave.isEnabled = false
        binding.btnSave.setText(R.string.msg_saving_ellipsis)

        lifecycleScope.launch {
            val result = snippetRepo.saveSnippetRule(account, zoneId, rule)
            saving = false
            binding.btnSave.isEnabled = true
            binding.btnSave.setText(R.string.save)
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.msg_rule_saved), Toast.LENGTH_SHORT).show()
                    checkDnsCoverageAndFinish(account, expr)
                }
                is Resource.Error ->
                    Toast.makeText(requireContext(), getString(R.string.msg_save_failed, result.message), Toast.LENGTH_LONG).show()
                is Resource.Loading -> {}
            }
        }
    }

    private fun removeRule() {
        val account = accountViewModel.defaultAccount.value ?: run {
            Toast.makeText(requireContext(), getString(R.string.msg_account_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            when (val r = snippetRepo.deleteSnippetRule(account, zoneId, snippetName)) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), getString(R.string.msg_rule_removed), Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                is Resource.Error ->
                    Toast.makeText(requireContext(), getString(R.string.msg_remove_failed, r.message), Toast.LENGTH_LONG).show()
                is Resource.Loading -> {}
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private interface SimpleTextWatcher : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    companion object {
        private const val ARG_ZONE_ID = "zone_id"
        private const val ARG_ZONE_NAME = "zone_name"
        private const val ARG_SNIPPET_NAME = "snippet_name"
        private const val ARG_EXPRESSION = "expression"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_ENABLED = "enabled"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            zoneId: String,
            snippetName: String,
            currentRule: SnippetRule?,
            zoneName: String = "",
        ) {
            SnippetRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ZONE_ID, zoneId)
                    putString(ARG_ZONE_NAME, zoneName)
                    putString(ARG_SNIPPET_NAME, snippetName)
                    if (currentRule != null) {
                        putString(ARG_EXPRESSION, currentRule.expression)
                        putString(ARG_DESCRIPTION, currentRule.description)
                        putBoolean(ARG_ENABLED, currentRule.enabled ?: true)
                    }
                }
            }.show(fragmentManager, "SnippetRuleDialog")
        }
    }
    // ==================== DNS 覆盖检查与绑定引导 ====================

    /** 从表达式中提取 http.host 条件引用的主机名（支持 eq/ne/contains/in） */
    private fun extractHostnamesFromExpression(expression: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("""http\.host\s+(?:eq|ne|==|!=|contains)\s+"([^"]+)"""").findAll(expression)
            .forEach { out += it.groupValues[1].lowercase().trimEnd('.') }
        Regex("""http\.host\s+in\s*\{([^}]*)\}""").findAll(expression).forEach { m ->
            Regex("\"([^\"]+)\"").findAll(m.groupValues[1])
                .forEach { out += it.groupValues[1].lowercase().trimEnd('.') }
        }
        return out.toList()
    }

    /**
     * 判断主机名是否已被开启代理的 DNS 记录覆盖：
     * 精确匹配或通配符逐级回退（a.b.example.com → *.b.example.com → *.example.com）
     */
    private fun isCoveredByProxiedRecord(hostname: String, records: List<DnsRecord>): Boolean {
        val proxied = records.filter { it.proxied }
            .map { it.name.lowercase().trimEnd('.') }
            .toSet()
        val parts = hostname.lowercase().trimEnd('.').split('.')
        for (i in parts.indices) {
            val candidate = (if (i == 0) "" else "*.") + parts.drop(i).joinToString(".")
            if (candidate in proxied) return true
        }
        return false
    }

    /** 规则保存成功后检查 DNS 覆盖；有未代理的主机名时弹三选项引导，否则直接结束 */
    private fun checkDnsCoverageAndFinish(account: Account, expression: String) {
        val hostnames = extractHostnamesFromExpression(expression)
        if (hostnames.isEmpty()) { dismiss(); return }
        lifecycleScope.launch {
            // ponytail: DNS 读取失败（Token 无 DNS 读权限等）时静默跳过检查，不打扰用户
            val records = when (val r = dnsRepo.listDnsRecords(account, zoneId)) {
                is Resource.Success -> r.data
                else -> null
            } ?: run { dismiss(); return@launch }
            val uncovered = hostnames.filter { !isCoveredByProxiedRecord(it, records) }
            if (uncovered.isEmpty()) { dismiss(); return@launch }
            showDnsBindingWarning(uncovered)
        }
    }

    /**
     * 对齐 Cloudflare 网页版样式：标题，
     * 单对话框内含 [忽略并继续] / [创建新代理 DNS 记录]（默认）单选 + 记录表单。
     */
    private fun showDnsBindingWarning(uncovered: List<String>) {
        // ponytail: 规则面板随后立即退场，后续回调一律挂 Activity——DialogFragment 销毁后其 scope 与 requireContext 均失效
        val account = accountViewModel.defaultAccount.value ?: run { dismiss(); return }
        val act = requireActivity()
        val ctx = act.applicationContext
        val host = uncovered.first()
        val fqdn = if (zoneName.isNotEmpty() && !host.endsWith(zoneName)) "$host.$zoneName" else host
        val dBinding = DialogSnippetDnsBinding.inflate(layoutInflater)

        dBinding.dnsExplainText.text = getString(R.string.snippet_dns_explain, host)

        fun updateTargetText(b: DialogSnippetDnsBinding, f: String) {
            val content = b.recordContentInput.text?.toString()?.trim().orEmpty()
            val contentArg = content.ifEmpty { "-" }
            b.dnsTargetText.text = getString(R.string.snippet_dns_target_format, f, contentArg)
        }

        val types = listOf("A", "AAAA", "CNAME")
        // 各类型默认值与提示，对齐网页版：A→192.0.2.1、AAAA→100::（黑洞地址，用于放弃请求）
        fun applyType(type: String) {
            when (type) {
                "A" -> {
                    dBinding.recordContentLayout.setHint(R.string.snippet_dns_hint_ipv4_required)
                    dBinding.recordContentLayout.helperText =
                        getString(R.string.snippet_dns_helper_ipv4_blackhole)
                    dBinding.recordContentInput.setText("192.0.2.1")
                }
                "AAAA" -> {
                    dBinding.recordContentLayout.setHint(R.string.snippet_dns_hint_ipv6_required)
                    dBinding.recordContentLayout.helperText =
                        getString(R.string.snippet_dns_helper_ipv6_blackhole)
                    dBinding.recordContentInput.setText("100::")
                }
                else -> {
                    dBinding.recordContentLayout.setHint(R.string.snippet_dns_hint_content)
                    dBinding.recordContentLayout.helperText = null
                    dBinding.recordContentInput.setText("")
                }
            }
            updateTargetText(dBinding, fqdn)
        }
        dBinding.recordTypeInput.setText(types.first(), false)
        dBinding.recordTypeInput.setAdapter(
            ArrayAdapter(act, android.R.layout.simple_list_item_1, types)
        )
        dBinding.recordTypeInput.setOnItemClickListener { _, _, pos, _ -> applyType(types[pos]) }
        dBinding.recordNameInput.setText(host)

        dBinding.recordContentInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = updateTargetText(dBinding, fqdn)
            }
        )
        applyType(types.first())

        dBinding.dnsActionGroup.setOnCheckedChangeListener { _, checkedId ->
            val creating = checkedId == R.id.radioCreate
            dBinding.recordFormCard.visibility = if (creating) View.VISIBLE else View.GONE
            dBinding.dnsTargetText.visibility = if (creating) View.VISIBLE else View.GONE
        }

        // 先把引导对话框弹出，再让规则面板退场，避免两层 Dialog 叠加互相遮挡
        val dialog = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.snippet_dns_warning_title)
            .setView(dBinding.root)
            .setPositiveButton(
                if (dBinding.radioCreate.isChecked) R.string.snippet_dns_btn_create_and_deploy
                else R.string.snippet_dns_btn_deploy_only,
                null,
            )
            // “取消”走 AlertDialog 默认行为只关引导框；规则面板已在下方 dismiss 退场
            .setNegativeButton(R.string.cancel, null)
            .show()
        dismiss()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener { btn ->
            if (!dBinding.radioCreate.isChecked) { dialog.dismiss(); return@setOnClickListener }
            val type = dBinding.recordTypeInput.text.toString().trim().uppercase()
            val name = dBinding.recordNameInput.text?.toString()?.trim() ?: ""
            val content = dBinding.recordContentInput.text?.toString()?.trim() ?: ""
            if (name.isEmpty() || content.isEmpty()) {
                Toast.makeText(ctx, getString(R.string.msg_name_and_content_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btn.isEnabled = false
            act.lifecycleScope.launch {
                val result = dnsRepo.createDnsRecord(
                    account,
                    zoneId,
                    DnsRecordRequest(
                        type = type,
                        name = name,
                        content = content,
                        proxied = true,
                        ttl = 300,
                    ),
                )
                when (result) {
                    is Resource.Success -> {
                        Toast.makeText(ctx, getString(R.string.msg_dns_created_and_proxied), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    is Resource.Error -> {
                        Toast.makeText(ctx, getString(R.string.msg_create_failed, result.message), Toast.LENGTH_LONG).show()
                        btn.isEnabled = true
                    }
                    is Resource.Loading -> {}
                }
            }
        }
    }

}
