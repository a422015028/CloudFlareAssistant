package com.muort.upworker.feature.zone

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.WafRule
import com.muort.upworker.core.model.WafRuleCreate
import com.muort.upworker.databinding.DialogWafExpressionBinding
import com.muort.upworker.databinding.DialogWafRuleBinding
import com.muort.upworker.databinding.ItemWafConditionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * WAF 自定义规则页（phase = http_request_firewall_custom）。
 * 对齐 orange-cloud WafRulesScreen：
 * - 列表项展示动作标签 + 描述 + 表达式（monospace）+ 启停开关 + 删除按钮
 * - 点击规则 → 编辑（支持的动作）或提示不支持
 * - 删除前确认
 * - 添加/编辑表单：名称 + 动作下拉 + 表达式 + 启用开关
 * - 可视化表达式构建器：字段/运算符/值，多条件 AND/OR
 */
@AndroidEntryPoint
class WafFragment : BaseZoneRulesetFragment() {

    override val phase: String = "http_request_firewall_custom"
    override val addDialogTitleResId: Int = R.string.waf_add_rule_dialog_title

    /** 可创建/编辑的动作（skip 需额外参数，暂不提供）。 */
    private fun supportedActionLabels(ctx: android.content.Context) = listOf(
        "block" to ctx.getString(R.string.waf_action_block),
        "challenge" to ctx.getString(R.string.waf_action_challenge),
        "managed_challenge" to ctx.getString(R.string.waf_action_managed_challenge),
        "js_challenge" to ctx.getString(R.string.waf_action_js_challenge),
        "log" to ctx.getString(R.string.waf_action_log),
    )

