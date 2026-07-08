package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class DeviceSettingsPolicyRequestAdapter : JsonSerializer<DeviceSettingsPolicyRequest>, JsonDeserializer<DeviceSettingsPolicyRequest> {

    override fun serialize(
        src: DeviceSettingsPolicyRequest,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        if (src.name.isNotBlank()) jsonObject.addProperty("name", src.name)
        src.description?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("description", it) }
        src.match?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("match", it) }
        src.precedence?.let { jsonObject.addProperty("precedence", it) }
        src.enabled?.let { jsonObject.addProperty("enabled", it) }
        
        if (src.captivePortal != null) jsonObject.addProperty("captive_portal", src.captivePortal)
        if (src.allowModeSwitch != null) jsonObject.addProperty("allow_mode_switch", src.allowModeSwitch)
        src.tunnelProtocol?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("tunnel_protocol", it) }
        if (src.switchLocked != null) jsonObject.addProperty("switch_locked", src.switchLocked)
        if (src.allowedToLeave != null) jsonObject.addProperty("allowed_to_leave", src.allowedToLeave)
        if (src.allowUpdates != null) jsonObject.addProperty("allow_updates", src.allowUpdates)
        if (src.autoConnect != null) jsonObject.addProperty("auto_connect", src.autoConnect)
        src.supportUrl?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("support_url", it) }
        
        if (src.serviceModeV2 != null && src.serviceModeV2.mode != null) {
            val serviceModeObj = JsonObject()
            serviceModeObj.addProperty("mode", src.serviceModeV2.mode)
            jsonObject.add("service_mode_v2", serviceModeObj)
        }
        
        src.exclude?.takeIf { it.isNotEmpty() }?.let {
            val excludeArray = JsonArray()
            it.forEach { item ->
                val itemObj = JsonObject()
                item.address?.let { addr -> itemObj.addProperty("address", addr) }
                item.host?.let { host -> itemObj.addProperty("host", host) }
                item.description?.let { desc -> itemObj.addProperty("description", desc) }
                excludeArray.add(itemObj)
            }
            jsonObject.add("exclude", excludeArray)
        }
        
        src.include?.takeIf { it.isNotEmpty() }?.let {
            val includeArray = JsonArray()
            it.forEach { item ->
                val itemObj = JsonObject()
                item.address?.let { addr -> itemObj.addProperty("address", addr) }
                item.host?.let { host -> itemObj.addProperty("host", host) }
                item.description?.let { desc -> itemObj.addProperty("description", desc) }
                includeArray.add(itemObj)
            }
            jsonObject.add("include", includeArray)
        }
        
        if (src.excludeOfficeIps != null) jsonObject.addProperty("exclude_office_ips", src.excludeOfficeIps)
        if (src.allowLocalNetworkExclusion != null) jsonObject.addProperty("allow_local_network_exclusion", src.allowLocalNetworkExclusion)
        if (src.registerInterfaceIpWithDns != null) jsonObject.addProperty("register_interface_ip_with_dns", src.registerInterfaceIpWithDns)
        if (src.sccmVpnBoundarySupport != null) jsonObject.addProperty("sccm_vpn_boundary_support", src.sccmVpnBoundarySupport)
        if (src.netbtEnabled != null) jsonObject.addProperty("enable_netbt", src.netbtEnabled)
        src.gatewayUniqueId?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("gateway_unique_id", it) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DeviceSettingsPolicyRequest {
        if (json == null || !json.isJsonObject) {
            return DeviceSettingsPolicyRequest(name = "")
        }
        
        val obj = json.asJsonObject
        
        return DeviceSettingsPolicyRequest(
            name = obj.get("name")?.asString ?: "",
            description = if (obj.has("description") && !obj.get("description").isJsonNull) obj.get("description").asString else null,
            match = if (obj.has("match") && !obj.get("match").isJsonNull) obj.get("match").asString else null,
            precedence = if (obj.has("precedence") && !obj.get("precedence").isJsonNull) obj.get("precedence").asInt else null,
            enabled = if (obj.has("enabled") && !obj.get("enabled").isJsonNull) obj.get("enabled").asBoolean else null,
            autoConnect = if (obj.has("auto_connect") && !obj.get("auto_connect").isJsonNull) obj.get("auto_connect").asInt else null,
            allowModeSwitch = if (obj.has("allow_mode_switch") && !obj.get("allow_mode_switch").isJsonNull) obj.get("allow_mode_switch").asBoolean else null,
            switchLocked = if (obj.has("switch_locked") && !obj.get("switch_locked").isJsonNull) obj.get("switch_locked").asBoolean else null,
            excludeOfficeIps = if (obj.has("exclude_office_ips") && !obj.get("exclude_office_ips").isJsonNull) obj.get("exclude_office_ips").asBoolean else null,
            allowedToLeave = if (obj.has("allowed_to_leave") && !obj.get("allowed_to_leave").isJsonNull) obj.get("allowed_to_leave").asBoolean else null,
            supportUrl = if (obj.has("support_url") && !obj.get("support_url").isJsonNull) obj.get("support_url").asString else null,
            captivePortal = if (obj.has("captive_portal") && !obj.get("captive_portal").isJsonNull) obj.get("captive_portal").asInt else null,
            disableAutoFallback = if (obj.has("disable_auto_fallback") && !obj.get("disable_auto_fallback").isJsonNull) obj.get("disable_auto_fallback").asBoolean else null,
            gatewayUniqueId = if (obj.has("gateway_unique_id") && !obj.get("gateway_unique_id").isJsonNull) obj.get("gateway_unique_id").asString else null,
            tunnelProtocol = if (obj.has("tunnel_protocol") && !obj.get("tunnel_protocol").isJsonNull) obj.get("tunnel_protocol").asString else null,
            allowUpdates = if (obj.has("allow_updates") && !obj.get("allow_updates").isJsonNull) obj.get("allow_updates").asBoolean else null,
            serviceModeV2 = if (obj.has("service_mode_v2") && !obj.get("service_mode_v2").isJsonNull) {
                val modeObj = obj.get("service_mode_v2").asJsonObject
                ServiceModeV2(mode = modeObj.get("mode")?.asString)
            } else null,
            registerInterfaceIpWithDns = if (obj.has("register_interface_ip_with_dns") && !obj.get("register_interface_ip_with_dns").isJsonNull) obj.get("register_interface_ip_with_dns").asBoolean else null,
            sccmVpnBoundarySupport = if (obj.has("sccm_vpn_boundary_support") && !obj.get("sccm_vpn_boundary_support").isJsonNull) obj.get("sccm_vpn_boundary_support").asBoolean else null,
            netbtEnabled = if (obj.has("enable_netbt") && !obj.get("enable_netbt").isJsonNull) obj.get("enable_netbt").asBoolean else null
        )
    }
}
