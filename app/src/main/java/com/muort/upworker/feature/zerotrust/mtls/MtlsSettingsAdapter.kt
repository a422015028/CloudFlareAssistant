package com.muort.upworker.feature.zerotrust.mtls

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.MtlsCertificateSetting
import com.muort.upworker.databinding.ItemMtlsSettingBinding

/**
 * Adapter for mTLS per-hostname settings list
 */
class MtlsSettingsAdapter(
    private val onEditClick: (MtlsCertificateSetting) -> Unit,
    private val onDeleteClick: (MtlsCertificateSetting) -> Unit
) : ListAdapter<MtlsCertificateSetting, MtlsSettingsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMtlsSettingBinding.inflate(
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
        private val binding: ItemMtlsSettingBinding,
        private val onEditClick: (MtlsCertificateSetting) -> Unit,
        private val onDeleteClick: (MtlsCertificateSetting) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(setting: MtlsCertificateSetting) {
            val ctx = binding.root.context

            binding.settingHostnameText.text = setting.hostname

            val forwardingOn = setting.clientCertificateForwarding == true
            binding.settingForwardingText.text = ctx.getString(
                R.string.zt_mtls_setting_forwarding_state,
                ctx.getString(if (forwardingOn) R.string.app_log_switch_on else R.string.app_log_switch_off)
            )

            val chinaOn = setting.chinaNetwork == true
            binding.settingChinaText.text = ctx.getString(
                R.string.zt_mtls_setting_china_state,
                ctx.getString(if (chinaOn) R.string.app_log_switch_on else R.string.app_log_switch_off)
            )

            binding.editSettingButton.setOnClickListener { onEditClick(setting) }
            binding.deleteSettingButton.setOnClickListener { onDeleteClick(setting) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MtlsCertificateSetting>() {
        override fun areItemsTheSame(
            oldItem: MtlsCertificateSetting,
            newItem: MtlsCertificateSetting
        ): Boolean {
            return oldItem.hostname == newItem.hostname
        }

        override fun areContentsTheSame(
            oldItem: MtlsCertificateSetting,
            newItem: MtlsCertificateSetting
        ): Boolean {
            return oldItem == newItem
        }
    }
}
