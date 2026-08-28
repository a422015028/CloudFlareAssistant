package com.muort.upworker.feature.zerotrust.access

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessPolicy
import com.muort.upworker.databinding.ItemAccessPolicyBinding

/**
 * Adapter for Access Policy list
 */
class AccessPolicyAdapter(
    private val onEditClick: (AccessPolicy) -> Unit,
    private val onDeleteClick: (AccessPolicy) -> Unit
) : ListAdapter<AccessPolicy, AccessPolicyAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccessPolicyBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAccessPolicyBinding,
        private val onEditClick: (AccessPolicy) -> Unit,
        private val onDeleteClick: (AccessPolicy) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(policy: AccessPolicy) {
            val ctx = binding.root.context
            binding.policyNameText.text = policy.name
            binding.policyDecisionChip.text = getDecisionLabel(policy.decision, ctx)
            binding.policyPrecedenceText.text = ctx.getString(R.string.zt_policy_precedence_label, policy.precedence ?: 0)
            
            val rulesInfo = buildString {
                append(ctx.resources.getQuantityString(R.plurals.zt_policy_include_rules, policy.include.size, policy.include.size))
                if (!policy.exclude.isNullOrEmpty()) {
                    append(ctx.resources.getQuantityString(R.plurals.zt_policy_exclude_rules, policy.exclude.size, policy.exclude.size))
                }
                if (!policy.require.isNullOrEmpty()) {
                    append(ctx.resources.getQuantityString(R.plurals.zt_policy_require_rules, policy.require.size, policy.require.size))
                }
            }
            binding.policyRulesText.text = rulesInfo

            binding.editPolicyButton.setOnClickListener {
                onEditClick(policy)
            }

            binding.deletePolicyButton.setOnClickListener {
                onDeleteClick(policy)
            }
        }

        private fun getDecisionLabel(decision: String, ctx: android.content.Context): String {
            return when (decision) {
                "allow" -> ctx.getString(R.string.zt_policy_decision_allow)
                "deny" -> ctx.getString(R.string.zt_policy_decision_deny)
                "bypass" -> ctx.getString(R.string.zt_policy_decision_bypass)
                "non_identity" -> ctx.getString(R.string.zt_policy_decision_non_identity)
                else -> decision
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AccessPolicy>() {
        override fun areItemsTheSame(oldItem: AccessPolicy, newItem: AccessPolicy): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AccessPolicy, newItem: AccessPolicy): Boolean {
            return oldItem == newItem
        }
    }
}
