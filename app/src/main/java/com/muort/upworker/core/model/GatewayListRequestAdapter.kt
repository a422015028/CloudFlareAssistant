package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class GatewayListRequestAdapter : JsonSerializer<GatewayListRequest>, JsonDeserializer<GatewayListRequest> {

    override fun serialize(
        src: GatewayListRequest,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        jsonObject.addProperty("name", src.name)
        jsonObject.addProperty("type", src.type)
        src.description?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("description", it) }
        src.items.takeIf { it.isNotEmpty() }?.let { jsonObject.add("items", context?.serialize(it)) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): GatewayListRequest {
        if (json == null || !json.isJsonObject) {
            return GatewayListRequest(name = "", type = "DOMAIN", items = emptyList())
        }
        
        val obj = json.asJsonObject
        
        return GatewayListRequest(
            name = obj.get("name")?.asString ?: "",
            description = if (obj.has("description") && !obj.get("description").isJsonNull) obj.get("description").asString else null,
            type = obj.get("type")?.asString ?: "DOMAIN",
            items = (if (obj.has("items") && !obj.get("items").isJsonNull) 
                context?.deserialize(obj.get("items"), List::class.java) as? List<GatewayListItem> else null) ?: emptyList()
        )
    }
}