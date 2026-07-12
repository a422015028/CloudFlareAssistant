package com.muort.upworker.core.model

import com.google.gson.annotations.SerializedName

// ==================== WAF 自定义规则（Rulesets, phase = http_request_firewall_custom） ====================

data class WafRuleset(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("rules") val rules: List<WafRule>? = null,
)

data class WafRule(
    @SerializedName("id") val id: String,
    @SerializedName("action") val action: String? = null,
    @SerializedName("expression") val expression: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("last_updated") val lastUpdated: String? = null,
)

data class WafRuleToggle(@SerializedName("enabled") val enabled: Boolean)

data class WafRuleCreate(
    @SerializedName("action") val action: String,
    @SerializedName("expression") val expression: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean,
)

data class WafEntrypointUpdate(@SerializedName("rules") val rules: List<WafRuleCreate>)

// ==================== Cache Rules（phase = http_request_cache_settings, action = set_cache_settings） ====================

data class CacheRuleset(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("rules") val rules: List<CacheRule>? = null,
)

data class CacheRule(
    @SerializedName("id") val id: String,
    @SerializedName("expression") val expression: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("action_parameters") val actionParameters: CacheActionParameters? = null,
)

data class CacheActionParameters(
    @SerializedName("cache") val cache: Boolean? = null,
    @SerializedName("edge_ttl") val edgeTtl: CacheEdgeTTL? = null,
    @SerializedName("browser_ttl") val browserTtl: CacheBrowserTTL? = null,
    // 仅解码用于「是否含高级设置」探测，含这些设置的规则编辑器只读
    @SerializedName("cache_key") val cacheKey: com.google.gson.JsonElement? = null,
    @SerializedName("cache_reserve") val cacheReserve: com.google.gson.JsonElement? = null,
    @SerializedName("read_timeout") val readTimeout: Int? = null,
    @SerializedName("origin_cache_control") val originCacheControl: Boolean? = null,
    @SerializedName("additional_cacheable_ports") val additionalCacheablePorts: List<Int>? = null,
) {
    val hasAdvancedSettings: Boolean
        get() = cacheKey != null || cacheReserve != null || readTimeout != null ||
            originCacheControl != null || (additionalCacheablePorts?.isNotEmpty() == true)
}

data class CacheEdgeTTL(
    @SerializedName("mode") val mode: String,
    @SerializedName("default") val defaultTtl: Int? = null,
)

data class CacheBrowserTTL(
    @SerializedName("mode") val mode: String,
    @SerializedName("default") val defaultTtl: Int? = null,
)

data class CacheRuleCreate(
    @SerializedName("action") val action: String,
    @SerializedName("expression") val expression: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("action_parameters") val actionParameters: CacheActionParameters? = null,
)

data class CacheRuleToggle(@SerializedName("enabled") val enabled: Boolean)
data class CacheEntrypointUpdate(@SerializedName("rules") val rules: List<CacheRuleCreate>)

// ==================== Rate Limiting（phase = http_ratelimit） ====================

data class RateLimitRuleset(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("rules") val rules: List<RateLimitRule>? = null,
)

data class RateLimitRule(
    @SerializedName("id") val id: String,
    @SerializedName("action") val action: String? = null,
    @SerializedName("expression") val expression: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("ratelimit") val ratelimit: RateLimitConfig? = null,
)

data class RateLimitConfig(
    @SerializedName("characteristics") val characteristics: List<String>? = null,
    @SerializedName("period") val period: Int? = null,
    @SerializedName("requests_per_period") val requestsPerPeriod: Int? = null,
    @SerializedName("mitigation_timeout") val mitigationTimeout: Int? = null,
    @SerializedName("counting_expression") val countingExpression: String? = null,
)

data class RateLimitRuleCreate(
    @SerializedName("action") val action: String,
    @SerializedName("expression") val expression: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("ratelimit") val ratelimit: RateLimitConfigInput,
)

data class RateLimitConfigInput(
    @SerializedName("characteristics") val characteristics: List<String>,
    @SerializedName("period") val period: Int,
    @SerializedName("requests_per_period") val requestsPerPeriod: Int,
    @SerializedName("mitigation_timeout") val mitigationTimeout: Int,
)

data class RateLimitToggle(@SerializedName("enabled") val enabled: Boolean)
data class RateLimitEntrypointUpdate(@SerializedName("rules") val rules: List<RateLimitRuleCreate>)

// ==================== Transform Rules（phase = http_request_transform / http_request_late_transform / http_response_headers_transform / http_response_late_transform） ====================

data class TransformRuleset(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("phase") val phase: String? = null,
    @SerializedName("rules") val rules: List<TransformRule>? = null,
)

data class TransformRule(
    @SerializedName("id") val id: String,
    @SerializedName("expression") val expression: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("action_parameters") val actionParameters: TransformActionParameters? = null,
)

data class TransformActionParameters(
    @SerializedName("uri") val uri: UriRewrite? = null,
    @SerializedName("headers") val headers: Map<String, HeaderTransform>? = null,
)

data class UriRewrite(
    @SerializedName("path") val path: RewriteTarget? = null,
    @SerializedName("query") val query: RewriteTarget? = null,
)

data class RewriteTarget(
    @SerializedName("value") val value: String? = null,
    @SerializedName("expression") val expression: String? = null,
)

data class HeaderTransform(
    @SerializedName("operation") val operation: String,
    @SerializedName("value") val value: String? = null,
    @SerializedName("expression") val expression: String? = null,
)

