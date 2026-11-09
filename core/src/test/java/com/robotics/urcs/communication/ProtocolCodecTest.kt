package com.robotics.urcs.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {
    @Test
    fun motorCommandEncodesStableJsonFields() {
        val json = ProtocolCodec.encode(RobotCommand.Motor(id = 1, speed = 120))
        assertEquals("{\"type\":\"motor\",\"id\":1,\"speed\":120}", json)
    }

    @Test
    fun malformedAndOutOfRangeCommandsAreRejected() {
        assertTrue(ProtocolCodec.decode("not-json") is DecodeResult.Error)
        assertTrue(ProtocolCodec.decode("{\"type\":\"motor\",\"id\":1,\"speed\":256}") is DecodeResult.Error)
        assertTrue(ProtocolCodec.decode("{\"type\":\"unknown\"}") is DecodeResult.Error)
        assertTrue(ProtocolCodec.decode("{\"type\":\"telemetry\",\"sensor\":\"range\",\"value\":NaN}") is DecodeResult.Error)
    }

    @Test
    fun telemetryReadsRealValuesAndRejectsMissingSensorId() {
        assertEquals(
            DecodeResult.Success(RobotMessage.Telemetry("range", 12.5)),
            ProtocolCodec.decode("{\"type\":\"telemetry\",\"sensor\":\"range\",\"value\":12.5}")
        )
        assertFalse(ProtocolCodec.decode("{\"type\":\"telemetry\",\"value\":12.5}") is DecodeResult.Success)
    }

    @Test
    fun decodeCommandParsesValidCommandsAndRejectsInvalid() {
        assertEquals(RobotCommand.Motor(1, 100), ProtocolCodec.decodeCommand("{\"type\":\"motor\",\"id\":1,\"speed\":100}").getOrThrow())
        assertEquals(RobotCommand.Servo(2, 90), ProtocolCodec.decodeCommand("{\"type\":\"servo\",\"id\":2,\"angle\":90}").getOrThrow())
        assertEquals(RobotCommand.Stop, ProtocolCodec.decodeCommand("{\"type\":\"stop\"}").getOrThrow())

        assertTrue(ProtocolCodec.decodeCommand("{\"type\":\"motor\",\"id\":1,\"speed\":300}").isFailure)
        assertTrue(ProtocolCodec.decodeCommand("{\"type\":\"servo\",\"id\":1,\"angle\":200}").isFailure)
        assertTrue(ProtocolCodec.decodeCommand("{\"type\":\"unknown\"}").isFailure)
    }
}
