package com.muort.upworker.feature.zone

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.SnippetRule
import com.muort.upworker.core.repository.SnippetRepository
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

    private val accountViewModel: AccountViewModel by activityViewModels()

    private var _binding: DialogSnippetRuleBinding? = null
    private val binding get() = _binding!!

    private val zoneId: String by lazy { requireArguments().getString(ARG_ZONE_ID)!! }
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
        val inflater = LayoutInflater.from(requireContext())
        val row = ItemSnippetConditionBinding.inflate(inflater, binding.conditionsContainer, false)
        val cond = conditions[index]

        fun syncValueInput() {
            val field = SnippetRuleExpression.FIELDS[cond.fieldIndex]
            val op = SnippetRuleExpression.OPS[cond.opIndex]
            row.headerNameLayout.visibility =
                if (field.needsHeaderName) View.VISIBLE else View.GONE
            row.valueLayout.visibility = if (op.noValue) View.GONE else View.VISIBLE
            val valueHint = when {
                op.expr == "in" -> "多个值用逗号分隔"
                field.type == SnippetRuleExpression.ValueType.IP -> "IP 或 CIDR"
                field.type == SnippetRuleExpression.ValueType.NUMBER -> "数字"
                field.expr == "ip.src.country" -> "国家代码（如 CN）"
                else -> "值"
            }
            row.valueLayout.hint = valueHint
        }

        // 字段
        val fieldLabels = SnippetRuleExpression.FIELDS.map { it.label }
        row.fieldSpinner.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fieldLabels)
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
                ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, ops.map { it.label })
            )
            row.opSpinner.setText(ops[cond.opIndex.coerceAtMost(ops.size - 1)].label, false)
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
        binding.previewText.text = expr.ifBlank { "（请添加条件）" }
        binding.builderCharCount.text = "${expr.length} / ${SnippetRepository.MAX_EXPRESSION_LENGTH}"
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
                    "表达式包含构建器不支持的高级特性（嵌套/not/函数），请继续使用表达式编辑器",
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
        binding.editorCharCount.text = "${len} / ${SnippetRepository.MAX_EXPRESSION_LENGTH}"
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
                .setMessage("确定移除「$snippetName」的规则吗？移除后该片段将不会执行。")
                .setPositiveButton("移除") { _, _ -> removeRule() }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun buildFinalExpression(): String? {
        if (binding.radioAllRequests.isChecked) return "true"
        return if (isBuilderMode) {
            SnippetRuleExpression.build(conditions, useAnd).ifBlank {
                Toast.makeText(requireContext(), "请至少填写一个完整条件", Toast.LENGTH_SHORT).show()
                null
            }
        } else {
            binding.expressionInput.text.toString().trim().ifBlank {
                Toast.makeText(requireContext(), "表达式不能为空", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    private fun saveRule() {
        if (saving) return
        val account = accountViewModel.defaultAccount.value ?: run {
            Toast.makeText(requireContext(), "账号未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val expr = buildFinalExpression() ?: return
        if (expr.length > SnippetRepository.MAX_EXPRESSION_LENGTH) {
            Toast.makeText(
                requireContext(),
                "表达式长度 ${expr.length} 超过上限 ${SnippetRepository.MAX_EXPRESSION_LENGTH} 字符",
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
        binding.btnSave.text = "保存中…"

        lifecycleScope.launch {
            val result = snippetRepo.saveSnippetRule(account, zoneId, rule)
            saving = false
            binding.btnSave.isEnabled = true
            binding.btnSave.text = "保存"
            when (result) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), "规则已保存", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                is Resource.Error ->
                    Toast.makeText(requireContext(), "保存失败：${result.message}", Toast.LENGTH_LONG).show()
                is Resource.Loading -> {}
            }
        }
    }

    private fun removeRule() {
        val account = accountViewModel.defaultAccount.value ?: run {
            Toast.makeText(requireContext(), "账号未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            when (val r = snippetRepo.deleteSnippetRule(account, zoneId, snippetName)) {
                is Resource.Success -> {
                    Toast.makeText(requireContext(), "规则已移除", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                is Resource.Error ->
                    Toast.makeText(requireContext(), "移除失败：${r.message}", Toast.LENGTH_LONG).show()
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
        private const val ARG_SNIPPET_NAME = "snippet_name"
        private const val ARG_EXPRESSION = "expression"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_ENABLED = "enabled"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            zoneId: String,
            snippetName: String,
            currentRule: SnippetRule?,
        ) {
            SnippetRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ZONE_ID, zoneId)
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
}
