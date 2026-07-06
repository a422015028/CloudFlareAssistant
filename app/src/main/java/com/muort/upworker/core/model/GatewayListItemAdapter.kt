package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class GatewayListItemAdapter : JsonSerializer<GatewayListItem>, JsonDeserializer<GatewayListItem> {

    override fun serialize(
        src: GatewayListItem,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        jsonObject.addProperty("value", src.value)
        src.description?.takeIf { it.isNotBlank() }?.let { jsonObject.addProperty("description", it) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): GatewayListItem {
        if (json == null || !json.isJsonObject) {
            return GatewayListItem(value = "")
        }
        
        val obj = json.asJsonObject
        
        return GatewayListItem(
            value = obj.get("value")?.asString ?: "",
            description = if (obj.has("description") && !obj.get("description").isJsonNull) obj.get("description").asString else null,
            createdAt = if (obj.has("created_at") && !obj.get("created_at").isJsonNull) obj.get("created_at").asString else null
        )
    }
}