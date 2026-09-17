package com.muort.upworker.feature.worker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.Schedule
import com.muort.upworker.core.util.CronTimezoneConverter

class BuildTriggersAdapter(
    private val schedules: List<Schedule>,
    private val formatDate: (String?) -> String,
    private val onDeleteClick: (Schedule) -> Unit
) : RecyclerView.Adapter<BuildTriggersAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val triggerNameText = itemView.findViewById<TextView>(R.id.triggerNameText)
        private val triggerCommandText = itemView.findViewById<TextView>(R.id.triggerCommandText)
        private val deleteBtn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteBtn)

        fun bind(schedule: Schedule, position: Int) {
            // Cloudflare 返回的是 UTC 表达式，展示时转回 UTC+8
            val localCron = CronTimezoneConverter.toLocal(schedule.cron)
            triggerNameText.text = itemView.context.getString(R.string.format_trigger_name, position + 1, localCron)
            triggerCommandText.text = formatDate(schedule.createdOn)
            
            deleteBtn.setOnClickListener {
                onDeleteClick(schedule)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_build_trigger, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(schedules[position], position)
    }

    override fun getItemCount(): Int = schedules.size
}