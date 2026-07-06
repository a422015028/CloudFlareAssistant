package com.muort.upworker.core.model

import com.google.gson.*
import java.lang.reflect.Type

class AccessRuleAdapter : JsonSerializer<AccessRule>, JsonDeserializer<AccessRule> {

    override fun serialize(
        src: AccessRule,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val jsonObject = JsonObject()
        
        src.email?.let { jsonObject.add("email", context?.serialize(it)) }
        src.emailDomain?.let { jsonObject.add("email_domain", context?.serialize(it)) }
        src.everyone?.let { jsonObject.add("everyone", context?.serialize(it)) }
        src.ip?.let { jsonObject.add("ip", context?.serialize(it)) }
        src.ipList?.let { jsonObject.add("ip_list", context?.serialize(it)) }
        src.certificate?.let { jsonObject.add("certificate", context?.serialize(it)) }
        src.accessGroup?.let { jsonObject.add("access_group", context?.serialize(it)) }
        src.azureGroup?.let { jsonObject.add("azure_group", context?.serialize(it)) }
        src.githubOrganization?.let { jsonObject.add("github_organization", context?.serialize(it)) }
        src.geo?.let { jsonObject.add("geo", context?.serialize(it)) }
        src.commonName?.let { jsonObject.add("common_name", context?.serialize(it)) }
        src.serviceToken?.let { jsonObject.add("service_token", context?.serialize(it)) }
        src.anyValidServiceToken?.let { jsonObject.add("any_valid_service_token", context?.serialize(it)) }
        src.devicePosture?.let { jsonObject.add("device_posture", context?.serialize(it)) }
        src.authMethod?.let { jsonObject.add("auth_method", context?.serialize(it)) }
        
        return jsonObject
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): AccessRule {
        if (json == null || !json.isJsonObject) {
            return AccessRule()
        }
        
        val obj = json.asJsonObject
        
        return AccessRule(
            email = if (obj.has("email") && !obj.get("email").isJsonNull) 
                context?.deserialize(obj.get("email"), Map::class.java) as? Map<String, String> else null,
            emailDomain = if (obj.has("email_domain") && !obj.get("email_domain").isJsonNull) 
                context?.deserialize(obj.get("email_domain"), Map::class.java) as? Map<String, String> else null,
            everyone = if (obj.has("everyone") && !obj.get("everyone").isJsonNull) 
                context?.deserialize(obj.get("everyone"), Map::class.java) as? Map<String, Any> else null,
            ip = if (obj.has("ip") && !obj.get("ip").isJsonNull) 
                context?.deserialize(obj.get("ip"), Map::class.java) as? Map<String, String> else null,
            ipList = if (obj.has("ip_list") && !obj.get("ip_list").isJsonNull) 
                context?.deserialize(obj.get("ip_list"), Map::class.java) as? Map<String, String> else null,
            certificate = if (obj.has("certificate") && !obj.get("certificate").isJsonNull) 
                context?.deserialize(obj.get("certificate"), Map::class.java) as? Map<String, Any> else null,
            accessGroup = if (obj.has("access_group") && !obj.get("access_group").isJsonNull) 
                context?.deserialize(obj.get("access_group"), Map::class.java) as? Map<String, String> else null,
            azureGroup = if (obj.has("azure_group") && !obj.get("azure_group").isJsonNull) 
                context?.deserialize(obj.get("azure_group"), Map::class.java) as? Map<String, String> else null,
            githubOrganization = if (obj.has("github_organization") && !obj.get("github_organization").isJsonNull) 
                context?.deserialize(obj.get("github_organization"), Map::class.java) as? Map<String, String> else null,
            geo = if (obj.has("geo") && !obj.get("geo").isJsonNull) 
                context?.deserialize(obj.get("geo"), Map::class.java) as? Map<String, List<String>> else null,
            commonName = if (obj.has("common_name") && !obj.get("common_name").isJsonNull) 
                context?.deserialize(obj.get("common_name"), Map::class.java) as? Map<String, String> else null,
            serviceToken = if (obj.has("service_token") && !obj.get("service_token").isJsonNull) 
                context?.deserialize(obj.get("service_token"), Map::class.java) as? Map<String, String> else null,
            anyValidServiceToken = if (obj.has("any_valid_service_token") && !obj.get("any_valid_service_token").isJsonNull) 
                context?.deserialize(obj.get("any_valid_service_token"), Map::class.java) as? Map<String, Any> else null,
            devicePosture = if (obj.has("device_posture") && !obj.get("device_posture").isJsonNull) 
                context?.deserialize(obj.get("device_posture"), Map::class.java) as? Map<String, String> else null,
            authMethod = if (obj.has("auth_method") && !obj.get("auth_method").isJsonNull) 
                context?.deserialize(obj.get("auth_method"), Map::class.java) as? Map<String, String> else null
        )
    }
}