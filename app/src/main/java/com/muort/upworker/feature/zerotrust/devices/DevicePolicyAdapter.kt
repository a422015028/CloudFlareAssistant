package com.muort.upworker.feature.zerotrust.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
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
            val ctx = binding.root.context
            val isDefault = policy.isDefault == true
            
            binding.policyNameText.text = if (isDefault) ctx.getString(R.string.zt_policy_default_profile) else (policy.name ?: ctx.getString(R.string.zt_policy_unnamed_profile))
            binding.policyDescriptionText.text = policy.description ?: (if (isDefault) ctx.getString(R.string.zt_policy_default_desc) else ctx.getString(R.string.zt_policy_no_desc))
            
            // Match rule - default policy doesn't have match
            binding.matchRuleText.text = if (isDefault) ctx.getString(R.string.zt_policy_match_all_devices) else ctx.getString(R.string.zt_policy_match_rule_format, policy.match ?: "any")
            
            // Settings
            binding.autoConnectText.text = ctx.getString(R.string.zt_policy_auto_connect_label, getAutoConnectLabel(policy.autoConnect, ctx))
            binding.modeSwitchText.text = if (policy.allowModeSwitch == true) ctx.getString(R.string.zt_policy_mode_switch_allow) else ctx.getString(R.string.zt_policy_mode_switch_deny)
            binding.precedenceText.text = if (isDefault) ctx.getString(R.string.zt_policy_precedence_default) else ctx.getString(R.string.zt_policy_precedence_label, policy.precedence ?: 0)
            
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

        private fun getAutoConnectLabel(autoConnect: Int?, ctx: android.content.Context): String {
            return when (autoConnect) {
                0 -> ctx.getString(R.string.zt_policy_auto_connect_off)
                1 -> ctx.getString(R.string.zt_policy_auto_connect_on)
                2 -> ctx.getString(R.string.zt_policy_auto_connect_force)
                else -> ctx.getString(R.string.zt_access_detail_default)
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
