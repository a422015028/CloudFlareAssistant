package com.muort.upworker.feature.zerotrust.gateway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.GatewayRule
import com.muort.upworker.databinding.ItemGatewayRuleBinding

class GatewayRuleAdapter(
    private val onEditClick: (GatewayRule) -> Unit,
    private val onDeleteClick: (GatewayRule) -> Unit,
    private val onEnabledChange: (GatewayRule, Boolean) -> Unit
) : ListAdapter<GatewayRule, GatewayRuleAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGatewayRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onEditClick, onDeleteClick, onEnabledChange)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemGatewayRuleBinding,
        private val onEditClick: (GatewayRule) -> Unit,
        private val onDeleteClick: (GatewayRule) -> Unit,
        private val onEnabledChange: (GatewayRule, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: GatewayRule) {
            binding.ruleNameText.text = rule.name
            val ruleType = rule.filters.firstOrNull() ?: "unknown"
            binding.ruleTypeChip.text = when (ruleType) {
                "dns" -> "DNS"
                "http" -> "HTTP"
                "l4" -> "网络 (L4)"
                else -> ruleType.uppercase()
            }
            binding.ruleDescriptionText.text = rule.description ?: "无描述"
            binding.ruleDescriptionText.visibility = if (rule.description.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.ruleActionText.text = "动作: ${getActionLabel(rule.action)}"
            binding.rulePrecedenceText.text = "优先级: ${rule.precedence ?: 0}"
            
            // Set switch without triggering listener
            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = rule.enabled ?: true
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChange(rule, isChecked)
            }

            binding.editButton.setOnClickListener { onEditClick(rule) }
            binding.deleteButton.setOnClickListener { onDeleteClick(rule) }
        }

        private fun getActionLabel(action: String?): String {
            return when (action) {
                "allow" -> "允许"
                "block" -> "阻止"
                "safe_search" -> "安全搜索"
                "ytrestricted" -> "YouTube 受限"
                "on" -> "开启"
                "off" -> "关闭"
                "isolate" -> "隔离"
                "noscan" -> "不扫描"
                else -> action ?: "未知"
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GatewayRule>() {
        override fun areItemsTheSame(oldItem: GatewayRule, newItem: GatewayRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GatewayRule, newItem: GatewayRule): Boolean {
            return oldItem == newItem
        }
    }
}
