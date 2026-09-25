package com.muort.upworker.feature.zerotrust.posture

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.PostureIntegration
import com.muort.upworker.databinding.ItemPostureIntegrationBinding

/**
 * Adapter for third-party posture integrations list
 */
class PostureIntegrationAdapter(
    private val onEditClick: (PostureIntegration) -> Unit,
    private val onDeleteClick: (PostureIntegration) -> Unit
) : ListAdapter<PostureIntegration, PostureIntegrationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPostureIntegrationBinding.inflate(
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
        private val binding: ItemPostureIntegrationBinding,
        private val onEditClick: (PostureIntegration) -> Unit,
        private val onDeleteClick: (PostureIntegration) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(integration: PostureIntegration) {
            val ctx = binding.root.context

            binding.integrationNameText.text = integration.name ?: ctx.getString(R.string.status_unknown)
            binding.integrationTypeChip.text =
                ctx.getString(PostureTypeSpecs.integrationLabel(integration.type))

            binding.integrationIntervalText.text =
                ctx.getString(R.string.zt_posture_integration_interval_label, integration.interval ?: "—")
            binding.integrationIdText.text = integration.id.orEmpty()

            binding.editIntegrationButton.setOnClickListener { onEditClick(integration) }
            binding.deleteIntegrationButton.setOnClickListener { onDeleteClick(integration) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PostureIntegration>() {
        override fun areItemsTheSame(oldItem: PostureIntegration, newItem: PostureIntegration): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PostureIntegration, newItem: PostureIntegration): Boolean {
            return oldItem == newItem
        }
    }
}
