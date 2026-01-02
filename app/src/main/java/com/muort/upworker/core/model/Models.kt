package com.muort.upworker.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// ==================== Common Response ====================

data class CloudFlareResponse<T>(
    @SerializedName("result") val result: T?,
    @SerializedName("success") val success: Boolean,
    @SerializedName("errors") val errors: List<CloudFlareError>?,
    @SerializedName("messages") val messages: List<String>?
)

data class CloudFlareError(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String
)

// ==================== Account ====================

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val accountId: String,
    val token: String,
    val zoneId: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // R2 S3 API credentials (optional, separate from API token)
    val r2AccessKeyId: String? = null,
    val r2SecretAccessKey: String? = null
)

// ==================== Zones ====================

@Entity(
    tableName = "zones",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index(value = ["accountId"])]
)
data class Zone(
    @PrimaryKey
    val id: String, // Zone ID from Cloudflare
    val accountId: Long, // Foreign key to Account
    val name: String, // Zone name (domain)
    val status: String, // active, pending, etc.
    val type: String? = null, // full, partial
    val paused: Boolean = false,
    val isSelected: Boolean = false, // Whether this zone is currently selected for the account
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// API response model for zones
data class ZoneInfo(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String,
    @SerializedName("paused") val paused: Boolean = false,
    @SerializedName("type") val type: String? = null,
    @SerializedName("development_mode") val developmentMode: Int? = null,
    @SerializedName("name_servers") val nameServers: List<String>? = null,
    @SerializedName("original_name_servers") val originalNameServers: List<String>? = null,
    @SerializedName("original_registrar") val originalRegistrar: String? = null,
    @SerializedName("original_dnshost") val originalDnshost: String? = null,
    @SerializedName("created_on") val createdOn: String? = null,
    @SerializedName("modified_on") val modifiedOn: String? = null,
    @SerializedName("activated_on") val activatedOn: String? = null
)

// ==================== Workers ====================

data class WorkerScript(
    @SerializedName("id") val id: String,
    @SerializedName("created_on") val createdOn: String?,
    @SerializedName("modified_on") val modifiedOn: String?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("bindings") val bindings: List<WorkerBinding>? = null
)

/**
 * Metadata for Worker Script multipart upload
 * https://developers.cloudflare.com/workers/configuration/multipart-upload-metadata/
 */
data class WorkerMetadata(
    @SerializedName("main_module") val mainModule: String? = null,
    @SerializedName("body_part") val bodyPart: String? = null,
    @SerializedName("compatibility_date") val compatibilityDate: String? = null,
    @SerializedName("compatibility_flags") val compatibilityFlags: List<String>? = null,
    @SerializedName("usage_model") val usageModel: String? = null,
    @SerializedName("bindings") val bindings: List<WorkerBinding>? = null,
    @SerializedName("vars") val vars: Map<String, String>? = null,
    @SerializedName("logpush") val logpush: Boolean? = null,
    @SerializedName("tail_consumers") val tailConsumers: List<TailConsumer>? = null
)

/**
 * Worker bindings for KV, R2, D1, etc.
 */
data class WorkerBinding(
    @SerializedName("type") val type: String, // "kv_namespace", "r2_bucket", "d1", "plain_text", "secret_text", etc.
    @SerializedName("name") val name: String, // Variable name in worker
    @SerializedName("namespace_id") val namespaceId: String? = null, // For KV
    @SerializedName("bucket_name") val bucketName: String? = null, // For R2
    @SerializedName("id") val databaseId: String? = null, // For D1 - Cloudflare expects "id" not "database_id"
    @SerializedName("service") val service: String? = null, // For service bindings
    @SerializedName("environment") val environment: String? = null,
    @SerializedName("text") val text: String? = null, // For plain_text and secret_text bindings
    @SerializedName("json") val json: Any? = null // For json type bindings
) {
    // Helper to get the value regardless of whether it's in text or json field
    fun getValue(): String? {
        return when {
            text != null -> text
            json != null -> {
                // If json is a string, return it directly; otherwise convert to JSON string
                if (json is String) json else com.google.gson.Gson().toJson(json)
            }
            else -> null
        }
    }
}

data class TailConsumer(
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String? = "production"
)

data class Route(
    @SerializedName("id") val id: String,
    @SerializedName("pattern") val pattern: String,
    @SerializedName("script") val script: String?,
    @SerializedName("zone_id") val zoneId: String?
)

data class CustomDomain(
    @SerializedName("id") val id: String,
    @SerializedName("hostname") val hostname: String,
    @SerializedName("service") val service: String?,
    @SerializedName("environment") val environment: String?
)

data class RouteRequest(
    @SerializedName("pattern") val pattern: String,
    @SerializedName("script") val script: String?
)

/**
 * Request to update Worker Script settings (bindings, etc.)
 * Used for updating configuration without re-uploading script code
 */
data class WorkerSettingsRequest(
    @SerializedName("bindings") val bindings: List<WorkerBinding>? = null,
    @SerializedName("compatibility_date") val compatibilityDate: String? = null,
    @SerializedName("compatibility_flags") val compatibilityFlags: List<String>? = null,
    @SerializedName("usage_model") val usageModel: String? = null,
    @SerializedName("logpush") val logpush: Boolean? = null
)

data class CustomDomainRequest(
    @SerializedName("hostname") val hostname: String,
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String = "production"
)

// ==================== DNS ====================

data class DnsRecord(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("content") val content: String,
    @SerializedName("proxied") val proxied: Boolean = false,
    @SerializedName("ttl") val ttl: Int = 1,
    @SerializedName("priority") val priority: Int? = null,
    @SerializedName("created_on") val createdOn: String? = null,
    @SerializedName("modified_on") val modifiedOn: String? = null
)

data class DnsRecordRequest(
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String,
    @SerializedName("content") val content: String,
    @SerializedName("proxied") val proxied: Boolean = false,
    @SerializedName("ttl") val ttl: Int = 1,
    @SerializedName("priority") val priority: Int? = null
)

// ==================== KV ====================

data class KvNamespace(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("supports_url_encoding") val supportsUrlEncoding: Boolean? = null
)

data class KvNamespaceRequest(
    @SerializedName("title") val title: String
)

data class KvKey(
    @SerializedName("name") val name: String,
    @SerializedName("expiration") val expiration: Long? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null,
    var value: String? = null // 添加value字段用于显示
)

// ==================== Pages ====================

data class PagesProject(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("subdomain") val subdomain: String?,
    @SerializedName("domains") val domains: List<String>?,
    @SerializedName("created_on") val createdOn: String?,
    @SerializedName("production_branch") val productionBranch: String?
)

data class PagesProjectRequest(
    @SerializedName("name") val name: String,
    @SerializedName("production_branch") val productionBranch: String = "main"
)

data class PagesDeployment(
    @SerializedName("id") val id: String,
    @SerializedName("short_id") val shortId: String?,
    @SerializedName("project_name") val projectName: String?,
    @SerializedName("environment") val environment: String?,
    @SerializedName("url") val url: String?,
    @SerializedName("created_on") val createdOn: String?,
    @SerializedName("modified_on") val modifiedOn: String?,
    @SerializedName("latest_stage") val latestStage: DeploymentStage?,
    @SerializedName("deployment_trigger") val deploymentTrigger: DeploymentTrigger?,
    @SerializedName("stages") val stages: List<DeploymentStage>?
)

data class DeploymentStage(
    @SerializedName("name") val name: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("started_on") val startedOn: String?,
    @SerializedName("ended_on") val endedOn: String?
)

data class DeploymentTrigger(
    @SerializedName("type") val type: String?,
    @SerializedName("metadata") val metadata: DeploymentMetadata?
)

data class DeploymentMetadata(
    @SerializedName("branch") val branch: String?,
    @SerializedName("commit_hash") val commitHash: String?,
    @SerializedName("commit_message") val commitMessage: String?
)

data class PagesProjectDetail(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("subdomain") val subdomain: String?,
    @SerializedName("domains") val domains: List<String>?,
    @SerializedName("created_on") val createdOn: String?,
    @SerializedName("production_branch") val productionBranch: String?,
    @SerializedName("source") val source: ProjectSource?,
    @SerializedName("build_config") val buildConfig: BuildConfig?,
    @SerializedName("deployment_configs") val deploymentConfigs: DeploymentConfigs?,
    @SerializedName("latest_deployment") val latestDeployment: PagesDeployment?
)

data class ProjectSource(
    @SerializedName("type") val type: String?,
    @SerializedName("config") val config: SourceConfig?
)

data class SourceConfig(
    @SerializedName("owner") val owner: String?,
    @SerializedName("repo_name") val repoName: String?,
    @SerializedName("production_branch") val productionBranch: String?
)

data class BuildConfig(
    @SerializedName("build_command") val buildCommand: String?,
    @SerializedName("destination_dir") val destinationDir: String?,
    @SerializedName("root_dir") val rootDir: String?,
    @SerializedName("web_analytics_tag") val webAnalyticsTag: String?,
    @SerializedName("web_analytics_token") val webAnalyticsToken: String?
)

// ==================== Pages Bindings ====================

data class KvBinding(
    @SerializedName("namespace_id") val namespaceId: String
)

data class R2Binding(
    @SerializedName("name") val name: String
)

data class D1Binding(
    @SerializedName("id") val id: String
)

data class DurableObjectBinding(
    @SerializedName("class_name") val className: String
)

data class ServiceBinding(
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String = "production"
)

data class KvBindingUpdate(
    @SerializedName("namespace_id") val namespaceId: String
)

data class R2BindingUpdate(
    @SerializedName("name") val name: String
)

data class D1BindingUpdate(
    @SerializedName("id") val id: String
)

data class DurableObjectBindingUpdate(
    @SerializedName("class_name") val className: String
)

data class ServiceBindingUpdate(
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String = "production"
)

data class DeploymentConfigs(
    @SerializedName("preview") val preview: EnvironmentConfig?,
    @SerializedName("production") val production: EnvironmentConfig?
)

data class EnvironmentConfig(
    @SerializedName("env_vars") val envVars: Map<String, EnvVar>?,
    @SerializedName("kv_namespaces") val kvNamespaces: Map<String, KvBinding>? = null,
    @SerializedName("r2_buckets") val r2Buckets: Map<String, R2Binding>? = null,
    @SerializedName("d1_databases") val d1Databases: Map<String, D1Binding>? = null,
    @SerializedName("durable_objects") val durableObjects: Map<String, DurableObjectBinding>? = null,
    @SerializedName("services") val services: Map<String, ServiceBinding>? = null
)

data class EnvVar(
    @SerializedName("type") val type: String?,
    @SerializedName("value") val value: String?
)

/**
 * Request model for updating Pages project configuration
 * Used with PATCH /accounts/{account_id}/pages/projects/{project_name}
 */
data class PagesProjectUpdateRequest(
    @SerializedName("deployment_configs") val deploymentConfigs: DeploymentConfigsUpdate? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("production_branch") val productionBranch: String? = null
)

data class DeploymentConfigsUpdate(
    @SerializedName("preview") val preview: EnvironmentConfigUpdate? = null,
    @SerializedName("production") val production: EnvironmentConfigUpdate? = null
)

/**
 * Environment configuration update model for Pages
 * To delete an environment variable, set its value to null
 */
data class EnvironmentConfigUpdate(
    @SerializedName("env_vars") val envVars: Map<String, EnvVarUpdate?>? = null,
    @SerializedName("kv_namespaces") val kvNamespaces: Map<String, KvBindingUpdate?>? = null,
    @SerializedName("r2_buckets") val r2Buckets: Map<String, R2BindingUpdate?>? = null,
    @SerializedName("d1_databases") val d1Databases: Map<String, D1BindingUpdate?>? = null,
    @SerializedName("durable_objects") val durableObjects: Map<String, DurableObjectBindingUpdate?>? = null,
    @SerializedName("services") val services: Map<String, ServiceBindingUpdate?>? = null
)

data class EnvVarUpdate(
    @SerializedName("type") val type: String = "plain_text",  // "plain_text" or "secret_text"
    @SerializedName("value") val value: String
)

// ==================== Pages Domains ====================

data class PagesDomain(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String,
    @SerializedName("status") val status: String?,
    @SerializedName("validation_data") val validationData: DomainValidationData?,
    @SerializedName("verification_data") val verificationData: DomainVerificationData?,
    @SerializedName("zone_tag") val zoneTag: String?,
    @SerializedName("created_on") val createdOn: String?
)

data class DomainValidationData(
    @SerializedName("status") val status: String?,
    @SerializedName("method") val method: String?
)

data class DomainVerificationData(
    @SerializedName("type") val type: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("value") val value: String?
)

data class PagesDomainRequest(
    @SerializedName("name") val name: String
)

// ==================== R2 ====================

data class R2Bucket(
    @SerializedName("name") val name: String,
    @SerializedName("creation_date") val creationDate: String?,
    @SerializedName("location") val location: String?
)

data class R2BucketRequest(
    @SerializedName("name") val name: String,
    @SerializedName("location") val location: String? = null
)

data class R2BucketsResponse(
    @SerializedName("buckets") val buckets: List<R2Bucket>
)

data class R2Object(
    @SerializedName("key") val key: String,
    @SerializedName("size") val size: Long?,
    @SerializedName("etag") val etag: String?,
    @SerializedName("uploaded") val uploaded: String?,
    @SerializedName("httpMetadata") val httpMetadata: R2HttpMetadata?
)

data class R2HttpMetadata(
    @SerializedName("contentType") val contentType: String?,
    @SerializedName("cacheControl") val cacheControl: String?
)

data class R2ObjectList(
    @SerializedName("objects") val objects: List<R2Object>?,
    @SerializedName("truncated") val truncated: Boolean?,
    @SerializedName("cursor") val cursor: String?,
    @SerializedName("delimitedPrefixes") val delimitedPrefixes: List<String>?
)

data class R2ObjectUpload(
    @SerializedName("key") val key: String,
    @SerializedName("size") val size: Long?,
    @SerializedName("etag") val etag: String?
)

data class R2CustomDomain(
    @SerializedName("domain") val domain: String,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("status") val status: Any? = null,  // Can be string or object
    @SerializedName("min_tls_version") val minTlsVersion: String? = null,
    @SerializedName("ciphers") val ciphers: Any? = null  // Can be array or other type
) {
    // Helper to get status as string
    val statusText: String
        get() = when (status) {
            is String -> status
            is Map<*, *> -> {
                val ssl = status["ssl"]?.toString() ?: "unknown"
                val ownership = status["ownership"]?.toString() ?: "unknown"
                "SSL:$ssl, 所有权:$ownership"
            }
            else -> "未知"
        }
}

data class R2CustomDomainsResponse(
    @SerializedName("domains") val domains: List<R2CustomDomain>
)

data class R2CustomDomainDeleteResponse(
    @SerializedName("domain") val domain: String
)

data class R2CustomDomainRequest(
    @SerializedName("domain") val domain: String,
    @SerializedName("zoneId") val zoneId: String,
    @SerializedName("enabled") val enabled: Boolean = true
)

// ==================== D1 ====================

data class D1Database(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("version") val version: String?,
    @SerializedName("file_size") val fileSize: Long? = null // 数据库文件大小（字节）
)


data class D1DatabaseRequest(
    @SerializedName("name") val name: String
)

// D1 表结构
data class D1Table(
    @SerializedName("name") val name: String,
    @SerializedName("columns") val columns: List<D1Column>?
)

data class D1Column(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String?
)

// D1 SQL 查询请求
data class D1QueryRequest(
    @SerializedName("sql") val sql: String,
    @SerializedName("params") val params: List<Any>? = null
)

// D1 SQL 查询结果
data class D1QueryResult(
    @SerializedName("results") val results: List<Map<String, Any?>>? = null,
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("error") val error: String? = null,
    @SerializedName("meta") val meta: Any? = null
)

// ==================== UI State ====================

sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val exception: Throwable? = null) : UiState<Nothing>()
}

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

