package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zone Settings 仓库：设置读写 + 全量 / 按 URL 清理缓存。
 * 对应 orange-cloud ZoneSettingsRepository。覆盖「设置」「性能已缓存」两个功能页。
 */
@Singleton
class ZoneSettingsRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    suspend fun getSetting(account: Account, zoneId: String, setting: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getZoneSetting(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, setting,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result?.value ?: "")
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun setSetting(
        account: Account, zoneId: String, setting: String, value: String,
    ): Resource<String> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.setZoneSetting(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, setting, ZoneSettingUpdate(value),
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                Resource.Success(resp.body()?.result?.value ?: value)
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun purgeAllCache(account: Account, zoneId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.purgeAllCache(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, PurgeRequest(purgeEverything = true),
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun purgeFiles(account: Account, zoneId: String, urls: List<String>): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.purgeFilesCache(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, PurgeFilesRequest(files = urls),
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }
}
