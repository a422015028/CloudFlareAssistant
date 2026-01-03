package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.ScriptVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptVersionDao {
    
    @Query("SELECT * FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName ORDER BY timestamp DESC")
    fun getVersionHistory(accountEmail: String, scriptName: String): Flow<List<ScriptVersion>>
    
    @Query("SELECT * FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestVersion(accountEmail: String, scriptName: String): ScriptVersion?
    
    @Insert
    suspend fun insertVersion(version: ScriptVersion): Long
    
    @Delete
    suspend fun deleteVersion(version: ScriptVersion)
    
    @Query("DELETE FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName AND isAutoSave = 1 AND id NOT IN (SELECT id FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun cleanOldAutoSaves(accountEmail: String, scriptName: String, keepCount: Int = 50)
    
    @Query("SELECT COUNT(*) FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName")
    suspend fun getVersionCount(accountEmail: String, scriptName: String): Int
    
    @Query("DELETE FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName")
    suspend fun deleteAllVersions(accountEmail: String, scriptName: String)
    
    @Query("DELETE FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName AND (description IS NULL OR description != '从Cloudflare加载')")
    suspend fun deleteNonCloudflareVersions(accountEmail: String, scriptName: String): Int
    
    @Query("SELECT * FROM script_versions WHERE accountEmail = :accountEmail AND scriptName = :scriptName AND description = '从Cloudflare加载' ORDER BY timestamp DESC")
    suspend fun getCloudflareVersions(accountEmail: String, scriptName: String): List<ScriptVersion>
    
    @Query("UPDATE script_versions SET timestamp = :newTimestamp WHERE id = :versionId")
    suspend fun updateVersionTimestamp(versionId: Long, newTimestamp: Long)
}
