package com.robotics.urcs.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraTorchPolicyTest {
    @Test
    fun torchChangeStartsOnlyWhenFlashIsAvailableAndIdle() {
        assertNull(beginTorchChange(TorchControlState(isOn = false), flashAvailable = false))
        assertNull(beginTorchChange(TorchControlState(isOn = false, isPending = true), flashAvailable = true))
        assertEquals(TorchControlState(isOn = false, isPending = true), beginTorchChange(TorchControlState(), true))
    }

    @Test
    fun failedTorchChangeKeepsLastKnownStateAndClearsPending() {
        val pending = TorchControlState(isOn = false, isPending = true)
        assertEquals(TorchControlState(isOn = false, isPending = false), finishTorchChange(pending, requestedOn = true, succeeded = false))
    }

    @Test
    fun successfulTorchChangeUpdatesStateOnlyAfterCompletion() {
        val pending = TorchControlState(isOn = false, isPending = true)
        assertEquals(TorchControlState(isOn = true, isPending = false), finishTorchChange(pending, requestedOn = true, succeeded = true))
    }
}
