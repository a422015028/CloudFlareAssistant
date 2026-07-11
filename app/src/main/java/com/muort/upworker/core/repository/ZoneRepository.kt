package com.muort.upworker.core.repository

import com.muort.upworker.core.database.ZoneDao
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CreateZoneRequest
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.model.ZoneInfo
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
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

    fun observeZone(zoneId: String): Flow<Zone?> {
        return zoneDao.observeZoneById(zoneId)
    }

    suspend fun getZone(zoneId: String): Zone? {
        return zoneDao.getZoneById(zoneId)
    }
    
    suspend fun getSelectedZone(accountId: Long): Zone? {
        return zoneDao.getSelectedZone(accountId)
    }
    
    /**
     * 从 API 获取 Zones 并保存到数据库
     * 支持两种认证方式：API Token 和 Global API Key
     */
    suspend fun fetchAndSaveZones(account: Account): Resource<List<Zone>> {
        return try {
            val response = api.listZones(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account)
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.success == true && body.result != null) {
                    // 保留当前已选中的 zoneId，避免刷新时丢失活动域名选择
                    val currentSelectedZoneId = zoneDao.getSelectedZone(account.id)?.id

                    // Convert API zones to database zones
                    val zones = body.result.map { zoneInfo ->
                        val zone = zoneInfo.toZone(account.id)
                        if (zone.id == currentSelectedZoneId) {
                            zone.copy(isSelected = true)
                        } else {
                            zone
                        }
                    }

                    // 同步删除：移除云端已不存在的域名，避免本地残留
                    val remoteIds = zones.map { it.id }
                    if (remoteIds.isNotEmpty()) {
                        zoneDao.deleteZonesNotInList(account.id, remoteIds)
                    } else {
                        // 云端为空，直接清空该账号下所有 zone
                        zoneDao.deleteZonesByAccount(account.id)
                    }

                    // Save to database (upsert)
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

    /** 新建 Zone（添加域名）。返回含 CF 分配的 name_servers，状态 pending。 */
    suspend fun createZone(account: Account, domain: String): Resource<Zone> {
        return try {
            val request = CreateZoneRequest(
                name = domain,
                account = CreateZoneRequest.AccountRef(account.accountId)
            )
            val response = api.createZone(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                request = request
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val zoneInfo = response.body()!!.result!!
                val zone = zoneInfo.toZone(account.id)
                zoneDao.insertZone(zone)
                Resource.Success(zone)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${response.code()}: ${response.message()}"
                Resource.Error(errorMsg)
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
            isSelected = false,
            nameServers = this.nameServers?.joinToString("\n"),
            plan = this.plan?.name
        )
    }
}
