package com.muort.upworker.core.repository

import com.muort.upworker.core.database.ZoneDao
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.model.ZoneInfo
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.network.CloudFlareApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZoneRepository @Inject constructor(
    private val api: CloudFlareApi,
    private val zoneDao: ZoneDao,
    private val accountRepository: AccountRepository
) {
    
    fun getZonesByAccount(accountId: Long): Flow<List<Zone>> {
        return zoneDao.getZonesByAccount(accountId)
    }
    
    suspend fun getSelectedZone(accountId: Long): Zone? {
        return zoneDao.getSelectedZone(accountId)
    }
    
    suspend fun fetchAndSaveZones(accountId: Long, token: String): Resource<List<Zone>> {
        return try {
            val response = api.listZones("Bearer $token")
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    // Convert API zones to database zones
                    val zones = body.result.map { zoneInfo ->
                        zoneInfo.toZone(accountId)
                    }
                    
                    // Save to database
                    zoneDao.insertZones(zones)
                    
                    Resource.Success(zones)
                } else {
                    val errorMsg = body?.errors?.firstOrNull()?.message ?: "获取Zone列表失败"
                    Resource.Error(errorMsg)
                }
            } else {
                Resource.Error("HTTP ${response.code()}: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "网络错误")
        }
    }
    
    suspend fun setSelectedZone(accountId: Long, zoneId: String) {
        // Update zone selection in zones table
        zoneDao.setSelectedZone(accountId, zoneId)
        
        // Also update the account's zoneId field for backward compatibility
        accountRepository.updateAccountZoneId(accountId, zoneId)
    }
    
    suspend fun deleteZonesByAccount(accountId: Long) {
        zoneDao.deleteZonesByAccount(accountId)
    }
    
    private fun ZoneInfo.toZone(accountId: Long): Zone {
        return Zone(
            id = this.id,
            accountId = accountId,
            name = this.name,
            status = this.status,
            type = this.type,
            paused = this.paused,
            isSelected = false
        )
    }
}
