package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    /**
     * 获取仪表盘指标数据
     * @param account 账号信息
     * @param timeRange 时间范围（1天/7天/30天）
     */
    suspend fun getDashboardMetrics(
        account: Account, 
        timeRange: TimeRange = TimeRange.ONE_DAY
    ): Resource<DashboardMetrics> = 
        withContext(Dispatchers.IO) {
            safeApiCall {
                Timber.d("Fetching dashboard metrics for account: ${account.accountId}, timeRange: ${timeRange.displayName}")
                
                // 使用时间范围枚举获取开始和结束时间
                val startDateTime = timeRange.getStartDateTime()
                val endDateTime = timeRange.getEndDateTime()
                
                val startDate = startDateTime.substring(0, 10) // 提取日期部分
                val endDate = endDateTime.substring(0, 10)
                
                // 如果有 zoneId，查询 Zone 级别的数据
                val zoneId = account.zoneId
                
                val query = buildAnalyticsQuery(zoneId)
                val variables = buildQueryVariables(
                    zoneId = zoneId,
                    accountId = account.accountId,
                    startDate = startDate,
                    endDate = endDate,
                    startDateTime = startDateTime,
                    endDateTime = endDateTime
                )
                
                val request = AnalyticsGraphQLRequest(
                    query = query,
                    variables = variables
                )
                
                val response = api.queryAnalytics(
                    token = "Bearer ${account.token}",
                    request = request
                )
                
                Timber.d("Analytics API response: ${response.code()}")
                
                if (response.isSuccessful) {
                    val analyticsResponse = response.body()
                    
                    if (analyticsResponse?.errors?.isNotEmpty() == true) {
                        val errorMsg = analyticsResponse.errors.joinToString(", ") { it.message }
                        Timber.e("GraphQL errors: $errorMsg")
                        return@safeApiCall Resource.Error("Analytics query failed: $errorMsg")
                    }
                    
                    // 获取 D1 数据库列表（REST API）
                    val d1Stats = fetchD1DatabaseStats(account)
                    
                    // 获取 R2 存储桶列表（REST API）
                    val r2Stats = fetchR2BucketStats(account)
                    
                    val metrics = parseAnalyticsData(analyticsResponse?.data, d1Stats, r2Stats)
                    Timber.d("Parsed metrics: $metrics")
                    Resource.Success(metrics)
                } else {
                    val errorMsg = response.message()
                    Timber.e("Failed to fetch analytics: $errorMsg")
                    Resource.Error("Failed to fetch analytics: $errorMsg")
                }
            }
        }
    
    /**
     * 构建 GraphQL 查询语句
     */
    private fun buildAnalyticsQuery(zoneId: String?): String {
        return if (zoneId != null) {
            // Zone + Workers 查询
            """
            query AnalyticsDashboard(${'$'}zoneTag: string, ${'$'}accountTag: string, ${'$'}sinceDate: string!, ${'$'}untilDate: string!, ${'$'}sinceTime: Time!, ${'$'}untilTime: Time!) {
              viewer {
                zones(filter: {zoneTag: ${'$'}zoneTag}) {
                  httpRequests1dGroups(
                    limit: 24,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      requests
                      bytes
                      cachedRequests
                      cachedBytes
                      threats
                      pageViews
                      encryptedRequests
                    }
                    uniq {
                      uniques
                    }
                    dimensions {
                      date
                    }
                  }
                  httpRequestsCacheGroups: httpRequests1dGroups(
                    limit: 10,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      requests
                      cachedRequests
                    }
                  }
                }
                accounts(filter: {accountTag: ${'$'}accountTag}) {
                  workersInvocationsAdaptive(
                    limit: 100,
                    filter: {datetime_geq: ${'$'}sinceTime, datetime_leq: ${'$'}untilTime}
                  ) {
                    sum {
                      requests
                      errors
                      subrequests
                    }
                    dimensions {
                      scriptName
                      datetime
                    }
                  }
                  d1AnalyticsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      rowsRead
                      rowsWritten
                    }
                  }
                  r2OperationsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      requests
                    }
                    dimensions {
                      actionType
                    }
                  }
                  r2StorageAdaptiveGroups(
                    limit: 1,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    max {
                      payloadSize
                    }
                  }
                }
              }
            }
            """.trimIndent()
        } else {
            // 仅 Workers 查询
            """
            query WorkersDashboard(${'$'}accountTag: string, ${'$'}sinceDate: string!, ${'$'}untilDate: string!, ${'$'}sinceTime: Time!, ${'$'}untilTime: Time!) {
              viewer {
                accounts(filter: {accountTag: ${'$'}accountTag}) {
                  workersInvocationsAdaptive(
                    limit: 100,
                    filter: {datetime_geq: ${'$'}sinceTime, datetime_leq: ${'$'}untilTime}
                  ) {
                    sum {
                      requests
                      errors
                      subrequests
                    }
                    dimensions {
                      scriptName
                      datetime
                    }
                  }
                  d1AnalyticsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      rowsRead
                      rowsWritten
                    }
                  }
                  r2OperationsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum {
                      requests
                    }
                    dimensions {
                      actionType
                    }
                  }
                  r2StorageAdaptiveGroups(
                    limit: 1,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    max {
                      payloadSize
                    }
                  }
                }
              }
            }
            """.trimIndent()
        }
    }
    
    /**
     * 构建查询变量
     */
    private fun buildQueryVariables(
        zoneId: String?,
        accountId: String,
        startDate: String,
        endDate: String,
        startDateTime: String,
        endDateTime: String
    ): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            if (zoneId != null) {
                put("zoneTag", zoneId)
                put("sinceDate", startDate)
                put("untilDate", endDate)
            }
            put("accountTag", accountId)
            put("sinceTime", startDateTime)
            put("untilTime", endDateTime)
        }
    }
    
    /**
     * D1 数据库统计信息（来自 REST API）
     */
    private data class D1Stats(
        val databaseCount: Int = 0,
        val totalStorageBytes: Long = 0
    )
    
    /**
     * R2 存储桶统计信息（来自 REST API）
     */
    private data class R2Stats(
        val bucketCount: Int = 0
    )
    
    /**
     * 获取 D1 数据库统计信息（通过 REST API）
     */
    private suspend fun fetchD1DatabaseStats(account: Account): D1Stats {
        return try {
            val response = api.listD1Databases(
                token = "Bearer ${account.token}",
                accountId = account.accountId
            )
            
            if (response.isSuccessful) {
                val databases = response.body()?.result ?: emptyList()
                val count = databases.size
                val totalBytes = databases.sumOf { it.fileSize ?: 0L }
                
                Timber.d("D1 Stats: count=$count, totalBytes=$totalBytes")
                D1Stats(databaseCount = count, totalStorageBytes = totalBytes)
            } else {
                Timber.w("Failed to fetch D1 databases: ${response.message()}")
                D1Stats()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching D1 database stats")
            D1Stats()
        }
    }
    
    /**
     * 获取 R2 存储桶统计信息（通过 REST API）
     */
    private suspend fun fetchR2BucketStats(account: Account): R2Stats {
        return try {
            val response = api.listR2Buckets(
                token = "Bearer ${account.token}",
                accountId = account.accountId
            )
            
            if (response.isSuccessful) {
                val buckets = response.body()?.result?.buckets ?: emptyList()
                val count = buckets.size
                
                Timber.d("R2 Stats: bucketCount=$count")
                R2Stats(bucketCount = count)
            } else {
                Timber.w("Failed to fetch R2 buckets: ${response.message()}")
                R2Stats()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching R2 bucket stats")
            R2Stats()
        }
    }
    
    /**
     * 解析 Analytics 数据
     */
    private fun parseAnalyticsData(data: AnalyticsData?, d1Stats: D1Stats = D1Stats(), r2Stats: R2Stats = R2Stats()): DashboardMetrics {
        if (data == null) {
            return DashboardMetrics()
        }
        
        var totalRequests = 0L
        var totalCachedRequests = 0L
        var bandwidthBytes = 0L
        var workersInvocations = 0L
        var workersSubrequests = 0L
        var workersErrors = 0L
        var threatsBlocked = 0L
        var pageViews = 0L
        var uniqueVisitors = 0L
        var dataSaved = 0L
        var encryptedRequests = 0L
        val requestsTimeSeries = mutableListOf<TimeSeriesPoint>()
        val bandwidthTimeSeries = mutableListOf<TimeSeriesPoint>()
        val threatsTimeSeries = mutableListOf<TimeSeriesPoint>()
        val cachedBytesTimeSeries = mutableListOf<TimeSeriesPoint>()
        val pageViewsTimeSeries = mutableListOf<TimeSeriesPoint>()
        
        // 解析 Zone 数据
        data.viewer?.zones?.firstOrNull()?.let { zone ->
            // HTTP 请求统计
            zone.httpRequests?.forEach { group ->
                totalRequests += group.sum.requests
                totalCachedRequests += group.sum.cachedRequests ?: 0
                bandwidthBytes += group.sum.bytes
                threatsBlocked += group.sum.threats ?: 0
                pageViews += group.sum.pageViews ?: 0
                uniqueVisitors += group.uniq?.uniques ?: 0
                dataSaved += group.sum.cachedBytes ?: 0
                encryptedRequests += group.sum.encryptedRequests ?: 0
                
                // 时间序列数据 (使用 date 字段)
                group.dimensions?.date?.let { date ->
                    try {
                        val timestamp = parseDate(date)
                        // 请求数时间序列
                        requestsTimeSeries.add(
                            TimeSeriesPoint(
                                timestamp = timestamp,
                                value = group.sum.requests.toDouble()
                            )
                        )
                        // 带宽时间序列 (转换为 MB)
                        bandwidthTimeSeries.add(
                            TimeSeriesPoint(
                                timestamp = timestamp,
                                value = group.sum.bytes.toDouble() / (1024 * 1024)
                            )
                        )
                        // 威胁拦截时间序列
                        threatsTimeSeries.add(
                            TimeSeriesPoint(
                                timestamp = timestamp,
                                value = (group.sum.threats ?: 0).toDouble()
                            )
                        )
                        // 缓存字节数时间序列 (转换为 MB)
                        cachedBytesTimeSeries.add(
                            TimeSeriesPoint(
                                timestamp = timestamp,
                                value = (group.sum.cachedBytes ?: 0).toDouble() / (1024 * 1024)
                            )
                        )
                        // 页面浏览量时间序列
                        pageViewsTimeSeries.add(
                            TimeSeriesPoint(
                                timestamp = timestamp,
                                value = (group.sum.pageViews ?: 0).toDouble()
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse date: $date")
                    }
                }
            }
        }
        
        // 解析 D1 和 R2 数据
        var d1ReadRows = 0L
        var d1WriteRows = 0L
        var r2ClassAOperations = 0L // A类操作（写操作：PutObject, DeleteObject 等）
        var r2ClassBOperations = 0L // B类操作（读操作：GetObject, ListObjects 等）
        var r2StorageBytes = 0L // R2 总存储字节数
        
        data.viewer?.accounts?.firstOrNull()?.let { account ->
            // Workers 数据
            account.workersInvocations?.forEach { group ->
                workersInvocations += group.sum.requests
                workersErrors += group.sum.errors
                workersSubrequests += group.sum.subrequests ?: 0
            }
            
            // D1 数据库数据 - 核心指标是行数，不是查询次数
            account.d1Analytics?.forEach { group ->
                d1ReadRows += group.sum.rowsRead ?: 0
                d1WriteRows += group.sum.rowsWritten ?: 0
            }
            
            // R2 操作数据 - 区分 A类/B类操作 (根据 Cloudflare R2 定价文档)
            // A 类操作（写入/变更/列表）: ListBuckets, ListObjects, PutObject, DeleteObject, CopyObject 等
            // B 类操作（读取）: GetObject, HeadObject, HeadBucket, GetBucket* 等
            account.r2Operations?.forEach { group ->
                val actionType = group.dimensions?.actionType ?: ""
                val count = group.sum.requests ?: 0
                
                // B类操作（读操作）的关键字
                val isClassB = actionType.equals("GetObject", ignoreCase = true) ||
                    actionType.equals("HeadObject", ignoreCase = true) ||
                    actionType.equals("HeadBucket", ignoreCase = true) ||
                    actionType.startsWith("Get", ignoreCase = true) ||
                    actionType.equals("UsageSummary", ignoreCase = true)
                
                if (isClassB) {
                    r2ClassBOperations += count
                } else {
                    // 其他所有操作都是 A 类（写/变更/列表）
                    r2ClassAOperations += count
                }
            }
            
            // R2 存储数据
            account.r2Storage?.firstOrNull()?.max?.payloadSize?.let { storageBytes ->
                r2StorageBytes = storageBytes
            }
        }
        
        // 计算指标
        val cacheHitRate = if (totalRequests > 0) {
            (totalCachedRequests.toDouble() / totalRequests.toDouble()) * 100
        } else {
            0.0
        }
        
        val workersErrorRate = if (workersInvocations > 0) {
            (workersErrors.toDouble() / workersInvocations.toDouble()) * 100
        } else {
            0.0
        }
        
        // 计算 HTTPS 加密请求占比
        val encryptedRequestRate = if (totalRequests > 0) {
            (encryptedRequests.toDouble() / totalRequests.toDouble()) * 100
        } else {
            0.0
        }
        
        // === 衡生指标计算（基于现有数据，无需额外 GraphQL 字段）===
        
        // 1. 源站承担流量 = 总流量 - 缓存流量
        val originBandwidth = bandwidthBytes - dataSaved
        
        // 2. 人均页面浏览量 = PV / UV (防止除以零)
        val pagesPerVisit = if (uniqueVisitors > 0) {
            pageViews.toDouble() / uniqueVisitors.toDouble()
        } else {
            0.0
        }
        
        // 3. 平均请求体积 = 总流量 / 总请求数 (转换为 KB)
        val avgRequestSize = if (totalRequests > 0) {
            (bandwidthBytes.toDouble() / totalRequests.toDouble()) / 1024.0
        } else {
            0.0
        }
        
        // 4. 未加密请求数 = 总请求数 - 加密请求数
        val unencryptedRequests = totalRequests - encryptedRequests
        
        // 确定健康状态
        val status = when {
            workersErrorRate > 10.0 -> HealthStatus.CRITICAL
            workersErrorRate > 5.0 -> HealthStatus.WARNING
            else -> HealthStatus.HEALTHY
        }
        
        return DashboardMetrics(
            totalRequests = totalRequests,
            cacheHitRate = cacheHitRate,
            bandwidthBytes = bandwidthBytes,
            workersInvocations = workersInvocations,
            workersSubrequests = workersSubrequests,
            workersErrorRate = workersErrorRate,
            threatsBlocked = threatsBlocked,
            pageViews = pageViews,
            uniqueVisitors = uniqueVisitors,
            dataSaved = dataSaved,
            encryptedRequestRate = encryptedRequestRate,
            originBandwidth = originBandwidth,
            pagesPerVisit = pagesPerVisit,
            avgRequestSize = avgRequestSize,
            unencryptedRequests = unencryptedRequests,
            d1ReadRows = d1ReadRows,
            d1WriteRows = d1WriteRows,
            d1StorageBytes = d1Stats.totalStorageBytes, // 来自 REST API
            d1DatabaseCount = d1Stats.databaseCount, // 来自 REST API
            r2ClassAOperations = r2ClassAOperations, // A类操作（写）- GraphQL
            r2ClassBOperations = r2ClassBOperations, // B类操作（读）- GraphQL
            r2StorageBytes = r2StorageBytes, // R2 总存储 - GraphQL
            r2BucketCount = r2Stats.bucketCount, // 来自 REST API
            requestsTimeSeries = requestsTimeSeries.sortedBy { it.timestamp },
            bandwidthTimeSeries = bandwidthTimeSeries.sortedBy { it.timestamp },
            threatsTimeSeries = threatsTimeSeries.sortedBy { it.timestamp },
            cachedBytesTimeSeries = cachedBytesTimeSeries.sortedBy { it.timestamp },
            pageViewsTimeSeries = pageViewsTimeSeries.sortedBy { it.timestamp },
            status = status
        )
    }
    
    /**
     * 解析日期字符串为 Unix 时间戳
     */
    private fun parseDate(date: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse date: $date")
            0L
        }
    }
    
    /**
     * 解析 ISO 8601 日期时间字符串为 Unix 时间戳
     */
    private fun parseISODateTime(datetime: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(datetime)?.time ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse datetime: $datetime")
            0L
        }
    }
}