// ==================== Analytics ====================

/**
 * GraphQL Analytics 请求体
 * 用于查询 Cloudflare Analytics 数据
 */
data class AnalyticsGraphQLRequest(
    @SerializedName("query") val query: String,
    @SerializedName("variables") val variables: Map<String, Any>? = null
)

/**
 * GraphQL Analytics 响应
 */
data class AnalyticsGraphQLResponse(
    @SerializedName("data") val data: AnalyticsData?,
    @SerializedName("errors") val errors: List<GraphQLError>?
)

data class GraphQLError(
    @SerializedName("message") val message: String,
    @SerializedName("path") val path: List<String>? = null
)

/**
 * Analytics 数据容器
 */
data class AnalyticsData(
    @SerializedName("viewer") val viewer: AnalyticsViewer?
)

data class AnalyticsViewer(
    @SerializedName("zones") val zones: List<ZoneAnalytics>?,
    @SerializedName("accounts") val accounts: List<AccountAnalytics>?
)

/**
 * Zone 级别的 Analytics
 */
data class ZoneAnalytics(
    @SerializedName("httpRequests1dGroups") val httpRequests: List<HttpRequestsGroup>?,
    @SerializedName("httpRequestsCacheGroups") val cacheGroups: List<CacheGroup>?
)

/**
 * Account 级别的 Analytics (Workers)
 */