data class TransformRuleCreate(
    @SerializedName("action") val action: String,
    @SerializedName("expression") val expression: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("action_parameters") val actionParameters: TransformActionParameters? = null,
)

data class TransformEntrypointUpdate(@SerializedName("rules") val rules: List<TransformRuleCreate>)

// ==================== IP 访问规则（firewall/access_rules/rules） ====================

data class FirewallAccessRule(
    @SerializedName("id") val id: String,
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("configuration") val configuration: AccessRuleConfig? = null,
    @SerializedName("notes") val notes: String? = null,
)

data class AccessRuleConfig(
    @SerializedName("target") val target: String? = null,
    @SerializedName("value") val value: String? = null,
)

data class AccessRuleConfigInput(
    @SerializedName("target") val target: String,
    @SerializedName("value") val value: String,
)

data class AccessRuleCreate(
    @SerializedName("mode") val mode: String,
    @SerializedName("configuration") val configuration: AccessRuleConfigInput,
    @SerializedName("notes") val notes: String? = null,
)

data class AccessRuleUpdate(
    @SerializedName("mode") val mode: String,
    @SerializedName("notes") val notes: String? = null,
)

// ==================== Email Routing ====================

data class EmailRoutingSettings(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("status") val status: String? = null,
) {
    val isEnabled: Boolean get() = enabled ?: false
}

data class EmailRoutingMatcher(
    @SerializedName("type") val type: String,
    @SerializedName("field") val field: String? = null,
    @SerializedName("value") val value: String? = null,
)

data class EmailRoutingAction(
    @SerializedName("type") val type: String,
    @SerializedName("value") val value: List<String>? = null,
)

data class EmailRoutingRule(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("priority") val priority: Int? = null,
    @SerializedName("matchers") val matchers: List<EmailRoutingMatcher> = emptyList(),
    @SerializedName("actions") val actions: List<EmailRoutingAction> = emptyList(),
) {
    val isEnabled: Boolean get() = enabled ?: false
    val isCatchAll: Boolean get() = matchers.any { it.type == "all" }
    val matchAddress: String? get() = matchers.firstOrNull { it.type == "literal" }?.value
}

data class EmailRoutingRuleInput(
    @SerializedName("name") val name: String? = null,
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("matchers") val matchers: List<EmailRoutingMatcher>,
    @SerializedName("actions") val actions: List<EmailRoutingAction>,
)

data class EmailDestinationAddress(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("verified") val verified: String? = null,
) {
    val isVerified: Boolean get() = verified != null
}

data class EmailDestinationCreate(@SerializedName("email") val email: String)

// ==================== SSL / 证书 ====================

data class SslCertificatePack(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("hosts") val hosts: List<String>? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("certificate_authority") val certificateAuthority: String? = null,
    @SerializedName("certificates") val certificates: List<SslCertEntry>? = null,
) {
    val isUniversal: Boolean get() = type == "universal"
    val expiresOnDay: String? get() = certificates?.mapNotNull { it.expiresOn }?.minOrNull()?.take(10)
    val issuer: String? get() = certificates?.firstNotNullOfOrNull { it.issuer }
}

data class SslCertEntry(
    @SerializedName("id") val id: String? = null,
    @SerializedName("issuer") val issuer: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("expires_on") val expiresOn: String? = null,
)

data class UniversalSslSettings(@SerializedName("enabled") val enabled: Boolean? = null)

// ==================== Zone Settings ====================

data class ZoneSetting(
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String,
)

data class ZoneSettingUpdate(@SerializedName("value") val value: String)

data class PurgeRequest(@SerializedName("purge_everything") val purgeEverything: Boolean)
data class PurgeFilesRequest(@SerializedName("files") val files: List<String>)
data class PurgeResult(@SerializedName("id") val id: String? = null)

// ==================== Load Balancer ====================

data class LoadBalancer(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("ttl") val ttl: Int? = null,
    @SerializedName("proxied") val proxied: Boolean? = null,
    @SerializedName("default_pools") val defaultPools: List<String>? = null,
    @SerializedName("fallback_pool") val fallbackPool: String? = null,
    @SerializedName("steering_policy") val steeringPolicy: String? = null,
    @SerializedName("session_affinity") val sessionAffinity: String? = null,
)

data class LoadBalancerToggle(@SerializedName("enabled") val enabled: Boolean)

data class Pool(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("monitor") val monitor: String? = null,
    @SerializedName("origins") val origins: List<Origin>? = null,
) {
    val enabledOriginsCount: Int get() = origins.orEmpty().count { it.enabled ?: true }
    val originsCount: Int get() = origins?.size ?: 0
}

data class Origin(
    @SerializedName("name") val name: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("weight") val weight: Double? = null,
    @SerializedName("port") val port: Int? = null,
)

data class Monitor(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("method") val method: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("expected_codes") val expectedCodes: String? = null,
    @SerializedName("interval") val interval: Int? = null,
    @SerializedName("timeout") val timeout: Int? = null,
    @SerializedName("retries") val retries: Int? = null,
    @SerializedName("port") val port: Int? = null,
    @SerializedName("description") val description: String? = null,
)

// ==================== Snippets ====================

data class Snippet(
    @SerializedName("snippet_name") val snippetName: String,
    @SerializedName("created_on") val createdOn: String? = null,
    @SerializedName("modified_on") val modifiedOn: String? = null,
)

data class SnippetRule(
    @SerializedName("id") val id: String? = null,
    @SerializedName("snippet_name") val snippetName: String = "",
    @SerializedName("expression") val expression: String = "",
    @SerializedName("description") val description: String? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
)