    /** 规则被点击 → 编辑（仅支持的动作可编辑）。 */
    override fun onRuleClicked(rule: WafRule) {
        val ctx = requireContext()
        val actions = supportedActionLabels(ctx)
        if (actions.any { it.first == rule.action }) {
            showRuleDialog(editingRule = rule)
        } else {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.waf_edit_not_supported_title)
                .setMessage(
                    getString(
                        R.string.waf_edit_not_supported_message,
                        rule.action ?: getString(R.string.status_unknown)
                    )
                )
                .setPositiveButton(R.string.confirm, null)
                .show()
        }
    }

    /** 删除前确认。 */
    override fun onRuleDeleteRequested(rule: WafRule) {
        val ruleLabel = rule.description ?: rule.expression?.take(40) ?: rule.id
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.waf_delete_rule_title)
            .setMessage(getString(R.string.waf_delete_rule_confirm, ruleLabel))
            .setPositiveButton(R.string.delete) { _, _ ->
                account?.let { rulesetViewModel.deleteRule(it, zoneId, rule) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** 添加按钮 → 表单（无初始值）。 */
    override fun showAddRuleDialog() {
        showRuleDialog(editingRule = null)
    }

    /** 添加 / 编辑共用表单。 */
    private fun showRuleDialog(editingRule: WafRule?) {
        val ctx = requireContext()
        val isEdit = editingRule != null
        val binding = DialogWafRuleBinding.inflate(LayoutInflater.from(ctx))

        binding.formTitle.text = if (isEdit) getString(R.string.waf_edit_rule_title) else getString(addDialogTitleResId)

        // 名称
        binding.nameInput.setText(editingRule?.description ?: "")

        // 动作下拉
        val actions = supportedActionLabels(ctx)
        val actionLabels = actions.map { it.second }
        val actionAdapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, actionLabels)
        binding.actionInput.setAdapter(actionAdapter)
        val currentActionIndex = actions.indexOfFirst { it.first == editingRule?.action }
        if (currentActionIndex >= 0) {
            binding.actionInput.setText(actionLabels[currentActionIndex], false)
        } else {
            binding.actionInput.setText(actionLabels[0], false) // 默认 Block
        }

        // 表达式
        binding.expressionInput.setText(editingRule?.expression ?: "")

        // 启用开关
        binding.enabledSwitch.isChecked = editingRule?.enabled ?: true

        // 表达式构建器
        binding.expressionBuilderBtn.setOnClickListener {
            showExpressionBuilder(binding.expressionInput)
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(binding.root)
            .setPositiveButton(if (isEdit) R.string.save else R.string.add) { _, _ ->
                val name = binding.nameInput.text.toString().trim().ifBlank { null }
                val actionIndex = actionLabels.indexOf(binding.actionInput.text.toString())
                val action = if (actionIndex >= 0) actions[actionIndex].first else "block"
                val expression = binding.expressionInput.text.toString().trim()
                val enabled = binding.enabledSwitch.isChecked

                if (expression.isEmpty()) {
                    toast(getString(R.string.msg_expression_empty))
                    return@setPositiveButton
                }

                val rule = WafRuleCreate(action = action, expression = expression, description = name, enabled = enabled)
                account?.let { acct ->
                    if (isEdit && editingRule != null) {
                        rulesetViewModel.updateRule(acct, zoneId, editingRule.id, rule) { ok, err ->
                            toast(if (ok) getString(R.string.msg_saved) else getString(R.string.msg_save_failed, err?.asString(requireContext()).orEmpty()))
                        }
                    } else {
                        rulesetViewModel.addRule(acct, zoneId, rule) { ok, err ->
                            toast(if (ok) getString(R.string.msg_added) else getString(R.string.msg_add_failed, err?.asString(requireContext()).orEmpty()))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        // 保存时禁用按钮防止重复提交
        val saveButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
        viewLifecycleOwner.lifecycleScope.launch {
            rulesetViewModel.state.collect { state ->
                saveButton.isEnabled = !state.isSaving
                saveButton.text = if (state.isSaving) getString(R.string.msg_saving) else if (isEdit) getString(R.string.save) else getString(R.string.add)
            }
        }
    }

    // ==================== 表达式构建器 ====================

    /** 可用字段。 */
    private fun wafFields(ctx: android.content.Context) = listOf(
        WafField("ip.src", ctx.getString(R.string.waf_field_client_ip), valueType = ValueType.IP),
        WafField("ip.geoip.country", ctx.getString(R.string.waf_field_country_code), valueType = ValueType.STRING),
        WafField("http.request.uri.path", ctx.getString(R.string.waf_field_uri_path), valueType = ValueType.STRING),
        WafField("http.host", ctx.getString(R.string.waf_field_hostname), valueType = ValueType.STRING),
        WafField("http.request.method", ctx.getString(R.string.waf_field_request_method), valueType = ValueType.STRING),
        WafField("http.user_agent", "User-Agent", valueType = ValueType.STRING),
        WafField("http.request.full_uri", ctx.getString(R.string.waf_field_full_uri), valueType = ValueType.STRING),
        WafField("cf.threat_score", ctx.getString(R.string.waf_field_threat_score), valueType = ValueType.NUMERIC),
    )

    /** 可用运算符。 */
    private fun wafOps(ctx: android.content.Context) = listOf(
        WafOp("eq", ctx.getString(R.string.waf_op_eq)),
        WafOp("ne", ctx.getString(R.string.waf_op_ne)),
        WafOp("contains", ctx.getString(R.string.waf_op_contains)),
        WafOp("gt", ctx.getString(R.string.waf_op_gt)),
        WafOp("lt", ctx.getString(R.string.waf_op_lt)),
    )

    private data class WafField(val expr: String, val label: String, val valueType: ValueType)
    private data class WafOp(val expr: String, val label: String)
    private enum class ValueType { STRING, IP, NUMERIC }

    /** 条件视图数据。 */
    private data class ConditionState(
        var fieldIndex: Int = 0,
        var opIndex: Int = 0,
        var value: String = "",
    )

    private fun showExpressionBuilder(expressionInput: TextInputEditText) {
        val ctx = requireContext()
        val exprBinding = DialogWafExpressionBinding.inflate(LayoutInflater.from(ctx))
        val fields = wafFields(ctx)
        val ops = wafOps(ctx)
        val conditions = mutableListOf(ConditionState())
        var useAnd = true

        fun updatePreview() {
            val preview = buildExpression(conditions, useAnd, fields, ops)
            exprBinding.previewText.text = preview.ifBlank { getString(R.string.waf_please_add_condition) }
        }

        // 初始条件
        addConditionView(exprBinding.conditionsContainer, conditions, 0, fields, ops) { updatePreview() }

        exprBinding.addConditionBtn.setOnClickListener {
            val index = conditions.size
            conditions.add(ConditionState(fieldIndex = 0, opIndex = 0, value = ""))
            addConditionView(exprBinding.conditionsContainer, conditions, index, fields, ops) { updatePreview() }
            exprBinding.logicSwitchRow.visibility = if (conditions.size > 1) View.VISIBLE else View.GONE
            updatePreview()
        }

        exprBinding.andChip.setOnClickListener {
            useAnd = true
            exprBinding.andChip.isChecked = true
            exprBinding.orChip.isChecked = false
            updatePreview()
        }
        exprBinding.orChip.setOnClickListener {
            useAnd = false
            exprBinding.orChip.isChecked = true
            exprBinding.andChip.isChecked = false
            updatePreview()
        }
        exprBinding.andChip.isChecked = true

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(exprBinding.root)
            .setPositiveButton(R.string.waf_apply_button) { _, _ ->
                val expr = buildExpression(conditions, useAnd, fields, ops)
                if (expr.isNotBlank()) {
                    expressionInput.setText(expr)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
    }

    /** 添加一个条件编辑器到容器。 */
    private fun addConditionView(
        container: LinearLayout,
        conditions: MutableList<ConditionState>,
        index: Int,
        fields: List<WafField>,
        ops: List<WafOp>,
        onChange: () -> Unit,
    ) {
        val ctx = requireContext()
        val condBinding = ItemWafConditionBinding.inflate(LayoutInflater.from(ctx))
        val cond = conditions[index]

        // 字段下拉
        val fieldLabels = fields.map { it.label }
        condBinding.fieldSpinner.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_list_item_1, fieldLabels)
        )
        condBinding.fieldSpinner.setText(fieldLabels[cond.fieldIndex], false)
        condBinding.fieldSpinner.setOnItemClickListener { _, _, position, _ ->
            cond.fieldIndex = position
            onChange()
        }

        // 运算符下拉
        val opLabels = ops.map { it.label }
        condBinding.opSpinner.setAdapter(
            ArrayAdapter(ctx, android.R.layout.simple_list_item_1, opLabels)
        )
        condBinding.opSpinner.setText(opLabels[cond.opIndex], false)
        condBinding.opSpinner.setOnItemClickListener { _, _, position, _ ->
            cond.opIndex = position
            onChange()
        }

        // 值输入
        condBinding.valueInput.setText(cond.value)
        condBinding.valueInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                cond.value = s?.toString() ?: ""
                onChange()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 移除按钮
        condBinding.removeConditionBtn.setOnClickListener {
            if (conditions.size > 1) {
                conditions.removeAt(index)
                // 重新构建所有条件视图
                container.removeAllViews()
                conditions.forEachIndexed { i, _ ->
                    addConditionView(container, conditions, i, fields, ops, onChange)
                }
                onChange()
            }
        }

        // 第一个条件不能移除
        if (index == 0) {
            condBinding.removeConditionBtn.visibility = View.GONE
        }

        container.addView(condBinding.root)
    }

    /** 将条件列表拼成 Wirefilter 表达式。 */
    private fun buildExpression(
        conditions: List<ConditionState>,
        useAnd: Boolean,
        fields: List<WafField>,
        ops: List<WafOp>,
    ): String {
        val parts = conditions.mapNotNull { cond ->
            val v = cond.value.trim()
            if (v.isEmpty()) return@mapNotNull null
            val field = fields[cond.fieldIndex]
            val op = ops[cond.opIndex]
            val rhs = when (field.valueType) {
                ValueType.IP -> {
                    // IP 地址：不支持 contains 等非 eq/ne 运算符时跳过
                    if (!listOf("eq", "ne").contains(op.expr)) return@mapNotNull null
                    v
                }
                ValueType.NUMERIC -> v.filter { it.isDigit() }.ifEmpty { return@mapNotNull null }
                ValueType.STRING -> "\"${v.replace("\"", "\\\"")}\""
            }
            "${field.expr} ${op.expr} $rhs"
        }
        if (parts.isEmpty()) return ""
        if (parts.size == 1) return parts.first()
        val joiner = if (useAnd) " and " else " or "
        return parts.joinToString(joiner) { "($it)" }
    }
}