data class AccountAnalytics(
    @SerializedName("workersInvocationsAdaptive") val workersInvocations: List<WorkersInvocationGroup>?,
    @SerializedName("d1AnalyticsAdaptiveGroups") val d1Analytics: List<D1AnalyticsGroup>?,
    @SerializedName("d1StorageAdaptiveGroups") val d1Storage: List<D1StorageGroup>?,
    @SerializedName("r2OperationsAdaptiveGroups") val r2Operations: List<R2OperationsGroup>?,
    @SerializedName("r2StorageAdaptiveGroups") val r2Storage: List<R2StorageGroup>?
)

/**
 * HTTP 请求统计组
 */
data class HttpRequestsGroup(
    @SerializedName("sum") val sum: RequestSum,
    @SerializedName("uniq") val uniq: RequestUniq? = null,
    @SerializedName("dimensions") val dimensions: RequestDimensions?
)

data class RequestSum(
    @SerializedName("requests") val requests: Long,
    @SerializedName("bytes") val bytes: Long,
    @SerializedName("cachedRequests") val cachedRequests: Long? = null,
    @SerializedName("cachedBytes") val cachedBytes: Long? = null,
    @SerializedName("threats") val threats: Long? = null,
    @SerializedName("pageViews") val pageViews: Long? = null,
    @SerializedName("encryptedRequests") val encryptedRequests: Long? = null
)

