package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.R2BackupConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface R2BackupConfigDao {
    
    @Query("SELECT * FROM r2_backup_config LIMIT 1")
    fun getConfig(): Flow<R2BackupConfig?>
    
    @Query("SELECT * FROM r2_backup_config LIMIT 1")
    suspend fun getConfigSync(): R2BackupConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: R2BackupConfig): Long
    
    @Update
    suspend fun update(config: R2BackupConfig)
    
    @Delete
    suspend fun delete(config: R2BackupConfig)
    
    @Query("DELETE FROM r2_backup_config")
    suspend fun deleteAll()
}
