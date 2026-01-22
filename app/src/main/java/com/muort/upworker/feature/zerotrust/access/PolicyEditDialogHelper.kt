package com.muort.upworker.feature.zerotrust.access

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessGroup
import com.muort.upworker.core.model.AccessPolicy
import com.muort.upworker.core.model.AccessPolicyRequest
import com.muort.upworker.core.model.AccessRule

/**
 * Helper class for showing policy create/edit dialogs
 */
class PolicyEditDialogHelper(private val context: Context) {

    private val decisionTypes = listOf(
        "allow" to "允许",
        "deny" to "拒绝",
        "bypass" to "绕过",
        "non_identity" to "非身份"
    )

    /**
     * Show dialog to create a new policy
     */
    fun showCreatePolicyDialog(
        groups: List<AccessGroup> = emptyList(),
        onPolicyCreated: (AccessPolicyRequest) -> Unit
    ) {
        showPolicyDialog(null, groups, onPolicyCreated)
    }

    /**
     * Show dialog to edit existing policy
     */
    fun showEditPolicyDialog(
        policy: AccessPolicy,
        groups: List<AccessGroup> = emptyList(),
        onPolicyUpdated: (AccessPolicyRequest) -> Unit
    ) {
        showPolicyDialog(policy, groups, onPolicyUpdated)
    }

