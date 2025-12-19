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
    @SerializedName("database_id") val databaseId: String? = null, // For D1
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
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
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

// ==================== Bindings ====================

data class KvBinding(
    @SerializedName("namespace_id") val namespaceId: String
)

data class KvBindingUpdate(
    @SerializedName("namespace_id") val namespaceId: String
)

data class R2Binding(
    @SerializedName("name") val name: String
)

data class R2BindingUpdate(
    @SerializedName("name") val name: String
)

data class D1Binding(
    @SerializedName("id") val id: String
)

data class D1BindingUpdate(
    @SerializedName("id") val id: String
)

data class DurableObjectBinding(
    @SerializedName("namespace_id") val namespaceId: String,
    @SerializedName("class_name") val className: String,
    @SerializedName("script_name") val scriptName: String? = null
)

data class DurableObjectBindingUpdate(
    @SerializedName("namespace_id") val namespaceId: String,
    @SerializedName("class_name") val className: String,
    @SerializedName("script_name") val scriptName: String? = null
)

data class ServiceBinding(
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String? = null
)

data class ServiceBindingUpdate(
    @SerializedName("service") val service: String,
    @SerializedName("environment") val environment: String? = null
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
