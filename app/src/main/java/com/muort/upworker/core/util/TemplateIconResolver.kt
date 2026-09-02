package com.muort.upworker.core.util

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.CatalogTemplate

/**
 * 模板智能图标匹配工具
 * 根据模板类型、标签、名称智能选择合适的 emoji 图标
 * 关键词从资源数组读取，支持国际化
 */
object TemplateIconResolver {

    /**
     * 获取模板的智能图标
     * 优先使用模板自身配置的 icon，否则根据类型和标签自动匹配
     */
    fun getIcon(context: Context, template: CatalogTemplate): String {
        val icon = template.icon
        if (!icon.isNullOrBlank() && icon.length <= 4) {
            return icon
        }
        return getSmartIcon(context, template)
    }

    /**
     * 根据模板类型和标签智能选择图标
     * 关键词从资源数组读取，无硬编码
     */
    private fun getSmartIcon(context: Context, template: CatalogTemplate): String {
        val tags = template.tags?.lowercase() ?: ""
        val name = template.name.lowercase()
        val combined = "$tags $name"
        val res = context.resources

        return when {
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_ai)) -> "🤖"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_proxy)) -> "🛡️"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_image)) -> "🖼️"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_mail)) -> "📧"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_shorturl)) -> "🔗"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_database)) -> "🗃️"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_storage)) -> "🗄️"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_subscribe)) -> "📋"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_mcp)) -> "🔌"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_accelerate)) -> "⚡"
            containsAny(combined, res.getStringArray(R.array.store_icon_keywords_monitor)) -> "📊"
            template.type == "pages" -> "🌐"
            template.type == "hybrid" -> "⚙️"
            else -> "📦"
        }
    }

    private fun containsAny(text: String, keywords: Array<String>): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }
}
