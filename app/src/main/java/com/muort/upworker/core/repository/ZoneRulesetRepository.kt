package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zone Rulesets 仓库：WAF 自定义规则 / Cache Rules / Rate Limit / Transform Rules 共用同一套
 * Rulesets entrypoint API（phase 不同）。对应 orange-cloud 的 SecurityRepository /
 * CacheRuleRepository / RateLimitRepository / TransformRepository。
 *
 * - GET    /zones/{id}/rulesets/phases/{phase}/entrypoint
 * - PUT    /zones/{id}/rulesets/phases/{phase}/entrypoint  （首条规则时创建）
 * - POST   /zones/{id}/rulesets/{rulesetId}/rules
 * - PATCH  /zones/{id}/rulesets/{rulesetId}/rules/{ruleId}
 * - DELETE /zones/{id}/rulesets/{rulesetId}/rules/{ruleId}
 */
@Singleton
class ZoneRulesetRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    /** 取某 phase 的 entrypoint ruleset；phase 还没规则集时返回 null。 */
    suspend fun getRuleset(account: Account, zoneId: String, phase: String): Resource<WafRuleset?> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getRulesetEntrypoint(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, phase,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result)
                } else if (resp.code() == 404 || isNoEntrypoint(resp.body())) {
                    // phase 还没有规则集，视为空
                    Resource.Success(null)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    /** phase 还没有规则集时，用首条规则创建 entrypoint。 */
    suspend fun createEntrypoint(
        account: Account, zoneId: String, phase: String, rule: WafRuleCreate,
    ): Resource<WafRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.createRulesetEntrypoint(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, phase, WafEntrypointUpdate(listOf(rule)),
            )
            resp.toResource("创建规则集失败")
        }
    }

    /** 向已有规则集追加规则，返回更新后的完整 ruleset。 */
    suspend fun addRule(
        account: Account, zoneId: String, rulesetId: String, rule: WafRuleCreate,
    ): Resource<WafRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.addRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule,
            )
            resp.toResource("添加规则失败")
        }
    }

    /** 启停单条规则。Cloudflare PATCH /rules 要求 action 和 expression 必填，所以发送完整规则体。 */
    suspend fun setRuleEnabled(
        account: Account, zoneId: String, rulesetId: String, rule: WafRule, enabled: Boolean,
    ): Resource<WafRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = WafRuleCreate(
                action = rule.action ?: "block",
                expression = rule.expression ?: "",
                description = rule.description,
                enabled = enabled,
            )
            val resp = api.updateRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule.id, body,
            )
            resp.toResource("切换规则失败")
        }
    }

    /** 整条更新规则（动作 / 表达式 / 名称 / 启用），返回更新后的完整 ruleset。 */
    suspend fun updateRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String, rule: WafRuleCreate,
    ): Resource<WafRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.updateRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId, rule,
            )
            resp.toResource("更新规则失败")
        }
    }

    /** 删除单条规则。 */
    suspend fun deleteRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.deleteRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    private fun isNoEntrypoint(body: CloudFlareResponse<*>?): Boolean {
        return body?.errors?.any {
            it.message.contains("could not find entrypoint", ignoreCase = true)
        } == true
    }

    private fun <T> Response<CloudFlareResponse<T>>.toResource(errorMsg: String): Resource<T> {
        return if (isSuccessful && body()?.success == true) {
            body()?.result?.let { Resource.Success(it) } ?: Resource.Error("$errorMsg: 无返回数据")
        } else {
            Resource.Error(body()?.errors?.firstOrNull()?.message
                ?: "HTTP ${code()}: ${message()}")
        }
    }

    // ==================== Cache Rules 专用（保留 action_parameters） ====================

    private val cachePhase = "http_request_cache_settings"

    suspend fun getCacheRuleset(account: Account, zoneId: String): Resource<CacheRuleset?> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getCacheRulesetEntrypoint(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, cachePhase,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result)
                } else if (resp.code() == 404 || isNoEntrypoint(resp.body())) {
                    Resource.Success(null)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun createCacheEntrypoint(
        account: Account, zoneId: String, rule: CacheRuleCreate,
    ): Resource<CacheRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.createCacheRulesetEntrypoint(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, cachePhase, CacheEntrypointUpdate(listOf(rule)),
            )
            resp.toResource("创建缓存规则集失败")
        }
    }

    suspend fun addCacheRule(
        account: Account, zoneId: String, rulesetId: String, rule: CacheRuleCreate,
    ): Resource<CacheRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.addCacheRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule,
            )
            resp.toResource("添加缓存规则失败")
        }
    }

    suspend fun setCacheRuleEnabled(
        account: Account, zoneId: String, rulesetId: String, rule: CacheRule, enabled: Boolean,
    ): Resource<CacheRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val body = CacheRuleCreate(
                action = rule.action ?: "set_cache_settings",
                expression = rule.expression ?: "",
                description = rule.description,
                enabled = enabled,
                actionParameters = rule.actionParameters,
            )
            val resp = api.updateCacheRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule.id, body,
            )
            resp.toResource("切换缓存规则失败")
        }
    }

    suspend fun updateCacheRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String, rule: CacheRuleCreate,
    ): Resource<CacheRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.updateCacheRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId, rule,
            )
            resp.toResource("更新缓存规则失败")
        }
    }

    suspend fun deleteCacheRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.deleteRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId,
            )
            if (resp.isSuccessful && resp.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                    ?: "HTTP ${resp.code()}: ${resp.message()}")
            }
        }
    }

    // ==================== Rate Limiting 专用（保留 ratelimit 配置） ====================

    private val rlPhase = "http_ratelimit"

    suspend fun getRateLimitRuleset(account: Account, zoneId: String): Resource<RateLimitRuleset?> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val resp = api.getRateLimitRulesetEntrypoint(
                    AuthHelper.getBearerToken(account),
                    AuthHelper.getEmail(account),
                    AuthHelper.getGlobalApiKey(account),
                    zoneId, rlPhase,
                )
                if (resp.isSuccessful && resp.body()?.success == true) {
                    Resource.Success(resp.body()?.result)
                } else if (resp.code() == 404 || isNoEntrypoint(resp.body())) {
                    Resource.Success(null)
                } else {
                    Resource.Error(resp.body()?.errors?.firstOrNull()?.message
                        ?: "HTTP ${resp.code()}: ${resp.message()}")
                }
            }
        }

    suspend fun createRateLimitEntrypoint(
        account: Account, zoneId: String, rule: RateLimitRuleCreate,
    ): Resource<RateLimitRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.createRateLimitEntrypoint(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rlPhase, RateLimitEntrypointUpdate(listOf(rule)),
            )
            resp.toResource("创建速率限制规则集失败")
        }
    }

    suspend fun addRateLimitRule(
        account: Account, zoneId: String, rulesetId: String, rule: RateLimitRuleCreate,
    ): Resource<RateLimitRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.addRateLimitRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule,
            )
            resp.toResource("添加速率限制规则失败")
        }
    }

    suspend fun setRateLimitRuleEnabled(
        account: Account, zoneId: String, rulesetId: String, rule: RateLimitRule, enabled: Boolean,
    ): Resource<RateLimitRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            // http_ratelimit phase 的 PATCH 要求 action/expression/ratelimit 必填，
            // 只发 {"enabled":false} 会报 20015/20125/20132，必须发送完整规则体。
            val rl = rule.ratelimit
            val body = RateLimitRuleCreate(
                action = rule.action ?: "block",
                expression = rule.expression ?: "",
                description = rule.description,
                enabled = enabled,
                ratelimit = RateLimitConfigInput(
                    characteristics = rl?.characteristics ?: listOf("ip.src", "cf.colo.id"),
                    period = rl?.period ?: 60,
                    requestsPerPeriod = rl?.requestsPerPeriod ?: 100,
                    mitigationTimeout = rl?.mitigationTimeout ?: 60,
                ),
            )
            val resp = api.updateRateLimitRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, rule.id, body,
            )
            resp.toResource("切换速率限制规则失败")
        }
    }

    suspend fun updateRateLimitRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String, rule: RateLimitRuleCreate,
    ): Resource<RateLimitRuleset> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.updateRateLimitRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId, rule,
            )
            resp.toResource("更新速率限制规则失败")
        }
    }

    suspend fun deleteRateLimitRule(
        account: Account, zoneId: String, rulesetId: String, ruleId: String,
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val resp = api.deleteRulesetRule(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                zoneId, rulesetId, ruleId,
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
