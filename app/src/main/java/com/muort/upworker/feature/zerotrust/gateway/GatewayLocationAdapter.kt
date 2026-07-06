package com.muort.upworker.feature.zerotrust.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.GatewayLocation
import com.muort.upworker.databinding.ItemGatewayLocationBinding

class GatewayLocationAdapter(
    private val onEditClick: (GatewayLocation) -> Unit,
    private val onDeleteClick: (GatewayLocation) -> Unit
) : ListAdapter<GatewayLocation, GatewayLocationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGatewayLocationBinding.inflate(
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
        private val binding: ItemGatewayLocationBinding,
        private val onEditClick: (GatewayLocation) -> Unit,
        private val onDeleteClick: (GatewayLocation) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(location: GatewayLocation) {
            binding.locationNameText.text = location.name
            
            binding.defaultChip.visibility = if (location.clientDefault == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            binding.ecsChip.visibility = if (location.ecsSupport == true) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            val networks = location.networks?.joinToString(", ") { it.network } ?: "无"
            binding.locationNetworksText.text = "来源 IP 白名单: $networks"
            
            binding.locationClientsText.text = "客户端: ${location.clientCount ?: 0}"
            
            val ipv4 = location.ipv4Destination
            val ipv6 = location.ip
            
            binding.dnsIpv4Text.text = "IPv4: ${ipv4 ?: "未分配"}"
            binding.dnsIpv6Text.text = "IPv6: ${ipv6 ?: "未分配"}"
            binding.dnsDohText.text = "DoH: ${location.dohSubdomain?.let { "https://$it.cloudflare-gateway.com/dns-query" } ?: "未分配"}"

            binding.dnsIpv4Text.setOnClickListener {
                if (!ipv4.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, ipv4, "IPv4 地址")
                }
            }
            
            binding.dnsIpv6Text.setOnClickListener {
                if (!ipv6.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, ipv6, "IPv6 地址")
                }
            }
            
            binding.dnsDohText.setOnClickListener {
                val dohUrl = location.dohSubdomain?.let { "https://$it.cloudflare-gateway.com/dns-query" }
                if (!dohUrl.isNullOrBlank()) {
                    copyToClipboard(binding.root.context, dohUrl, "DoH 地址")
                }
            }

            binding.editButton.setOnClickListener { onEditClick(location) }
            
            binding.deleteButton.setOnClickListener {
                if (location.clientDefault == true) {
                    Toast.makeText(binding.root.context, "无法删除默认位置，请先设置其他位置为默认", Toast.LENGTH_SHORT).show()
                } else {
                    onDeleteClick(location)
                }
            }
        }
        
        private fun copyToClipboard(context: Context, text: String, label: String) {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(label, text)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(context, "$label 已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GatewayLocation>() {
        override fun areItemsTheSame(oldItem: GatewayLocation, newItem: GatewayLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GatewayLocation, newItem: GatewayLocation): Boolean {
            return oldItem == newItem
        }
    }
}