data class RequestUniq(
    @SerializedName("uniques") val uniques: Long? = null
)

data class RequestCount(
    @SerializedName("uniques") val uniques: Long? = null
)

data class RequestDimensions(
    @SerializedName("date") val date: String?,
    @SerializedName("datetime") val datetime: String?
)

/**
 * 缓存统计组
 */
data class CacheGroup(
    @SerializedName("sum") val sum: CacheSum,
    @SerializedName("dimensions") val dimensions: CacheDimensions?
)

data class CacheSum(
    @SerializedName("requests") val requests: Long,
    @SerializedName("cachedRequests") val cachedRequests: Long
)

data class CacheDimensions(
    @SerializedName("cacheStatus") val cacheStatus: String?
)

/**
 * Workers 调用统计组
 */
data class WorkersInvocationGroup(
    @SerializedName("sum") val sum: WorkersSum,
    @SerializedName("dimensions") val dimensions: WorkersDimensions?
)

data class WorkersSum(
    @SerializedName("requests") val requests: Long,
    @SerializedName("errors") val errors: Long,
    @SerializedName("subrequests") val subrequests: Long? = null,
    @SerializedName("cpuTime") val cpuTime: Long? = null, // 单位: 微秒
    @SerializedName("duration") val duration: Long? = null, // 平均执行时间 (微秒)
    @SerializedName("wallTime") val wallTime: Long? = null // 墙钟时间 (微秒)
)

