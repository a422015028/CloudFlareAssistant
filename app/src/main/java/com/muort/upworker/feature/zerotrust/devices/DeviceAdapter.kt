package com.muort.upworker.feature.zerotrust.devices

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.Device
import com.muort.upworker.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onRevokeClick: (Device) -> Unit,
    private val onItemClick: (Device) -> Unit
) : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding, onRevokeClick, onItemClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onRevokeClick: (Device) -> Unit,
        private val onItemClick: (Device) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            val ctx = binding.root.context
            binding.deviceNameText.text = device.name ?: device.model ?: ctx.getString(R.string.zt_device_unknown_device)

            // Device type chip
            val deviceType = device.type ?: device.deviceType
            binding.deviceTypeChip.text = getDeviceTypeLabel(deviceType, ctx)
            binding.deviceTypeChip.setChipIconResource(getDeviceTypeIcon(deviceType))

            // Status chip
            val isRevoked = device.revokedAt != null
            binding.statusChip.text = if (isRevoked) ctx.getString(R.string.zt_device_status_revoked) else ctx.getString(R.string.zt_device_status_active)
            binding.statusChip.setChipBackgroundColorResource(
                if (isRevoked) android.R.color.holo_red_light else android.R.color.holo_green_light
            )

            // User info
            val userEmail = device.lastSeenUser?.email ?: device.user?.email
                ?: device.lastSeenUser?.name ?: device.user?.name
            val userUnknown = ctx.getString(R.string.zt_device_status_unknown)
            binding.userInfoText.text = ctx.getString(R.string.zt_device_user_label, userEmail ?: userUnknown)
            binding.userInfoText.visibility = if (userEmail != null) View.VISIBLE else View.GONE

            // Policy info - 最后活跃的设备配置文件
            val policyName = device.lastSeenRegistration?.policy?.name
                ?: device.policyName
                ?: ctx.getString(R.string.zt_access_detail_default)
            binding.policyNameText.text = ctx.getString(R.string.zt_device_policy_label, policyName)
            binding.policyNameText.visibility = View.VISIBLE

            // IP address
            val ipAddr = device.publicIp ?: device.ip
            binding.ipAddressText.text = ctx.getString(R.string.zt_device_ip_label, ipAddr ?: userUnknown)
            binding.ipAddressText.visibility = if (ipAddr != null) View.VISIBLE else View.GONE

            // Last seen
            val lastSeen = device.lastSeenAt ?: device.updatedAt ?: device.updated
            binding.lastUpdatedText.text = ctx.getString(R.string.zt_device_last_seen_label, formatDate(lastSeen, ctx))

            // Revoke button - hide if already revoked
            binding.revokeButton.visibility = if (isRevoked) View.GONE else View.VISIBLE
            binding.revokeButton.setOnClickListener { onRevokeClick(device) }

            // Item click for detail dialog
            binding.root.setOnClickListener { onItemClick(device) }
        }

        private fun getDeviceTypeLabel(type: String?, ctx: android.content.Context): String {
            return when (type?.lowercase()) {
                "windows" -> "Windows"
                "mac", "macos" -> "macOS"
                "linux" -> "Linux"
                "android" -> "Android"
                "ios" -> "iOS"
                "chromeos" -> "ChromeOS"
                else -> type?.uppercase() ?: ctx.getString(R.string.zt_device_status_unknown)
            }
        }

        private fun getDeviceTypeIcon(type: String?): Int {
            return when (type?.lowercase()) {
                "windows", "mac", "macos", "linux", "chromeos" -> android.R.drawable.ic_menu_myplaces
                "android", "ios" -> android.R.drawable.ic_menu_call
                else -> android.R.drawable.ic_menu_myplaces
            }
        }

        private fun formatDate(dateString: String?, ctx: android.content.Context): String {
            if (dateString == null) return ctx.getString(R.string.zt_device_status_unknown)
            return try {
                dateString.substring(0, 10)
            } catch (e: Exception) {
                dateString
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem == newItem
        }
    }
}
