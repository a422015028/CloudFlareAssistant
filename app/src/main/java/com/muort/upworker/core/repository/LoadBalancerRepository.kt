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
 * 负载均衡仓库：Load Balancer（zone 级）+ Pool / Monitor（account 级，只读）。
 * 对应 orange-cloud LoadBalancerRepository。v1 仅支持 LB 列表 + 启停 + 删除。
 */
@Singleton
class LoadBalancerRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    suspend fun listLoadBalancers(account: Account, zoneId: String): Resource<List<LoadBalancer>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.listLoadBalancers(
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

    suspend fun setEnabled(
        account: Account, zoneId: String, lbId: String, enabled: Boolean,
    ): Resource<LoadBalancer> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.toggleLoadBalancer(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, lbId, LoadBalancerToggle(enabled),
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                resp.body()?.result?.let { Resource.Success(it) } ?: Resource.Error("切换失败：无返回数据")
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    suspend fun deleteLoadBalancer(account: Account, zoneId: String, lbId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.deleteLoadBalancer(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, lbId,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun listPools(account: Account): Resource<List<Pool>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.listLoadBalancerPools(
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

    suspend fun listMonitors(account: Account): Resource<List<Monitor>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.listLoadBalancerMonitors(
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
}
