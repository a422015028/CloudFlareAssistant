package com.muort.upworker.feature.zone

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
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
    override val addDialogTitle: String = "添加 WAF 规则"

    /** 可创建/编辑的动作（skip 需额外参数，暂不提供）。 */
    private val supportedActions = listOf(
        "block" to "阻断 (Block)",
        "challenge" to "质询 (Challenge)",
        "managed_challenge" to "托管质询 (Managed Challenge)",
        "js_challenge" to "JS 质询 (JS Challenge)",
        "log" to "记录 (Log)",
    )

    /** 规则被点击 → 编辑（仅支持的动作可编辑）。 */
    override fun onRuleClicked(rule: WafRule) {
        if (supportedActions.any { it.first == rule.action }) {
            showRuleDialog(editingRule = rule)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("不支持编辑")
                .setMessage("该规则的动作（${rule.action ?: "未知"}）包含额外参数，无法在此编辑。请到 Cloudflare 控制台修改。")
                .setPositiveButton("确定", null)
                .show()
        }
    }

    /** 删除前确认。 */
    override fun onRuleDeleteRequested(rule: WafRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除规则")
            .setMessage("确定要删除规则「${rule.description ?: rule.expression?.take(40) ?: rule.id}」吗？")
            .setPositiveButton("删除") { _, _ ->
                account?.let { rulesetViewModel.deleteRule(it, zoneId, rule) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 添加按钮 → 表单（无初始值）。 */
    override fun showAddRuleDialog() {
        showRuleDialog(editingRule = null)
    }

    /** 添加 / 编辑共用表单。 */
    private fun showRuleDialog(editingRule: WafRule?) {
        val isEdit = editingRule != null
        val binding = DialogWafRuleBinding.inflate(LayoutInflater.from(requireContext()))

        binding.formTitle.text = if (isEdit) "编辑 WAF 规则" else addDialogTitle

        // 名称
        binding.nameInput.setText(editingRule?.description ?: "")

        // 动作下拉
        val actionLabels = supportedActions.map { it.second }
        val actionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, actionLabels)
        binding.actionInput.setAdapter(actionAdapter)
        val currentActionIndex = supportedActions.indexOfFirst { it.first == editingRule?.action }
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

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(if (isEdit) "保存" else "添加") { _, _ ->
                val name = binding.nameInput.text.toString().trim().ifBlank { null }
                val actionIndex = actionLabels.indexOf(binding.actionInput.text.toString())
                val action = if (actionIndex >= 0) supportedActions[actionIndex].first else "block"
                val expression = binding.expressionInput.text.toString().trim()
                val enabled = binding.enabledSwitch.isChecked

                if (expression.isEmpty()) {
                    toast("表达式不能为空")
                    return@setPositiveButton
                }

                val rule = WafRuleCreate(action = action, expression = expression, description = name, enabled = enabled)
                account?.let { acct ->
                    if (isEdit && editingRule != null) {
                        rulesetViewModel.updateRule(acct, zoneId, editingRule.id, rule) { ok, err ->
                            toast(if (ok) "保存成功" else "保存失败: $err")
                        }
                    } else {
                        rulesetViewModel.addRule(acct, zoneId, rule) { ok, err ->
                            toast(if (ok) "添加成功" else "添加失败: $err")
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()

        // 保存时禁用按钮防止重复提交
        val saveButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
        viewLifecycleOwner.lifecycleScope.launch {
            rulesetViewModel.state.collect { state ->
                saveButton.isEnabled = !state.isSaving
                saveButton.text = if (state.isSaving) "保存中..." else if (isEdit) "保存" else "添加"
            }
        }
    }

    // ==================== 表达式构建器 ====================

    /** 可用字段。 */
    private val wafFields = listOf(
        WafField("ip.src", "客户端 IP", valueType = ValueType.IP),
        WafField("ip.geoip.country", "国家代码", valueType = ValueType.STRING),
        WafField("http.request.uri.path", "URI 路径", valueType = ValueType.STRING),
        WafField("http.host", "主机名", valueType = ValueType.STRING),
        WafField("http.request.method", "请求方法", valueType = ValueType.STRING),
        WafField("http.user_agent", "User-Agent", valueType = ValueType.STRING),
        WafField("http.request.full_uri", "完整 URI", valueType = ValueType.STRING),
        WafField("cf.threat_score", "威胁评分", valueType = ValueType.NUMERIC),
    )

    /** 可用运算符。 */
    private val wafOps = listOf(
        WafOp("eq", "等于 (=)"),
        WafOp("ne", "不等于 (≠)"),
        WafOp("contains", "包含"),
        WafOp("gt", "大于 (>)"),
        WafOp("lt", "小于 (<)"),
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
        val exprBinding = DialogWafExpressionBinding.inflate(LayoutInflater.from(requireContext()))
        val conditions = mutableListOf(ConditionState())
        var useAnd = true

        fun updatePreview() {
            val preview = buildExpression(conditions, useAnd)
            exprBinding.previewText.text = preview.ifBlank { "（请添加条件）" }
        }

        // 初始条件
        addConditionView(exprBinding.conditionsContainer, conditions, 0) { updatePreview() }

        exprBinding.addConditionBtn.setOnClickListener {
            val index = conditions.size
            conditions.add(ConditionState(fieldIndex = 0, opIndex = 0, value = ""))
            addConditionView(exprBinding.conditionsContainer, conditions, index) { updatePreview() }
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

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(exprBinding.root)
            .setPositiveButton("应用") { _, _ ->
                val expr = buildExpression(conditions, useAnd)
                if (expr.isNotBlank()) {
                    expressionInput.setText(expr)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        dialog.show()
    }

    /** 添加一个条件编辑器到容器。 */
    private fun addConditionView(
        container: LinearLayout,
        conditions: MutableList<ConditionState>,
        index: Int,
        onChange: () -> Unit,
    ) {
        val condBinding = ItemWafConditionBinding.inflate(LayoutInflater.from(requireContext()))
        val cond = conditions[index]

        // 字段下拉
        val fieldLabels = wafFields.map { it.label }
        condBinding.fieldSpinner.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fieldLabels)
        )
        condBinding.fieldSpinner.setText(fieldLabels[cond.fieldIndex], false)
        condBinding.fieldSpinner.setOnItemClickListener { _, _, position, _ ->
            cond.fieldIndex = position
            onChange()
        }

        // 运算符下拉
        val opLabels = wafOps.map { it.label }
        condBinding.opSpinner.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, opLabels)
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
                    addConditionView(container, conditions, i, onChange)
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
    private fun buildExpression(conditions: List<ConditionState>, useAnd: Boolean): String {
        val parts = conditions.mapNotNull { cond ->
            val v = cond.value.trim()
            if (v.isEmpty()) return@mapNotNull null
            val field = wafFields[cond.fieldIndex]
            val op = wafOps[cond.opIndex]
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
