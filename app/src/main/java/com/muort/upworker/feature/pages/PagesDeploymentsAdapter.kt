package com.muort.upworker.feature.pages

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.PagesDeployment

class PagesDeploymentsAdapter(
    private val deployments: List<PagesDeployment>,
    private val runningDeploymentId: String?,
    private val formatDate: (String?) -> String,
    private val onItemClick: (PagesDeployment) -> Unit,
    private val onRollbackClick: (PagesDeployment) -> Unit,
    private val onRetryClick: (PagesDeployment) -> Unit
) : RecyclerView.Adapter<PagesDeploymentsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deploymentInfoText = itemView.findViewById<TextView>(R.id.deploymentInfoText)
        private val deploymentSourceText = itemView.findViewById<TextView>(R.id.deploymentSourceText)
        private val deploymentTimeText = itemView.findViewById<TextView>(R.id.deploymentTimeText)
        private val runningBadge = itemView.findViewById<LinearLayout>(R.id.runningBadge)
        private val rollbackBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.rollbackBtn)
        private val retryBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.retryBtn)

        fun bind(deployment: PagesDeployment, position: Int) {
            val status = deployment.latestStage?.status ?: "unknown"
            val statusText = when (status.lowercase()) {
                "success" -> "成功"
                "failed", "failure" -> "失败"
                else -> status
            }
            val shortId = deployment.shortId ?: deployment.id.take(8)
            val time = formatDate(deployment.createdOn)
            
            deploymentInfoText.text = "#${deployments.size - position} - $shortId ($statusText)"
            deploymentTimeText.text = time
            
            val triggerType = deployment.deploymentTrigger?.type ?: "unknown"
            val sourceText = when (triggerType.lowercase()) {
                "api" -> "来源: api"
                "dash" -> "来源: dash"
                "wrangler" -> "来源: wrangler"
                "github" -> "来源: github"
                "gitlab" -> "来源: gitlab"
                else -> "来源: $triggerType"
            }
            deploymentSourceText.text = sourceText
            
            val isRunning = deployment.id == runningDeploymentId
            runningBadge.visibility = if (isRunning) View.VISIBLE else View.GONE
            
            rollbackBtn.visibility = if (!isRunning && status == "success") {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            retryBtn.visibility = View.VISIBLE

            rollbackBtn.setOnClickListener {
                onRollbackClick(deployment)
            }

            retryBtn.setOnClickListener {
                onRetryClick(deployment)
            }
            
            itemView.setOnClickListener {
                onItemClick(deployment)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pages_deployment, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(deployments[position], position)
    }

    override fun getItemCount(): Int = deployments.size
}