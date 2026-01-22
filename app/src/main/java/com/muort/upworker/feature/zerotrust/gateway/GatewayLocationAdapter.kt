package com.muort.upworker.feature.zerotrust.gateway

import android.view.LayoutInflater
import android.view.ViewGroup
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
            
            val networks = location.networks?.joinToString(", ") { it.network } ?: "无网络"
            binding.locationNetworksText.text = "网络: $networks"
            
            binding.locationClientsText.text = "客户端: ${location.clientDefault ?: false}"
            
            val ipv4 = location.ipv4Destination
            val ipv6 = location.ip
            
            binding.dnsIpv4Text.text = "IPv4: ${ipv4 ?: "未分配"}"
            binding.dnsIpv6Text.text = "IPv6: ${ipv6 ?: "未分配"}"

            binding.editButton.setOnClickListener { onEditClick(location) }
            binding.deleteButton.setOnClickListener { onDeleteClick(location) }
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
