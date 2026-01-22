package com.muort.upworker.feature.zerotrust.access

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
            binding.policyNameText.text = policy.name
            binding.policyDecisionChip.text = getDecisionLabel(policy.decision)
            binding.policyPrecedenceText.text = "优先级: ${policy.precedence ?: 0}"
            
            val rulesInfo = buildString {
                append("Include: ${policy.include.size} 规则")
                if (!policy.exclude.isNullOrEmpty()) {
                    append(" • Exclude: ${policy.exclude.size} 规则")
                }
                if (!policy.require.isNullOrEmpty()) {
                    append(" • Require: ${policy.require.size} 规则")
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

        private fun getDecisionLabel(decision: String): String {
            return when (decision) {
                "allow" -> "允许"
                "deny" -> "拒绝"
                "bypass" -> "绕过"
                "non_identity" -> "非身份"
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
