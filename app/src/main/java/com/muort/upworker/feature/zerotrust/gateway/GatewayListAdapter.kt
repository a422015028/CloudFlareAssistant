package com.muort.upworker.feature.zerotrust.gateway

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.GatewayList
import com.muort.upworker.databinding.ItemGatewayListBinding

class GatewayListAdapter(
    private val onEditClick: (GatewayList) -> Unit,
    private val onDeleteClick: (GatewayList) -> Unit
) : ListAdapter<GatewayList, GatewayListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGatewayListBinding.inflate(
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
        private val binding: ItemGatewayListBinding,
        private val onEditClick: (GatewayList) -> Unit,
        private val onDeleteClick: (GatewayList) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(list: GatewayList) {
            binding.listNameText.text = list.name
            binding.listTypeChip.text = when (list.type) {
                "DOMAIN" -> "域名"
                "IP" -> "IP 地址"
                "URL" -> "URL"
                else -> list.type
            }
            binding.listDescriptionText.text = list.description ?: "无描述"
            binding.itemCountText.text = "项目数: ${list.count ?: 0}"

            binding.editButton.setOnClickListener { onEditClick(list) }
            binding.deleteButton.setOnClickListener { onDeleteClick(list) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GatewayList>() {
        override fun areItemsTheSame(oldItem: GatewayList, newItem: GatewayList): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GatewayList, newItem: GatewayList): Boolean {
            return oldItem == newItem
        }
    }
}
