package com.robotics.urcs.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotProfileCodecTest {
    private val profile = RobotProfile(name = "Lab rover", host = "192.168.4.1", port = 8080, protocol = "TCP")

    @Test
    fun profileRoundTripsAsJson() {
        assertEquals(ProfileDecodeResult.Success(profile), ProfileCodec.decode(ProfileCodec.encode(profile)))
    }

    @Test
    fun importsRejectInvalidNameOrPort() {
        assertTrue(ProfileCodec.decode("{\"name\":\" \",\"host\":\"robot\",\"port\":8080,\"protocol\":\"TCP\"}") is ProfileDecodeResult.Error)
        assertTrue(ProfileCodec.decode("{\"name\":\"Rover\",\"host\":\"robot\",\"port\":70000,\"protocol\":\"TCP\"}") is ProfileDecodeResult.Error)
    }

    @Test
    fun tcpProfileRequiresHostOnEncode() {
        assertTrue(runCatching {
            ProfileCodec.encode(RobotProfile(name = "Rover", host = "   ", port = 8080, protocol = "TCP"))
        }.isFailure)
    }
}
