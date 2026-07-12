package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.HeaderTransform
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.RewriteTarget
import com.muort.upworker.core.model.TransformActionParameters
import com.muort.upworker.core.model.TransformRule
import com.muort.upworker.core.model.TransformRuleCreate
import com.muort.upworker.core.model.TransformRuleset
import com.muort.upworker.core.model.UriRewrite
import com.muort.upworker.core.repository.ZoneRulesetRepository
import com.muort.upworker.R
import com.muort.upworker.databinding.DialogTransformRuleBinding
import com.muort.upworker.databinding.ItemTransformHeaderRowBinding
import com.muort.upworker.databinding.ItemTransformRuleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 转换规则页。对齐 orange-cloud ZoneTransformScreen：
 * - 三个 phase 分区：URL 重写 / 请求头修改 / 响应头修改
 * - 每个 phase 独立的规则列表 + 添加按钮
 * - 规则：描述（或表达式）+ 表达式（monospace）+ 启停 + 删除
 * - 编辑器：表达式 + 描述 + 启用 + phase-specific 字段
 */
@AndroidEntryPoint
class TransformRulesFragment : BaseZoneFeatureFragment() {

    @Inject lateinit var repo: ZoneRulesetRepository

    private lateinit var adapter: TransformAdapter

    /** 三个 phase 的规则集。key = phase raw */
    private val rulesets = mutableMapOf<String, TransformRuleset?>()
    private val togglingRuleIds = mutableSetOf<String>()
    private var loaded = false

