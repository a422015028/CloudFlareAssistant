package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class DevicePolicyUpdateAdapter : JsonSerializer<DevicePolicyUpdate>, JsonDeserializer<DevicePolicyUpdate> {

    override fun serialize(
        src: DevicePolicyUpdate,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()

        if (src.allowModeSwitch != null) jsonObject.addProperty("allow_mode_switch", src.allowModeSwitch)
        if (src.allowUpdates != null) jsonObject.addProperty("allow_updates", src.allowUpdates)
        if (src.allowedToLeave != null) jsonObject.addProperty("allowed_to_leave", src.allowedToLeave)
        if (src.autoConnect != null) jsonObject.addProperty("auto_connect", src.autoConnect)
        if (src.captivePortal != null) jsonObject.addProperty("captive_portal", src.captivePortal)
        if (src.excludeOfficeIps != null) jsonObject.addProperty("exclude_office_ips", src.excludeOfficeIps)
        if (src.registerInterfaceIpWithDns != null) jsonObject.addProperty("register_interface_ip_with_dns", src.registerInterfaceIpWithDns)
        if (src.sccmVpnBoundarySupport != null) jsonObject.addProperty("sccm_vpn_boundary_support", src.sccmVpnBoundarySupport)
        if (src.switchLocked != null) jsonObject.addProperty("switch_locked", src.switchLocked)
        src.tunnelProtocol?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("tunnel_protocol", it) }
        if (src.lanAllowMinutes != null) {
            if (src.lanAllowMinutes == 0) {
                jsonObject.add("lan_allow_minutes", JsonNull.INSTANCE)
            } else {
                jsonObject.addProperty("lan_allow_minutes", src.lanAllowMinutes)
            }
        }
        if (src.netbtEnabled != null) jsonObject.addProperty("enable_netbt", src.netbtEnabled)

        if (src.serviceModeV2 != null && src.serviceModeV2.mode != null) {
            val serviceModeObj = JsonObject()
            serviceModeObj.addProperty("mode", src.serviceModeV2.mode)
            jsonObject.add("service_mode_v2", serviceModeObj)
        }

        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DevicePolicyUpdate {
        if (json == null || !json.isJsonObject) {
            return DevicePolicyUpdate()
        }

        val obj = json.asJsonObject

        return DevicePolicyUpdate(
            allowModeSwitch = if (obj.has("allow_mode_switch") && !obj.get("allow_mode_switch").isJsonNull) obj.get("allow_mode_switch").asBoolean else null,
            allowUpdates = if (obj.has("allow_updates") && !obj.get("allow_updates").isJsonNull) obj.get("allow_updates").asBoolean else null,
            allowedToLeave = if (obj.has("allowed_to_leave") && !obj.get("allowed_to_leave").isJsonNull) obj.get("allowed_to_leave").asBoolean else null,
            autoConnect = if (obj.has("auto_connect") && !obj.get("auto_connect").isJsonNull) obj.get("auto_connect").asInt else null,
            captivePortal = if (obj.has("captive_portal") && !obj.get("captive_portal").isJsonNull) obj.get("captive_portal").asInt else null,
            excludeOfficeIps = if (obj.has("exclude_office_ips") && !obj.get("exclude_office_ips").isJsonNull) obj.get("exclude_office_ips").asBoolean else null,
            registerInterfaceIpWithDns = if (obj.has("register_interface_ip_with_dns") && !obj.get("register_interface_ip_with_dns").isJsonNull) obj.get("register_interface_ip_with_dns").asBoolean else null,
            sccmVpnBoundarySupport = if (obj.has("sccm_vpn_boundary_support") && !obj.get("sccm_vpn_boundary_support").isJsonNull) obj.get("sccm_vpn_boundary_support").asBoolean else null,
            serviceModeV2 = if (obj.has("service_mode_v2") && !obj.get("service_mode_v2").isJsonNull) {
                val modeObj = obj.get("service_mode_v2").asJsonObject
                ServiceModeV2(mode = modeObj.get("mode")?.asString)
            } else null,
            switchLocked = if (obj.has("switch_locked") && !obj.get("switch_locked").isJsonNull) obj.get("switch_locked").asBoolean else null,
            tunnelProtocol = if (obj.has("tunnel_protocol") && !obj.get("tunnel_protocol").isJsonNull) obj.get("tunnel_protocol").asString else null,
            lanAllowMinutes = if (obj.has("lan_allow_minutes") && !obj.get("lan_allow_minutes").isJsonNull) obj.get("lan_allow_minutes").asInt else null,
            netbtEnabled = if (obj.has("enable_netbt") && !obj.get("enable_netbt").isJsonNull) obj.get("enable_netbt").asBoolean else null
        )
    }
}
