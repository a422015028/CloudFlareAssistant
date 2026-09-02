# CloudFlareAssistant 模板商店集成方案

> **目标**：在 CloudFlareAssistant Android 应用中集成模板商店功能，用户可浏览、搜索、一键部署 Cloudflare Worker/Pages 模板，参考 cf-manager 的 Catalog 体系设计。

**技术栈**：Kotlin · Room · Retrofit · Hilt · MVVM · Flow · Material 3 · Coil

***

## 一、总体架构设计

### 1.1 架构分层

```
UI Layer (Fragment + ViewBinding)
    │
    ▼
ViewModel Layer (StateFlow + Hilt)
    │
    ▼
Repository Layer (CatalogRepository + TemplateDeployRepository)
    │
    ├── Local: Room Database (catalog_sources, templates, favorites)
    │
    └── Remote: Retrofit (Catalog API + Cloudflare API)
```

### 1.2 核心设计原则

1. **复用现有体系**：遵循项目现有 MVVM + Repository + Room 模式
2. **兼容 cf-manager Catalog Schema**：模板数据格式与 cf-manager 完全兼容，可复用同一批模板源
3. **离线优先**：模板数据缓存在本地 Room，无网络时仍可浏览已缓存模板
4. **渐进式部署**：先实现 Worker 模板部署，再扩展 Pages 和 Hybrid
5. **多账户支持**：部署时可选择目标账户，复用现有 Account 体系

***

## 二、数据模型设计

### 2.1 Catalog 源数据模型

```kotlin
// app/src/main/java/com/muort/upworker/core/model/CatalogModels.kt

/**
 * Catalog 数据源（对应 cf-manager 的 catalog_sources 表）
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

/**
 * 模板作者信息
 */
data class CatalogAuthor(
    val name: String,
    val url: String? = null
)

/**
 * 模板源码来源
 */
data class CatalogSourceInfo(
    val kind: String,      // raw / release / repo-archive
    val url: String,       // 源码拉取地址
    val assetName: String? = null,
    val subPath: String? = null,
    val size: Long? = null,
    val mainModule: String? = null
)

/**
 * 模板绑定配置
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

/**
 * Catalog 模板（核心数据模型）
 */
@Entity(
    tableName = "catalog_templates",
    indices = [
        androidx.room.Index(value = ["sourceId"]),
        androidx.room.Index(value = ["type"]),
        androidx.room.Index(value = ["templateId"], unique = true)
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
    
    // 作者信息（JSON 存储）
    val authorName: String? = null,
    val authorUrl: String? = null,
    
    // 标签（逗号分隔存储）
    val tags: String? = null,
    
    val icon: String? = null,    // emoji 或图片 URL
    val homepage: String? = null,
    val readmeUrl: String? = null,
    
    // 源码信息（JSON 存储，简化版）
    val sourceKind: String? = null,
    val sourceUrl: String? = null,
    
    // hybrid 模式的双源码
    val workerSourceKind: String? = null,
    val workerSourceUrl: String? = null,
    val pagesSourceKind: String? = null,
    val pagesSourceUrl: String? = null,
    
    // 绑定配置（JSON 数组存储）
    val bindingsJson: String? = null,
    
    // 环境变量（JSON 存储）
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

/**
 * 模板收藏
 */
@Entity(
    tableName = "catalog_favorites",
    indices = [androidx.room.Index(value = ["templateId"], unique = true)]
)
data class CatalogFavorite(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 模板列表项（UI 层使用，包含来源数量等聚合信息）
 */
data class TemplateItem(
    val template: CatalogTemplate,
    val sourceCount: Int,  // 有多少个源包含此模板
    val isFavorite: Boolean
)
```

### 2.2 部署相关模型

```kotlin
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
```

***

## 三、数据库层设计

### 3.1 DAO 接口

