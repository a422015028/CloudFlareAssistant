package com.muort.upworker.feature.zerotrust.tunnels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.NetworkRouteItem
import com.muort.upworker.databinding.ItemTeamnetRouteBinding

class TeamnetRouteAdapter(
    private val onDeleteClick: (NetworkRouteItem) -> Unit
) : ListAdapter<NetworkRouteItem, TeamnetRouteAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamnetRouteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTeamnetRouteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(route: NetworkRouteItem) {
            binding.networkTextView.text = route.displayText
            binding.routeTypeChip.text = route.typeLabel

            binding.commentTextView.text = route.comment
            binding.commentTextView.visibility =
                if (route.comment.isNullOrBlank()) android.view.View.GONE
                else android.view.View.VISIBLE

            binding.deleteButton.setOnClickListener {
                onDeleteClick(route)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NetworkRouteItem>() {
        override fun areItemsTheSame(oldItem: NetworkRouteItem, newItem: NetworkRouteItem): Boolean {
            return oldItem.id == newItem.id && oldItem::class == newItem::class
        }

        override fun areContentsTheSame(oldItem: NetworkRouteItem, newItem: NetworkRouteItem): Boolean {
            return oldItem == newItem
        }
    }
}
