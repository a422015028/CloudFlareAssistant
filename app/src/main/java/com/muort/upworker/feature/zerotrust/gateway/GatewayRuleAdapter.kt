package com.muort.upworker.feature.zerotrust.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
                "l4" -> "网络"
                else -> ruleType.uppercase()
            }
            
            binding.ruleActionChip.text = getActionLabel(rule.action)
            
            binding.ruleTrafficText.text = rule.traffic ?: "无匹配条件"
            binding.ruleTrafficText.visibility = if (rule.traffic.isNullOrBlank()) View.GONE else View.VISIBLE
            
            binding.ruleTrafficText.setOnClickListener {
                if (!rule.traffic.isNullOrBlank()) {
                    val clipboard = binding.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("规则表达式", rule.traffic)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(binding.root.context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
            }
            
            binding.rulePrecedenceText.text = "优先级: ${rule.precedence ?: 0}"
            
            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = rule.enabled
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
                "safesearch" -> "安全搜索"
                "ytrestricted" -> "YouTube限制"
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