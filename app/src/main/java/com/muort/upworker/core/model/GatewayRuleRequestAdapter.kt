package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class GatewayRuleRequestAdapter : JsonSerializer<GatewayRuleRequest>, JsonDeserializer<GatewayRuleRequest> {

    override fun serialize(
        src: GatewayRuleRequest,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        jsonObject.addProperty("name", src.name)
        jsonObject.addProperty("action", src.action)
        
        src.enabled?.let { jsonObject.addProperty("enabled", it) }
        src.description?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("description", it) }
        src.filters.takeIf { it.isNotEmpty() }?.let { jsonObject.add("filters", context?.serialize(it)) }
        src.traffic?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("traffic", it) }
        src.identity?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("identity", it) }
        src.devicePosture?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("device_posture", it) }
        src.precedence?.let { jsonObject.addProperty("precedence", it) }
        src.ruleSettings?.let { jsonObject.add("rule_settings", context?.serialize(it)) }
        src.expiration?.let { jsonObject.add("expiration", context?.serialize(it)) }
        src.schedule?.let { jsonObject.add("schedule", context?.serialize(it)) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): GatewayRuleRequest {
        if (json == null || !json.isJsonObject) {
            return GatewayRuleRequest(name = "", action = "", filters = emptyList())
        }
        
        val obj = json.asJsonObject
        
        return GatewayRuleRequest(
            name = obj.get("name")?.asString ?: "",
            description = if (obj.has("description") && !obj.get("description").isJsonNull) obj.get("description").asString else null,
            action = obj.get("action")?.asString ?: "",
            enabled = if (obj.has("enabled") && !obj.get("enabled").isJsonNull) obj.get("enabled").asBoolean else null,
            filters = (if (obj.has("filters") && !obj.get("filters").isJsonNull) 
                context?.deserialize(obj.get("filters"), List::class.java) as? List<String> else null) ?: emptyList(),
            traffic = if (obj.has("traffic") && !obj.get("traffic").isJsonNull) obj.get("traffic").asString else null,
            identity = if (obj.has("identity") && !obj.get("identity").isJsonNull) obj.get("identity").asString else null,
            devicePosture = if (obj.has("device_posture") && !obj.get("device_posture").isJsonNull) obj.get("device_posture").asString else null,
            precedence = if (obj.has("precedence") && !obj.get("precedence").isJsonNull) obj.get("precedence").asInt else null,
            ruleSettings = if (obj.has("rule_settings") && !obj.get("rule_settings").isJsonNull) 
                context?.deserialize(obj.get("rule_settings"), GatewayRuleSettings::class.java) else null,
            expiration = if (obj.has("expiration") && !obj.get("expiration").isJsonNull) 
                context?.deserialize(obj.get("expiration"), GatewayRuleExpiration::class.java) else null,
            schedule = if (obj.has("schedule") && !obj.get("schedule").isJsonNull) 
                context?.deserialize(obj.get("schedule"), GatewayRuleSchedule::class.java) else null
        )
    }
}