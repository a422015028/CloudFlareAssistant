package com.muort.upworker.feature.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.muort.upworker.R
import com.muort.upworker.core.model.TemplateItem

/**
 * 模板商店卡片适配器
 * 展示模板网格列表
 */
class StoreCardAdapter(
    private val onItemClick: (TemplateItem) -> Unit,
    private val onDeployClick: (TemplateItem) -> Unit,
    private val onFavoriteClick: (TemplateItem) -> Unit
) : ListAdapter<TemplateItem, StoreCardAdapter.ViewHolder>(TemplateDiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardView)
        private val iconText = itemView.findViewById<TextView>(R.id.iconText)
        private val nameText = itemView.findViewById<TextView>(R.id.nameText)
        private val descriptionText = itemView.findViewById<TextView>(R.id.descriptionText)
        private val typeChip = itemView.findViewById<Chip>(R.id.typeChip)
        private val versionText = itemView.findViewById<TextView>(R.id.versionText)
        private val authorText = itemView.findViewById<TextView>(R.id.authorText)
        private val sourceCountText = itemView.findViewById<TextView>(R.id.sourceCountText)
        private val favoriteBtn = itemView.findViewById<MaterialButton>(R.id.favoriteBtn)
        private val deployBtn = itemView.findViewById<MaterialButton>(R.id.deployBtn)

        fun bind(item: TemplateItem) {
            val context = itemView.context
            val template = item.template

            // 图标（优先显示 emoji，否则显示默认图标）
            val icon = template.icon
            if (!icon.isNullOrBlank() && icon.length <= 4) {
                iconText.text = icon
            } else {
                iconText.text = "📦"
            }

            // 名称
            nameText.text = template.name

            // 描述
            descriptionText.text = template.description
            descriptionText.visibility = if (template.description.isNullOrBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            // 类型标签
            val typeText = when (template.type) {
                "worker" -> context.getString(R.string.store_worker)
                "pages" -> context.getString(R.string.store_pages)
                "hybrid" -> context.getString(R.string.store_hybrid)
                else -> template.type
            }
            typeChip.text = typeText

            // 版本
            versionText.text = context.getString(R.string.store_version_prefix, template.version)

            // 作者
            val authorName = template.authorName
            if (authorName.isNullOrBlank()) {
                authorText.visibility = View.GONE
            } else {
                authorText.visibility = View.VISIBLE
                authorText.text = context.getString(R.string.store_by_author, authorName)
            }

            // 来源名称
            val sourceName = item.template.sourceName
            sourceCountText.text = if (item.sourceCount > 1) {
                context.getString(R.string.store_source_count_multi, sourceName, item.sourceCount)
            } else {
                context.getString(R.string.store_source_count, sourceName)
            }

            // 收藏状态
            if (item.isFavorite) {
                favoriteBtn.setIconResource(R.drawable.ic_favorite_24)
                favoriteBtn.contentDescription = context.getString(R.string.store_remove_favorite)
            } else {
                favoriteBtn.setIconResource(R.drawable.ic_favorite_border_24)
                favoriteBtn.contentDescription = context.getString(R.string.store_add_favorite)
            }

            // 点击事件
            cardView.setOnClickListener { onItemClick(item) }
            deployBtn.setOnClickListener { onDeployClick(item) }
            favoriteBtn.setOnClickListener { onFavoriteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_store_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * DiffUtil 回调
     */
    class TemplateDiffCallback : DiffUtil.ItemCallback<TemplateItem>() {
        override fun areItemsTheSame(oldItem: TemplateItem, newItem: TemplateItem): Boolean {
            return oldItem.template.templateId == newItem.template.templateId
        }

        override fun areContentsTheSame(oldItem: TemplateItem, newItem: TemplateItem): Boolean {
            return oldItem == newItem
        }
    }
}
