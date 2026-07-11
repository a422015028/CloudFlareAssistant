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
 * SSL / 证书仓库：边缘证书包 + Universal SSL 开关 + 删除证书包。
 * 对应 orange-cloud SslRepository。SSL/TLS 加密模式（ssl mode）走 ZoneSettingsRepository。
 */
@Singleton
class SslRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    /** 列出该 Zone 的证书包（含未激活的，status=all）。 */
    suspend fun listCertificatePacks(account: Account, zoneId: String): Resource<List<SslCertificatePack>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listCertificatePacks(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result ?: emptyList())
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun getUniversalEnabled(account: Account, zoneId: String): Resource<Boolean> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getUniversalSslSettings(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result?.enabled ?: false)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun setUniversalEnabled(account: Account, zoneId: String, enabled: Boolean): Resource<Boolean> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.setUniversalSslSettings(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, UniversalSslSettings(enabled),
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result?.enabled ?: enabled)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun deletePack(account: Account, zoneId: String, packId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteCertificatePack(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, packId,
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
