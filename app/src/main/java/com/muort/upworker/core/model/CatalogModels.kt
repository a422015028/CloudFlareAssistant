package com.muort.upworker.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ==================== Catalog Source ====================

/**
 * Catalog 数据源
 * 对应 cf-manager 的 catalog_sources 表
 */
@Entity(tableName = "catalog_sources")
data class CatalogSource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,           // catalog.json 地址
    val name: String,          // 源名称（如"官方源"）
    val isDefault: Boolean = false,  // 是否为默认源
    val enabled: Boolean = true,     // 是否启用
    val lastSynced: Long? = null,    // 上次同步时间戳
    val lastStatus: String = "idle", // ok / error / loading / idle
    val lastError: String? = null,   // 上次错误信息
    val etag: String? = null,        // HTTP ETag 用于增量更新
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ==================== Catalog Template ====================

/**
 * Catalog 模板
 * 存储从各个数据源拉取的模板信息
 */
@Entity(
    tableName = "catalog_templates",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["type"]),
        Index(value = ["templateId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = CatalogSource::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CatalogTemplate(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    val templateId: String,      // 模板唯一 ID（来自 catalog）
    val sourceId: Long,          // 所属数据源 ID
    val sourceName: String,      // 所属数据源名称（冗余，方便查询）

    val name: String,            // 模板名称
    val description: String? = null,
    val version: String,         // 语义化版本号
    val type: String,            // worker / pages / hybrid

    // 作者信息
    val authorName: String? = null,
    val authorUrl: String? = null,

    // 标签（逗号分隔存储）
    val tags: String? = null,

    val icon: String? = null,    // emoji 或图片 URL
    val homepage: String? = null,
    val readmeUrl: String? = null,

    // 源码信息（普通 worker/pages 类型使用）
    val sourceKind: String? = null,   // raw / release / repo-archive
    val sourceUrl: String? = null,    // 源码拉取地址

    // hybrid 模式的双源码
    val workerSourceKind: String? = null,
    val workerSourceUrl: String? = null,
    val pagesSourceKind: String? = null,
    val pagesSourceUrl: String? = null,

    // 绑定配置（JSON 数组字符串存储）
    val bindingsJson: String? = null,

    // 环境变量（JSON 字符串存储）
    val envJson: String? = null,

    // 路由（逗号分隔）
    val routes: String? = null,

    // Cron 定时任务（逗号分隔）
    val crons: String? = null,

    // 兼容性配置
    val compatibilityDate: String? = null,
    val compatibilityFlags: String? = null,  // 逗号分隔

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ==================== Catalog Favorite ====================

/**
 * 模板收藏
 */
@Entity(
    tableName = "catalog_favorites",
    indices = [Index(value = ["templateId"], unique = true)]
)
data class CatalogFavorite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== Binding ====================

/**
 * 模板绑定配置
 * 用于解析 bindingsJson 字段
 */
data class CatalogBinding(
    val type: String,           // kv / d1 / r2 / ai / var / durable_object / service / queue
    val name: String,           // 绑定变量名（如 MY_KV）
    val title: String? = null,  // 显示名
    val resourceName: String? = null,  // Cloudflare 资源名
    val action: String = "create-or-reuse",  // create-or-reuse / prompt
    val required: Boolean = false,
    val secret: Boolean = true, // 仅对 var 生效
    val value: String? = null,  // var 的默认值
    val initSqlUrl: String? = null,  // D1 初始化 SQL URL
    val initSql: String? = null      // D1 初始化 SQL 内联
)

// ==================== UI Layer Models ====================

/**
 * 模板列表项（UI 层使用，包含来源数量、收藏状态等聚合信息）
 */
data class TemplateItem(
    val template: CatalogTemplate,
    val sourceCount: Int,       // 有多少个源包含此模板
    val isFavorite: Boolean
)

/**
 * 部署预检结果
 */
data class DeployPreflightResult(
    val canProceed: Boolean,
    val workerExists: Boolean = false,
    val warnings: List<String> = emptyList(),
    val configDiff: ConfigDiff? = null,
    val secretsOverride: List<String> = emptyList()
)

data class ConfigDiff(
    val added: List<BindingInfo> = emptyList(),
    val removed: List<BindingInfo> = emptyList(),
    val modified: List<BindingInfo> = emptyList()
)

data class BindingInfo(
    val name: String,
    val type: String
)

/**
 * 部署结果
 */
data class DeployResult(
    val success: Boolean,
    val url: String? = null,
    val error: String? = null,
    val rolledBack: Boolean = false,
    val rollbackErrors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * 部署时的绑定选择
 */
data class BindingSelection(
    val mode: String = "auto",  // auto / existing
    val existingId: String? = null,
    val runInitSql: Boolean = false  // 仅 D1
)

/**
 * 部署时的单个绑定配置（完整版本，用于 TemplateDeployRepository）
 */
data class DeployBindingConfig(
    val name: String,               // Worker 中的变量名
    val type: String,               // kv / d1 / r2 / var / ai / ...
    val title: String? = null,      // 显示名
    val resourceName: String,       // Cloudflare 资源名（KV title / D1 name / R2 bucket name）
    val required: Boolean = false,
    val secret: Boolean = true,     // 仅 var 类型有效
    val mode: String = "auto",      // auto / existing
    val existingId: String? = null, // 现有资源 ID
    val runInitSql: Boolean = false, // D1 是否执行初始化 SQL
    val initSqlUrl: String? = null, // D1 初始化 SQL URL
    val initSql: String? = null     // D1 初始化 SQL 内联
)

/**
 * 部署预检结果
 */
data class DeployPreflightInfo(
    val exists: Boolean,
    val newBindings: List<CatalogBinding>,
    val existingBindings: List<Any>,
    val secretsToOverride: List<String>,
    val warnings: List<String>,
    val envVarCount: Int,
    val secretCount: Int
)

/**
 * 部署结果
 */
data class DeployResultInfo(
    val success: Boolean,
    val url: String? = null,
    val scriptName: String = "",
    val createdResources: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val subdomainEnabled: Boolean = false
)