```kotlin
// app/src/main/java/com/muort/upworker/core/database/CatalogDao.kt

@Dao
interface CatalogDao {
    // ========== Catalog Sources ==========
    
    @Query("SELECT * FROM catalog_sources ORDER BY isDefault DESC, id ASC")
    fun getAllSources(): Flow<List<CatalogSource>>
    
    @Query("SELECT * FROM catalog_sources WHERE enabled = 1 ORDER BY isDefault DESC, id ASC")
    suspend fun getEnabledSources(): List<CatalogSource>
    
    @Query("SELECT * FROM catalog_sources WHERE id = :id")
    suspend fun getSourceById(id: Long): CatalogSource?
    
    @Query("SELECT * FROM catalog_sources WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultSource(): CatalogSource?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: CatalogSource): Long
    
    @Update
    suspend fun updateSource(source: CatalogSource)
    
    @Query("DELETE FROM catalog_sources WHERE id = :id AND isDefault = 0")
    suspend fun deleteSource(id: Long)
    
    // ========== Templates ==========
    
    @Query("SELECT * FROM catalog_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<CatalogTemplate>>
    
    @Query("SELECT * FROM catalog_templates WHERE type = :type ORDER BY name ASC")
    fun getTemplatesByType(type: String): Flow<List<CatalogTemplate>>
    
    @Query("SELECT * FROM catalog_templates WHERE templateId = :templateId LIMIT 1")
    suspend fun getTemplateById(templateId: String): CatalogTemplate?
    
    @Query("SELECT * FROM catalog_templates WHERE sourceId = :sourceId")
    suspend fun getTemplatesBySource(sourceId: Long): List<CatalogTemplate>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplates(templates: List<CatalogTemplate>)
    
    @Query("DELETE FROM catalog_templates WHERE sourceId = :sourceId")
    suspend fun deleteTemplatesBySource(sourceId: Long)
    
    @Query("DELETE FROM catalog_templates WHERE sourceId = :sourceId AND templateId NOT IN (:keepIds)")
    suspend fun deleteTemplatesNotInList(sourceId: Long, keepIds: List<String>)
    
    // 搜索
    @Query("""
        SELECT * FROM catalog_templates 
        WHERE name LIKE '%' || :query || '%' 
           OR description LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchTemplates(query: String): Flow<List<CatalogTemplate>>
    
    // ========== Favorites ==========
    
    @Query("SELECT cf.* FROM catalog_favorites fav JOIN catalog_templates cf ON fav.templateId = cf.templateId ORDER BY fav.createdAt DESC")
    fun getFavoriteTemplates(): Flow<List<CatalogTemplate>>
    
    @Query("SELECT COUNT(*) FROM catalog_favorites WHERE templateId = :templateId")
    suspend fun isFavorite(templateId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: CatalogFavorite)
    
    @Query("DELETE FROM catalog_favorites WHERE templateId = :templateId")
    suspend fun removeFavorite(templateId: String)
}
```

### 3.2 数据库迁移

在 `AppDatabase.kt` 中新增版本 9 → 10 迁移：

```kotlin
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Catalog 源表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS catalog_sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0,
                enabled INTEGER NOT NULL DEFAULT 1,
                lastSynced INTEGER,
                lastStatus TEXT NOT NULL DEFAULT 'idle',
                lastError TEXT,
                etag TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        
        // 模板表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS catalog_templates (
                localId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateId TEXT NOT NULL,
                sourceId INTEGER NOT NULL,
                sourceName TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                version TEXT NOT NULL,
                type TEXT NOT NULL,
                authorName TEXT,
                authorUrl TEXT,
                tags TEXT,
                icon TEXT,
                homepage TEXT,
                readmeUrl TEXT,
                sourceKind TEXT,
                sourceUrl TEXT,
                workerSourceKind TEXT,
                workerSourceUrl TEXT,
                pagesSourceKind TEXT,
                pagesSourceUrl TEXT,
                bindingsJson TEXT,
                envJson TEXT,
                routes TEXT,
                crons TEXT,
                compatibilityDate TEXT,
                compatibilityFlags TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_templates_sourceId ON catalog_templates(sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_catalog_templates_type ON catalog_templates(type)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_templates_templateId ON catalog_templates(templateId)")
        
        // 收藏表
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS catalog_favorites (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateId TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_favorites_templateId ON catalog_favorites(templateId)")
    }
}
```

同时在 `@Database` 注解中把 version 从 9 升级到 10，并添加 `catalogDao()` 抽象方法。

***

## 四、Repository 层设计

### 4.1 CatalogRepository

