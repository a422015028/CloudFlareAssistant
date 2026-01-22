package com.muort.upworker.feature.zerotrust.tunnels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.CloudflareTunnel
import com.muort.upworker.databinding.ItemTunnelBinding

class TunnelAdapter(
    private val onDeleteClick: (CloudflareTunnel) -> Unit,
    private val onItemClick: (CloudflareTunnel) -> Unit,
    private val onConfigClick: (CloudflareTunnel) -> Unit
) : ListAdapter<CloudflareTunnel, TunnelAdapter.TunnelViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TunnelViewHolder {
        val binding = ItemTunnelBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TunnelViewHolder(binding, onDeleteClick, onItemClick, onConfigClick)
    }

    override fun onBindViewHolder(holder: TunnelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TunnelViewHolder(
        private val binding: ItemTunnelBinding,
        private val onDeleteClick: (CloudflareTunnel) -> Unit,
        private val onItemClick: (CloudflareTunnel) -> Unit,
        private val onConfigClick: (CloudflareTunnel) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tunnel: CloudflareTunnel) {
            binding.tunnelNameText.text = tunnel.name
            
            // Status chip
            val status = tunnel.status ?: "unknown"
            binding.statusChip.text = getStatusLabel(status)
            binding.statusChip.setChipBackgroundColorResource(getStatusColor(status))
            
            // Tunnel type
            val tunnelType = tunnel.tunType ?: "cfd_tunnel"
            binding.tunnelTypeChip.text = getTunnelTypeLabel(tunnelType)
            
            // Connections
            val connectionCount = tunnel.connections?.size ?: 0
            binding.connectionsText.text = "连接数: $connectionCount"
            
            // Connection details
            val colos = tunnel.connections?.mapNotNull { it.coloName }?.distinct()
            if (!colos.isNullOrEmpty()) {
                binding.connectionDetailsText.text = "Colo: ${colos.joinToString(", ")}"
                binding.connectionDetailsText.visibility = View.VISIBLE
            } else {
                binding.connectionDetailsText.visibility = View.GONE
            }
            
            // Created date
            binding.createdDateText.text = "创建: ${formatDate(tunnel.createdAt)}"
            
            // Delete button - only show if not deleted
            val isDeleted = tunnel.deletedAt != null
            binding.deleteButton.visibility = if (isDeleted) View.GONE else View.VISIBLE
            binding.deleteButton.setOnClickListener { onDeleteClick(tunnel) }
            
            // Config button - only for remote config tunnels that are not deleted
            val isRemoteConfig = tunnel.remoteConfig == true
            binding.configButton.visibility = if (isRemoteConfig && !isDeleted) View.VISIBLE else View.GONE
            binding.configButton.setOnClickListener { onConfigClick(tunnel) }
            
            // Item click for detail
            binding.root.setOnClickListener { onItemClick(tunnel) }
        }

        private fun getStatusLabel(status: String): String {
            return when (status.lowercase()) {
                "active" -> "活跃"
                "inactive" -> "未活跃"
                "degraded" -> "降级"
                "down" -> "离线"
                else -> status
            }
        }

        private fun getStatusColor(status: String): Int {
            return when (status.lowercase()) {
                "active" -> android.R.color.holo_green_light
                "inactive" -> android.R.color.darker_gray
                "degraded" -> android.R.color.holo_orange_light
                "down" -> android.R.color.holo_red_light
                else -> android.R.color.darker_gray
            }
        }

        private fun getTunnelTypeLabel(type: String): String {
            return when (type) {
                "cfd_tunnel" -> "cloudflared"
                "warp_connector" -> "WARP Connector"
                else -> type
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

    private class DiffCallback : DiffUtil.ItemCallback<CloudflareTunnel>() {
        override fun areItemsTheSame(oldItem: CloudflareTunnel, newItem: CloudflareTunnel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CloudflareTunnel, newItem: CloudflareTunnel): Boolean {
            return oldItem == newItem
        }
    }
}
