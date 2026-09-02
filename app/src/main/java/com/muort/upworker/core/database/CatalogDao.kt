package com.muort.upworker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muort.upworker.core.model.CatalogFavorite
import com.muort.upworker.core.model.CatalogSource
import com.muort.upworker.core.model.CatalogTemplate
import kotlinx.coroutines.flow.Flow

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

    /**
     * 删除指定数据源下不在 keepIds 列表中的模板
     * 用于同步时移除云端已删除的模板
     */
    @Query("DELETE FROM catalog_templates WHERE sourceId = :sourceId AND templateId NOT IN (:keepIds)")
    suspend fun deleteTemplatesNotInList(sourceId: Long, keepIds: List<String>)

    /**
     * 搜索模板（按名称、描述、标签模糊匹配）
     */
    @Query("""
        SELECT * FROM catalog_templates 
        WHERE name LIKE '%' || :query || '%' 
           OR description LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchTemplates(query: String): Flow<List<CatalogTemplate>>

    // ========== Favorites ==========

    /**
     * 获取所有收藏的模板（按收藏时间倒序）
     */
    @Query("""
        SELECT cf.* FROM catalog_favorites fav 
        JOIN catalog_templates cf ON fav.templateId = cf.templateId 
        ORDER BY fav.createdAt DESC
    """)
    fun getFavoriteTemplates(): Flow<List<CatalogTemplate>>

    @Query("SELECT COUNT(*) FROM catalog_favorites WHERE templateId = :templateId")
    suspend fun isFavorite(templateId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: CatalogFavorite)

    @Query("DELETE FROM catalog_favorites WHERE templateId = :templateId")
    suspend fun removeFavorite(templateId: String)
}
