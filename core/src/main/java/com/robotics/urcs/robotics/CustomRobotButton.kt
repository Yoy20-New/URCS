package com.robotics.urcs.robotics

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.robotics.urcs.communication.ProtocolCodec
import com.robotics.urcs.communication.RobotCommand

data class CustomRobotButton(
    val id: String,
    val label: String,
    val commandJson: String
)

object CustomRobotButtonCodec {
    private const val MAX_BUTTONS = 8
    private const val MAX_LABEL_LENGTH = 20

    fun encode(buttons: List<CustomRobotButton>): String {
        require(buttons.size <= MAX_BUTTONS) { "At most $MAX_BUTTONS custom buttons are allowed" }
        require(buttons.map { it.id }.distinct().size == buttons.size) { "Custom button ids must be unique" }
        val array = JsonArray()
        buttons.forEach { button ->
            validateButton(button)
            array.add(JsonObject().apply {
                addProperty("id", button.id.trim())
                addProperty("label", button.label.trim())
                add("command", JsonParser.parseString(encodeCommand(button)))
            })
        }
        return array.toString()
    }

    fun decode(json: String): Result<List<CustomRobotButton>> = runCatching {
        val parsed = JsonParser.parseString(json)
        require(parsed.isJsonArray) { "Custom buttons must be a JSON array" }
        require(parsed.asJsonArray.size() <= MAX_BUTTONS) { "At most $MAX_BUTTONS custom buttons are allowed" }
        val buttons = parsed.asJsonArray.map { item ->
            require(item.isJsonObject) { "Each custom button must be an object" }
            val obj = item.asJsonObject
            val id = obj.string("id") ?: error("Custom button id is required")
            val label = obj.string("label") ?: error("Custom button label is required")
            val command = obj.get("command")?.takeIf { it.isJsonObject }?.toString()
                ?: error("Custom button command must be an object")
            CustomRobotButton(id, label, command).also(::validateButton)
        }
        require(buttons.map { it.id }.distinct().size == buttons.size) { "Custom button ids must be unique" }
        buttons
    }

    fun encodeCommand(button: CustomRobotButton): String {
        val command = parseCommand(button.commandJson).getOrThrow()
        return ProtocolCodec.encode(command)
    }

    fun parseCommand(json: String): Result<RobotCommand> = runCatching {
        val parsed = JsonParser.parseString(json)
        require(parsed.isJsonObject) { "Command must be a JSON object" }
        val obj = parsed.asJsonObject
        val cmd = when (obj.string("type")) {
            "motor" -> RobotCommand.Motor(
                obj.requiredInt("id"),
                obj.requiredInt("speed")
            )
            "servo" -> RobotCommand.Servo(
                obj.requiredInt("id"),
                obj.requiredInt("angle")
            )
            "stop" -> RobotCommand.Stop
            else -> error("Only motor, servo, and stop commands are allowed")
        }
        ProtocolCodec.encode(cmd)
        cmd
    }

    private fun validateButton(button: CustomRobotButton) {
        require(button.id.isNotBlank() && button.id.length <= 64) { "Custom button id is invalid" }
        require(button.label.isNotBlank() && button.label.length <= MAX_LABEL_LENGTH) {
            "Button label must contain 1 to $MAX_LABEL_LENGTH characters"
        }
        parseCommand(button.commandJson).getOrThrow()
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.requiredInt(key: String): Int {
        val primitive = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?: error("Command field '$key' must be an integer")
        return runCatching { primitive.asInt }.getOrElse {
            primitive.asString.toIntOrNull() ?: error("Command field '$key' must be an integer")
        }
    }
}
