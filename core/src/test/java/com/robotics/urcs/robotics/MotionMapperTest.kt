package com.robotics.urcs.robotics

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionMapperTest {
    @Test
    fun valuesInsideDeadZoneProduceNeutralMotion() {
        assertEquals(MotionVector(0f, 0f), MotionMapper.map(x = 0.03f, y = -0.08f, deadZone = 0.1f))
    }

    @Test
    fun joystickAxesMapToClampedForwardAndTurn() {
        assertEquals(MotionVector(1f, -1f), MotionMapper.map(x = -1.4f, y = -1.2f, deadZone = 0f))
        assertEquals(MotionVector(0.8f, 0.6f), MotionMapper.map(x = 0.6f, y = -0.8f, deadZone = 0f))
    }

    @Test
    fun inversionAndSensitivityApplyAfterDeadZone() {
        assertEquals(
            MotionVector(0.4f, -0.2f),
            MotionMapper.map(x = 0.4f, y = 0.8f, deadZone = 0.1f, sensitivity = 0.5f, invertX = true, invertY = true)
        )
    }

    @Test
    fun nonFiniteJoystickInputCannotBecomeMovement() {
        assertEquals(MotionVector(0f, 0f), MotionMapper.map(x = Float.NaN, y = Float.POSITIVE_INFINITY, deadZone = 0.1f))
    }
}
