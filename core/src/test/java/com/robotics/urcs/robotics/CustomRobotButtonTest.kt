package com.robotics.urcs.robotics

import com.robotics.urcs.communication.RobotCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRobotButtonTest {
    @Test
    fun encodesAndDecodesMotorAndStopButtons() {
        val buttons = listOf(
            CustomRobotButton("forward", "Forward", "{\"type\":\"motor\",\"id\":1,\"speed\":150}"),
            CustomRobotButton("halt", "Halt", "{\"type\":\"stop\"}")
        )

        assertEquals(buttons, CustomRobotButtonCodec.decode(CustomRobotButtonCodec.encode(buttons)).getOrThrow())
        assertEquals("{\"type\":\"motor\",\"id\":1,\"speed\":150}", CustomRobotButtonCodec.encodeCommand(buttons.first()))
    }

    @Test
    fun rejectsInvalidButtonDefinitionsAndMalformedStoredData() {
        assertTrue(runCatching {
            CustomRobotButtonCodec.encode(listOf(CustomRobotButton("", " ", "{\"type\":\"motor\",\"id\":1,\"speed\":300}")))
        }.isFailure)
        assertTrue(CustomRobotButtonCodec.decode("[broken]").isFailure)
    }

    @Test
    fun parsesServoCommandCorrectly() {
        val result = CustomRobotButtonCodec.parseCommand("{\"type\":\"servo\",\"id\":2,\"angle\":90}")
        assertTrue(result.isSuccess)
        assertEquals(RobotCommand.Servo(2, 90), result.getOrThrow())
    }

    @Test
    fun rejectsInvalidCommandBoundsAndTypes() {
        assertTrue(CustomRobotButtonCodec.parseCommand("{\"type\":\"motor\",\"id\":1,\"speed\":300}").isFailure)
        assertTrue(CustomRobotButtonCodec.parseCommand("{\"type\":\"servo\",\"id\":1,\"angle\":200}").isFailure)
        assertTrue(CustomRobotButtonCodec.parseCommand("{\"type\":\"unknown\"}").isFailure)
        assertTrue(CustomRobotButtonCodec.parseCommand("{\"type\":\"motor\",\"id\":\"abc\",\"speed\":100}").isFailure)
    }
}
