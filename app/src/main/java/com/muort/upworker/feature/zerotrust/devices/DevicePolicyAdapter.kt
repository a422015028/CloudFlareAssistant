package com.muort.upworker.feature.zerotrust.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.DeviceSettingsPolicy
import com.muort.upworker.databinding.ItemDevicePolicyBinding

class DevicePolicyAdapter(
    private val onEditClick: (DeviceSettingsPolicy) -> Unit,
    private val onDeleteClick: (DeviceSettingsPolicy) -> Unit,
    private val onEnabledChange: (DeviceSettingsPolicy, Boolean) -> Unit
) : ListAdapter<DeviceSettingsPolicy, DevicePolicyAdapter.PolicyViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PolicyViewHolder {
        val binding = ItemDevicePolicyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PolicyViewHolder(binding, onEditClick, onDeleteClick, onEnabledChange)
    }

    override fun onBindViewHolder(holder: PolicyViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PolicyViewHolder(
        private val binding: ItemDevicePolicyBinding,
        private val onEditClick: (DeviceSettingsPolicy) -> Unit,
        private val onDeleteClick: (DeviceSettingsPolicy) -> Unit,
        private val onEnabledChange: (DeviceSettingsPolicy, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(policy: DeviceSettingsPolicy) {
            binding.policyNameText.text = policy.name ?: "未命名策略"
            binding.policyDescriptionText.text = policy.description ?: "无描述"
            
            // Match rule
            binding.matchRuleText.text = "匹配规则: ${policy.match ?: "any"}"
            
            // Settings
            binding.autoConnectText.text = "自动连接: ${getAutoConnectLabel(policy.autoConnect)}"
            binding.modeSwitchText.text = "模式切换: ${if (policy.allowModeSwitch == true) "允许" else "禁止"}"
            binding.precedenceText.text = "优先级: ${policy.precedence ?: 0}"
            
            // Enabled switch
            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = policy.enabled ?: true
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChange(policy, isChecked)
            }
            
            binding.editButton.setOnClickListener { onEditClick(policy) }
            binding.deleteButton.setOnClickListener { onDeleteClick(policy) }
        }

        private fun getAutoConnectLabel(autoConnect: Int?): String {
            return when (autoConnect) {
                0 -> "关闭"
                1 -> "开启"
                2 -> "强制"
                else -> "默认"
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DeviceSettingsPolicy>() {
        override fun areItemsTheSame(oldItem: DeviceSettingsPolicy, newItem: DeviceSettingsPolicy): Boolean {
            return oldItem.policyId == newItem.policyId
        }

        override fun areContentsTheSame(oldItem: DeviceSettingsPolicy, newItem: DeviceSettingsPolicy): Boolean {
            return oldItem == newItem
        }
    }
}
