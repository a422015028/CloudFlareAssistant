package com.muort.upworker.feature.zerotrust.tunnels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.TunnelConnection
import com.muort.upworker.databinding.ItemTunnelConnectionBinding
import java.text.SimpleDateFormat
import java.util.*

class TunnelConnectionAdapter : ListAdapter<TunnelConnection, TunnelConnectionAdapter.ConnectionViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConnectionViewHolder {
        val binding = ItemTunnelConnectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ConnectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConnectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ConnectionViewHolder(
        private val binding: ItemTunnelConnectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(connection: TunnelConnection) {
            // Colo name (datacenter)
            binding.coloNameText.text = connection.coloName ?: "Unknown Colo"
            
            // Connection status
            val isPendingReconnect = connection.isPendingReconnect == true
            binding.connectionStatusChip.text = if (isPendingReconnect) "重连中" else "已连接"
            binding.connectionStatusChip.setChipBackgroundColorResource(
                if (isPendingReconnect) android.R.color.holo_orange_light else android.R.color.holo_green_light
            )
            
            // Client info
            val clientVersion = connection.clientVersion ?: "Unknown"
            binding.clientInfoText.text = "cloudflared $clientVersion"
            
            // Origin IP
            val originIp = connection.originIp
            if (!originIp.isNullOrBlank()) {
                binding.originIpText.text = "源 IP: $originIp"
            } else {
                binding.originIpText.text = "源 IP: N/A"
            }
            
            // Opened at
            binding.openedAtText.text = "连接时间: ${formatDateTime(connection.openedAt)}"
        }

        private fun formatDateTime(dateString: String?): String {
            if (dateString == null) return "N/A"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString.substringBefore(".").substringBefore("Z"))
                outputFormat.format(date!!)
            } catch (e: Exception) {
                dateString.substringBefore("T")
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<TunnelConnection>() {
        override fun areItemsTheSame(oldItem: TunnelConnection, newItem: TunnelConnection): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TunnelConnection, newItem: TunnelConnection): Boolean {
            return oldItem == newItem
        }
    }
}
