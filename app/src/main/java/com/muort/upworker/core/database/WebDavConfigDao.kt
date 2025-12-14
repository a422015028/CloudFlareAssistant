package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.WebDavConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDavConfigDao {
    
    @Query("SELECT * FROM webdav_config LIMIT 1")
    fun getConfig(): Flow<WebDavConfig?>
    
    @Query("SELECT * FROM webdav_config LIMIT 1")
    suspend fun getConfigSync(): WebDavConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: WebDavConfig): Long
    
    @Update
    suspend fun update(config: WebDavConfig)
    
    @Delete
    suspend fun delete(config: WebDavConfig)
    
    @Query("DELETE FROM webdav_config")
    suspend fun deleteAll()
}
