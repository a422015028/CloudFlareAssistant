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
    private val onRollbackClick: (WorkerVersion) -> Unit,
    private val onDeleteClick: (WorkerVersion) -> Unit
) : RecyclerView.Adapter<WorkerHistoryAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val revisionIdText = itemView.findViewById<TextView>(R.id.revisionIdText)
        private val revisionSourceText = itemView.findViewById<TextView>(R.id.revisionSourceText)
        private val revisionTimeText = itemView.findViewById<TextView>(R.id.revisionTimeText)
        private val runningBadge = itemView.findViewById<LinearLayout>(R.id.runningBadge)
        private val rollbackBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.rollbackBtn)
        private val deleteBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteBtn)

        fun bind(version: WorkerVersion) {
            val shortId = version.id.take(8)
            revisionIdText.text = "#${version.number} - $shortId"
            
            val source = version.metadata?.source ?: "unknown"
            val sourceText = when (source.lowercase()) {
                "api" -> "来源: api"
                "dash" -> "来源: dash"
                "wrangler" -> "来源: wrangler"
                else -> "来源: $source"
            }
            revisionSourceText.text = sourceText
            
            revisionTimeText.text = formatDate(version.metadata?.createdOn)
            
            val isRunning = version.id == runningVersionId || version.number == versions.firstOrNull()?.number
            runningBadge.visibility = if (isRunning) View.VISIBLE else View.GONE
            
            rollbackBtn.visibility = if (isRunning) View.GONE else View.VISIBLE
            deleteBtn.visibility = if (isRunning) View.GONE else View.VISIBLE
            
            itemView.setOnClickListener {
                onItemClick(version)
            }
            
            rollbackBtn.setOnClickListener {
                onRollbackClick(version)
            }
            
            deleteBtn.setOnClickListener {
                onDeleteClick(version)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(versions[position])
    }

    override fun getItemCount() = versions.size
}