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
    private val onEditClick: (AccessGroup) -> Unit,
    private val onDeleteClick: (AccessGroup) -> Unit
) : ListAdapter<AccessGroup, AccessGroupAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccessGroupBinding.inflate(
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
        private val binding: ItemAccessGroupBinding,
        private val onEditClick: (AccessGroup) -> Unit,
        private val onDeleteClick: (AccessGroup) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: AccessGroup) {
            binding.groupNameText.text = group.name

            val rulesInfo = buildString {
                append("包含规则: ${group.include.size}")
                if (!group.exclude.isNullOrEmpty()) {
                    append(" • 排除规则: ${group.exclude.size}")
                }
                if (!group.require.isNullOrEmpty()) {
                    append(" • 必须规则: ${group.require.size}")
                }
            }
            binding.groupRulesText.text = rulesInfo

            binding.groupDefaultChip.visibility = if (group.isDefault == true) View.VISIBLE else View.GONE

            binding.editGroupButton.setOnClickListener {
                onEditClick(group)
            }

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
