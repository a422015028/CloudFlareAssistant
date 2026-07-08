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
            val isDefault = policy.isDefault == true
            
            binding.policyNameText.text = if (isDefault) "默认配置文件" else (policy.name ?: "未命名配置文件")
            binding.policyDescriptionText.text = policy.description ?: (if (isDefault) "应用于所有未匹配其他配置文件的设备" else "无描述")
            
            // Match rule - default policy doesn't have match
            binding.matchRuleText.text = if (isDefault) "匹配规则: 所有设备" else "匹配规则: ${policy.match ?: "any"}"
            
            // Settings
            binding.autoConnectText.text = "自动连接: ${getAutoConnectLabel(policy.autoConnect)}"
            binding.modeSwitchText.text = "模式切换: ${if (policy.allowModeSwitch == true) "允许" else "禁止"}"
            binding.precedenceText.text = if (isDefault) "优先级: 默认" else "优先级: ${policy.precedence ?: 0}"
            
            // Enabled switch
            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = policy.enabled ?: true
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onEnabledChange(policy, isChecked)
            }
            
            binding.editButton.setOnClickListener { onEditClick(policy) }
            
            // Default policy cannot be deleted
            if (isDefault) {
                binding.deleteButton.visibility = android.view.View.GONE
            } else {
                binding.deleteButton.visibility = android.view.View.VISIBLE
                binding.deleteButton.setOnClickListener { onDeleteClick(policy) }
            }
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
            val oldIsDefault = oldItem.isDefault == true
            val newIsDefault = newItem.isDefault == true
            if (oldIsDefault && newIsDefault) return true
            return oldItem.policyId == newItem.policyId
        }

        override fun areContentsTheSame(oldItem: DeviceSettingsPolicy, newItem: DeviceSettingsPolicy): Boolean {
            return oldItem == newItem
        }
    }
}
