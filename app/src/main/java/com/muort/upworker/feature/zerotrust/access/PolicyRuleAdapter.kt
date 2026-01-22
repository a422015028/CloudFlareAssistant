package com.muort.upworker.feature.zerotrust.access

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.AccessRule
import com.muort.upworker.databinding.ItemPolicyRuleBinding

/**
 * Adapter for Policy Rules list
 */
class PolicyRuleAdapter(
    private val onDeleteClick: (AccessRule) -> Unit
) : ListAdapter<AccessRule, PolicyRuleAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPolicyRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemPolicyRuleBinding,
        private val onDeleteClick: (AccessRule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: AccessRule) {
            binding.ruleTypeText.text = getRuleTypeLabel(rule)
            binding.ruleValueText.text = getRuleValue(rule)

            binding.deleteRuleButton.setOnClickListener {
                onDeleteClick(rule)
            }
        }

        private fun getRuleTypeLabel(rule: AccessRule): String {
            return when {
                rule.email != null -> "Email"
                rule.emailDomain != null -> "Email Domain"
                rule.ip != null -> "IP Address"
                rule.everyone != null -> "Everyone"
                rule.accessGroup != null -> "Access Group"
                rule.geo != null -> "Geo Location"
                rule.commonName != null -> "Certificate CN"
                rule.ipList != null -> "IP List"
                rule.certificate != null -> "Certificate"
                rule.authMethod != null -> "Auth Method"
                rule.devicePosture != null -> "Device Posture"
                rule.serviceToken != null -> "Service Token"
                rule.anyValidServiceToken != null -> "Any Service Token"
                else -> "Unknown"
            }
        }

        private fun getRuleValue(rule: AccessRule): String {
            return when {
                rule.email != null -> rule.email["email"] ?: "N/A"
                rule.emailDomain != null -> rule.emailDomain["domain"] ?: "N/A"
                rule.ip != null -> rule.ip["ip"] ?: "N/A"
                rule.everyone != null -> "所有人"
                rule.accessGroup != null -> "Group ID: ${rule.accessGroup["id"]}"
                rule.geo != null -> "Country: ${rule.geo["country_code"]?.joinToString()}"
                rule.commonName != null -> rule.commonName["common_name"] ?: "N/A"
                rule.ipList != null -> "IP List: ${rule.ipList["id"]}"
                rule.certificate != null -> "Certificate"
                rule.authMethod != null -> rule.authMethod["auth_method"] ?: "N/A"
                rule.devicePosture != null -> "Posture: ${rule.devicePosture["integration_uid"]}"
                rule.serviceToken != null -> "Token: ${rule.serviceToken["token_id"]}"
                rule.anyValidServiceToken != null -> "Any Valid Token"
                else -> "N/A"
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AccessRule>() {
        override fun areItemsTheSame(oldItem: AccessRule, newItem: AccessRule): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: AccessRule, newItem: AccessRule): Boolean {
            return oldItem == newItem
        }
    }
}