```kotlin
// app/src/main/java/com/muort/upworker/core/repository/CatalogRepository.kt

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val catalogDao: CatalogDao,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient  // 复用现有 OkHttp 客户端
) {
    companion object {
        // 默认官方源（与 cf-manager 兼容）
        const val DEFAULT_CATALOG_URL = "https://cf-store.surge.sh/catalog.json"
        const val DEFAULT_CATALOG_NAME = "官方源"
        
        // 兜底地址
        private val FALLBACK_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/hefy2027/cf-store@main/catalog.json",
            "https://raw.githubusercontent.com/hefy2027/cf-store/main/catalog.json"
        )
        
        // 缓存有效期：5 分钟
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
    
    // 内存缓存
    private val memoryCache = mutableMapOf<Long, List<CatalogTemplate>>()
    
    /** 确保默认源存在 */
    suspend fun ensureDefaultSource() {
        val existing = catalogDao.getDefaultSource()
        if (existing == null) {
            catalogDao.insertSource(
                CatalogSource(
                    url = DEFAULT_CATALOG_URL,
                    name = DEFAULT_CATALOG_NAME,
                    isDefault = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    /** 获取所有数据源（Flow） */
    fun observeSources(): Flow<List<CatalogSource>> = catalogDao.getAllSources()
    
    /** 获取所有模板（Flow，包含收藏状态） */
    fun observeAllTemplates(): Flow<List<TemplateItem>> {
        // 合并模板 + 收藏状态
        return combine(
            catalogDao.getAllTemplates(),
            getFavoriteTemplateIds()
        ) { templates, favIds ->
            // 按 templateId 去重，统计来源数量
            val idCount = mutableMapOf<String, Int>()
            val idTemplate = mutableMapOf<String, CatalogTemplate>()
            
            templates.forEach { t ->
                idCount[t.templateId] = (idCount[t.templateId] ?: 0) + 1
                if (!idTemplate.containsKey(t.templateId)) {
                    idTemplate[t.templateId] = t
                }
            }
            
            idTemplate.values.map { t ->
                TemplateItem(
                    template = t,
                    sourceCount = idCount[t.templateId] ?: 1,
                    isFavorite = favIds.contains(t.templateId)
                )
            }.sortedBy { it.template.name }
        }
    }
    
    private fun getFavoriteTemplateIds(): Flow<Set<String>> {
        return catalogDao.getFavoriteTemplates().map { list ->
            list.map { it.templateId }.toSet()
        }
    }
    
    /** 搜索模板 */
    fun searchTemplates(query: String): Flow<List<TemplateItem>> {
        return combine(
            catalogDao.searchTemplates(query),
            getFavoriteTemplateIds()
        ) { templates, favIds ->
            templates.map { t ->
                TemplateItem(
                    template = t,
                    sourceCount = 1, // 简化：搜索结果不统计多源
                    isFavorite = favIds.contains(t.templateId)
                )
            }
        }
    }
    
    /** 刷新所有启用的数据源 */
    suspend fun refreshAllSources(): List<CatalogSource> {
        val sources = catalogDao.getEnabledSources()
        val results = mutableListOf<CatalogSource>()
        
        for (source in sources) {
            val result = refreshSource(source)
            results.add(result)
        }
        
        return results
    }
    
    /** 刷新单个数据源 */
    suspend fun refreshSource(source: CatalogSource): CatalogSource {
        // 更新状态为 loading
        val loadingSource = source.copy(
            lastStatus = "loading",
            updatedAt = System.currentTimeMillis()
        )
        catalogDao.updateSource(loadingSource)
        
        val urls = if (source.isDefault) {
            listOf(source.url) + FALLBACK_URLS
        } else {
            listOf(source.url)
        }
        
        var lastError: String? = null
        var catalogJson: JsonObject? = null
        var etag: String? = null
        
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).apply {
                    if (url == source.url && source.etag != null) {
                        header("If-None-Match", source.etag)
                    }
                }.build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (response.code == 304) {
                    // 未修改，直接返回
                    return source.copy(
                        lastStatus = "ok",
                        lastSynced = System.currentTimeMillis(),
                        lastError = null,
                        updatedAt = System.currentTimeMillis()
                    ).also { catalogDao.updateSource(it) }
                }
                
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code} ($url)"
                    continue
                }
                
                val body = response.body?.string() ?: continue
                val json = JsonParser.parseString(body).asJsonObject
                
                // 简单校验
                if (!json.has("templates") || !json.get("templates").isJsonArray) {
                    lastError = "Invalid catalog format ($url)"
                    continue
                }
                
                catalogJson = json
                etag = response.header("etag")
                break
                
            } catch (e: Exception) {
                lastError = "${e.message} ($url)"
                continue
            }
        }
        
        return if (catalogJson != null) {
            // 解析并保存模板
            val templates = parseTemplates(catalogJson, source)
            saveTemplatesForSource(source, templates)
            
            source.copy(
                lastStatus = "ok",
                lastSynced = System.currentTimeMillis(),
                lastError = null,
                etag = etag,
                updatedAt = System.currentTimeMillis()
            ).also { catalogDao.updateSource(it) }
        } else {
            source.copy(
                lastStatus = "error",
                lastError = lastError,
                updatedAt = System.currentTimeMillis()
            ).also { catalogDao.updateSource(it) }
        }
    }
    
    private fun parseTemplates(json: JsonObject, source: CatalogSource): List<CatalogTemplate> {
        val templatesArray = json.getAsJsonArray("templates") ?: return emptyList()
        val now = System.currentTimeMillis()
        
        return templatesArray.mapNotNull { templateEl ->
            try {
                val t = templateEl.asJsonObject
                val bindings = t.getAsJsonArray("bindings")
                
                CatalogTemplate(
                    templateId = t.get("id")?.asString ?: return@mapNotNull null,
                    sourceId = source.id,
                    sourceName = source.name,
                    name = t.get("name")?.asString ?: return@mapNotNull null,
                    description = t.get("description")?.asString,
                    version = t.get("version")?.asString ?: "0.0.0",
                    type = t.get("type")?.asString ?: "worker",
                    authorName = t.getAsJsonObject("author")?.get("name")?.asString,
                    authorUrl = t.getAsJsonObject("author")?.get("url")?.asString,
                    tags = t.getAsJsonArray("tags")?.joinToString(",") { it.asString },
                    icon = t.get("icon")?.asString,
                    homepage = t.get("homepage")?.asString,
                    readmeUrl = t.get("readmeUrl")?.asString,
                    sourceKind = t.getAsJsonObject("source")?.get("kind")?.asString,
                    sourceUrl = t.getAsJsonObject("source")?.get("url")?.asString,
                    workerSourceKind = t.getAsJsonObject("sources")?.getAsJsonObject("worker")?.get("kind")?.asString,
                    workerSourceUrl = t.getAsJsonObject("sources")?.getAsJsonObject("worker")?.get("url")?.asString,
                    pagesSourceKind = t.getAsJsonObject("sources")?.getAsJsonObject("pages")?.get("kind")?.asString,
                    pagesSourceUrl = t.getAsJsonObject("sources")?.getAsJsonObject("pages")?.get("url")?.asString,
                    bindingsJson = bindings?.toString(),
                    envJson = t.getAsJsonObject("env")?.toString(),
                    routes = t.getAsJsonArray("routes")?.joinToString(",") { it.asString },
                    crons = t.getAsJsonArray("crons")?.joinToString(",") { it.asString },
                    compatibilityDate = t.get("compatibility_date")?.asString,
                    compatibilityFlags = t.getAsJsonArray("compatibility_flags")?.joinToString(",") { it.asString },
                    createdAt = now,
                    updatedAt = now
                )
            } catch (e: Exception) {
                null // 跳过解析失败的模板
            }
        }
    }
    
    private suspend fun saveTemplatesForSource(source: CatalogSource, templates: List<CatalogTemplate>) {
        val templateIds = templates.map { it.templateId }
        
        // 删除已移除的模板
        catalogDao.deleteTemplatesNotInList(source.id, templateIds)
        
        // 插入/更新模板
        catalogDao.insertTemplates(templates)
    }
    
    // ========== 数据源管理 ==========
    
    suspend fun addSource(url: String, name: String): CatalogSource? {
        // 测试 URL 是否有效
        val testResult = testCatalogUrl(url)
        if (!testResult.success) return null
        
        val id = catalogDao.insertSource(
            CatalogSource(
                url = url,
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        
        // 立即刷新
        val source = catalogDao.getSourceById(id) ?: return null
        return refreshSource(source)
    }
    
    suspend fun testCatalogUrl(url: String): TestResult {
        // 安全检查：只允许 https
        if (!url.startsWith("https://")) {
            return TestResult(false, error = "URL must be HTTPS")
        }
        
        return try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return TestResult(false, error = "HTTP ${response.code}")
            }
            
            val body = response.body?.string() ?: return TestResult(false, error = "Empty response")
            val json = JsonParser.parseString(body).asJsonObject
            
            if (!json.has("templates") || !json.get("templates").isJsonArray) {
                return TestResult(false, error = "Invalid catalog format")
            }
            
            val count = json.getAsJsonArray("templates").size()
            TestResult(true, templateCount = count)
        } catch (e: Exception) {
            TestResult(false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun deleteSource(id: Long) {
        catalogDao.deleteSource(id)
        catalogDao.deleteTemplatesBySource(id)
    }
    
    // ========== 收藏 ==========
    
    suspend fun toggleFavorite(templateId: String): Boolean {
        val isFav = catalogDao.isFavorite(templateId) > 0
        if (isFav) {
            catalogDao.removeFavorite(templateId)
        } else {
            catalogDao.addFavorite(CatalogFavorite(templateId = templateId))
        }
        return !isFav
    }
    
    fun observeFavorites(): Flow<List<CatalogTemplate>> = catalogDao.getFavoriteTemplates()
    
    data class TestResult(
        val success: Boolean,
        val templateCount: Int = 0,
        val error: String? = null
    )
}
```

