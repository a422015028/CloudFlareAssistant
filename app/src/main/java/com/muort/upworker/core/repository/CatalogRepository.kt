package com.muort.upworker.core.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.muort.upworker.R
import com.muort.upworker.core.database.CatalogDao
import com.muort.upworker.core.model.CatalogBinding
import com.muort.upworker.core.model.CatalogFavorite
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.model.CatalogTemplate
import com.muort.upworker.core.model.TemplateItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从 JSONObject 中获取可选字符串，不存在或为空时返回 null
 */
private fun JSONObject.optStringOrNull(name: String): String? {
    val value = optString(name)
    return if (value.isNullOrEmpty()) null else value
}

/**
 * Catalog 模板仓库
 * 负责管理模板数据源、模板列表、收藏等功能
 *
 * 设计参考 cf-manager 的 store 模块：
 * - 多数据源支持（官方源 + 第三方源）
 * - 本地 Room 缓存 + 后台刷新
 * - ETag 增量更新
 * - 离线优先策略
 */
@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val catalogDao: CatalogDao,
    private val gson: Gson
) {

    companion object {
        // 默认官方源（与 cf-manager 兼容）
        const val DEFAULT_CATALOG_URL = "https://cf.muort.com/cf-store.json"

        // 兜底地址（按优先级排列）
        private val FALLBACK_URLS = listOf(
            "https://cdn.jsdelivr.net/gh/hefy2027/cf-store@main/catalog.json",
            "https://raw.githubusercontent.com/hefy2027/cf-store/main/catalog.json"
        )

        // 缓存有效期：5 分钟
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        // HTTP 超时时间
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L
    }

    // 专用 OkHttpClient（用于拉取 catalog，与 Cloudflare API 客户端分离）
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    // 后台刷新专用 scope（与 UI 生命周期无关，确保刷新操作能完整执行）
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ========== 数据源管理 ==========

    /**
     * 确保默认源存在
     * 首次启动时自动创建官方源
     */
    suspend fun ensureDefaultSource() {
        val existing = catalogDao.getDefaultSource()
        if (existing == null) {
            Timber.d("[Catalog] 创建默认数据源: $DEFAULT_CATALOG_URL")
            catalogDao.insertSource(
                CatalogSource(
                    url = DEFAULT_CATALOG_URL,
                    name = appContext.getString(R.string.store_default_source_name),
                    isDefault = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 获取所有数据源（响应式 Flow） */
    fun observeSources(): Flow<List<CatalogSource>> =
        catalogDao.getAllSources()

    /**
     * 添加新的数据源
     * @return 添加成功返回源对象，失败返回 null
     */
    suspend fun addSource(url: String, name: String): CatalogSource? {
        // 先测试 URL 是否有效
        val testResult = testCatalogUrl(url)
        if (!testResult.success) {
            Timber.w("[Catalog] 添加源失败: ${testResult.error}")
            return null
        }

        val id = catalogDao.insertSource(
            CatalogSource(
                url = url,
                name = name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        // 立即刷新一次
        val source = catalogDao.getSourceById(id) ?: return null
        return refreshSource(source)
    }

    /**
     * 测试 catalog URL 是否有效
     */
    suspend fun testCatalogUrl(url: String): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                // 安全检查：只允许 https
                if (!url.startsWith("https://")) {
                    return@withContext TestResult(false, error = "URL must use HTTPS")
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext TestResult(false, error = "HTTP ${response.code}")
                }

                val body = response.body?.string()
                    ?: return@withContext TestResult(false, error = "Empty response")

                // 解析 JSON 并校验格式
                val json = JSONObject(body)
                if (!json.has("templates") || !json.get("templates").let { it is JSONArray }) {
                    return@withContext TestResult(false, error = "Invalid catalog format: missing templates array")
                }

                val count = json.getJSONArray("templates").length()
                TestResult(true, templateCount = count)
            } catch (e: Exception) {
                Timber.e(e, "[Catalog] 测试 URL 失败: $url")
                TestResult(false, error = e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 删除数据源（默认源不可删除）
     */
    suspend fun deleteSource(id: Long) {
        catalogDao.deleteSource(id)
        catalogDao.deleteTemplatesBySource(id)
    }

    /**
     * 更新数据源启用状态
     * 启用时先设置 loading 状态，然后在后台刷新（不依赖 UI 生命周期）
     * 禁用时清除该源的模板数据并重置状态为未同步
     */
    fun updateSourceEnabled(id: Long, enabled: Boolean) {
        applicationScope.launch {
            val source = catalogDao.getSourceById(id) ?: return@launch

            if (enabled) {
                // 先设置 loading 状态
                val loadingSource = source.copy(
                    enabled = true,
                    lastStatus = "loading",
                    updatedAt = System.currentTimeMillis()
                )
                catalogDao.updateSource(loadingSource)

                // 后台刷新，结果通过数据库 Flow 自动通知 UI
                try {
                    refreshSource(loadingSource)
                } catch (e: Exception) {
                    Timber.e(e, "[Catalog] 启用源时刷新失败: ${source.name}")
                    val errorSource = loadingSource.copy(
                        lastStatus = "error",
                        lastError = e.message,
                        updatedAt = System.currentTimeMillis()
                    )
                    catalogDao.updateSource(errorSource)
                }
            } else {
                // 禁用时清除该源的模板数据，并重置状态为未同步
                catalogDao.updateSource(source.copy(enabled = false))
                catalogDao.deleteTemplatesBySource(id)
                val resetSource = source.copy(
                    enabled = false,
                    lastStatus = "idle",
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )
                catalogDao.updateSource(resetSource)
            }
        }
    }

    /**
     * 更新自定义数据源的名称和 URL
     */
    suspend fun updateSource(id: Long, name: String, url: String): Boolean {
        val source = catalogDao.getSourceById(id) ?: return false
        if (source.isDefault) return false
        catalogDao.updateSource(source.copy(name = name, url = url))
        return true
    }

    // ========== 模板数据 ==========

    /**
     * 获取所有模板（响应式 Flow）
     * 自动去重、统计多源数量、合并收藏状态
     */
    fun observeAllTemplates(): Flow<List<TemplateItem>> {
        return combine(
            catalogDao.getAllTemplates(),
            getFavoriteTemplateIds()
        ) { templates, favIds ->
            buildTemplateItems(templates, favIds)
        }
    }

    /**
     * 搜索模板
     */
    fun searchTemplates(query: String): Flow<List<TemplateItem>> {
        return combine(
            catalogDao.searchTemplates(query),
            getFavoriteTemplateIds()
        ) { templates, favIds ->
            buildTemplateItems(templates, favIds)
        }
    }

    /**
     * 获取收藏的模板
     */
    fun observeFavorites(): Flow<List<CatalogTemplate>> =
        catalogDao.getFavoriteTemplates()

    private fun getFavoriteTemplateIds(): Flow<Set<String>> {
        return catalogDao.getFavoriteTemplates().map { list ->
            list.map { it.templateId }.toSet()
        }
    }

    /**
     * 将模板列表转换为 TemplateItem（去重 + 统计来源数 + 收藏状态）
     */
    private fun buildTemplateItems(
        templates: List<CatalogTemplate>,
        favIds: Set<String>
    ): List<TemplateItem> {
        val idCount = mutableMapOf<String, Int>()
        val idTemplate = mutableMapOf<String, CatalogTemplate>()

        templates.forEach { t ->
            idCount[t.templateId] = (idCount[t.templateId] ?: 0) + 1
            // 保留先出现的模板（按 sourceId 排序，默认源优先）
            if (!idTemplate.containsKey(t.templateId)) {
                idTemplate[t.templateId] = t
            }
        }

        return idTemplate.values.map { t ->
            TemplateItem(
                template = t,
                sourceCount = idCount[t.templateId] ?: 1,
                isFavorite = favIds.contains(t.templateId)
            )
        }.sortedBy { it.template.name }
    }

    // ========== 刷新逻辑 ==========

    /**
     * 刷新所有启用的数据源
     */
    suspend fun refreshAllSources(): List<CatalogSource> {
        return withContext(Dispatchers.IO) {
            val sources = catalogDao.getEnabledSources()
            val results = mutableListOf<CatalogSource>()

            for (source in sources) {
                try {
                    val result = refreshSource(source)
                    results.add(result)
                } catch (e: Exception) {
                    Timber.e(e, "[Catalog] 刷新源失败: ${source.name}")
                    results.add(source.copy(lastStatus = "error", lastError = e.message))
                }
            }

            results
        }
    }

    /**
     * 刷新单个数据源
     * @return 更新后的数据源对象
     */
    suspend fun refreshSource(source: CatalogSource): CatalogSource {
        return withContext(Dispatchers.IO) {
            // 更新状态为 loading
            val loadingSource = source.copy(
                lastStatus = "loading",
                updatedAt = System.currentTimeMillis()
            )
            catalogDao.updateSource(loadingSource)

            // 构建候选 URL 列表
            val urls = if (source.isDefault) {
                listOf(source.url) + FALLBACK_URLS
            } else {
                listOf(source.url)
            }

            var lastError: String? = null
            var catalogJson: JSONObject? = null
            var etag: String? = null

            urlLoop@ for (url in urls) {
                try {
                    val requestBuilder = Request.Builder().url(url)

                    // ETag 仅对主记录 URL 携带（主 URL 与 source.url 相同时）
                    if (url == source.url && source.etag != null) {
                        requestBuilder.header("If-None-Match", source.etag)
                    }

                    val response = okHttpClient.newCall(requestBuilder.build()).execute()

                    // 304 Not Modified：内容未变，直接返回成功
                    if (response.code == 304) {
                        Timber.d("[Catalog] 源 ${source.name} 内容未变更 (304)")
                        return@withContext source.copy(
                            lastStatus = "ok",
                            lastSynced = System.currentTimeMillis(),
                            lastError = null,
                            updatedAt = System.currentTimeMillis()
                        ).also { catalogDao.updateSource(it) }
                    }

                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code} ($url)"
                        continue@urlLoop
                    }

                    val body = response.body?.string()
                    if (body == null) {
                        lastError = "Empty response ($url)"
                        continue@urlLoop
                    }

                    val json = try {
                        JSONObject(body)
                    } catch (e: Exception) {
                        lastError = "Invalid JSON ($url): ${e.message}"
                        continue@urlLoop
                    }

                    // 简单校验：必须有 templates 数组
                    val templatesObj = json.opt("templates")
                    if (templatesObj == null || templatesObj !is JSONArray) {
                        lastError = "Invalid catalog format: missing templates array ($url)"
                        continue@urlLoop
                    }

                    catalogJson = json
                    etag = response.header("etag")
                    Timber.d("[Catalog] 从 $url 拉取成功，共 ${templatesObj.length()} 个模板")
                    break@urlLoop

                } catch (e: Exception) {
                    lastError = "${e.message} ($url)"
                    Timber.w("[Catalog] 拉取失败: $lastError")
                    continue@urlLoop
                }
            }

            if (catalogJson != null) {
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
    }

    /**
     * 从 catalog JSON 解析模板列表
     */
    private fun parseTemplates(json: JSONObject, source: CatalogSource): List<CatalogTemplate> {
        val templatesArray = try {
            json.getJSONArray("templates")
        } catch (e: Exception) {
            Timber.w(e, "[Catalog] 解析 templates 数组失败")
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val result = mutableListOf<CatalogTemplate>()

        for (i in 0 until templatesArray.length()) {
            try {
                val t = templatesArray.getJSONObject(i)

                val templateId = t.optString("id")
                val name = t.optString("name")
                val version = t.optString("version", "0.0.0")
                val type = t.optString("type", "worker")

                if (templateId.isBlank() || name.isBlank()) {
                    Timber.w("[Catalog] 跳过无效模板: id=$templateId, name=$name")
                    continue
                }

                // 作者信息
                var authorName: String? = null
                var authorUrl: String? = null
                if (t.has("author") && !t.isNull("author")) {
                    val author = t.getJSONObject("author")
                    authorName = author.optString("name")
                    authorUrl = author.optString("url")
                }

                // 标签
                val tags = if (t.has("tags") && !t.isNull("tags")) {
                    val tagArr = t.getJSONArray("tags")
                    val tagList = mutableListOf<String>()
                    for (j in 0 until tagArr.length()) {
                        tagList.add(tagArr.getString(j))
                    }
                    tagList.joinToString(",")
                } else null

                // 源码信息（普通类型）
                var sourceKind: String? = null
                var sourceUrl: String? = null
                var mainModule: String? = null
                if (t.has("source") && !t.isNull("source")) {
                    val src = t.getJSONObject("source")
                    sourceKind = src.optString("kind")
                    sourceUrl = src.optString("url")
                    mainModule = src.optStringOrNull("mainModule")
                }

                // hybrid 双源码
                var workerSourceKind: String? = null
                var workerSourceUrl: String? = null
                var workerMainModule: String? = null
                var pagesSourceKind: String? = null
                var pagesSourceUrl: String? = null
                if (t.has("sources") && !t.isNull("sources")) {
                    val sources = t.getJSONObject("sources")
                    if (sources.has("worker") && !sources.isNull("worker")) {
                        val ws = sources.getJSONObject("worker")
                        workerSourceKind = ws.optString("kind")
                        workerSourceUrl = ws.optString("url")
                        workerMainModule = ws.optStringOrNull("mainModule")
                    }
                    if (sources.has("pages") && !sources.isNull("pages")) {
                        val ps = sources.getJSONObject("pages")
                        pagesSourceKind = ps.optString("kind")
                        pagesSourceUrl = ps.optString("url")
                    }
                }

                // 静态资源配置（序列化为 JSON 字符串存储）
                val assetsJson = if (t.has("assets") && !t.isNull("assets")) {
                    t.getJSONObject("assets").toString()
                } else null

                // 绑定配置（序列化为 JSON 字符串存储）
                val bindingsJson = if (t.has("bindings") && !t.isNull("bindings")) {
                    t.getJSONArray("bindings").toString()
                } else null

                // 环境变量
                val envJson = if (t.has("env") && !t.isNull("env")) {
                    t.getJSONObject("env").toString()
                } else null

                // 路由
                val routes = if (t.has("routes") && !t.isNull("routes")) {
                    val routeArr = t.getJSONArray("routes")
                    val routeList = mutableListOf<String>()
                    for (j in 0 until routeArr.length()) {
                        routeList.add(routeArr.getString(j))
                    }
                    routeList.joinToString(",")
                } else null

                // Cron 定时任务
                val crons = if (t.has("crons") && !t.isNull("crons")) {
                    val cronArr = t.getJSONArray("crons")
                    val cronList = mutableListOf<String>()
                    for (j in 0 until cronArr.length()) {
                        cronList.add(cronArr.getString(j))
                    }
                    cronList.joinToString(",")
                } else null

                // 兼容性标志
                val compatibilityFlags = if (t.has("compatibility_flags") && !t.isNull("compatibility_flags")) {
                    val flagArr = t.getJSONArray("compatibility_flags")
                    val flagList = mutableListOf<String>()
                    for (j in 0 until flagArr.length()) {
                        flagList.add(flagArr.getString(j))
                    }
                    flagList.joinToString(",")
                } else null

                result.add(
                    CatalogTemplate(
                        templateId = templateId,
                        sourceId = source.id,
                        sourceName = source.name,
                        name = name,
                        description = t.optStringOrNull("description"),
                        version = version,
                        type = type,
                        authorName = authorName,
                        authorUrl = authorUrl,
                        tags = tags,
                        icon = t.optStringOrNull("icon"),
                        homepage = t.optStringOrNull("homepage"),
                        readmeUrl = t.optStringOrNull("readmeUrl"),
                        sourceKind = sourceKind,
                        sourceUrl = sourceUrl,
                        mainModule = mainModule,
                        workerSourceKind = workerSourceKind,
                        workerSourceUrl = workerSourceUrl,
                        workerMainModule = workerMainModule,
                        pagesSourceKind = pagesSourceKind,
                        pagesSourceUrl = pagesSourceUrl,
                        assetsJson = assetsJson,
                        bindingsJson = bindingsJson,
                        envJson = envJson,
                        routes = routes,
                        crons = crons,
                        compatibilityDate = t.optStringOrNull("compatibility_date"),
                        compatibilityFlags = compatibilityFlags,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } catch (e: Exception) {
                Timber.w(e, "[Catalog] 解析模板失败 (index=$i)")
                continue
            }
        }

        Timber.d("[Catalog] 成功解析 ${result.size} 个模板")
        return result
    }

    /**
     * 保存指定数据源的模板
     * 智能同步：新增/更新存在的，移除已删除的
     */
    private suspend fun saveTemplatesForSource(source: CatalogSource, templates: List<CatalogTemplate>) {
        val templateIds = templates.map { it.templateId }

        // 删除云端已移除的模板（保持本地数据与远端一致）
        if (templateIds.isNotEmpty()) {
            catalogDao.deleteTemplatesNotInList(source.id, templateIds)
        } else {
            // 如果远端返回空，不执行删除（避免误删），由调用方判断
            Timber.w("[Catalog] 源 ${source.name} 返回空模板列表，跳过删除操作")
        }

        // 插入/更新模板
        if (templates.isNotEmpty()) {
            catalogDao.insertTemplates(templates)
        }
    }

    // ========== 收藏功能 ==========

    /**
     * 切换收藏状态
     * @return 切换后的收藏状态（true = 已收藏）
     */
    suspend fun toggleFavorite(templateId: String): Boolean {
        val isFav = catalogDao.isFavorite(templateId) > 0
        return if (isFav) {
            catalogDao.removeFavorite(templateId)
            false
        } else {
            catalogDao.addFavorite(CatalogFavorite(templateId = templateId))
            true
        }
    }

    /**
     * 检查模板是否已收藏
     */
    suspend fun isFavorite(templateId: String): Boolean {
        return catalogDao.isFavorite(templateId) > 0
    }

    // ========== 辅助方法 ==========

    /**
     * 解析模板的绑定配置
     */
    fun parseBindings(bindingsJson: String?): List<CatalogBinding> {
        if (bindingsJson.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<CatalogBinding>>() {}.type
            gson.fromJson(bindingsJson, type)
        } catch (e: Exception) {
            Timber.w(e, "[Catalog] 解析 bindings 失败")
            emptyList()
        }
    }

    /**
     * 解析模板的环境变量
     */
    fun parseEnvVars(envJson: String?): Map<String, String> {
        if (envJson.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(envJson, type)
        } catch (e: Exception) {
            Timber.w(e, "[Catalog] 解析 env 失败")
            emptyMap()
        }
    }

    // ========== 数据类 ==========

    /**
     * URL 测试结果
     */
    data class TestResult(
        val success: Boolean,
        val templateCount: Int = 0,
        val error: String? = null
    )
}