data class WorkersDimensions(
    @SerializedName("scriptName") val scriptName: String?,
    @SerializedName("datetime") val datetime: String?
)

/**
 * D1 数据库统计组
 */
data class D1AnalyticsGroup(
    @SerializedName("sum") val sum: D1Sum,
    @SerializedName("dimensions") val dimensions: D1Dimensions?
)

data class D1Sum(
    @SerializedName("rowsRead") val rowsRead: Long? = null,
    @SerializedName("rowsWritten") val rowsWritten: Long? = null
)

data class D1Dimensions(
    @SerializedName("date") val date: String?,
    @SerializedName("databaseId") val databaseId: String?
)

/**
 * D1 存储统计组
 */
data class D1StorageGroup(
    @SerializedName("max") val max: D1StorageMax
)

data class D1StorageMax(
    @SerializedName("storageInBytes") val storageInBytes: Long? = null, // 总存储
    @SerializedName("billedStorageInByteMonths") val billedStorageInByteMonths: Long? = null // 计量存储
)

/**
 * R2 操作统计组 - A类/B类操作
 */
data class R2OperationsGroup(
    @SerializedName("sum") val sum: R2OperationsSum,
    @SerializedName("dimensions") val dimensions: R2OperationsDimensions? = null
)

data class R2OperationsSum(
    @SerializedName("requests") val requests: Long? = null
)