### 4.2 TemplateDeployRepository

```kotlin
// app/src/main/java/com/muort/upworker/core/repository/TemplateDeployRepository.kt

@Singleton
class TemplateDeployRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val api: CloudFlareApi,
    private val workerRepository: WorkerRepository,
    private val pagesRepository: PagesRepository,
    private val gson: Gson
) {
    /**
     * 部署前预检
     */
    suspend fun preflightDeploy(
        account: Account,
        template: CatalogTemplate,
        name: String,
        bindingSelections: Map<String, BindingSelection> = emptyMap(),
        secretValues: Map<String, String> = emptyMap(),
        deployType: String? = null  // worker / pages / both（仅 hybrid）
    ): DeployPreflightResult {
        return withContext(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            var workerExists = false
            val secretsOverride = mutableListOf<String>()
            
            // 检查 Worker 是否已存在
            if (template.type == "worker" || template.type == "hybrid") {
                try {
                    val existing = workerRepository.getWorkerSettings(account, name)
                    if (existing is Resource.Success) {
                        workerExists = true
                        warnings.add("Worker '$name' 已存在，将覆盖现有配置")
                        
                        // 检查哪些 secrets 会被覆盖
                        val bindings = existing.data?.bindings ?: emptyList()
                        val existingSecrets = bindings.filter { it.type == "secret_text" }.map { it.name }
                        val templateBindings = parseBindings(template.bindingsJson)
                        val newSecretNames = templateBindings
                            .filter { it.type == "var" && it.secret }
                            .map { it.name }
                        
                        secretsOverride.addAll(existingSecrets.intersect(newSecretNames.toSet()))
                    }
                } catch (e: Exception) {
                    // 不存在，正常
                }
            }
            
            // 检查 Pages 项目是否存在
            if (template.type == "pages" || (template.type == "hybrid" && deployType != "worker")) {
                try {
                    // Pages 项目列表检查
                } catch (e: Exception) {
                    // 不存在或出错
                }
            }
            
            // 检查必填 secrets
            val bindings = parseBindings(template.bindingsJson)
            val requiredSecrets = bindings.filter { 
                it.type == "var" && it.action == "prompt" && it.required 
            }
            for (s in requiredSecrets) {
                if (secretValues[s.name].isNullOrBlank()) {
                    warnings.add("必填项 '${s.title ?: s.name}' 未填写")
                }
            }
            
            DeployPreflightResult(
                canProceed = warnings.none { it.contains("未填写") },
                workerExists = workerExists,
                warnings = warnings,
                secretsOverride = secretsOverride
            )
        }
    }
    
    /**
     * 部署 Worker 模板
     */
    suspend fun deployWorkerTemplate(
        account: Account,
        template: CatalogTemplate,
        name: String,
        bindingSelections: Map<String, BindingSelection> = emptyMap(),
        secretValues: Map<String, String> = emptyMap()
    ): DeployResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 下载源码
                val sourceUrl = template.sourceUrl 
                    ?: template.workerSourceUrl
                    ?: return@withContext DeployResult(false, error = "模板无源码地址")
                
                val sourceCode = downloadSourceCode(sourceUrl, template.sourceKind ?: template.workerSourceKind)
                    ?: return@withContext DeployResult(false, error = "下载源码失败")
                
                // 2. 创建临时文件
                val tempFile = File(appContext.cacheDir, "$name.js")
                tempFile.writeText(sourceCode, Charsets.UTF_8)
                
                // 3. 处理绑定（简化版：先上传脚本，再设置绑定）
                val result = workerRepository.uploadWorkerScriptWithBindings(
                    account = account,
                    scriptName = name,
                    scriptFile = tempFile,
                    customCompatibilityDate = template.compatibilityDate,
                    customCompatibilityFlags = template.compatibilityFlags?.split(",")?.filter { it.isNotBlank() }
                )
                
                // 4. 设置环境变量和 secrets
                val bindings = parseBindings(template.bindingsJson)
                val varBindings = bindings.filter { it.type == "var" }
                
                if (varBindings.isNotEmpty()) {
                    // 设置环境变量...
                }
                
                // 5. 设置 Cron Triggers
                val crons = template.crons?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
                if (crons.isNotEmpty()) {
                    // 设置 Cron...
                }
                
                DeployResult(
                    success = true,
                    url = "https://$name.${account.accountId}.workers.dev"
                )
                
            } catch (e: Exception) {
                DeployResult(
                    success = false,
                    error = e.message ?: "部署失败"
                )
            }
        }
    }
    
    private suspend fun downloadSourceCode(url: String, kind: String?): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            when (kind) {
                "raw" -> response.body?.string()
                "release", "repo-archive" -> {
                    // ZIP 文件需要解压，找到入口文件
                    val bytes = response.body?.bytes() ?: return null
                    extractEntryFromZip(bytes)
                }
                else -> response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractEntryFromZip(zipBytes: ByteArray): String? {
        // 简单实现：从 ZIP 中找到第一个 .js 文件
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".js")) {
                    val content = zis.readBytes().toString(Charsets.UTF_8)
                    return content
                }
                entry = zis.nextEntry
            }
        }
        return null
    }
    
    private fun parseBindings(json: String?): List<CatalogBinding> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<CatalogBinding>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

***

## 五、ViewModel 层设计

### 5.1 StoreViewModel

```kotlin
// app/src/main/java/com/muort/upworker/feature/store/StoreViewModel.kt

