package com.robotics.urcs.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBindingPolicyTest {
    @Test
    fun fallsBackToFrontWhenOnlyFrontCameraExists() {
        assertEquals(CameraFacing.FRONT, selectCameraFacing(CameraFacing.BACK, hasFront = true, hasBack = false))
    }

    @Test
    fun honorsRequestedLensAndFallsBackWhenThatLensIsMissing() {
        assertEquals(CameraFacing.BACK, selectCameraFacing(CameraFacing.FRONT, hasFront = false, hasBack = true))
        assertEquals(CameraFacing.FRONT, selectCameraFacing(CameraFacing.FRONT, hasFront = true, hasBack = true))
    }

    @Test
    fun reportsNoCameraWhenNeitherLensExists() {
        assertEquals(null, selectCameraFacing(CameraFacing.BACK, hasFront = false, hasBack = false))
    }

    @Test
    fun ignoresDisposedOrStaleProviderCallbacks() {
        assertTrue(CameraBindingPolicy.shouldBind(isScreenActive = true, callbackGeneration = 4, currentGeneration = 4))
        assertFalse(CameraBindingPolicy.shouldBind(isScreenActive = false, callbackGeneration = 4, currentGeneration = 4))
        assertFalse(CameraBindingPolicy.shouldBind(isScreenActive = true, callbackGeneration = 3, currentGeneration = 4))
    }
}
