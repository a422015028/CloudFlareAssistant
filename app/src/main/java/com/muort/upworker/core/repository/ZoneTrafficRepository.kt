package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zone 流量分析仓库：GraphQL Analytics API 查询 httpRequests1dGroups，
 * 归一化为 [TrafficDataPoint] 列表。对应 orange-cloud AnalyticsRepository.zoneTraffic。
 */
@Singleton
class ZoneTrafficRepository @Inject constructor(
    private val api: CloudFlareApi,
) {
    /** 查询最近 N 天的 Zone 流量数据。dayCount=1 取当天，7/30 取历史。 */
    suspend fun getZoneTraffic(
        account: Account, zoneId: String, dayCount: Int = 7,
    ): Resource<List<TrafficDataPoint>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val today = LocalDate.now(ZoneOffset.UTC)
            val since = today.minusDays(dayCount.toLong())
            val sinceStr = since.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val untilStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val query = """
                query ZoneTraffic(${'$'}zoneTag: string, ${'$'}since: String!, ${'$'}until: String!) {
                  viewer {
                    zones(filter: {zoneTag: ${'$'}zoneTag}) {
                      httpRequests1dGroups(
                        limit: $dayCount
                        filter: {date_geq: ${'$'}since, date_leq: ${'$'}until}
                      ) {
                        sum {
                          requests
                          bytes
                          cachedRequests
                          threats
                          pageViews
                        }
                        uniq {
                          uniques
                        }
                        dimensions {
                          date
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = mapOf(
                "zoneTag" to zoneId,
                "since" to sinceStr,
                "until" to untilStr,
            )

            val resp = api.queryAnalytics(
                AuthHelper.getBearerToken(account),
                AuthHelper.getEmail(account),
                AuthHelper.getGlobalApiKey(account),
                AnalyticsGraphQLRequest(query = query, variables = variables),
            )

            if (!resp.isSuccessful) {
                return@safeApiCall Resource.Error("HTTP ${resp.code()}: ${resp.message()}")
            }

            val body = resp.body() ?: return@safeApiCall Resource.Error("空响应")
            if (body.errors?.isNotEmpty() == true) {
                return@safeApiCall Resource.Error(body.errors.joinToString(", ") { it.message })
            }

            val groups = body.data?.viewer?.zones?.firstOrNull()?.httpRequests ?: emptyList()
            val points = groups.mapNotNull { g ->
                val date = g.dimensions?.date ?: return@mapNotNull null
                TrafficDataPoint(
                    date = date,
                    requests = g.sum.requests,
                    bytes = g.sum.bytes,
                    threats = g.sum.threats ?: 0,
                    pageViews = g.sum.pageViews ?: 0,
                    uniques = g.uniq?.uniques ?: 0,
                    cachedRequests = g.sum.cachedRequests ?: 0,
                )
            }.sortedBy { it.date }

            Timber.d("Zone traffic: ${points.size} data points for zone $zoneId")
            Resource.Success(points)
        }
    }
}
