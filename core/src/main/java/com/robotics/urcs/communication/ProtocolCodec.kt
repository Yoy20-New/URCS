package com.robotics.urcs.communication

import com.google.gson.JsonObject
import com.google.gson.JsonParser

sealed interface RobotCommand {
    data class Motor(val id: Int, val speed: Int) : RobotCommand
    data class Servo(val id: Int, val angle: Int) : RobotCommand
    data object Stop : RobotCommand
}

sealed interface RobotMessage {
    data class Telemetry(val sensorId: String, val value: Double) : RobotMessage
    data class Acknowledgement(val message: String?) : RobotMessage
}

sealed interface DecodeResult {
    data class Success(val message: RobotMessage) : DecodeResult
    data class Error(val reason: String) : DecodeResult
}

object ProtocolCodec {
    fun encode(command: RobotCommand): String {
        val json = JsonObject()
        when (command) {
            is RobotCommand.Motor -> {
                require(command.id > 0) { "Motor id must be positive" }
                require(command.speed in -255..255) { "Motor speed must be between -255 and 255" }
                json.addProperty("type", "motor")
                json.addProperty("id", command.id)
                json.addProperty("speed", command.speed)
            }
            is RobotCommand.Servo -> {
                require(command.id > 0) { "Servo id must be positive" }
                require(command.angle in 0..180) { "Servo angle must be between 0 and 180" }
                json.addProperty("type", "servo")
                json.addProperty("id", command.id)
                json.addProperty("angle", command.angle)
            }
            RobotCommand.Stop -> json.addProperty("type", "stop")
        }
        return json.toString()
    }

    fun decode(json: String): DecodeResult {
        return try {
            val root = JsonParser.parseString(json)
            if (!root.isJsonObject) return DecodeResult.Error("Message must be a JSON object")
            val obj = root.asJsonObject
            when (obj.string("type")) {
                "telemetry" -> {
                    val sensor = obj.string("sensor")?.trim().orEmpty()
                    val value = obj.number("value")
                    if (sensor.isBlank() || value == null || !value.isFinite()) {
                        DecodeResult.Error("Telemetry requires a sensor id and finite value")
                    } else DecodeResult.Success(RobotMessage.Telemetry(sensor, value))
                }
                "ack" -> DecodeResult.Success(RobotMessage.Acknowledgement(obj.string("message")))
                else -> DecodeResult.Error("Unsupported or missing message type")
            }
        } catch (_: Exception) {
            DecodeResult.Error("Invalid JSON message")
        }
    }

    fun decodeCommand(json: String): Result<RobotCommand> = runCatching {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Command must be a JSON object" }
        val obj = root.asJsonObject
        when (obj.string("type")) {
            "motor" -> {
                val id = obj.number("id")?.toInt() ?: throw IllegalArgumentException("Missing motor id")
                val speed = obj.number("speed")?.toInt() ?: throw IllegalArgumentException("Missing motor speed")
                require(id > 0) { "Motor id must be positive" }
                require(speed in -255..255) { "Motor speed must be between -255 and 255" }
                RobotCommand.Motor(id, speed)
            }
            "servo" -> {
                val id = obj.number("id")?.toInt() ?: throw IllegalArgumentException("Missing servo id")
                val angle = obj.number("angle")?.toInt() ?: throw IllegalArgumentException("Missing servo angle")
                require(id > 0) { "Servo id must be positive" }
                require(angle in 0..180) { "Servo angle must be between 0 and 180" }
                RobotCommand.Servo(id, angle)
            }
            "stop" -> RobotCommand.Stop
            else -> throw IllegalArgumentException("Unknown command type: ${obj.string("type")}")
        }
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.number(key: String): Double? = runCatching {
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble
    }.getOrNull()
}
