package com.robotics.urcs.robotics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ControllerCommandGateTest {
    @Test
    fun stopLatchPreventsDriveCommandsThatWereQueuedEarlier() = runBlocking {
        val gate = ControllerCommandGate()
        val sent = mutableListOf<String>()
        val leftStarted = CompletableDeferred<Unit>()
        val releaseLeft = CompletableDeferred<Unit>()

        val drive = async {
            gate.drive(
                sendLeft = { leftStarted.complete(Unit); releaseLeft.await(); sent += "left" },
                sendRight = { sent += "right" }
            )
        }
        leftStarted.await()
        val stop = async { gate.emergencyStop { sent += "stop" } }
        yield()
        releaseLeft.complete(Unit)
        drive.await()
        stop.await()

        assertEquals(listOf("left", "stop"), sent)
    }

    @Test
    fun driveAfterStopIsIgnoredUntilExplicitResume() = runBlocking {
        val gate = ControllerCommandGate()
        val sent = mutableListOf<String>()
        gate.emergencyStop { sent += "stop" }
        gate.drive(sendLeft = { sent += "left" }, sendRight = { sent += "right" })
        gate.resume()
        gate.drive(sendLeft = { sent += "left" }, sendRight = { sent += "right" })

        assertEquals(listOf("stop", "left", "right"), sent)
    }

    @Test
    fun aSingleCustomCommandIsBlockedWhileStopIsLatched() = runBlocking {
        val gate = ControllerCommandGate()
        gate.latchStop()
        var sent = false

        gate.command { sent = true }

        assertFalse(sent)
    }
}
