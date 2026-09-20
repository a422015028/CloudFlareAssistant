package com.muort.upworker.feature.zerotrust.tunnels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.IngressRule
import com.muort.upworker.databinding.ItemIngressRuleBinding

/**
 * Ingress Rule 列表适配器
 */
class IngressRuleAdapter(
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val readOnly: Boolean = false
) : ListAdapter<IngressRule, IngressRuleAdapter.IngressRuleViewHolder>(DiffCallback()) {

    fun getRules(): List<IngressRule> = currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngressRuleViewHolder {
        val binding = ItemIngressRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return IngressRuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngressRuleViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class IngressRuleViewHolder(private val binding: ItemIngressRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: IngressRule, position: Int) {
            val context = binding.root.context

            // 判断是否为 catch-all 规则（hostname 为空）
            val isCatchAll = rule.hostname.isNullOrBlank()

            // Hostname + Path
            val hostPath = buildString {
                if (isCatchAll) {
                    append(context.getString(R.string.tunnel_catch_all_rule))
                } else {
                    append(rule.hostname)
                    if (!rule.path.isNullOrBlank()) {
                        append(rule.path)
                    }
                }
            }
            binding.hostPathText.text = hostPath

            // Service
            binding.serviceText.text = rule.service

            // Catch-all chip
            binding.catchAllChip.visibility = if (isCatchAll) View.VISIBLE else View.GONE

            // 只读模式下隐藏操作按钮
            if (readOnly) {
                binding.editButton.visibility = View.GONE
                binding.deleteButton.visibility = View.GONE
            } else {
                binding.editButton.visibility = View.VISIBLE
                // catch-all 规则不允许删除
                binding.deleteButton.visibility = if (isCatchAll) View.GONE else View.VISIBLE

                binding.editButton.setOnClickListener {
                    onEdit(adapterPosition)
                }
                binding.deleteButton.setOnClickListener {
                    onDelete(adapterPosition)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<IngressRule>() {
        override fun areItemsTheSame(oldItem: IngressRule, newItem: IngressRule): Boolean {
            return oldItem.hostname == newItem.hostname
                && oldItem.path == newItem.path
                && oldItem.service == newItem.service
        }

        override fun areContentsTheSame(oldItem: IngressRule, newItem: IngressRule): Boolean {
            return oldItem == newItem
        }
    }
}
