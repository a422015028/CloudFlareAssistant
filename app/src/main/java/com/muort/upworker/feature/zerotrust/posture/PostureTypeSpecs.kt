package com.muort.upworker.feature.zerotrust.posture

import androidx.annotation.StringRes
import com.muort.upworker.R

/**
 * Static metadata for device posture rule types and posture integrations.
 * The API's `input` / `config` payloads are heterogeneous JSON, so each type
 * ships with a pre-filled JSON template the user can edit in the UI.
 */
object PostureTypeSpecs {

    /** Rule type → (label, JSON template for `input`, supported platforms) */
    val ruleTypes: List<RuleTypeSpec> = listOf(
        RuleTypeSpec("file", R.string.zt_posture_type_file, """{"operating_system":"windows","path":"C:\\Program Files\\App\\app.exe","exists":true}""",
            setOf("windows", "mac", "linux")),
        RuleTypeSpec("application", R.string.zt_posture_type_application, """{"operating_system":"windows","path":"C:\\Program Files\\App\\app.exe","thumbprint":""}""",
            setOf("windows", "mac")),
        RuleTypeSpec("os_version", R.string.device_os_version, """{"operating_system":"windows","operator":">=","version":"10.0.0"}""",
            setOf("windows", "mac", "linux", "android", "ios")),
        RuleTypeSpec("firewall", R.string.zt_posture_type_firewall, """{"enabled":true,"operating_system":"windows"}""",
            setOf("windows", "mac")),
        RuleTypeSpec("disk_encryption", R.string.zt_posture_type_disk_encryption, """{"requireAll":true}""",
            setOf("windows", "mac")),
        RuleTypeSpec("domain_joined", R.string.zt_posture_type_domain_joined, """{"operating_system":"windows","domain":"example.com"}""",
            setOf("windows")),
        RuleTypeSpec("antivirus", R.string.zt_posture_type_antivirus, """{"update_window_days":7}""",
            setOf("windows", "mac")),
        RuleTypeSpec("client_certificate", R.string.zt_posture_type_client_certificate, """{"certificate_id":"<mTLS-CERTIFICATE-UUID>","cn":"user@example.com"}""",
            setOf("windows", "mac", "linux", "android", "ios", "chromeos")),
        RuleTypeSpec("client_certificate_v2", R.string.zt_posture_type_client_certificate_v2, """{"certificate_id":"<mTLS-CERTIFICATE-UUID>","check_private_key":true,"operating_system":"windows","cn":""}""",
            setOf("windows", "mac", "linux")),
        RuleTypeSpec("serial_number", R.string.zt_posture_type_serial_number, """{"id":"<ACCESS-LIST-UUID>"}""",
            setOf("windows", "mac", "linux", "android", "ios", "chromeos")),
        RuleTypeSpec("unique_client_id", R.string.zt_posture_type_unique_client_id, """{"id":"<LIST-ID>","operating_system":"android"}""",
            setOf("android", "ios", "chromeos")),
        RuleTypeSpec("warp", R.string.zt_posture_type_warp, "{}",
            setOf("windows", "mac", "linux", "android", "ios", "chromeos")),
        RuleTypeSpec("gateway", R.string.zt_posture_type_gateway, "{}",
            setOf("windows", "mac", "linux", "android", "ios", "chromeos")),
        RuleTypeSpec("crowdstrike_s2s", R.string.zt_posture_type_crowdstrike_s2s, """{"connection_id":"<INTEGRATION-ID>","state":"online"}""",
            setOf("windows", "mac", "linux"),
            connectionIdIntegrationType = "crowdstrike_s2s"),
        RuleTypeSpec("sentinelone_s2s", R.string.zt_posture_type_sentinelone_s2s, """{"connection_id":"<INTEGRATION-ID>","infected":false,"is_active":true}""",
            setOf("windows", "mac"),
            connectionIdIntegrationType = "sentinelone_s2s"),
        RuleTypeSpec("sentinelone", R.string.zt_posture_type_sentinelone, """{"operating_system":"windows","path":"C:\\Program Files\\SentinelOne\\SentinelAgent.exe"}""",
            setOf("windows", "mac")),
        RuleTypeSpec("carbonblack", R.string.zt_posture_type_carbonblack, """{"operating_system":"windows","path":"C:\\Program Files\\CarbonBlack\\cb.exe"}""",
            setOf("windows", "mac")),
        RuleTypeSpec("tanium", R.string.zt_posture_type_tanium, """{"connection_id":"<INTEGRATION-ID>","operator":">","eid_last_seen":"7d"}""",
            setOf("windows", "mac", "linux"),
            connectionIdIntegrationType = "tanium_s2s"),
        RuleTypeSpec("tanium_s2s", R.string.zt_posture_type_tanium_s2s, """{"connection_id":"<INTEGRATION-ID>","operator":">=","total_score":70}""",
            setOf("windows", "mac", "linux"),
            connectionIdIntegrationType = "tanium_s2s"),
        RuleTypeSpec("intune", R.string.zt_posture_type_intune, """{"connection_id":"<INTEGRATION-ID>","compliance_status":"compliant"}""",
            setOf("windows", "mac", "android", "ios"),
            connectionIdIntegrationType = "intune"),
        RuleTypeSpec("workspace_one", R.string.zt_posture_type_workspace_one, """{"connection_id":"<INTEGRATION-ID>","compliance_status":"compliant"}""",
            setOf("windows", "mac", "android", "ios"),
            connectionIdIntegrationType = "workspace_one"),
        RuleTypeSpec("kolide", R.string.zt_posture_type_kolide, """{"connection_id":"<INTEGRATION-ID>","auth_state":["Good"],"countOperator":"<","issue_count":"5"}""",
            setOf("windows", "mac", "linux"),
            connectionIdIntegrationType = "kolide"),
        RuleTypeSpec("custom_s2s", R.string.zt_posture_type_custom_s2s, """{"connection_id":"<INTEGRATION-ID>","operator":">=","score":80}""",
            setOf("windows", "mac", "linux", "android", "ios", "chromeos"),
            connectionIdIntegrationType = "custom_s2s")
    )

