package com.muort.upworker.feature.zerotrust.posture

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.DevicePostureRule
import com.muort.upworker.databinding.ItemPostureRuleBinding

/**
 * Adapter for device posture rule list
 */
class PostureRuleAdapter(
    private val onCardClick: (DevicePostureRule) -> Unit,
    private val onEditClick: (DevicePostureRule) -> Unit,
    private val onDeleteClick: (DevicePostureRule) -> Unit
) : ListAdapter<DevicePostureRule, PostureRuleAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPostureRuleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onCardClick, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemPostureRuleBinding,
        private val onCardClick: (DevicePostureRule) -> Unit,
        private val onEditClick: (DevicePostureRule) -> Unit,
        private val onDeleteClick: (DevicePostureRule) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: DevicePostureRule) {
            val ctx = binding.root.context

            binding.ruleNameText.text = rule.name ?: ctx.getString(R.string.status_unknown)
            binding.ruleTypeChip.text = ctx.getString(PostureTypeSpecs.ruleLabel(rule.type))

            val platforms = rule.match.orEmpty().mapNotNull { match ->
                PostureTypeSpecs.platforms.firstOrNull { it.platform == match.platform }
                    ?.let { ctx.getString(it.labelRes) }
            }
            binding.rulePlatformsText.text =
                ctx.getString(R.string.zt_posture_platforms_label, platforms.joinToString(", "))

            binding.ruleScheduleText.text = if (rule.schedule.isNullOrBlank()) {
                ctx.getString(R.string.zt_posture_schedule_label, "5m")
            } else {
                ctx.getString(R.string.zt_posture_schedule_label, rule.schedule)
            }

            binding.root.setOnClickListener { onCardClick(rule) }
            binding.editRuleButton.setOnClickListener { onEditClick(rule) }
            binding.deleteRuleButton.setOnClickListener { onDeleteClick(rule) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DevicePostureRule>() {
        override fun areItemsTheSame(oldItem: DevicePostureRule, newItem: DevicePostureRule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DevicePostureRule, newItem: DevicePostureRule): Boolean {
            return oldItem == newItem
        }
    }
}
