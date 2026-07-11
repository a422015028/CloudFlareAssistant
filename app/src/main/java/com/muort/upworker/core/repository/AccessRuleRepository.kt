package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** IP 访问规则仓库（legacy firewall access rules）。对应 orange-cloud FirewallRepository。 */
@Singleton
class AccessRuleRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    suspend fun listRules(account: Account, zoneId: String): Resource<List<FirewallAccessRule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listAccessRules(
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
        account: Account, zoneId: String, rule: AccessRuleCreate,
    ): Resource<FirewallAccessRule> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.createAccessRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rule,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("创建失败：无返回数据")
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun updateRule(
        account: Account, zoneId: String, ruleId: String, update: AccessRuleUpdate,
    ): Resource<FirewallAccessRule> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.updateAccessRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, ruleId, update,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("更新失败：无返回数据")
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun deleteRule(account: Account, zoneId: String, ruleId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteAccessRule(
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
}
