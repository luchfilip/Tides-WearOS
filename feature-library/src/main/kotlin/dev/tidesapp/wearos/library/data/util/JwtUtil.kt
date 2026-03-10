package dev.tidesapp.wearos.library.data.util

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object JwtUtil {
    fun getUserId(token: String): String? {
        return try {
            val parts = token.removePrefix("Bearer ").split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            val json = Json.parseToJsonElement(payload).jsonObject
            json["uid"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    fun getCountryCode(token: String): String {
        return try {
            val parts = token.removePrefix("Bearer ").split(".")
            if (parts.size < 2) return "US"
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            val json = Json.parseToJsonElement(payload).jsonObject
            json["cc"]?.jsonPrimitive?.content ?: "US"
        } catch (e: Exception) {
            "US"
        }
    }
}
