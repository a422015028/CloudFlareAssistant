package com.muort.upworker.feature.zerotrust.access

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessApplication

/**
 * Adapter for Access Applications list
 */
class AccessApplicationAdapter(
    private val onItemClick: (AccessApplication) -> Unit,
    private val onDeleteClick: (AccessApplication) -> Unit
) : ListAdapter<AccessApplication, AccessApplicationAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_access_application, parent, false)
        return ViewHolder(view, onItemClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        itemView: View,
        private val onItemClick: (AccessApplication) -> Unit,
        private val onDeleteClick: (AccessApplication) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        private val nameTextView: TextView = itemView.findViewById(R.id.appNameTextView)
        private val domainTextView: TextView = itemView.findViewById(R.id.appDomainTextView)
        private val typeTextView: TextView = itemView.findViewById(R.id.appTypeTextView)
        private val deleteButton: View = itemView.findViewById(R.id.deleteButton)
        
        fun bind(app: AccessApplication) {
            nameTextView.text = app.name
            domainTextView.text = app.domain ?: itemView.context.getString(R.string.zt_access_domain_not_set)
            typeTextView.text = getTypeLabel(app.type)
            
            cardView.setOnClickListener { onItemClick(app) }
            deleteButton.setOnClickListener { onDeleteClick(app) }
        }
        
        private fun getTypeLabel(type: String): String {
            val ctx = itemView.context
            return when (type) {
                "self_hosted" -> ctx.getString(R.string.zt_app_type_self_hosted)
                "saas" -> ctx.getString(R.string.zt_app_type_saas)
                "ssh" -> ctx.getString(R.string.zt_app_type_ssh)
                "vnc" -> ctx.getString(R.string.zt_app_type_vnc)
                "app_launcher" -> ctx.getString(R.string.zt_app_type_app_launcher)
                "warp" -> ctx.getString(R.string.zt_app_type_warp)
                "biso" -> ctx.getString(R.string.zt_app_type_biso)
                "bookmark" -> ctx.getString(R.string.zt_app_type_bookmark)
                else -> type
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<AccessApplication>() {
        override fun areItemsTheSame(oldItem: AccessApplication, newItem: AccessApplication): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: AccessApplication, newItem: AccessApplication): Boolean {
            return oldItem == newItem
        }
    }
}
