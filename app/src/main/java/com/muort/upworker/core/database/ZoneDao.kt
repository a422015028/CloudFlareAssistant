package com.muort.upworker.core.database

import androidx.room.*
import com.muort.upworker.core.model.Zone
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    
    @Query("SELECT * FROM zones WHERE accountId = :accountId ORDER BY name ASC")
    fun getZonesByAccount(accountId: Long): Flow<List<Zone>>
    
    @Query("SELECT * FROM zones WHERE accountId = :accountId AND isSelected = 1 LIMIT 1")
    suspend fun getSelectedZone(accountId: Long): Zone?
    
    @Query("SELECT * FROM zones WHERE id = :zoneId")
    suspend fun getZoneById(zoneId: String): Zone?
    
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
