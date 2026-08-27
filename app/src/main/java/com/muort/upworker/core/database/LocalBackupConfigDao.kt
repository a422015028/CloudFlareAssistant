package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.LocalBackupConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalBackupConfigDao {

    @Query("SELECT * FROM local_backup_config LIMIT 1")
    fun getConfig(): Flow<LocalBackupConfig?>

    @Query("SELECT * FROM local_backup_config LIMIT 1")
    suspend fun getConfigSync(): LocalBackupConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: LocalBackupConfig): Long

    @Update
    suspend fun update(config: LocalBackupConfig)

    @Delete
    suspend fun delete(config: LocalBackupConfig)

    @Query("DELETE FROM local_backup_config")
    suspend fun deleteAll()
}
