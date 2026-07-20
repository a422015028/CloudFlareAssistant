package com.muort.upworker.feature.account

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.muort.upworker.R
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.ScopeCategory

/** 权限列表项（分组标题或权限组） */
sealed class PermissionItem {
    data class Header(val title: String) : PermissionItem()
    data class Group(val group: PermissionGroup, val category: ScopeCategory) : PermissionItem()
}

class PermissionGroupAdapter(
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val fullList = mutableListOf<PermissionItem>()
    private val filteredList = mutableListOf<PermissionItem>()
    private val selectedIds = mutableSetOf<String>()
    private var query: String = ""

    fun setData(items: List<PermissionItem>) {
        fullList.clear()
        fullList.addAll(items)
        applyFilter()
    }

    fun setQuery(q: String) {
        query = q.trim()
        applyFilter()
    }

    private fun applyFilter() {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(fullList)
        } else {
            // 仅保留名称/说明匹配的权限组；保留仍有可见子项的标题
            val matched = fullList.filterIsInstance<PermissionItem.Group>()
                .filter { matches(it.group, query) }
            val byCategory = matched.groupBy { it.category }
            ScopeCategory.values().forEach { cat ->
                val list = byCategory[cat]
                if (!list.isNullOrEmpty()) {
                    filteredList.add(PermissionItem.Header(titleFor(cat)))
                    filteredList.addAll(list)
                }
            }
        }
        notifyDataSetChanged()
    }

    private fun matches(g: PermissionGroup, q: String): Boolean {
        val ql = q.lowercase()
        return g.name.lowercase().contains(ql) ||
                (g.description?.lowercase()?.contains(ql) == true)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun selectedCount(): Int = selectedIds.size

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    /** 选中当前过滤后可见的全部权限组 */
    fun selectAllVisible() {
        filteredList.filterIsInstance<PermissionItem.Group>().forEach {
            selectedIds.add(it.group.id)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (filteredList[position] is PermissionItem.Header) TYPE_HEADER else TYPE_GROUP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_permission_header, parent, false))
        } else {
            GroupViewHolder(inflater.inflate(R.layout.item_permission_group, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = filteredList[position]
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as PermissionItem.Header)
            is GroupViewHolder -> holder.bind(item as PermissionItem.Group)
        }
    }

    override fun getItemCount(): Int = filteredList.size

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text: TextView = itemView.findViewById(R.id.headerText)
        fun bind(item: PermissionItem.Header) {
            text.text = item.title
        }
    }

    inner class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: MaterialCheckBox = itemView.findViewById(R.id.permCheckBox)
        private val desc: TextView = itemView.findViewById(R.id.permDescText)

        fun bind(item: PermissionItem.Group) {
            checkBox.text = item.group.name
            val d = item.group.description
            if (d.isNullOrBlank()) {
                desc.visibility = View.GONE
            } else {
                desc.visibility = View.VISIBLE
                desc.text = d
            }
            // 先移除监听，再设置选中状态，避免回调重入
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.group.id in selectedIds
            val id = item.group.id
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedIds.add(id) else selectedIds.remove(id)
                onSelectionChanged()
            }
            // 点击整行时切换复选框（由上面的监听同步 selectedIds，避免重复逻辑）
            itemView.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_GROUP = 1

        fun titleFor(cat: ScopeCategory): String = when (cat) {
            ScopeCategory.USER -> "用户权限（User）"
            ScopeCategory.ACCOUNT -> "账号权限（Account）"
            ScopeCategory.ZONE -> "域名权限（Zone）"
        }
    }
}