data class R2OperationsDimensions(
    @SerializedName("actionType") val actionType: String? = null // ListBuckets, GetObject, PutObject 等
)

/**
 * R2 存储统计组 - 存储数据
 */
data class R2StorageGroup(
    @SerializedName("max") val max: R2StorageMax? = null
)

data class R2StorageMax(
    @SerializedName("payloadSize") val payloadSize: Long? = null // 存储字节数
)

/**
 * 仪表盘数据汇总
 * 用于 UI 展示
 */
data class DashboardMetrics(
    val totalRequests: Long = 0,
    val cacheHitRate: Double = 0.0, // 0-100 百分比
    val bandwidthBytes: Long = 0,
    val workersInvocations: Long = 0,
    val workersSubrequests: Long = 0, // Workers 子请求数
    val workersErrorRate: Double = 0.0, // 0-100 百分比
    val threatsBlocked: Long = 0, // 威胁拦截数
    val pageViews: Long = 0, // 页面浏览量
    val uniqueVisitors: Long = 0, // 独立访客数
    val dataSaved: Long = 0, // 已节省流量（缓存字节数）
    val encryptedRequestRate: Double = 0.0, // HTTPS 加密请求占比 (0-100 百分比)
    // === 以下为衡生指标（基于现有数据计算）===
    val originBandwidth: Long = 0, // 源站承担流量 = bytes - cachedBytes
    val pagesPerVisit: Double = 0.0, // 人均页面浏览量 = pageViews / uniques
    val avgRequestSize: Double = 0.0, // 平均请求体积 (KB) = bytes / requests / 1024
    val unencryptedRequests: Long = 0, // 未加密请求数 = requests - encryptedRequests
    // === D1 数据库监控 ===
    val d1ReadRows: Long = 0, // D1 已读取行数 (主要计费指标) - GraphQL
    val d1WriteRows: Long = 0, // D1 已写入行数 (主要计费指标) - GraphQL
    val d1StorageBytes: Long = 0, // D1 总存储（字节）- REST API
    val d1DatabaseCount: Int = 0, // D1 数据库数量 - REST API
    // === R2 存储监控 ===
    val r2ClassAOperations: Long = 0, // R2 A类操作（写操作）- GraphQL
    val r2ClassBOperations: Long = 0, // R2 B类操作（读操作）- GraphQL
    val r2StorageBytes: Long = 0, // R2 总存储（字节）- GraphQL
    val r2BucketCount: Int = 0, // R2 存储桶数量 - REST API
    val requestsTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val bandwidthTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val threatsTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val cachedBytesTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val pageViewsTimeSeries: List<TimeSeriesPoint> = emptyList(),
    val status: HealthStatus = HealthStatus.HEALTHY
)

/**
 * 时间序列数据点
 */
data class TimeSeriesPoint(
    val timestamp: Long, // Unix timestamp
    val value: Double
)

/**
 * 时间范围枚举
 */
enum class TimeRange(val days: Int, val displayName: String) {
    ONE_DAY(1, "24小时"),
    SEVEN_DAYS(7, "7天"),
    THIRTY_DAYS(30, "30天");
    
    /**
     * 获取GraphQL查询的开始时间
     */
    fun getStartDateTime(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_MONTH, -days)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return dateFormat.format(calendar.time)
    }
    
    /**
     * 获取GraphQL查询的结束时间
     */
    fun getEndDateTime(): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return dateFormat.format(java.util.Date())
    }
}

/**
 * 健康状态枚举
 */
enum class HealthStatus {
    HEALTHY,      // 正常
    WARNING,      // 警告 (错误率 5-10%)
    CRITICAL      // 严重 (错误率 > 10% 或 D1 超限)
}
