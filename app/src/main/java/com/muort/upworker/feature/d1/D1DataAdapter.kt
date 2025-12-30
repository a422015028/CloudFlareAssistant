package com.muort.upworker.feature.d1

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R

class D1DataAdapter(private var columns: List<String>, private var rows: List<Map<String, Any?>>) : RecyclerView.Adapter<D1DataAdapter.RowViewHolder>() {

    fun updateData(newColumns: List<String>, newRows: List<Map<String, Any?>>) {
        this.columns = newColumns
        this.rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) 0 else 1 // 0 for header, 1 for data
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_d1_data_row, parent, false)
        return RowViewHolder(view)
    }

    override fun getItemCount(): Int = rows.size + 1 // +1 for header

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        if (position == 0) {
            holder.bindHeader(columns)
        } else {
            val row = rows[position - 1]
            holder.bindData(row, columns)
        }
    }

    class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val linearLayout = itemView as android.widget.LinearLayout

        private fun formatValue(value: Any?): String {
            return when (value) {
                null -> "NULL"
                is Number -> {
                    // 检查是否可能是时间戳（13位毫秒或10位秒）
                    val longValue = value.toLong()
                    val stringValue = value.toString()

                    // 如果是科学计数法或看起来像时间戳，尝试格式化为日期
                    if (stringValue.contains('E') || stringValue.contains('e') ||
                        (longValue > 1000000000000L && longValue < 2000000000000L) || // 13位毫秒时间戳
                        (longValue > 1000000000L && longValue < 2000000000L)) {   // 10位秒时间戳

                        try {
                            val timestamp = if (longValue > 2000000000L) {
                                // 毫秒时间戳
                                longValue
                            } else {
                                // 秒时间戳，转换为毫秒
                                longValue * 1000L
                            }

                            val date = java.util.Date(timestamp)
                            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            format.format(date)
                        } catch (e: Exception) {
                            // 如果转换失败，返回原始值
                            value.toString()
                        }
                    } else {
                        // 普通数字，直接显示
                        value.toString()
                    }
                }
                else -> value.toString()
            }
        }

        fun bindHeader(columns: List<String>) {
            setupColumns(columns.size)
            for (i in columns.indices) {
                val textView = linearLayout.getChildAt(i) as android.widget.TextView
                textView.text = columns[i]
                textView.setTypeface(null, android.graphics.Typeface.BOLD)
                textView.setBackgroundColor(0xFFEEEEEE.toInt())
                textView.gravity = android.view.Gravity.CENTER
                // 确保标题也能被选择
                textView.setTextIsSelectable(true)
            }
        }

        fun bindData(row: Map<String, Any?>, columns: List<String>) {
            setupColumns(columns.size)
            for (i in columns.indices) {
                val textView = linearLayout.getChildAt(i) as android.widget.TextView
                val rawValue = row[columns[i]]
                val displayValue = formatValue(rawValue)
                textView.text = displayValue
                textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                textView.setBackgroundColor(0xFFFFFFFF.toInt())
                textView.gravity = android.view.Gravity.START
                // 确保数据行也能被选择
                textView.setTextIsSelectable(true)
            }
        }

        private fun setupColumns(columnCount: Int) {
            // 移除多余的列
            while (linearLayout.childCount > columnCount) {
                linearLayout.removeViewAt(linearLayout.childCount - 1)
            }
            // 添加缺少的列
            while (linearLayout.childCount < columnCount) {
                val textView = android.widget.TextView(itemView.context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, // width 0 for weight
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f // weight 1 for equal distribution
                    )
                    setPadding(8, 8, 8, 8)
                    minWidth = 80
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    // 启用文本选择和复制功能
                    setTextIsSelectable(true)
                    // 设置文本颜色以确保选择效果可见
                    setTextColor(android.graphics.Color.BLACK)
                }
                linearLayout.addView(textView)
            }
        }
    }
}