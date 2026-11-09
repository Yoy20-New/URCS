package com.robotics.urcs.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEngineTest {
    private val rule = AutomationRule("near-stop", "distance", Comparison.BELOW, 15.0, AutomationAction.STOP_ROBOT, true)

    @Test
    fun ruleFiresOnlyWhenEnabledAndThresholdMatches() {
        assertEquals(RuleDecision.FIRE, RuleEngine.evaluate(rule, mapOf("distance" to 12.0), hardwareConnected = true))
        assertEquals(RuleDecision.NO_MATCH, RuleEngine.evaluate(rule, mapOf("distance" to 20.0), hardwareConnected = true))
        assertEquals(RuleDecision.NO_MATCH, RuleEngine.evaluate(rule.copy(enabled = false), mapOf("distance" to 12.0), hardwareConnected = true))
    }

    @Test
    fun absentSensorAndDisconnectedHardwareCannotFire() {
        assertEquals(RuleDecision.MISSING_SENSOR, RuleEngine.evaluate(rule, emptyMap(), hardwareConnected = true))
        assertEquals(RuleDecision.HARDWARE_DISCONNECTED, RuleEngine.evaluate(rule, mapOf("distance" to 12.0), hardwareConnected = false))
    }

    @Test
    fun invalidRulesAreRejected() {
        assertEquals(RuleValidation.Invalid("Sensor is required"), RuleEngine.validate(rule.copy(sensorId = " ")))
        assertEquals(RuleValidation.Invalid("Threshold must be finite"), RuleEngine.validate(rule.copy(threshold = Double.NaN)))
    }

    @Test
    fun nonFiniteSensorValuesNeverTriggerRules() {
        assertEquals(RuleDecision.NO_MATCH, RuleEngine.evaluate(rule, mapOf("distance" to Double.NaN), hardwareConnected = true))
    }
}
