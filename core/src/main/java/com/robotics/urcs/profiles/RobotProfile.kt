package com.robotics.urcs.profiles

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class RobotProfile(
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String
)

sealed interface ProfileDecodeResult {
    data class Success(val profile: RobotProfile) : ProfileDecodeResult
    data class Error(val reason: String) : ProfileDecodeResult
}

object ProfileCodec {
    fun encode(profile: RobotProfile): String {
        require(profile.name.isNotBlank()) { "Profile name is required" }
        require(profile.port in 1..65535) { "Port must be between 1 and 65535" }
        require(profile.protocol in SUPPORTED_PROTOCOLS) { "Unsupported profile protocol" }
        require(profile.protocol != "TCP" || profile.host.isNotBlank()) { "TCP profiles require a host" }
        return JsonObject().apply {
            addProperty("name", profile.name.trim())
            addProperty("host", profile.host.trim())
            addProperty("port", profile.port)
            addProperty("protocol", profile.protocol)
        }.toString()
    }

    fun decode(json: String): ProfileDecodeResult {
        return try {
            val parsed = JsonParser.parseString(json)
            if (!parsed.isJsonObject) return ProfileDecodeResult.Error("Profile must be a JSON object")
            val obj = parsed.asJsonObject
            val name = obj.string("name")?.trim().orEmpty()
            val host = obj.string("host")?.trim().orEmpty()
            val protocol = obj.string("protocol")?.trim().orEmpty()
            val port = obj.int("port")
            when {
                name.isBlank() -> ProfileDecodeResult.Error("Profile name is required")
                port == null || port !in 1..65535 -> ProfileDecodeResult.Error("Port must be between 1 and 65535")
                protocol !in SUPPORTED_PROTOCOLS -> ProfileDecodeResult.Error("Unsupported profile protocol")
                protocol == "TCP" && host.isBlank() -> ProfileDecodeResult.Error("TCP profiles require a host")
                else -> ProfileDecodeResult.Success(RobotProfile(name, host, port, protocol))
            }
        } catch (_: Exception) {
            ProfileDecodeResult.Error("Invalid profile JSON")
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.int(key: String): Int? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
    }.getOrNull()

    private val SUPPORTED_PROTOCOLS = setOf("TCP", "BLE", "USB")
}
