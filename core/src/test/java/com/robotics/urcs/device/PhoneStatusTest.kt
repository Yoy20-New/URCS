package com.robotics.urcs.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PhoneStatusTest {
    @Test
    fun reportsMobileTransportWithoutGuessingItsRadioGeneration() {
        assertEquals("Mobile network · radio generation unavailable", networkSummary(NetworkTransport.CELLULAR, true))
        assertEquals("Wi-Fi", networkSummary(NetworkTransport.WIFI, true))
        assertEquals("Offline", networkSummary(NetworkTransport.NONE, false))
    }

    @Test
    fun formatsBatteryChargeAndClockInTheSelectedTimeZone() {
        assertEquals("78% · Charging", batterySummary(78, true))
        assertEquals("23% · On battery", batterySummary(23, false))
        assertEquals("17:04 · Sun, 8 Nov 2026 · Asia/Kolkata", formatLocalClock(
            Instant.parse("2026-11-08T11:34:00Z"), ZoneId.of("Asia/Kolkata")
        ))
    }

    @Test
    fun formatsOnlyFiniteValidCoordinates() {
        assertEquals("12.34568, 76.54321", formatCoordinates(12.345678, 76.54321))
        assertNull(formatCoordinates(91.0, 0.0))
        assertNull(formatCoordinates(0.0, Double.NaN))
        assertEquals("Battery unavailable", batterySummary(null, null))
    }
}
