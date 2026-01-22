package com.muort.upworker.feature.zerotrust.access

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.AccessGroup
import com.muort.upworker.databinding.ItemAccessGroupBinding

/**
 * Adapter for Access Group list
 */
class AccessGroupAdapter(
    private val onDeleteClick: (AccessGroup) -> Unit
) : ListAdapter<AccessGroup, AccessGroupAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccessGroupBinding.inflate(
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
        private val binding: ItemAccessGroupBinding,
        private val onDeleteClick: (AccessGroup) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: AccessGroup) {
            binding.groupNameText.text = group.name

            val rulesInfo = buildString {
                append("Include: ${group.include.size} 规则")
                if (!group.exclude.isNullOrEmpty()) {
                    append(" • Exclude: ${group.exclude.size} 规则")
                }
                if (!group.require.isNullOrEmpty()) {
                    append(" • Require: ${group.require.size} 规则")
                }
            }
            binding.groupRulesText.text = rulesInfo

            binding.groupDefaultChip.visibility = if (group.isDefault == true) View.VISIBLE else View.GONE

            binding.deleteGroupButton.setOnClickListener {
                onDeleteClick(group)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AccessGroup>() {
        override fun areItemsTheSame(oldItem: AccessGroup, newItem: AccessGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AccessGroup, newItem: AccessGroup): Boolean {
            return oldItem == newItem
        }
    }
}
