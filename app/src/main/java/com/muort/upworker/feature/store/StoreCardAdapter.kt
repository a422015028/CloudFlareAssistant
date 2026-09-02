package com.muort.upworker.feature.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.muort.upworker.R
import com.muort.upworker.core.model.CatalogBinding
import com.muort.upworker.core.model.TemplateItem
import com.muort.upworker.core.repository.CatalogRepository
import com.muort.upworker.core.util.TemplateIconResolver

/**
 * 模板商店卡片适配器
 * 展示模板网格列表
 */
class StoreCardAdapter(
    private val onItemClick: (TemplateItem) -> Unit,
    private val onDeployClick: (TemplateItem) -> Unit,
    private val onFavoriteClick: (TemplateItem) -> Unit,
    private val catalogRepository: CatalogRepository? = null
) : ListAdapter<TemplateItem, StoreCardAdapter.ViewHolder>(TemplateDiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView.findViewById<MaterialCardView>(R.id.cardView)
        private val iconText = itemView.findViewById<TextView>(R.id.iconText)
        private val nameText = itemView.findViewById<TextView>(R.id.nameText)
        private val descriptionText = itemView.findViewById<TextView>(R.id.descriptionText)
        private val typeChip = itemView.findViewById<Chip>(R.id.typeChip)
        private val versionText = itemView.findViewById<TextView>(R.id.versionText)
        private val githubBtn = itemView.findViewById<ImageView>(R.id.githubBtn)
        private val badgesScrollView = itemView.findViewById<View>(R.id.badgesScrollView)
        private val authorText = itemView.findViewById<TextView>(R.id.authorText)
        private val sourceCountText = itemView.findViewById<TextView>(R.id.sourceCountText)
        private val favoriteBtn = itemView.findViewById<MaterialButton>(R.id.favoriteBtn)
        private val deployBtn = itemView.findViewById<MaterialButton>(R.id.deployBtn)
        private val badgesContainer = itemView.findViewById<ChipGroup>(R.id.badgesContainer)
        private val badgeKv = itemView.findViewById<Chip>(R.id.badgeKv)
        private val badgeD1 = itemView.findViewById<Chip>(R.id.badgeD1)
        private val badgeR2 = itemView.findViewById<Chip>(R.id.badgeR2)
        private val badgeAi = itemView.findViewById<Chip>(R.id.badgeAi)
        private val badgeVar = itemView.findViewById<Chip>(R.id.badgeVar)
        private val badgeNone = itemView.findViewById<Chip>(R.id.badgeNone)

        fun bind(item: TemplateItem) {
            val context = itemView.context
            val template = item.template

            // 智能图标：优先使用模板配置的 icon，否则根据类型和标签自动选择
            iconText.text = TemplateIconResolver.getIcon(context, template)

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

            // GitHub 按钮：优先取 homepage，其次取 sourceUrl（仅 GitHub 链接时显示）
            val githubUrl = resolveGithubUrl(template)
            if (githubUrl != null) {
                githubBtn.visibility = View.VISIBLE
                githubBtn.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    context.startActivity(intent)
                }
            } else {
                githubBtn.visibility = View.GONE
                githubBtn.setOnClickListener(null)
            }

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

            // 绑定类型徽标
            setupBindingBadges(item)

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

        /**
         * 设置绑定类型徽标
         * 使用 Chip 组件，自动跟随 Material3 动态配色
         * 无绑定模板显示"无绑定"标签
         */
        private fun setupBindingBadges(item: TemplateItem) {
            val context = itemView.context
            badgesScrollView.visibility = View.VISIBLE
            val bindings = catalogRepository?.parseBindings(item.template.bindingsJson)
                ?: emptyList()

            val bindingTypes = bindings.map { it.type }.toSet()
            val hasBindings = bindingTypes.isNotEmpty()

            // 有绑定类型时显示对应 Chip，无绑定时显示"无绑定"
            if (hasBindings) {
                badgeKv.visibility = if (bindingTypes.contains("kv")) {
                    badgeKv.text = context.getString(R.string.store_badge_kv)
                    View.VISIBLE
                } else View.GONE

                badgeD1.visibility = if (bindingTypes.contains("d1")) {
                    badgeD1.text = context.getString(R.string.store_badge_d1)
                    View.VISIBLE
                } else View.GONE

                badgeR2.visibility = if (bindingTypes.contains("r2")) {
                    badgeR2.text = context.getString(R.string.store_badge_r2)
                    View.VISIBLE
                } else View.GONE

                badgeAi.visibility = if (bindingTypes.contains("ai")) {
                    badgeAi.text = context.getString(R.string.store_badge_ai)
                    View.VISIBLE
                } else View.GONE

                badgeVar.visibility = if (bindingTypes.contains("var")) {
                    badgeVar.text = context.getString(R.string.store_badge_var)
                    View.VISIBLE
                } else View.GONE

                badgeNone.visibility = View.GONE
            } else {
                // 无绑定：显示"无绑定"标签
                badgeKv.visibility = View.GONE
                badgeD1.visibility = View.GONE
                badgeR2.visibility = View.GONE
                badgeAi.visibility = View.GONE
                badgeVar.visibility = View.GONE
                badgeNone.visibility = View.VISIBLE
                badgeNone.text = context.getString(R.string.store_badge_none)
            }
        }
    }

    /**
     * 解析模板的 GitHub 链接
     * 优先级：homepage > sourceUrl > workerSourceUrl
     * 仅当链接是 GitHub 仓库时才返回
     */
    private fun resolveGithubUrl(
        template: com.muort.upworker.core.model.CatalogTemplate
    ): String? {
        val candidates = listOfNotNull(
            template.homepage,
            template.sourceUrl,
            template.workerSourceUrl
        )
        return candidates.firstOrNull { url ->
            url.contains("github.com", ignoreCase = true)
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
