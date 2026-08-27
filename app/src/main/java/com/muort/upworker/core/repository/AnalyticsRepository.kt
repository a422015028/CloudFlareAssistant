package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
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
        zoneId: String,
        timeRange: TimeRange = TimeRange.ONE_DAY
    ): Resource<DashboardMetrics> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                Timber.d("Fetching dashboard metrics for zone: $zoneId, timeRange: ${timeRange.displayName}")
                
                // 使用时间范围枚举获取开始和结束时间
                val startDateTime = timeRange.getStartDateTime()
                val endDateTime = timeRange.getEndDateTime()
                
                val startDate = startDateTime.substring(0, 10) // 提取日期部分
                val endDate = endDateTime.substring(0, 10)
                
                val query = buildAnalyticsQuery()
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
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                    
                    val metrics = parseAnalyticsData(analyticsResponse?.data)
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
    private fun buildAnalyticsQuery(): String {
        return """
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
                  kvOperationsAdaptiveGroups(
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
                  kvStorageAdaptiveGroups(
                    limit: 1,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    max {
                      byteCount
                    }
                  }
                }
              }
            }
            """.trimIndent()
    }
    
    /**
     * 构建查询变量
     */
    private fun buildQueryVariables(
        zoneId: String,
        accountId: String,
        startDate: String,
        endDate: String,
        startDateTime: String,
        endDateTime: String
    ): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put("zoneTag", zoneId)
            put("sinceDate", startDate)
            put("untilDate", endDate)
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
     * KV 命名空间统计信息（来自 REST API）
     */
    private data class KvStats(
        val namespaceCount: Int = 0
    )

    /**
     * D1/R2/KV 用量数据（来自 GraphQL）
     */
    private data class StorageUsageData(
        val d1ReadRows: Long = 0,
        val d1WriteRows: Long = 0,
        val r2ClassAOperations: Long = 0,
        val r2ClassBOperations: Long = 0,
        val r2StorageBytes: Long = 0,
        val kvReads: Long = 0,
        val kvWrites: Long = 0,
        val kvStorageBytes: Long = 0,
    )

    /**
     * 通过 GraphQL 查询账号级 D1/R2/KV 用量数据
     */
    private suspend fun fetchStorageUsageFromGraphQL(
        account: Account,
        timeRange: TimeRange
    ): StorageUsageData {
        return try {
            val startDate = timeRange.getStartDateTime().substring(0, 10)
            val endDate = timeRange.getEndDateTime().substring(0, 10)
            val startTime = timeRange.getStartDateTime()
            val endTime = timeRange.getEndDateTime()

            val query = """
            query StorageUsage(${'$'}accountTag: string, ${'$'}sinceDate: string!, ${'$'}untilDate: string!, ${'$'}sinceTime: Time!, ${'$'}untilTime: Time!) {
              viewer {
                accounts(filter: {accountTag: ${'$'}accountTag}) {
                  d1AnalyticsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum { rowsRead rowsWritten }
                  }
                  r2OperationsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum { requests }
                    dimensions { actionType }
                  }
                  r2StorageAdaptiveGroups(
                    limit: 1,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    max { payloadSize }
                  }
                  kvOperationsAdaptiveGroups(
                    limit: 100,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    sum { requests }
                    dimensions { actionType }
                  }
                  kvStorageAdaptiveGroups(
                    limit: 1,
                    filter: {date_geq: ${'$'}sinceDate, date_leq: ${'$'}untilDate}
                  ) {
                    max { byteCount }
                  }
                }
              }
            }
            """.trimIndent()

            val variables = mapOf(
                "accountTag" to account.accountId,
                "sinceDate" to startDate,
                "untilDate" to endDate,
                "sinceTime" to startTime,
                "untilTime" to endTime
            )

            val response = api.queryAnalytics(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                request = AnalyticsGraphQLRequest(query = query, variables = variables)
            )

            if (!response.isSuccessful) return StorageUsageData()
            val data = response.body()?.data ?: return StorageUsageData()

            var d1ReadRows = 0L
            var d1WriteRows = 0L
            var r2ClassA = 0L
            var r2ClassB = 0L
            var r2Storage = 0L
            var kvReads = 0L
            var kvWrites = 0L
            var kvStorage = 0L

            data.viewer?.accounts?.firstOrNull()?.let { acc ->
                // D1
                acc.d1Analytics?.forEach { g ->
                    d1ReadRows += g.sum.rowsRead ?: 0
                    d1WriteRows += g.sum.rowsWritten ?: 0
                }
                // R2 操作
                acc.r2Operations?.forEach { g ->
                    val action = g.dimensions?.actionType ?: ""
                    val count = g.sum.requests ?: 0
                    val isClassB = action.equals("GetObject", ignoreCase = true) ||
                        action.equals("HeadObject", ignoreCase = true) ||
                        action.equals("HeadBucket", ignoreCase = true) ||
                        action.startsWith("Get", ignoreCase = true) ||
                        action.equals("UsageSummary", ignoreCase = true)
                    if (isClassB) r2ClassB += count else r2ClassA += count
                }
                // R2 存储
                acc.r2Storage?.firstOrNull()?.max?.payloadSize?.let { r2Storage = it }
                // KV 操作
                acc.kvOperations?.forEach { g ->
                    val action = g.dimensions?.actionType ?: ""
                    val count = g.sum.requests ?: 0
                    if (action.equals("read", ignoreCase = true)) kvReads += count else kvWrites += count
                }
                // KV 存储
                acc.kvStorage?.firstOrNull()?.max?.byteCount?.let { kvStorage = it }
            }

            StorageUsageData(
                d1ReadRows = d1ReadRows,
                d1WriteRows = d1WriteRows,
                r2ClassAOperations = r2ClassA,
                r2ClassBOperations = r2ClassB,
                r2StorageBytes = r2Storage,
                kvReads = kvReads,
                kvWrites = kvWrites,
                kvStorageBytes = kvStorage,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch storage usage from GraphQL")
            StorageUsageData()
        }
    }
    
    /**
     * 获取 D1 数据库统计信息（通过 REST API）
     */
    private suspend fun fetchD1DatabaseStats(account: Account): D1Stats {
        return try {
            val response = api.listD1Databases(
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
                token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
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
     * 获取 KV 命名空间统计信息（通过 REST API）
     */
    private suspend fun fetchKvNamespaceStats(account: Account): KvStats {
        return try {
            val response = api.listKvNamespaces(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId
            )

            if (response.isSuccessful) {
                val namespaces = response.body()?.result ?: emptyList()
                val count = namespaces.size

                Timber.d("KV Stats: namespaceCount=$count")
                KvStats(namespaceCount = count)
            } else {
                Timber.w("Failed to fetch KV namespaces: ${response.message()}")
                KvStats()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching KV namespace stats")
            KvStats()
        }
    }

    /**
     * 解析 Analytics 数据
     */
    private fun parseAnalyticsData(data: AnalyticsData?): DashboardMetrics {
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
        
        // 解析 Workers 数据
        data.viewer?.accounts?.firstOrNull()?.let { account ->
            account.workersInvocations?.forEach { group ->
                workersInvocations += group.sum.requests
                workersErrors += group.sum.errors
                workersSubrequests += group.sum.subrequests ?: 0
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
     * 获取账户分析概览数据（聚合账户下所有 Zone，对应官网 /analytics 页面）
     * 24小时使用小时级分组（httpRequests1hGroups），7天/30天使用天级分组（httpRequests1dGroups）
     */
    suspend fun getAccountAnalyticsOverview(
        account: Account,
        timeRange: TimeRange = TimeRange.ONE_DAY
    ): Resource<AccountAnalyticsOverview> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                Timber.d("Fetching account analytics overview for account: ${account.accountId}, timeRange: ${timeRange.displayName}")

                // 获取账户下所有 Zone
                val zonesResponse = api.listZones(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account)
                )
                if (!zonesResponse.isSuccessful) {
                    return@safeApiCall Resource.Error("获取域名列表失败: ${zonesResponse.message()}")
                }
                val zoneIds = zonesResponse.body()?.result?.map { it.id } ?: emptyList()
                if (zoneIds.isEmpty()) {
                    Timber.d("No zones in account, returning empty overview")
                    return@safeApiCall Resource.Success(AccountAnalyticsOverview())
                }

                val hourly = timeRange == TimeRange.ONE_DAY
                val query = buildAccountAnalyticsQuery(zoneIds, hourly)
                val variables = mutableMapOf<String, Any>()
                if (hourly) {
                    variables["since"] = timeRange.getStartDateTime()
                    variables["until"] = timeRange.getEndDateTime()
                    variables["prevSince"] = timeRange.getPrevStartDateTime()
                    variables["prevUntil"] = timeRange.getPrevEndDateTime()
                } else {
                    variables["sinceDate"] = timeRange.getStartDateTime().substring(0, 10)
                    variables["untilDate"] = timeRange.getEndDateTime().substring(0, 10)
                    variables["prevSinceDate"] = timeRange.getPrevStartDateTime().substring(0, 10)
                    variables["prevUntilDate"] = timeRange.getPrevEndDateTime().substring(0, 10)
                }

                val response = api.queryAccountAnalytics(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    request = AnalyticsGraphQLRequest(query = query, variables = variables)
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.errors?.isNotEmpty() == true) {
                        val errorMsg = body.errors.joinToString(", ") { it.message }
                        Timber.e("GraphQL errors: $errorMsg")
                        return@safeApiCall Resource.Error("分析查询失败: $errorMsg")
                    }
                    val overview = parseAccountAnalytics(body?.data, hourly)
                    Timber.d("Parsed account analytics overview: requests=${overview.requests}")

                    // 并行加载 D1/R2/KV 存储监控数据
                    val d1Stats = runCatching { fetchD1DatabaseStats(account) }.getOrNull()
                    val r2Stats = runCatching { fetchR2BucketStats(account) }.getOrNull()
                    val kvStats = runCatching { fetchKvNamespaceStats(account) }.getOrNull()

                    // 通过 GraphQL 查询 D1/R2/KV 的用量数据（复用已有的解析逻辑）
                    val usageData = fetchStorageUsageFromGraphQL(account, timeRange)

                    val overviewWithStorage = overview.copy(
                        d1ReadRows = usageData.d1ReadRows,
                        d1WriteRows = usageData.d1WriteRows,
                        d1StorageBytes = d1Stats?.totalStorageBytes ?: 0L,
                        d1DatabaseCount = d1Stats?.databaseCount ?: 0,
                        r2ClassAOperations = usageData.r2ClassAOperations,
                        r2ClassBOperations = usageData.r2ClassBOperations,
                        r2StorageBytes = usageData.r2StorageBytes,
                        r2BucketCount = r2Stats?.bucketCount ?: 0,
                        kvReads = usageData.kvReads,
                        kvWrites = usageData.kvWrites,
                        kvStorageBytes = usageData.kvStorageBytes,
                        kvNamespaceCount = kvStats?.namespaceCount ?: 0,
                    )

                    Resource.Success(overviewWithStorage)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("Failed to fetch account analytics: ${response.code()}, $errorBody")
                    Resource.Error("获取分析数据失败: ${response.message()}")
                }
            }
        }

    /**
     * 构建账户分析 GraphQL 查询（每个 Zone 两个别名查询：z{n} 当前期、p{n} 上一期）
     */
    private fun buildAccountAnalyticsQuery(zoneIds: List<String>, hourly: Boolean): String {
        val dataset = if (hourly) "httpRequests1hGroups" else "httpRequests1dGroups"
        val currentFilter = if (hourly) {
            "datetime_geq: \$since, datetime_lt: \$until"
        } else {
            "date_geq: \$sinceDate, date_leq: \$untilDate"
        }
        val prevFilter = if (hourly) {
            "datetime_geq: \$prevSince, datetime_lt: \$prevUntil"
        } else {
            "date_geq: \$prevSinceDate, date_leq: \$prevUntilDate"
        }
        val dimension = if (hourly) "datetime" else "date"

        fun zoneBlock(alias: String, zoneId: String, timeFilter: String, withNetworkMaps: Boolean) = """
        $alias: zones(filter: {zoneTag: "$zoneId"}) {
          groups: $dataset(
            limit: ${if (hourly) 25 else 32},
            filter: {$timeFilter}
          ) {
            sum {
              requests
              bytes
              cachedRequests
              cachedBytes
              encryptedRequests
              encryptedBytes
              pageViews
              threats
              responseStatusMap {
                edgeResponseStatus
                requests
              }
              ${if (withNetworkMaps) """
              clientHTTPVersionMap {
                clientHTTPProtocol
                requests
              }
              clientSSLMap {
                clientSSLProtocol
                requests
              }
              contentTypeMap {
                edgeResponseContentTypeName
                requests
              }
              countryMap {
                clientCountryName
                requests
                bytes
              }
              """ else ""}
            }
            uniq {
              uniques
            }
            dimensions {
              $dimension
            }
          }
        }
        """.trimIndent()

        val zoneFields = zoneIds.mapIndexed { index, zoneId ->
            zoneBlock("z$index", zoneId, currentFilter, true) + "\n" +
                zoneBlock("p$index", zoneId, prevFilter, false)
        }.joinToString("\n")

        val variableDefs = if (hourly) {
            "(\$since: Time!, \$until: Time!, \$prevSince: Time!, \$prevUntil: Time!)"
        } else {
            "(\$sinceDate: string!, \$untilDate: string!, \$prevSinceDate: string!, \$prevUntilDate: string!)"
        }

        return """
        query AccountAnalyticsOverview$variableDefs {
          viewer {
            $zoneFields
          }
        }
        """.trimIndent()
    }

    /**
     * 解析账户分析数据（聚合所有 Zone，当前期 z{n} 与上一期 p{n} 对比计算环比）
     */
    private fun parseAccountAnalytics(data: AccountAnalyticsData?, hourly: Boolean): AccountAnalyticsOverview {
        val empty = AccountAnalyticsOverview()
        if (data?.viewer == null) return empty

        val current = aggregatePeriod(data, "z", hourly)
        val prev = aggregatePeriod(data, "p", hourly)

        val requestsSeries = mutableListOf<TimeSeriesPoint>()
        val bandwidthSeries = mutableListOf<TimeSeriesPoint>()
        val visitorsSeries = mutableListOf<TimeSeriesPoint>()
        val pageViewsSeries = mutableListOf<TimeSeriesPoint>()
        val encryptedRequestsSeries = mutableListOf<TimeSeriesPoint>()
        val encryptedRequestRateSeries = mutableListOf<TimeSeriesPoint>()
        val encryptedBytesSeries = mutableListOf<TimeSeriesPoint>()
        val encryptedBytesRateSeries = mutableListOf<TimeSeriesPoint>()
        val cachedRequestsSeries = mutableListOf<TimeSeriesPoint>()
        val cachedRequestRateSeries = mutableListOf<TimeSeriesPoint>()
        val cachedBytesSeries = mutableListOf<TimeSeriesPoint>()
        val cachedBytesRateSeries = mutableListOf<TimeSeriesPoint>()
        val error4xxSeries = mutableListOf<TimeSeriesPoint>()
        val error4xxRateSeries = mutableListOf<TimeSeriesPoint>()
        val error5xxSeries = mutableListOf<TimeSeriesPoint>()
        val error5xxRateSeries = mutableListOf<TimeSeriesPoint>()

        current.seriesMap.forEach { (timestamp, b) ->
            requestsSeries.add(TimeSeriesPoint(timestamp, b[0].toDouble()))
            bandwidthSeries.add(TimeSeriesPoint(timestamp, b[1].toDouble()))
            visitorsSeries.add(TimeSeriesPoint(timestamp, b[2].toDouble()))
            pageViewsSeries.add(TimeSeriesPoint(timestamp, b[3].toDouble()))
            encryptedRequestsSeries.add(TimeSeriesPoint(timestamp, b[4].toDouble()))
            encryptedBytesSeries.add(TimeSeriesPoint(timestamp, b[5].toDouble()))
            cachedRequestsSeries.add(TimeSeriesPoint(timestamp, b[6].toDouble()))
            cachedBytesSeries.add(TimeSeriesPoint(timestamp, b[7].toDouble()))
            error4xxSeries.add(TimeSeriesPoint(timestamp, b[8].toDouble()))
            error5xxSeries.add(TimeSeriesPoint(timestamp, b[9].toDouble()))

            fun pct(numerator: Long, denominator: Long): Double =
                if (denominator > 0) numerator.toDouble() / denominator.toDouble() * 100 else 0.0
            encryptedRequestRateSeries.add(TimeSeriesPoint(timestamp, pct(b[4], b[0])))
            encryptedBytesRateSeries.add(TimeSeriesPoint(timestamp, pct(b[5], b[1])))
            cachedRequestRateSeries.add(TimeSeriesPoint(timestamp, pct(b[6], b[0])))
            cachedBytesRateSeries.add(TimeSeriesPoint(timestamp, pct(b[7], b[1])))
            error4xxRateSeries.add(TimeSeriesPoint(timestamp, pct(b[8], b[0])))
            error5xxRateSeries.add(TimeSeriesPoint(timestamp, pct(b[9], b[0])))
        }

        fun rate(numerator: Long, denominator: Long): Double =
            if (denominator > 0) numerator.toDouble() / denominator.toDouble() * 100 else 0.0

        // 环比：上期无数据返回 null（UI 不显示），上期为 0 且本期有数据视为 +100%
        fun delta(cur: Long, prevV: Long): Double? = when {
            prevV == 0L && cur == 0L -> null
            prevV == 0L -> 100.0
            else -> (cur - prevV).toDouble() / prevV.toDouble() * 100.0
        }

        fun rateDelta(curRate: Double, prevRate: Double): Double? = when {
            prevRate == 0.0 && curRate == 0.0 -> null
            prevRate == 0.0 -> 100.0
            else -> (curRate - prevRate) / prevRate * 100.0
        }

        val curEncryptedReqRate = rate(current.encryptedRequests, current.requests)
        val curEncryptedBytesRate = rate(current.encryptedBytes, current.bytes)
        val curCachedReqRate = rate(current.cachedRequests, current.requests)
        val curCachedBytesRate = rate(current.cachedBytes, current.bytes)
        val curError4xxRate = rate(current.error4xx, current.requests)
        val curError5xxRate = rate(current.error5xx, current.requests)
        val prevEncryptedReqRate = rate(prev.encryptedRequests, prev.requests)
        val prevEncryptedBytesRate = rate(prev.encryptedBytes, prev.bytes)
        val prevCachedReqRate = rate(prev.cachedRequests, prev.requests)
        val prevCachedBytesRate = rate(prev.cachedBytes, prev.bytes)
        val prevError4xxRate = rate(prev.error4xx, prev.requests)
        val prevError5xxRate = rate(prev.error5xx, prev.requests)

        return AccountAnalyticsOverview(
            requests = current.requests,
            bandwidthBytes = current.bytes,
            uniqueVisitors = current.uniques,
            pageViews = current.pageViews,
            encryptedRequests = current.encryptedRequests,
            encryptedBytes = current.encryptedBytes,
            cachedRequests = current.cachedRequests,
            cachedBytes = current.cachedBytes,
            error4xxRequests = current.error4xx,
            error5xxRequests = current.error5xx,
            threats = current.threats,
            encryptedRequestRate = curEncryptedReqRate,
            encryptedBytesRate = curEncryptedBytesRate,
            cachedRequestRate = curCachedReqRate,
            cachedBytesRate = curCachedBytesRate,
            error4xxRate = curError4xxRate,
            error5xxRate = curError5xxRate,
            requestsTimeSeries = requestsSeries,
            bandwidthTimeSeries = bandwidthSeries,
            visitorsTimeSeries = visitorsSeries,
            pageViewsTimeSeries = pageViewsSeries,
            encryptedRequestsTimeSeries = encryptedRequestsSeries,
            encryptedRequestRateTimeSeries = encryptedRequestRateSeries,
            encryptedBytesTimeSeries = encryptedBytesSeries,
            encryptedBytesRateTimeSeries = encryptedBytesRateSeries,
            cachedRequestsTimeSeries = cachedRequestsSeries,
            cachedRequestRateTimeSeries = cachedRequestRateSeries,
            cachedBytesTimeSeries = cachedBytesSeries,
            cachedBytesRateTimeSeries = cachedBytesRateSeries,
            error4xxTimeSeries = error4xxSeries,
            error4xxRateTimeSeries = error4xxRateSeries,
            error5xxTimeSeries = error5xxSeries,
            error5xxRateTimeSeries = error5xxRateSeries,
            requestsDelta = delta(current.requests, prev.requests),
            bandwidthDelta = delta(current.bytes, prev.bytes),
            visitorsDelta = delta(current.uniques, prev.uniques),
            pageViewsDelta = delta(current.pageViews, prev.pageViews),
            encryptedRequestsDelta = delta(current.encryptedRequests, prev.encryptedRequests),
            encryptedRequestRateDelta = rateDelta(curEncryptedReqRate, prevEncryptedReqRate),
            encryptedBytesDelta = delta(current.encryptedBytes, prev.encryptedBytes),
            encryptedBytesRateDelta = rateDelta(curEncryptedBytesRate, prevEncryptedBytesRate),
            cachedRequestsDelta = delta(current.cachedRequests, prev.cachedRequests),
            cachedRequestRateDelta = rateDelta(curCachedReqRate, prevCachedReqRate),
            cachedBytesDelta = delta(current.cachedBytes, prev.cachedBytes),
            cachedBytesRateDelta = rateDelta(curCachedBytesRate, prevCachedBytesRate),
            error4xxDelta = delta(current.error4xx, prev.error4xx),
            error4xxRateDelta = rateDelta(curError4xxRate, prevError4xxRate),
            error5xxDelta = delta(current.error5xx, prev.error5xx),
            error5xxRateDelta = rateDelta(curError5xxRate, prevError5xxRate),
            httpVersionStats = current.httpVersionMap.toNetworkStats(),
            sslProtocolStats = current.sslMap.toNetworkStats(),
            contentTypeStats = current.contentTypeMap.toNetworkStats(),
            regionStats = current.countryAgg.map { (code, agg) ->
                RegionStatItem(
                    name = code,
                    requests = agg.requests,
                    bytes = agg.bytes,
                    requestsTimeSeries = agg.series.map { (ts, b) -> TimeSeriesPoint(ts, b[0].toDouble()) },
                    bytesTimeSeries = agg.series.map { (ts, b) -> TimeSeriesPoint(ts, b[1].toDouble()) }
                )
            }.filter { it.requests > 0 }.sortedByDescending { it.requests }
        )
    }

    /**
     * 将 map 转换为按请求数降序的网络统计列表
     */
    private fun Map<String, Long>.toNetworkStats(): List<NetworkStatItem> {
        return entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { NetworkStatItem(it.key, it.value) }
    }

    /**
     * 单期聚合结果
     */
    private data class PeriodTotals(
        val requests: Long,
        val bytes: Long,
        val uniques: Long,
        val pageViews: Long,
        val encryptedRequests: Long,
        val encryptedBytes: Long,
        val cachedRequests: Long,
        val cachedBytes: Long,
        val error4xx: Long,
        val error5xx: Long,
        val threats: Long,
        val seriesMap: SortedMap<Long, LongArray>,
        val httpVersionMap: Map<String, Long>,
        val sslMap: Map<String, Long>,
        val contentTypeMap: Map<String, Long>,
        val countryAgg: Map<String, CountryAgg>
    )

    /**
     * 单个国家在单期内的聚合（总量 + 逐时间桶序列，桶数组 0:requests 1:bytes）
     */
    private class CountryAgg {
        var requests = 0L
        var bytes = 0L
        val series = sortedMapOf<Long, LongArray>()
    }

    /**
     * 聚合单个时间段的多个 Zone 数据（按别名前缀过滤：z=当前期，p=上一期）
     * seriesMap 时间桶索引: 0:requests 1:bytes 2:uniques 3:pageViews 4:encryptedRequests
     * 5:encryptedBytes 6:cachedRequests 7:cachedBytes 8:error4xx 9:error5xx
     */
    private fun aggregatePeriod(data: AccountAnalyticsData, prefix: String, hourly: Boolean): PeriodTotals {
        var requests = 0L
        var bytes = 0L
        var uniques = 0L
        var pageViews = 0L
        var encryptedRequests = 0L
        var encryptedBytes = 0L
        var cachedRequests = 0L
        var cachedBytes = 0L
        var error4xx = 0L
        var error5xx = 0L
        var threats = 0L
        val seriesMap = sortedMapOf<Long, LongArray>()
        val httpVersionMap = mutableMapOf<String, Long>()
        val sslMap = mutableMapOf<String, Long>()
        val contentTypeMap = mutableMapOf<String, Long>()
        val countryAgg = mutableMapOf<String, CountryAgg>()

        data.viewer?.filterKeys { it.startsWith(prefix) }?.values?.forEach zonesLoop@{ zoneList ->
            zoneList.forEach zoneLoop@{ zoneNode ->
                zoneNode.groups?.forEach groupLoop@{ group ->
                    val sum = group.sum ?: return@groupLoop
                    val gRequests = sum.requests ?: 0
                    val gBytes = sum.bytes ?: 0
                    val gPageViews = sum.pageViews ?: 0
                    val gEncryptedRequests = sum.encryptedRequests ?: 0
                    val gEncryptedBytes = sum.encryptedBytes ?: 0
                    val gCachedRequests = sum.cachedRequests ?: 0
                    val gCachedBytes = sum.cachedBytes ?: 0
                    val gUniques = group.uniq?.uniques ?: 0

                    requests += gRequests
                    bytes += gBytes
                    pageViews += gPageViews
                    encryptedRequests += gEncryptedRequests
                    encryptedBytes += gEncryptedBytes
                    cachedRequests += gCachedRequests
                    cachedBytes += gCachedBytes
                    threats += sum.threats ?: 0
                    uniques += gUniques

                    var g4xx = 0L
                    var g5xx = 0L
                    sum.responseStatusMap?.forEach statusLoop@{ entry ->
                        val status = entry.edgeResponseStatus ?: return@statusLoop
                        val req = entry.requests ?: 0
                        when {
                            status in 400..499 -> { error4xx += req; g4xx += req }
                            status >= 500 -> { error5xx += req; g5xx += req }
                        }
                    }

                    sum.clientHTTPVersionMap?.forEach { entry ->
                        val name = entry.clientHTTPProtocol ?: return@forEach
                        httpVersionMap[name] = (httpVersionMap[name] ?: 0) + (entry.requests ?: 0)
                    }
                    sum.clientSSLMap?.forEach { entry ->
                        val name = entry.clientSSLProtocol ?: return@forEach
                        sslMap[name] = (sslMap[name] ?: 0) + (entry.requests ?: 0)
                    }
                    sum.contentTypeMap?.forEach { entry ->
                        val name = entry.edgeResponseContentTypeName ?: return@forEach
                        contentTypeMap[name] = (contentTypeMap[name] ?: 0) + (entry.requests ?: 0)
                    }

                    // 时间序列聚合
                    val timestamp = when {
                        hourly -> group.dimensions?.datetime?.let { parseISODateTime(it) }
                        else -> group.dimensions?.date?.let { parseDate(it) }
                    } ?: return@groupLoop
                    val bucket = seriesMap.getOrPut(timestamp) { LongArray(10) }
                    bucket[0] += gRequests
                    bucket[1] += gBytes
                    bucket[2] += gUniques
                    bucket[3] += gPageViews
                    bucket[4] += gEncryptedRequests
                    bucket[5] += gEncryptedBytes
                    bucket[6] += gCachedRequests
                    bucket[7] += gCachedBytes
                    bucket[8] += g4xx
                    bucket[9] += g5xx

                    sum.countryMap?.forEach { entry ->
                        val code = entry.clientCountryName ?: return@forEach
                        val cReq = entry.requests ?: 0
                        val cBytes = entry.bytes ?: 0
                        val agg = countryAgg.getOrPut(code) { CountryAgg() }
                        agg.requests += cReq
                        agg.bytes += cBytes
                        val cBucket = agg.series.getOrPut(timestamp) { longArrayOf(0L, 0L) }
                        cBucket[0] += cReq
                        cBucket[1] += cBytes
                    }
                }
            }
        }

        // 按整期时间轴为每个国家补零：低流量国家的流量若集中在单个时间桶，
        // 序列只有 1 个点无法画折线；补零后与官网一致（零线 + 单桶尖峰）
        if (seriesMap.isNotEmpty()) {
            countryAgg.values.forEach { agg ->
                seriesMap.keys.forEach { ts -> agg.series.getOrPut(ts) { longArrayOf(0L, 0L) } }
            }
        }

        return PeriodTotals(
            requests = requests,
            bytes = bytes,
            uniques = uniques,
            pageViews = pageViews,
            encryptedRequests = encryptedRequests,
            encryptedBytes = encryptedBytes,
            cachedRequests = cachedRequests,
            cachedBytes = cachedBytes,
            error4xx = error4xx,
            error5xx = error5xx,
            threats = threats,
            seriesMap = seriesMap,
            httpVersionMap = httpVersionMap,
            sslMap = sslMap,
            contentTypeMap = contentTypeMap,
            countryAgg = countryAgg
        )
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