@HiltViewModel
class StoreViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val catalogRepository: CatalogRepository,
    private val templateDeployRepository: TemplateDeployRepository
) : ViewModel() {
    
    // 筛选状态
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedType = MutableStateFlow<String?>(null) // null = all
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()
    
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()
    
    private val _sortBy = MutableStateFlow("name") // name / version
    val sortBy: StateFlow<String> = _sortBy.asStateFlow()
    
    private val _favOnly = MutableStateFlow(false)
    val favOnly: StateFlow<Boolean> = _favOnly.asStateFlow()
    
    // 数据源
    val sources = catalogRepository.observeSources().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    
    // 模板列表（带筛选）
    val templates: StateFlow<List<TemplateItem>> = combine(
        if (_searchQuery.value.isBlank()) {
            catalogRepository.observeAllTemplates()
        } else {
            catalogRepository.searchTemplates(_searchQuery.value)
        },
        _selectedType,
        _selectedTags,
        _sortBy,
        _favOnly
    ) { list, type, tags, sort, favOnly ->
        var result = list
        
        // 类型筛选
        if (type != null) {
            result = result.filter { it.template.type == type }
        }
        
        // 标签筛选
        if (tags.isNotEmpty()) {
            result = result.filter { item ->
                val itemTags = item.template.tags?.split(",")?.toSet() ?: emptySet()
                tags.all { itemTags.contains(it) }
            }
        }
        
        // 仅收藏
        if (favOnly) {
            result = result.filter { it.isFavorite }
        }
        
        // 排序
        result.sortedWith(compareBy<TemplateItem> { !it.isFavorite }.thenBy {
            when (sort) {
                "version" -> it.template.version
                else -> it.template.name
            }
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // 所有标签（用于标签云）
    val allTags: StateFlow<List<String>> = catalogRepository.observeAllTemplates()
        .map { list ->
            list.flatMap { it.template.tags?.split(",")?.filter { t -> t.isNotBlank() } ?: emptyList() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // 各类型计数
    val typeCounts: StateFlow<Map<String, Int>> = catalogRepository.observeAllTemplates()
        .map { list ->
            mapOf(
                "worker" to list.count { it.template.type == "worker" },
                "pages" to list.count { it.template.type == "pages" },
                "hybrid" to list.count { it.template.type == "hybrid" }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    // 刷新状态
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // 部署状态
    private val _deployState = MutableStateFlow<DeployState>(DeployState.Idle)
    val deployState: StateFlow<DeployState> = _deployState.asStateFlow()
    
    // 预检结果
    private val _preflightResult = MutableStateFlow<DeployPreflightResult?>(null)
    val preflightResult: StateFlow<DeployPreflightResult?> = _preflightResult.asStateFlow()
    
    init {
        viewModelScope.launch {
            catalogRepository.ensureDefaultSource()
        }
    }
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSelectedType(type: String?) {
        _selectedType.value = type
    }
    
    fun toggleTag(tag: String) {
        val current = _selectedTags.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTags.value = current
    }
    
    fun setSortBy(sort: String) {
        _sortBy.value = sort
    }
    
    fun setFavOnly(enabled: Boolean) {
        _favOnly.value = enabled
    }
    
    fun clearFilters() {
        _searchQuery.value = ""
        _selectedType.value = null
        _selectedTags.value = emptySet()
        _favOnly.value = false
    }
    
    fun refreshTemplates() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                catalogRepository.refreshAllSources()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    fun toggleFavorite(templateId: String) {
        viewModelScope.launch {
            catalogRepository.toggleFavorite(templateId)
        }
    }
    
    // 预检
    fun preflight(
        account: Account,
        template: CatalogTemplate,
        name: String,
        bindingSelections: Map<String, BindingSelection> = emptyMap(),
        secretValues: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            _deployState.value = DeployState.Preflighting
            _preflightResult.value = null
            
            val result = templateDeployRepository.preflightDeploy(
                account, template, name, bindingSelections, secretValues
            )
            _preflightResult.value = result
            _deployState.value = DeployState.Idle
        }
    }
    
    // 部署
    fun deploy(
        account: Account,
        template: CatalogTemplate,
        name: String,
        bindingSelections: Map<String, BindingSelection> = emptyMap(),
        secretValues: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            _deployState.value = DeployState.Deploying
            
            val result = templateDeployRepository.deployWorkerTemplate(
                account, template, name, bindingSelections, secretValues
            )
            
            _deployState.value = if (result.success) {
                DeployState.Success(result)
            } else {
                DeployState.Error(result.error ?: "部署失败")
            }
        }
    }
    
    fun resetDeployState() {
        _deployState.value = DeployState.Idle
        _preflightResult.value = null
    }
    
    sealed class DeployState {
        object Idle : DeployState()
        object Preflighting : DeployState()
        object Deploying : DeployState()
        data class Success(val result: DeployResult) : DeployState()
        data class Error(val message: String) : DeployState()
    }
}
```

***

## 六、UI 层设计

### 6.1 页面结构

```
StoreFragment (模板商店主页)
    ├── 顶部：源状态条（显示各源同步状态）+ 刷新按钮
    ├── StoreCategoryNav：类型切换 + 标签云
    ├── StoreToolbar：搜索框 + 排序 + 仅收藏
    ├── 模板卡片网格（RecyclerView + GridLayoutManager）
    │   └── StoreCard：图标 + 名称 + 描述 + 绑定标签 + 作者 + 版本 + 部署按钮
    └── 空状态 / 加载状态

TemplateDetailDialog (模板详情对话框 - BottomSheet)
    ├── 头部：名称 + 版本 + 类型 + 作者 + 来源
    ├── README 展示（WebView 或 MarkdownView）
    ├── 绑定列表
    ├── 环境变量
    └── 部署按钮

DeployDialog (部署对话框)
    ├── 选择账户
    ├── 输入名称
    ├── 资源绑定选择（KV/D1/R2）
    ├── Secrets 输入
    ├── 预检结果展示
    └── 部署按钮
```

### 6.2 核心布局文件

| 文件路径                                                 | 说明      |
| ---------------------------------------------------- | ------- |
| `app/src/main/res/layout/fragment_store.xml`         | 商店主页面   |
| `app/src/main/res/layout/item_store_card.xml`        | 模板卡片    |
| `app/src/main/res/layout/dialog_template_detail.xml` | 模板详情对话框 |
| `app/src/main/res/layout/dialog_deploy_template.xml` | 部署对话框   |
| `app/src/main/res/layout/item_binding_row.xml`       | 绑定项布局   |
| `app/src/main/res/layout/item_store_source.xml`      | 源状态标签   |

### 6.3 核心 Fragment / Activity

| 文件路径                                    | 说明            |
| --------------------------------------- | ------------- |
| `feature/store/StoreFragment.kt`        | 商店主页 Fragment |
| `feature/store/StoreCardAdapter.kt`     | 模板卡片适配器       |
| `feature/store/TemplateDetailDialog.kt` | 模板详情对话框       |
| `feature/store/DeployTemplateDialog.kt` | 部署对话框         |

### 6.4 首页入口修改

在 `fragment_home.xml` 中添加"模板商店"卡片（放在 Workers 旁边）：

```xml
<!-- 替换 rowCount 为 7，新增一张卡片 -->
<GridLayout
    android:id="@+id/featureGrid"
    ...
    android:rowCount="7">
    
    <!-- 新增：模板商店卡片 -->
    <com.google.android.material.card.MaterialCardView
        android:id="@+id/storeCard"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_columnWeight="1"
        android:layout_marginEnd="6dp"
        android:layout_marginBottom="12dp"
        android:clickable="true"
        android:focusable="true"
        app:cardElevation="2dp"
        app:cardCornerRadius="8dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp"
            android:gravity="center">

            <ImageView
                android:layout_width="36dp"
                android:layout_height="36dp"
                android:src="@android:drawable/ic_menu_view"
                app:tint="?attr/colorPrimary" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/card_template_store"
                android:textSize="14sp"
                android:textStyle="bold"
                android:layout_marginTop="8dp"
                android:gravity="center"
                android:maxLines="1"
                android:ellipsize="end" />

        </LinearLayout>

    </com.google.android.material.card.MaterialCardView>

    <!-- ... 其他卡片 ... -->
</GridLayout>
```

### 6.5 导航图配置

在 `nav_graph.xml` 中添加：

```xml
<!-- 从首页跳转到模板商店 -->
<action
    android:id="@+id/action_home_to_store"
    app:destination="@id/storeFragment" />

<!-- 模板商店 Fragment -->
<fragment
    android:id="@+id/storeFragment"
    android:name="com.muort.upworker.feature.store.StoreFragment"
    android:label="模板商店"
    tools:layout="@layout/fragment_store" />
```

***

## 七、字符串资源

在 `strings.xml` 中添加：

```xml
<!-- 模板商店 -->
<string name="card_template_store">模板商店</string>
<string name="store_title">模板商店</string>
<string name="store_search_hint">搜索模板...</string>
<string name="store_no_templates">暂无模板</string>
<string name="store_refresh">刷新</string>
<string name="store_all">全部</string>
<string name="store_worker">Worker</string>
<string name="store_pages">Pages</string>
<string name="store_hybrid">Hybrid</string>
<string name="store_favorites">收藏</string>
<string name="store_sort_name">按名称</string>
<string name="store_sort_version">按版本</string>
<string name="store_deploy">部署</string>
<string name="store_detail">详情</string>
<string name="store_author">作者</string>
<string name="store_source">来源</string>
<string name="store_version">版本</string>
<string name="store_type">类型</string>
<string name="store_bindings">绑定</string>
<string name="store_env_vars">环境变量</string>
<string name="store_deploy_template">部署模板</string>
<string name="store_deploying">部署中...</string>
<string name="store_deploy_success">部署成功</string>
<string name="store_deploy_failed">部署失败</string>
<string name="store_target_account">目标账户</string>
<string name="store_template_name">模板名称</string>
<string name="store_name_hint">请输入部署名称</string>
<string name="store_preflighting">预检中...</string>
<string name="store_confirm_deploy">确认部署</string>
<string name="store_readme_loading">加载 README...</string>
<string name="store_no_readme">暂无 README</string>
<string name="store_add_favorite">收藏</string>
<string name="store_remove_favorite">取消收藏</string>
<string name="store_source_ok">已同步</string>
<string name="store_source_error">同步失败</string>
<string name="store_source_loading">同步中</string>
```

***

## 八、依赖注入配置

### 8.1 DatabaseModule 更新

```kotlin
// 在 DatabaseModule.kt 中添加
@Provides
fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()
```

### 8.2 NetworkModule 更新

需要提供 OkHttpClient 供 CatalogRepository 使用（如果还没有的话）。

***

## 九、实施阶段划分

### 阶段一：基础架构（数据层 + Repository）

* [ ] 创建数据模型（CatalogModels.kt）

* [ ] 创建 CatalogDao

* [ ] 数据库迁移（v9 → v10）

* [ ] 创建 CatalogRepository

* [ ] 单元测试：数据解析、数据库操作

### 阶段二：模板列表页面

* [ ] 创建 StoreViewModel

* [ ] 创建 StoreFragment 布局

* [ ] 创建 StoreCard 布局 + Adapter

* [ ] 实现搜索、筛选、排序

* [ ] 实现收藏功能

* [ ] 首页入口 + 导航配置

### 阶段三：模板详情

* [ ] 创建 TemplateDetailDialog

* [ ] README 展示（Markdown 渲染）

* [ ] 绑定信息展示

* [ ] 部署入口按钮

### 阶段四：部署功能（Worker 优先）

* [ ] 创建 TemplateDeployRepository

* [ ] 创建 DeployTemplateDialog

* [ ] 实现预检逻辑

* [ ] 实现 Worker 模板部署

* [ ] 部署结果展示

### 阶段五：增强功能

* [ ] 数据源管理（添加/删除第三方源）

* [ ] Pages 模板部署支持

* [ ] Hybrid 模板支持

* [ ] Cron Triggers 支持

* [ ] D1 初始化 SQL 支持

* [ ] 多账户批量部署

***

## 十、关键技术点

### 10.1 Catalog Schema 兼容性

* 模板数据格式与 cf-manager 完全兼容

* 使用相同的默认源地址（cf-store.surge.sh）

* 支持相同的兜底策略（surge → jsDelivr → GitHub raw）

### 10.2 离线优先策略

* 模板数据持久化到 Room 数据库

* 进入页面先显示缓存数据

* 后台静默刷新（5 分钟间隔）

* 下拉刷新强制重新拉取

### 10.3 部署安全性

* 部署前预检：检查是否会覆盖现有 Worker

* Secrets 覆盖提示

* 保留现有绑定（复用 WorkerRepository 的保留逻辑）

* 失败时的错误提示

### 10.4 性能优化

* 模板列表分页加载（模板数量多时）

* 图片加载使用 Coil

* 搜索使用 Room LIKE 查询

* Flow + StateFlow 响应式更新

***

## 十一、文件清单总览

### 新增文件（约 18 个）

```
app/src/main/java/com/muort/upworker/core/model/
    └── CatalogModels.kt              # 数据模型

app/src/main/java/com/muort/upworker/core/database/
    └── CatalogDao.kt                 # DAO 接口

app/src/main/java/com/muort/upworker/core/repository/
    ├── CatalogRepository.kt          # Catalog 数据仓库
    └── TemplateDeployRepository.kt   # 模板部署仓库

app/src/main/java/com/muort/upworker/feature/store/
    ├── StoreFragment.kt              # 商店主页
    ├── StoreViewModel.kt             # 商店 ViewModel
    ├── StoreCardAdapter.kt           # 模板卡片适配器
    ├── TemplateDetailDialog.kt       # 模板详情对话框
    └── DeployTemplateDialog.kt       # 部署对话框

app/src/main/res/layout/
    ├── fragment_store.xml            # 商店主页布局
    ├── item_store_card.xml           # 模板卡片布局
    ├── dialog_template_detail.xml    # 详情对话框布局
    ├── dialog_deploy_template.xml    # 部署对话框布局
    └── item_binding_row.xml          # 绑定项布局

app/src/main/res/values/
    └── strings.xml (新增条目)        # 字符串资源
```

### 修改文件（约 5 个）

```
app/src/main/java/com/muort/upworker/core/database/
    └── AppDatabase.kt                # 新增 DAO + 迁移

app/src/main/java/com/muort/upworker/core/database/
    └── DatabaseModule.kt             # 新增 DAO 提供

app/src/main/java/com/muort/upworker/feature/home/
    └── HomeFragment.kt               # 新增入口

app/src/main/res/layout/
    └── fragment_home.xml             # 新增卡片

app/src/main/res/navigation/
    └── nav_graph.xml                 # 新增导航
```

***

## 十二、风险与注意事项

1. **ZIP 解压兼容性**：Pages 模板需要解压 ZIP，注意内存管理
2. **大模板下载**：部分模板可能较大，需要进度提示和超时处理
3. **绑定资源创建**：自动创建 KV/D1/R2 资源时需要处理权限和配额
4. **版本兼容**：不同模板的 compatibility\_date 可能不同，需正确传递
5. **安全性**：第三方模板源可能包含恶意代码，需提醒用户风险
6. **网络安全**：SSRF 防护，禁止内网地址的 catalog 源
7. **数据一致性**：多源同 ID 模板的去重和合并策略

***

## 十三、后续可扩展方向

1. **模板分类推荐**：基于标签的智能推荐
2. **模板评论/评分**：社区互动功能
3. **本地模板导入**：支持从本地文件导入模板
4. **模板分享**：生成分享链接/二维码
5. **部署历史**：记录每次部署的模板版本
6. **一键更新**：检测模板新版本并提示更新
7. **模板搜索增强**：按作者、按功能筛选