    override val emptyText: String = "暂无转换规则"
    override val showAddFab: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TransformAdapter(
            onToggle = { phase, rule, on -> toggleRule(phase, rule, on) },
            onEdit = { phase, rule -> showEditor(phase, rule) },
            onDelete = { phase, rule -> confirmDelete(phase, rule) },
            onAdd = { phase -> showEditor(phase, null) },
        )
        binding.recyclerView.adapter = adapter
    }

    override suspend fun onAccountReady(account: Account) = load(account)

    override fun onRetry() {
        account?.let { load(it) }
    }

    // ==================== 加载 ====================

    private fun load(account: Account) {
        viewLifecycleOwner.lifecycleScope.launch {
            showLoading()
            var hasError: String? = null
            for (phase in PHASES) {
                when (val r = repo.getTransformRuleset(account, zoneId, phase.raw)) {
                    is Resource.Success -> rulesets[phase.raw] = r.data
                    is Resource.Error -> hasError = r.message
                    is Resource.Loading -> {}
                }
            }
            loaded = true
            if (rulesets.values.all { it?.rules.isNullOrEmpty() } && hasError != null) {
                showError(hasError)
            } else {
                renderAll()
            }
        }
    }

    private fun renderAll() {
        showList()
        adapter.update(rulesets, togglingRuleIds)
    }

    // ==================== 启停 ====================

    private fun toggleRule(phase: TransformPhase, rule: TransformRule, on: Boolean) {
        val account = account ?: return
        val ruleset = rulesets[phase.raw] ?: return
        togglingRuleIds.add(rule.id)
        binding.recyclerView.post { renderAll() }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = repo.toggleTransformRule(account, zoneId, ruleset.id, rule, on)) {
                is Resource.Success -> rulesets[phase.raw] = r.data
                is Resource.Error -> toast("操作失败: ${r.message}")
                is Resource.Loading -> {}
            }
            togglingRuleIds.remove(rule.id)
            binding.recyclerView.post { renderAll() }
        }
    }

    // ==================== 编辑器 ====================

    private fun showEditor(phase: TransformPhase, existing: TransformRule?) {
        val ctx = requireContext()
        val b = DialogTransformRuleBinding.inflate(LayoutInflater.from(ctx))

        // 预填
        b.expressionInput.setText(existing?.expression ?: "")
        b.descriptionInput.setText(existing?.description ?: "")
        b.enabledSwitch.isChecked = existing?.enabled ?: true

        // 显示 phase-specific 字段
        if (phase.isUrlRewrite) {
            b.urlRewriteSection.visibility = View.VISIBLE
            b.headersSection.visibility = View.GONE
            b.pathInput.setText(existing?.actionParameters?.uri?.path?.value ?: "")
            b.queryInput.setText(existing?.actionParameters?.uri?.query?.value ?: "")
        } else {
            b.urlRewriteSection.visibility = View.GONE
            b.headersSection.visibility = View.VISIBLE
            // 预填已有 headers
            existing?.actionParameters?.headers?.forEach { (name, h) ->
                addHeaderRow(b.headersContainer, name, h.operation, h.value)
            }
        }

        b.addHeaderButton.setOnClickListener {
            addHeaderRow(b.headersContainer, "", "set", null)
        }

        val title = if (existing == null) "添加${phase.title}" else "编辑${phase.title}"
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(b.root)
            .setPositiveButton("保存") { _, _ ->
                val expr = b.expressionInput.text.toString().trim()
                if (expr.isEmpty()) {
                    toast("表达式不能为空"); return@setPositiveButton
                }
                val desc = b.descriptionInput.text.toString().trim().ifBlank { null }
                val enabled = b.enabledSwitch.isChecked
                val params = if (phase.isUrlRewrite) {
                    TransformActionParameters(
                        uri = UriRewrite(
                            path = b.pathInput.text.toString().trim().ifBlank { null }?.let { RewriteTarget(value = it) },
                            query = b.queryInput.text.toString().trim().ifBlank { null }?.let { RewriteTarget(value = it) },
                        ),
                    )
                } else {
                    val headers = collectHeaders(b.headersContainer)
                    TransformActionParameters(headers = headers.ifEmpty { null })
                }
                val draft = TransformRuleCreate(
                    action = "rewrite",
                    expression = expr,
                    description = desc,
                    enabled = enabled,
                    actionParameters = params,
                )
                saveRule(phase, existing?.id, draft)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addHeaderRow(container: ViewGroup, name: String, op: String, value: String?) {
        val row = ItemTransformHeaderRowBinding.inflate(LayoutInflater.from(container.context), container, false)
        row.headerNameInput.setText(name)
        row.headerValueInput.setText(value ?: "")
        when (op) {
            "add" -> { row.radioAdd.isChecked = true; row.headerValueLayout.visibility = View.VISIBLE }
            "remove" -> { row.radioRemove.isChecked = true; row.headerValueLayout.visibility = View.GONE }
            else -> { row.radioSet.isChecked = true; row.headerValueLayout.visibility = View.VISIBLE }
        }
        row.opRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            row.headerValueLayout.visibility = if (checkedId == R.id.radioRemove) View.GONE else View.VISIBLE
        }
        row.removeButton.setOnClickListener { container.removeView(row.root) }
        container.addView(row.root)
    }

    private fun collectHeaders(container: ViewGroup): Map<String, HeaderTransform> {
        val map = mutableMapOf<String, HeaderTransform>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val row = ItemTransformHeaderRowBinding.bind(child)
            val name = row.headerNameInput.text.toString().trim()
            if (name.isBlank()) continue
            val op = when (row.opRadioGroup.checkedRadioButtonId) {
                R.id.radioAdd -> "add"
                R.id.radioRemove -> "remove"
                else -> "set"
            }
            val value = if (op == "remove") null else row.headerValueInput.text.toString().trim().ifBlank { null }
            map[name] = HeaderTransform(operation = op, value = value)
        }
        return map
    }

    private fun saveRule(phase: TransformPhase, ruleId: String?, draft: TransformRuleCreate) {
        val account = account ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val ruleset = rulesets[phase.raw]
            val result = when {
                ruleId != null && ruleset != null ->
                    repo.updateTransformRule(account, zoneId, ruleset.id, ruleId, draft)
                ruleset != null ->
                    repo.addTransformRule(account, zoneId, ruleset.id, draft)
                else ->
                    repo.createTransformEntrypoint(account, zoneId, phase.raw, draft)
            }
            when (result) {
                is Resource.Success -> { rulesets[phase.raw] = result.data; toast("已保存"); renderAll() }
                is Resource.Error -> toast("保存失败: ${result.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== 删除 ====================

    private fun confirmDelete(phase: TransformPhase, rule: TransformRule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除此规则？")
            .setMessage(rule.description?.takeIf { it.isNotBlank() } ?: rule.expression ?: "")
            .setPositiveButton("删除") { _, _ ->
                account?.let { deleteRule(it, phase, rule) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteRule(account: Account, phase: TransformPhase, rule: TransformRule) {
        val ruleset = rulesets[phase.raw] ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = repo.deleteTransformRule(account, zoneId, ruleset.id, rule.id)) {
                is Resource.Success -> {
                    // 重读该 phase（可能因删空而 entrypoint 消失）
                    val refreshed = repo.getTransformRuleset(account, zoneId, phase.raw)
                    rulesets[phase.raw] = (refreshed as? Resource.Success)?.data
                    toast("已删除"); renderAll()
                }
                is Resource.Error -> toast("删除失败: ${r.message}")
                is Resource.Loading -> {}
            }
        }
    }

    // ==================== Phase 定义 ====================

    enum class TransformPhase(val raw: String, val title: String, val isUrlRewrite: Boolean) {
        REQUEST_URL("http_request_transform", "URL 重写", true),
        REQUEST_HEAD("http_request_late_transform", "请求头修改", false),
        RESPONSE_HEAD("http_response_headers_transform", "响应头修改", false),
    }

    companion object {
        private val PHASES = TransformPhase.entries
    }

    // ==================== 多视图类型适配器 ====================

    private sealed class ListItem {
        data class PhaseHeader(val phase: TransformPhase) : ListItem()
        data class RuleItem(
            val phase: TransformPhase,
            val rule: TransformRule,
        ) : ListItem()
        data class EmptyHint(val phase: TransformPhase) : ListItem()
    }

    private class TransformAdapter(
        private val onToggle: (TransformPhase, TransformRule, Boolean) -> Unit,
        private val onEdit: (TransformPhase, TransformRule) -> Unit,
        private val onDelete: (TransformPhase, TransformRule) -> Unit,
        private val onAdd: (TransformPhase) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<ListItem>()
        private var togglingIds: Set<String> = emptySet()

        companion object {
            private const val TYPE_HEADER = 1
            private const val TYPE_RULE = 2
            private const val TYPE_EMPTY = 3
        }

        fun update(rulesets: Map<String, TransformRuleset?>, togglingIds: Set<String>) {
            this.togglingIds = togglingIds
            items.clear()
            for (phase in PHASES) {
                items += ListItem.PhaseHeader(phase)
                val rules = rulesets[phase.raw]?.rules.orEmpty()
                if (rules.isEmpty()) {
                    items += ListItem.EmptyHint(phase)
                } else {
                    items += rules.map { ListItem.RuleItem(phase, it) }
                }
            }
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is ListItem.PhaseHeader -> TYPE_HEADER
            is ListItem.RuleItem -> TYPE_RULE
            is ListItem.EmptyHint -> TYPE_EMPTY
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                TYPE_HEADER -> HeaderVH(parent, onAdd)
                TYPE_RULE -> RuleVH(parent, onToggle, onEdit, onDelete)
                TYPE_EMPTY -> EmptyVH(parent)
                else -> throw IllegalArgumentException("unknown type $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ListItem.PhaseHeader -> (holder as HeaderVH).bind(item.phase)
                is ListItem.RuleItem -> (holder as RuleVH).bind(item.phase, item.rule, togglingIds)
                is ListItem.EmptyHint -> (holder as EmptyVH).bind(item.phase)
            }
        }

        // ---- Phase 区头 ----
        class HeaderVH(
            parent: ViewGroup,
            private val onAdd: (TransformPhase) -> Unit,
        ) : RecyclerView.ViewHolder(
            android.widget.LinearLayout(parent.context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(48, 36, 0, 8)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            },
        ) {
            private val titleText: android.widget.TextView = android.widget.TextView(itemView.context).apply {
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFF666666.toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            private val addButton: android.widget.ImageButton = android.widget.ImageButton(parent.context).apply {
                setImageResource(android.R.drawable.ic_input_add)
                background = null
                setOnClickListener { }
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            init {
                val layout = itemView as android.widget.LinearLayout
                layout.addView(titleText)
                layout.addView(addButton)
            }

            fun bind(phase: TransformPhase) {
                titleText.text = phase.title
                addButton.setOnClickListener { onAdd(phase) }
            }
        }

        // ---- 规则卡片 ----
        class RuleVH(
            parent: ViewGroup,
            private val onToggle: (TransformPhase, TransformRule, Boolean) -> Unit,
            private val onEdit: (TransformPhase, TransformRule) -> Unit,
            private val onDelete: (TransformPhase, TransformRule) -> Unit,
        ) : RecyclerView.ViewHolder(ItemTransformRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {
            private val b = ItemTransformRuleBinding.bind(itemView)

            fun bind(phase: TransformPhase, rule: TransformRule, togglingIds: Set<String>) {
                val title = rule.description?.takeIf { it.isNotBlank() } ?: "未命名规则"
                b.titleText.text = title
                b.expressionText.text = rule.expression ?: ""
                b.expressionText.visibility = if (b.expressionText.text.isBlank()) View.GONE else View.VISIBLE

                val isBusy = togglingIds.contains(rule.id)
                b.toggleSwitch.setOnCheckedChangeListener(null)
                b.toggleSwitch.isChecked = rule.enabled ?: false
                b.toggleSwitch.isEnabled = !isBusy
                b.toggleSwitch.setOnCheckedChangeListener { _, checked -> onToggle(phase, rule, checked) }

                b.deleteButton.setOnClickListener { onDelete(phase, rule) }
                b.contentArea.setOnClickListener { onEdit(phase, rule) }
                b.root.alpha = if (isBusy) 0.5f else 1f
            }
        }

        // ---- 空提示 ----
        class EmptyVH(parent: ViewGroup) : RecyclerView.ViewHolder(
            android.widget.TextView(parent.context).apply {
                text = "暂无规则"
                textSize = 13f
                setTextColor(0xFF999999.toInt())
                setPadding(56, 8, 16, 8)
            },
        ) {
            fun bind(phase: TransformPhase) {}
        }
    }
}