    private fun showPolicyDialog(
        existingPolicy: AccessPolicy?,
        groups: List<AccessGroup>,
        onSave: (AccessPolicyRequest) -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_policy_edit, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.policyNameInput)
        val decisionSpinner = dialogView.findViewById<Spinner>(R.id.decisionSpinner)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.precedenceInput)
        val sessionDurationInput = dialogView.findViewById<TextInputEditText>(R.id.sessionDurationInput)

        // Rule lists
        val includeRecyclerView = dialogView.findViewById<RecyclerView>(R.id.includeRulesRecyclerView)
        val excludeRecyclerView = dialogView.findViewById<RecyclerView>(R.id.excludeRulesRecyclerView)
        val requireRecyclerView = dialogView.findViewById<RecyclerView>(R.id.requireRulesRecyclerView)

        // Add rule buttons
        val addIncludeButton = dialogView.findViewById<MaterialButton>(R.id.addIncludeRuleButton)
        val addExcludeButton = dialogView.findViewById<MaterialButton>(R.id.addExcludeRuleButton)
        val addRequireButton = dialogView.findViewById<MaterialButton>(R.id.addRequireRuleButton)

        // Empty state texts
        val noIncludeText = dialogView.findViewById<View>(R.id.noIncludeRulesText)
        val noExcludeText = dialogView.findViewById<View>(R.id.noExcludeRulesText)
        val noRequireText = dialogView.findViewById<View>(R.id.noRequireRulesText)

        // Setup decision spinner
        val decisionAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            decisionTypes.map { it.second }
        )
        decisionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        decisionSpinner.adapter = decisionAdapter

        // Rule lists
        val includeRules = mutableListOf<AccessRule>()
        val excludeRules = mutableListOf<AccessRule>()
        val requireRules = mutableListOf<AccessRule>()

        // Setup rule adapters
        lateinit var includeAdapter: PolicyRuleAdapter
        lateinit var excludeAdapter: PolicyRuleAdapter
        lateinit var requireAdapter: PolicyRuleAdapter
        
        includeAdapter = PolicyRuleAdapter { rule ->
            includeRules.remove(rule)
            includeAdapter.submitList(includeRules.toList())
            includeRecyclerView.visibility = if (includeRules.isEmpty()) View.GONE else View.VISIBLE
            noIncludeText.visibility = if (includeRules.isEmpty()) View.VISIBLE else View.GONE
        }
        excludeAdapter = PolicyRuleAdapter { rule ->
            excludeRules.remove(rule)
            excludeAdapter.submitList(excludeRules.toList())
            excludeRecyclerView.visibility = if (excludeRules.isEmpty()) View.GONE else View.VISIBLE
            noExcludeText.visibility = if (excludeRules.isEmpty()) View.VISIBLE else View.GONE
        }
        requireAdapter = PolicyRuleAdapter { rule ->
            requireRules.remove(rule)
            requireAdapter.submitList(requireRules.toList())
            requireRecyclerView.visibility = if (requireRules.isEmpty()) View.GONE else View.VISIBLE
            noRequireText.visibility = if (requireRules.isEmpty()) View.VISIBLE else View.GONE
        }

        includeRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = includeAdapter
        }
        excludeRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = excludeAdapter
        }
        requireRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = requireAdapter
        }

        // Populate existing policy data
        existingPolicy?.let { policy ->
            nameInput.setText(policy.name)
            decisionSpinner.setSelection(decisionTypes.indexOfFirst { it.first == policy.decision })
            precedenceInput.setText(policy.precedence?.toString() ?: "0")
            sessionDurationInput.setText(policy.sessionDuration ?: "")
            
            includeRules.addAll(policy.include)
            policy.exclude?.let { excludeRules.addAll(it) }
            policy.require?.let { requireRules.addAll(it) }
            
            includeAdapter.submitList(includeRules.toList())
            includeRecyclerView.visibility = if (includeRules.isEmpty()) View.GONE else View.VISIBLE
            noIncludeText.visibility = if (includeRules.isEmpty()) View.VISIBLE else View.GONE
            
            excludeAdapter.submitList(excludeRules.toList())
            excludeRecyclerView.visibility = if (excludeRules.isEmpty()) View.GONE else View.VISIBLE
            noExcludeText.visibility = if (excludeRules.isEmpty()) View.VISIBLE else View.GONE
            
            requireAdapter.submitList(requireRules.toList())
            requireRecyclerView.visibility = if (requireRules.isEmpty()) View.GONE else View.VISIBLE
            noRequireText.visibility = if (requireRules.isEmpty()) View.VISIBLE else View.GONE
        }

        // Rule dialog helper
        val ruleDialogHelper = PolicyRuleDialogHelper(context)

        // Add rule button handlers
        addIncludeButton.setOnClickListener {
            ruleDialogHelper.showAddRuleDialog(groups) { rule ->
                includeRules.add(rule)
                includeAdapter.submitList(includeRules.toList())
                includeRecyclerView.visibility = if (includeRules.isEmpty()) View.GONE else View.VISIBLE
                noIncludeText.visibility = if (includeRules.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        addExcludeButton.setOnClickListener {
            ruleDialogHelper.showAddRuleDialog(groups) { rule ->
                excludeRules.add(rule)
                excludeAdapter.submitList(excludeRules.toList())
                excludeRecyclerView.visibility = if (excludeRules.isEmpty()) View.GONE else View.VISIBLE
                noExcludeText.visibility = if (excludeRules.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        addRequireButton.setOnClickListener {
            ruleDialogHelper.showAddRuleDialog(groups) { rule ->
                requireRules.add(rule)
                requireAdapter.submitList(requireRules.toList())
                requireRecyclerView.visibility = if (requireRules.isEmpty()) View.GONE else View.VISIBLE
                noRequireText.visibility = if (requireRules.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        val title = if (existingPolicy == null) "创建策略" else "编辑策略"
        val positiveButton = if (existingPolicy == null) "创建" else "保存"

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(positiveButton) { _, _ ->
                val name = nameInput.text.toString()
                val decision = decisionTypes[decisionSpinner.selectedItemPosition].first
                val precedence = precedenceInput.text.toString().toIntOrNull() ?: 0
                val sessionDuration = sessionDurationInput.text.toString().ifBlank { null }

                if (name.isBlank() || includeRules.isEmpty()) {
                    return@setPositiveButton
                }

                val policyRequest = AccessPolicyRequest(
                    name = name,
                    decision = decision,
                    include = includeRules,
                    exclude = excludeRules.ifEmpty { null },
                    require = requireRules.ifEmpty { null },
                    precedence = precedence,
                    sessionDuration = sessionDuration
                )

                onSave(policyRequest)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateRuleList(
        recyclerView: RecyclerView,
        adapter: PolicyRuleAdapter,
        rules: List<AccessRule>,
        emptyText: View
    ) {
        adapter.submitList(rules.toList())
        recyclerView.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
        emptyText.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }
}
