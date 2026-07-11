package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.Zone
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
        @Query("DELETE FROM zones")
        suspend fun deleteAllZones()
    
    @Query("SELECT * FROM zones WHERE accountId = :accountId ORDER BY name ASC")
    fun getZonesByAccount(accountId: Long): Flow<List<Zone>>
    
    @Query("SELECT * FROM zones WHERE accountId = :accountId AND isSelected = 1 LIMIT 1")
    suspend fun getSelectedZone(accountId: Long): Zone?
    
    @Query("SELECT * FROM zones WHERE id = :zoneId")
    suspend fun getZoneById(zoneId: String): Zone?

    @Query("SELECT * FROM zones WHERE id = :zoneId")
    fun observeZoneById(zoneId: String): Flow<Zone?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: Zone)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<Zone>)
    
    @Update
    suspend fun updateZone(zone: Zone)
    
    @Delete
    suspend fun deleteZone(zone: Zone)
    
    @Query("DELETE FROM zones WHERE accountId = :accountId")
    suspend fun deleteZonesByAccount(accountId: Long)

    /** 删除指定账号下不在 remoteIds 列表中的 zone（用于同步删除云端已移除的域名）。 */
    @Query("DELETE FROM zones WHERE accountId = :accountId AND id NOT IN (:remoteIds)")
    suspend fun deleteZonesNotInList(accountId: Long, remoteIds: List<String>)
    
    @Transaction
    suspend fun setSelectedZone(accountId: Long, zoneId: String) {
        // Deselect all zones for this account
        deselectAllZonesForAccount(accountId)
        // Select the specified zone
        selectZone(zoneId)
    }
    
    @Query("UPDATE zones SET isSelected = 0 WHERE accountId = :accountId")
    suspend fun deselectAllZonesForAccount(accountId: Long)
    
    @Query("UPDATE zones SET isSelected = 1 WHERE id = :zoneId")
    suspend fun selectZone(zoneId: String)
}
