package com.muort.upworker.feature.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.WorkerVersion

class WorkerHistoryAdapter(
    private val versions: List<WorkerVersion>,
    private val runningVersionId: String?,
    private val formatDate: (String?) -> String,
    private val onItemClick: (WorkerVersion) -> Unit,
    private val onDeleteClick: (WorkerVersion) -> Unit
) : RecyclerView.Adapter<WorkerHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val revisionIdText = itemView.findViewById<TextView>(R.id.revisionIdText)
        private val revisionSourceText = itemView.findViewById<TextView>(R.id.revisionSourceText)
        private val revisionTimeText = itemView.findViewById<TextView>(R.id.revisionTimeText)
        private val runningBadge = itemView.findViewById<LinearLayout>(R.id.runningBadge)
        private val deleteBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteBtn)

        fun bind(version: WorkerVersion, position: Int) {
            val shortId = version.id.take(8)
            val time = formatDate(version.metadata?.createdOn)
            
            revisionIdText.text = "#${versions.size - position} - $shortId"
            revisionTimeText.text = time
            
            val source = version.metadata?.source ?: "unknown"
            revisionSourceText.text = "来源: $source"
            
            val isRunning = version.id == runningVersionId
            runningBadge.visibility = if (isRunning) View.VISIBLE else View.GONE
            
            deleteBtn.visibility = if (isRunning) View.GONE else View.VISIBLE
            
            deleteBtn.setOnClickListener {
                onDeleteClick(version)
            }
            
            itemView.setOnClickListener {
                onItemClick(version)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_history, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(versions[position], position)
    }

    override fun getItemCount(): Int = versions.size
}