    /** Integration type → (label, JSON template for `config`) */
    val integrationTypes: List<IntegrationTypeSpec> = listOf(
        IntegrationTypeSpec("workspace_one", R.string.zt_posture_integration_workspace_one,
            """{"api_url":"https://<host>.awmdm.com/API","auth_url":"https://na.uemauth.workspaceone.com/connect/token","client_id":"","client_secret":""}"""),
        IntegrationTypeSpec("crowdstrike_s2s", R.string.zt_posture_type_crowdstrike_s2s,
            """{"api_url":"https://api.crowdstrike.com","client_id":"","client_secret":"","customer_id":""}"""),
        IntegrationTypeSpec("uptycs", R.string.zt_posture_integration_uptycs,
            """{"api_url":"https://<subdomain>.uptycs.io","client_key":"","client_secret":"","customer_id":""}"""),
        IntegrationTypeSpec("intune", R.string.zt_posture_type_intune,
            """{"client_id":"","client_secret":"","customer_id":""}"""),
        IntegrationTypeSpec("kolide", R.string.zt_posture_type_kolide,
            """{"client_id":"","client_secret":""}"""),
        IntegrationTypeSpec("tanium_s2s", R.string.zt_posture_type_tanium,
            """{"api_url":"https://tanium.example.com","client_secret":""}"""),
        IntegrationTypeSpec("sentinelone_s2s", R.string.zt_posture_integration_sentinelone_s2s,
            """{"api_url":"https://<host>.sentinelone.net","client_secret":""}"""),
        IntegrationTypeSpec("custom_s2s", R.string.zt_posture_type_custom_s2s,
            """{"api_url":"https://posture.example.com","access_client_id":"","access_client_secret":""}""")
    )

    val platforms: List<PlatformSpec> = listOf(
        PlatformSpec("windows", R.string.os_windows),
        PlatformSpec("mac", R.string.os_macos),
        PlatformSpec("linux", R.string.os_linux),
        PlatformSpec("android", R.string.os_android),
        PlatformSpec("ios", R.string.os_ios),
        PlatformSpec("chromeos", R.string.os_chromeos)
    )

    /** WARP polling frequency options */
    val scheduleOptions: List<String> = listOf("1m", "5m", "15m", "30m", "1h", "6h", "24h")

    /** Integration polling frequency options */
    val intervalOptions: List<String> = listOf("5m", "10m", "30m", "1h", "12h", "24h")

    fun ruleTemplate(type: String): String =
        ruleTypes.firstOrNull { it.type == type }?.inputTemplate ?: "{}"

    fun ruleLabel(type: String?): Int =
        ruleTypes.firstOrNull { it.type == type }?.labelRes ?: R.string.zt_posture_type_unknown

    fun integrationTemplate(type: String): String =
        integrationTypes.firstOrNull { it.type == type }?.configTemplate ?: "{}"

    fun integrationLabel(type: String?): Int =
        integrationTypes.firstOrNull { it.type == type }?.labelRes ?: R.string.zt_posture_type_unknown

    data class RuleTypeSpec(
        val type: String,
        @param:StringRes val labelRes: Int,
        val inputTemplate: String,
        val supportedPlatforms: Set<String> = emptySet(),
        /** Integration type that provides the connection_id for S2S rules, or null. */
        val connectionIdIntegrationType: String? = null
    ) {
        /** Returns true if this rule type supports the given platform. */
        fun supportsPlatform(platform: String): Boolean =
            supportedPlatforms.isEmpty() || supportedPlatforms.contains(platform)
    }

    data class IntegrationTypeSpec(
        val type: String,
        @param:StringRes val labelRes: Int,
        val configTemplate: String
    )

    data class PlatformSpec(
        val platform: String,
        @param:StringRes val labelRes: Int
    )
}
