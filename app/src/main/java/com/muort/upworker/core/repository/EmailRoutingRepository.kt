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
 * Email Routing 仓库：域名级设置 / 规则 + 账号级目的地址。
 * 对应 orange-cloud EmailRoutingRepository。
 */
@Singleton
class EmailRoutingRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    // —— 设置 ——

    suspend fun getSettings(account: Account, zoneId: String): Resource<EmailRoutingSettings> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getEmailRoutingSettings(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    resp.body()?.result?.let { Resource.Success(it) }
                        ?: Resource.Error("获取设置失败：无返回数据")
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun setEnabled(account: Account, zoneId: String, enabled: Boolean): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val action = if (enabled) "enable" else "disable"
                val resp = api.setEmailRoutingEnabled(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, action, emptyMap(),
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    // —— 规则 ——

    suspend fun listRules(account: Account, zoneId: String): Resource<List<EmailRoutingRule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listEmailRoutingRules(
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

    suspend fun createRule(
        account: Account, zoneId: String, input: EmailRoutingRuleInput,
    ): Resource<EmailRoutingRule> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.createEmailRoutingRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, input,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("创建失败：无返回数据")
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun deleteRule(account: Account, zoneId: String, ruleId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteEmailRoutingRule(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, ruleId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    // —— 目的地址（账号级） ——

    suspend fun listAddresses(account: Account): Resource<List<EmailDestinationAddress>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listEmailDestinationAddresses(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    account.accountId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result ?: emptyList())
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun createAddress(account: Account, email: String): Resource<EmailDestinationAddress> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.createEmailDestinationAddress(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    account.accountId, EmailDestinationCreate(email),
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("添加失败：无返回数据")
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun deleteAddress(account: Account, addressId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteEmailDestinationAddress(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    account.accountId, addressId,
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
