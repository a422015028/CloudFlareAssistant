package com.muort.upworker.feature.backup

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.muort.upworker.R
import java.text.SimpleDateFormat
import java.util.Locale

class BackupFilesAdapter(
    private val onRestoreClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<String, BackupFilesAdapter.BackupFileViewHolder>(BackupFileDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_backup_file, parent, false)
        return BackupFileViewHolder(view, onRestoreClick, onDeleteClick)
    }
    
    override fun onBindViewHolder(holder: BackupFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class BackupFileViewHolder(
        itemView: View,
        private val onRestoreClick: (String) -> Unit,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val fileNameText: TextView = itemView.findViewById(R.id.fileNameText)
        private val fileTimeText: TextView = itemView.findViewById(R.id.fileTimeText)
        private val restoreButton: MaterialButton = itemView.findViewById(R.id.restoreButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(fileName: String) {
            fileNameText.text = fileName
            
            // 解析文件名中的时间戳
            val timestamp = parseTimestamp(fileName)
            fileTimeText.text = if (timestamp != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                dateFormat.format(timestamp)
            } else {
                "未知时间"
            }
            
            restoreButton.setOnClickListener {
                onRestoreClick(fileName)
            }
            
            deleteButton.setOnClickListener {
                onDeleteClick(fileName)
            }
        }
        
        private fun parseTimestamp(fileName: String): Long? {
            // 文件名格式: cloudflare_backup_yyyyMMdd_HHmmss.json
            return try {
                val pattern = "cloudflare_backup_(\\d{8})_(\\d{6})\\.json".toRegex()
                val matchResult = pattern.find(fileName)
                if (matchResult != null) {
                    val dateStr = matchResult.groupValues[1]
                    val timeStr = matchResult.groupValues[2]
                    val fullStr = "$dateStr$timeStr"
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
                    dateFormat.parse(fullStr)?.time
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private class BackupFileDiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
        
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
