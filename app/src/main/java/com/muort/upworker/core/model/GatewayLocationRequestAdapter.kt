package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class GatewayLocationRequestAdapter : JsonSerializer<GatewayLocationRequest>, JsonDeserializer<GatewayLocationRequest> {

    override fun serialize(
        src: GatewayLocationRequest,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        jsonObject.addProperty("name", src.name)
        
        src.clientDefault?.let { jsonObject.addProperty("client_default", it) }
        src.dnsDestinationIpsId?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("dns_destination_ips_id", it) }
        src.ecsSupport?.let { jsonObject.addProperty("ecs_support", it) }
        src.networks?.takeIf { it.isNotEmpty() }?.let { jsonObject.add("networks", context?.serialize(it)) }
        src.endpoints?.let { jsonObject.add("endpoints", context?.serialize(it)) }
        src.maxTtl?.let { jsonObject.add("max_ttl", context?.serialize(it)) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): GatewayLocationRequest {
        if (json == null || !json.isJsonObject) {
            return GatewayLocationRequest(name = "")
        }
        
        val obj = json.asJsonObject
        
        return GatewayLocationRequest(
            name = obj.get("name")?.asString ?: "",
            clientDefault = if (obj.has("client_default") && !obj.get("client_default").isJsonNull) obj.get("client_default").asBoolean else null,
            dnsDestinationIpsId = if (obj.has("dns_destination_ips_id") && !obj.get("dns_destination_ips_id").isJsonNull) obj.get("dns_destination_ips_id").asString else null,
            ecsSupport = if (obj.has("ecs_support") && !obj.get("ecs_support").isJsonNull) obj.get("ecs_support").asBoolean else null,
            networks = if (obj.has("networks") && !obj.get("networks").isJsonNull) 
                context?.deserialize(obj.get("networks"), List::class.java) as? List<LocationNetwork> else null,
            endpoints = if (obj.has("endpoints") && !obj.get("endpoints").isJsonNull) 
                context?.deserialize(obj.get("endpoints"), LocationEndpoints::class.java) else null,
            maxTtl = if (obj.has("max_ttl") && !obj.get("max_ttl").isJsonNull) 
                context?.deserialize(obj.get("max_ttl"), LocationMaxTtl::class.java) else null
        )
    }
}