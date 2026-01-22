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
            binding.deviceNameText.text = device.name ?: device.model ?: "未知设备"
            
            // Device type chip
            val deviceType = device.type ?: device.deviceType
            binding.deviceTypeChip.text = getDeviceTypeLabel(deviceType)
            binding.deviceTypeChip.setChipIconResource(getDeviceTypeIcon(deviceType))
            
            // Status chip
            val isRevoked = device.revokedAt != null
            binding.statusChip.text = if (isRevoked) "已撤销" else "活跃"
            binding.statusChip.setChipBackgroundColorResource(
                if (isRevoked) android.R.color.holo_red_light else android.R.color.holo_green_light
            )
            
            // User info
            val userEmail = device.user?.email ?: device.user?.name
            binding.userInfoText.text = "用户: ${userEmail ?: "未知"}"
            binding.userInfoText.visibility = if (userEmail != null) View.VISIBLE else View.GONE
            
            // IP address
            binding.ipAddressText.text = "IP: ${device.ip ?: "未知"}"
            binding.ipAddressText.visibility = if (device.ip != null) View.VISIBLE else View.GONE
            
            // Last updated
            val updated = device.updated ?: device.created
            binding.lastUpdatedText.text = "更新: ${formatDate(updated)}"
            
            // Revoke button - hide if already revoked
            binding.revokeButton.visibility = if (isRevoked) View.GONE else View.VISIBLE
            binding.revokeButton.setOnClickListener { onRevokeClick(device) }
            
            // Item click for detail dialog
            binding.root.setOnClickListener { onItemClick(device) }
        }

        private fun getDeviceTypeLabel(type: String?): String {
            return when (type?.lowercase()) {
                "windows" -> "Windows"
                "mac", "macos" -> "macOS"
                "linux" -> "Linux"
                "android" -> "Android"
                "ios" -> "iOS"
                "chromeos" -> "ChromeOS"
                else -> type?.uppercase() ?: "未知"
            }
        }

        private fun getDeviceTypeIcon(type: String?): Int {
            return when (type?.lowercase()) {
                "windows", "mac", "macos", "linux", "chromeos" -> android.R.drawable.ic_menu_myplaces
                "android", "ios" -> android.R.drawable.ic_menu_call
                else -> android.R.drawable.ic_menu_myplaces
            }
        }

        private fun formatDate(dateString: String?): String {
            if (dateString == null) return "未知"
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
