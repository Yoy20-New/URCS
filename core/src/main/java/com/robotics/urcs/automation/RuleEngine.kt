package com.robotics.urcs.automation

enum class Comparison { BELOW, ABOVE }
enum class AutomationAction { STOP_ROBOT }

data class AutomationRule(
    val id: String,
    val sensorId: String,
    val comparison: Comparison,
    val threshold: Double,
    val action: AutomationAction,
    val enabled: Boolean
)

sealed interface RuleValidation {
    data object Valid : RuleValidation
    data class Invalid(val reason: String) : RuleValidation
}

enum class RuleDecision { FIRE, NO_MATCH, MISSING_SENSOR, HARDWARE_DISCONNECTED, INVALID_RULE }

object RuleEngine {
    fun validate(rule: AutomationRule): RuleValidation = when {
        rule.id.isBlank() -> RuleValidation.Invalid("Rule id is required")
        rule.sensorId.isBlank() -> RuleValidation.Invalid("Sensor is required")
        !rule.threshold.isFinite() -> RuleValidation.Invalid("Threshold must be finite")
        else -> RuleValidation.Valid
    }

    fun evaluate(rule: AutomationRule, sensorValues: Map<String, Double>, hardwareConnected: Boolean): RuleDecision {
        if (!rule.enabled) return RuleDecision.NO_MATCH
        if (validate(rule) !is RuleValidation.Valid) return RuleDecision.INVALID_RULE
        if (!hardwareConnected) return RuleDecision.HARDWARE_DISCONNECTED
        val value = sensorValues[rule.sensorId] ?: return RuleDecision.MISSING_SENSOR
        if (!value.isFinite()) return RuleDecision.NO_MATCH
        val matches = when (rule.comparison) {
            Comparison.BELOW -> value < rule.threshold
            Comparison.ABOVE -> value > rule.threshold
        }
        return if (matches) RuleDecision.FIRE else RuleDecision.NO_MATCH
    }
}
