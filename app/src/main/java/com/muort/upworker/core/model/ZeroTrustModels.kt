package com.muort.upworker.core.model

import com.google.gson.annotations.SerializedName

// ==================== Zero Trust - Access Applications ====================

/**
 * Access Application
 * https://developers.cloudflare.com/api/resources/zero_trust/subresources/access/subresources/applications/
 */
data class AccessApplication(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("domain") val domain: String?,
    @SerializedName("type") val type: String, // "self_hosted", "saas", "ssh", "vnc", "app_launcher", "warp", "biso", "bookmark"
    @SerializedName("session_duration") val sessionDuration: String? = null, // e.g. "24h"
    @SerializedName("allowed_idps") val allowedIdps: List<String>? = null,
    @SerializedName("auto_redirect_to_identity") val autoRedirectToIdentity: Boolean? = null,
    @SerializedName("enable_binding_cookie") val enableBindingCookie: Boolean? = null,
    @SerializedName("app_launcher_visible") val appLauncherVisible: Boolean? = null,
    @SerializedName("service_auth_401_redirect") val serviceAuth401Redirect: Boolean? = null,
    @SerializedName("custom_deny_message") val customDenyMessage: String? = null,
    @SerializedName("custom_deny_url") val customDenyUrl: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("skip_interstitial") val skipInterstitial: Boolean? = null,
    @SerializedName("cors_headers") val corsHeaders: CorsHeaders? = null,
    @SerializedName("saas_app") val saasApp: SaasApplication? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AccessApplicationRequest(
    @SerializedName("name") val name: String,
    @SerializedName("domain") val domain: String?,
    @SerializedName("type") val type: String = "self_hosted",
    @SerializedName("session_duration") val sessionDuration: String? = "24h",
    @SerializedName("allowed_idps") val allowedIdps: List<String>? = null,
    @SerializedName("auto_redirect_to_identity") val autoRedirectToIdentity: Boolean? = null,
    @SerializedName("enable_binding_cookie") val enableBindingCookie: Boolean? = null,
    @SerializedName("app_launcher_visible") val appLauncherVisible: Boolean? = true,
    @SerializedName("cors_headers") val corsHeaders: CorsHeaders? = null
)

data class CorsHeaders(
    @SerializedName("allow_all_headers") val allowAllHeaders: Boolean? = null,
    @SerializedName("allow_all_methods") val allowAllMethods: Boolean? = null,
    @SerializedName("allow_all_origins") val allowAllOrigins: Boolean? = null,
    @SerializedName("allowed_headers") val allowedHeaders: List<String>? = null,
    @SerializedName("allowed_methods") val allowedMethods: List<String>? = null,
    @SerializedName("allowed_origins") val allowedOrigins: List<String>? = null,
    @SerializedName("allow_credentials") val allowCredentials: Boolean? = null,
    @SerializedName("max_age") val maxAge: Int? = null
)

data class SaasApplication(
    @SerializedName("consumer_service_url") val consumerServiceUrl: String? = null,
    @SerializedName("sp_entity_id") val spEntityId: String? = null,
    @SerializedName("name_id_format") val nameIdFormat: String? = null
)

// ==================== Zero Trust - Access Policies ====================

/**
 * Access Policy
 */
data class AccessPolicy(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("decision") val decision: String, // "allow", "deny", "bypass", "non_identity"
    @SerializedName("include") val include: List<AccessRule>,
    @SerializedName("exclude") val exclude: List<AccessRule>? = null,
    @SerializedName("require") val require: List<AccessRule>? = null,
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("session_duration") val sessionDuration: String? = null,
    @SerializedName("purpose_justification_required") val purposeJustificationRequired: Boolean? = null,
    @SerializedName("purpose_justification_prompt") val purposeJustificationPrompt: String? = null,
    @SerializedName("approval_required") val approvalRequired: Boolean? = null,
    @SerializedName("isolation_required") val isolationRequired: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AccessPolicyRequest(
    @SerializedName("name") val name: String,
    @SerializedName("decision") val decision: String,
    @SerializedName("include") val include: List<AccessRule>,
    @SerializedName("exclude") val exclude: List<AccessRule>? = null,
    @SerializedName("require") val require: List<AccessRule>? = null,
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("session_duration") val sessionDuration: String? = null
)

/**
 * Access Rule - Supports multiple selector types
 * Only one field should be set per rule
 */
data class AccessRule(
    // Identity selectors
    @SerializedName("email") val email: Map<String, String>? = null, // {"email": "user@example.com"}
    @SerializedName("email_domain") val emailDomain: Map<String, String>? = null, // {"email_domain": {"domain": "example.com"}}
    @SerializedName("everyone") val everyone: Map<String, Any>? = null, // {}
    @SerializedName("ip") val ip: Map<String, String>? = null, // {"ip": "192.168.1.1"}
    @SerializedName("ip_list") val ipList: Map<String, String>? = null, // {"id": "list_id"}
    @SerializedName("certificate") val certificate: Map<String, Any>? = null,
    @SerializedName("access_group") val accessGroup: Map<String, String>? = null, // {"id": "group_id"}
    @SerializedName("azure_group") val azureGroup: Map<String, String>? = null,
    @SerializedName("github_organization") val githubOrganization: Map<String, String>? = null,
    @SerializedName("geo") val geo: Map<String, List<String>>? = null, // {"country_code": ["US", "CA"]}
    @SerializedName("common_name") val commonName: Map<String, String>? = null,
    @SerializedName("service_token") val serviceToken: Map<String, String>? = null,
    @SerializedName("any_valid_service_token") val anyValidServiceToken: Map<String, Any>? = null,
    @SerializedName("device_posture") val devicePosture: Map<String, String>? = null,
    @SerializedName("auth_method") val authMethod: Map<String, String>? = null
)

// ==================== Zero Trust - Access Groups ====================

/**
 * Access Group
 */
data class AccessGroup(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("include") val include: List<AccessRule>,
    @SerializedName("exclude") val exclude: List<AccessRule>? = null,
    @SerializedName("require") val require: List<AccessRule>? = null,
    @SerializedName("is_default") val isDefault: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class AccessGroupRequest(
    @SerializedName("name") val name: String,
    @SerializedName("include") val include: List<AccessRule>,
    @SerializedName("exclude") val exclude: List<AccessRule>? = null,
    @SerializedName("require") val require: List<AccessRule>? = null,
    @SerializedName("is_default") val isDefault: Boolean? = false
)

// ==================== Zero Trust - Gateway Rules ====================

/**
 * Gateway Rule (DNS, HTTP, Network policies)
 */
data class GatewayRule(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("action") val action: String, // "allow", "block", "safesearch", "ytrestricted", "on", "off", "scan", "noscan", "isolate", "noisolate", "override", "l4_override", "egress", "audit_ssh"
    @SerializedName("enabled") val enabled: Boolean,
    @SerializedName("filters") val filters: List<String>, // ["dns", "http", "l4"]
    @SerializedName("traffic") val traffic: String? = null, // Wirefilter expression
    @SerializedName("identity") val identity: String? = null, // Identity selector
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("rule_settings") val ruleSettings: GatewayRuleSettings? = null,
    @SerializedName("schedule") val schedule: RuleSchedule? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class GatewayRuleRequest(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("action") val action: String,
    @SerializedName("enabled") val enabled: Boolean = true,
    @SerializedName("filters") val filters: List<String>,
    @SerializedName("traffic") val traffic: String? = null,
    @SerializedName("identity") val identity: String? = null,
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("rule_settings") val ruleSettings: GatewayRuleSettings? = null
)

data class GatewayRuleSettings(
    @SerializedName("block_page_enabled") val blockPageEnabled: Boolean? = null,
    @SerializedName("block_reason") val blockReason: String? = null,
    @SerializedName("override_ips") val overrideIps: List<String>? = null,
    @SerializedName("override_host") val overrideHost: String? = null,
    @SerializedName("l4override") val l4Override: L4Override? = null,
    @SerializedName("biso_admin_controls") val bisoAdminControls: BisoAdminControls? = null,
    @SerializedName("check_session") val checkSession: CheckSession? = null,
    @SerializedName("insecure_disable_dnssec_validation") val insecureDisableDnssecValidation: Boolean? = null
)

data class L4Override(
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("port") val port: Int? = null
)

data class BisoAdminControls(
    @SerializedName("disable_printing") val disablePrinting: Boolean? = null,
    @SerializedName("disable_copy_paste") val disableCopyPaste: Boolean? = null,
    @SerializedName("disable_download") val disableDownload: Boolean? = null,
    @SerializedName("disable_upload") val disableUpload: Boolean? = null,
    @SerializedName("disable_keyboard") val disableKeyboard: Boolean? = null
)

data class CheckSession(
    @SerializedName("enforce") val enforce: Boolean? = null,
    @SerializedName("duration") val duration: String? = null
)

data class RuleSchedule(
    @SerializedName("time_zone") val timeZone: String? = null,
    @SerializedName("mon") val monday: String? = null,
    @SerializedName("tue") val tuesday: String? = null,
    @SerializedName("wed") val wednesday: String? = null,
    @SerializedName("thu") val thursday: String? = null,
    @SerializedName("fri") val friday: String? = null,
    @SerializedName("sat") val saturday: String? = null,
    @SerializedName("sun") val sunday: String? = null
)

// ==================== Zero Trust - Gateway Lists ====================

/**
 * Gateway List (Custom lists for DNS, IP, URLs)
 */
data class GatewayList(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String, // "SERIAL" (domains), "IP", "URL"
    @SerializedName("count") val count: Int? = null,
    @SerializedName("items") val items: List<GatewayListItem>? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class GatewayListRequest(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("items") val items: List<GatewayListItem>
)

data class GatewayListItem(
    @SerializedName("value") val value: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class GatewayListPatchRequest(
    @SerializedName("append") val append: List<GatewayListItem>? = null,
    @SerializedName("remove") val remove: List<String>? = null
)

// ==================== Zero Trust - Gateway Locations ====================

/**
 * Gateway Location (Network locations for DNS filtering)
 */
data class GatewayLocation(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("networks") val networks: List<LocationNetwork>? = null,
    @SerializedName("policy_ids") val policyIds: List<String>? = null,
    @SerializedName("ip") val ip: String? = null, // IPv4 address
    @SerializedName("doh_subdomain") val dohSubdomain: String? = null,
    @SerializedName("anonymized_logs_enabled") val anonymizedLogsEnabled: Boolean? = null,
    @SerializedName("ipv4_destination") val ipv4Destination: String? = null,
    @SerializedName("client_default") val clientDefault: Boolean? = null,
    @SerializedName("ecs_support") val ecsSupport: Boolean? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class GatewayLocationRequest(
    @SerializedName("name") val name: String,
    @SerializedName("networks") val networks: List<LocationNetwork>? = null,
    @SerializedName("client_default") val clientDefault: Boolean? = false,
    @SerializedName("ecs_support") val ecsSupport: Boolean? = false
)

data class LocationNetwork(
    @SerializedName("network") val network: String // CIDR notation
)

// ==================== Zero Trust - Devices ====================

/**
 * Device (WARP client device)
 */
data class Device(
    @SerializedName("id") val id: String,
    @SerializedName("key") val key: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("model") val model: String? = null,
    @SerializedName("type") val type: String? = null, // "windows", "mac", "linux", "android", "ios"
    @SerializedName("os_version") val osVersion: String? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("user") val user: DeviceUser? = null,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("mac_address") val macAddress: String? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("updated") val updated: String? = null,
    @SerializedName("revoked_at") val revokedAt: String? = null,
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("manufacturer") val manufacturer: String? = null,
    @SerializedName("version") val version: String? = null
)

data class DeviceUser(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null
)

// ==================== Zero Trust - Device Policies ====================

/**
 * Device Settings Policy (WARP client configuration)
 */
data class DeviceSettingsPolicy(
    @SerializedName("policy_id") val policyId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("match") val match: String? = null,
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("enabled") val enabled: Boolean? = null,
    @SerializedName("exclude_office_ips") val excludeOfficeIps: Boolean? = null,
    @SerializedName("allow_mode_switch") val allowModeSwitch: Boolean? = null,
    @SerializedName("switch_locked") val switchLocked: Boolean? = null,
    @SerializedName("auto_connect") val autoConnect: Int? = null, // 0 or 1 or 2
    @SerializedName("allowed_to_leave") val allowedToLeave: Boolean? = null,
    @SerializedName("support_url") val supportUrl: String? = null,
    @SerializedName("captive_portal") val captivePortal: Int? = null,
    @SerializedName("disable_auto_fallback") val disableAutoFallback: Boolean? = null,
    @SerializedName("fallback_domains") val fallbackDomains: List<FallbackDomain>? = null,
    @SerializedName("exclude") val exclude: List<SplitTunnel>? = null,
    @SerializedName("include") val include: List<SplitTunnel>? = null,
    @SerializedName("gateway_unique_id") val gatewayUniqueId: String? = null
)

data class DeviceSettingsPolicyRequest(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("match") val match: String? = null,
    @SerializedName("precedence") val precedence: Int? = null,
    @SerializedName("enabled") val enabled: Boolean? = true
)

data class DevicePolicyUpdate(
    @SerializedName("allow_mode_switch") val allowModeSwitch: Boolean? = null,
    @SerializedName("switch_locked") val switchLocked: Boolean? = null,
    @SerializedName("auto_connect") val autoConnect: Int? = null,
    @SerializedName("disable_auto_fallback") val disableAutoFallback: Boolean? = null,
    @SerializedName("exclude") val exclude: List<SplitTunnel>? = null,
    @SerializedName("include") val include: List<SplitTunnel>? = null
)

data class SplitTunnel(
    @SerializedName("address") val address: String? = null,
    @SerializedName("host") val host: String? = null,
    @SerializedName("description") val description: String? = null
)

data class FallbackDomain(
    @SerializedName("suffix") val suffix: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("dns_server") val dnsServer: List<String>? = null
)

// ==================== Zero Trust - Tunnels ====================

/**
 * Cloudflare Tunnel (cloudflared)
 */
data class CloudflareTunnel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("account_tag") val accountTag: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
    @SerializedName("connections") val connections: List<TunnelConnection>? = null,
    @SerializedName("conns_active_at") val connsActiveAt: String? = null,
    @SerializedName("conns_inactive_at") val connsInactiveAt: String? = null,
    @SerializedName("tun_type") val tunType: String? = null, // "cfd_tunnel", "warp_connector"
    @SerializedName("status") val status: String? = null, // "active", "inactive", "degraded", "down"
    @SerializedName("remote_config") val remoteConfig: Boolean? = null
)

data class TunnelCreateRequest(
    @SerializedName("name") val name: String,
    @SerializedName("tunnel_secret") val tunnelSecret: String? = null,
    @SerializedName("config_src") val configSrc: String? = "local" // "local" or "cloudflare"
)

data class TunnelConnection(
    @SerializedName("id") val id: String? = null,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("client_version") val clientVersion: String? = null,
    @SerializedName("colo_name") val coloName: String? = null,
    @SerializedName("is_pending_reconnect") val isPendingReconnect: Boolean? = null,
    @SerializedName("opened_at") val openedAt: String? = null,
    @SerializedName("origin_ip") val originIp: String? = null
)

/**
 * Tunnel Configuration
 */
data class TunnelConfiguration(
    @SerializedName("config") val config: TunnelConfig? = null,
    @SerializedName("tunnel_id") val tunnelId: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class TunnelConfigurationRequest(
    @SerializedName("config") val config: TunnelConfig
)

data class TunnelConfig(
    @SerializedName("ingress") val ingress: List<IngressRule>? = null,
    @SerializedName("warp-routing") val warpRouting: WarpRouting? = null,
    @SerializedName("originRequest") val originRequest: OriginRequest? = null
)

data class IngressRule(
    @SerializedName("hostname") val hostname: String? = null,
    @SerializedName("service") val service: String,
    @SerializedName("path") val path: String? = null,
    @SerializedName("originRequest") val originRequest: OriginRequest? = null
)

data class WarpRouting(
    @SerializedName("enabled") val enabled: Boolean? = null
)

data class OriginRequest(
    @SerializedName("connectTimeout") val connectTimeout: Int? = null,
    @SerializedName("tlsTimeout") val tlsTimeout: Int? = null,
    @SerializedName("tcpKeepAlive") val tcpKeepAlive: Int? = null,
    @SerializedName("noHappyEyeballs") val noHappyEyeballs: Boolean? = null,
    @SerializedName("keepAliveConnections") val keepAliveConnections: Int? = null,
    @SerializedName("httpHostHeader") val httpHostHeader: String? = null,
    @SerializedName("originServerName") val originServerName: String? = null,
    @SerializedName("caPool") val caPool: String? = null,
    @SerializedName("noTLSVerify") val noTLSVerify: Boolean? = null,
    @SerializedName("disableChunkedEncoding") val disableChunkedEncoding: Boolean? = null,
    @SerializedName("proxyAddress") val proxyAddress: String? = null,
    @SerializedName("proxyPort") val proxyPort: Int? = null,
    @SerializedName("proxyType") val proxyType: String? = null
)

// ==================== Zero Trust - Service Tokens ====================

/**
 * Service Token (Non-user authentication for services)
 */
data class ServiceToken(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("client_secret") val clientSecret: String? = null, // Only returned on creation
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class ServiceTokenRequest(
    @SerializedName("name") val name: String,
    @SerializedName("duration") val duration: String? = "8760h" // Default: 1 year
)
