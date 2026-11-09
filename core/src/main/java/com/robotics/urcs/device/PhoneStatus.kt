package com.robotics.urcs.device

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class NetworkTransport { NONE, WIFI, CELLULAR, ETHERNET, OTHER }

fun networkSummary(transport: NetworkTransport, hasInternet: Boolean): String {
    if (transport == NetworkTransport.NONE) return "Offline"
    val label = when (transport) {
        NetworkTransport.WIFI -> "Wi-Fi"
        NetworkTransport.CELLULAR -> "Mobile network · radio generation unavailable"
        NetworkTransport.ETHERNET -> "Ethernet"
        NetworkTransport.OTHER -> "Other network"
        NetworkTransport.NONE -> "Offline"
    }
    return if (hasInternet) label else "$label · no internet"
}

fun batterySummary(levelPercent: Int?, charging: Boolean?): String {
    if (levelPercent == null || levelPercent !in 0..100) return "Battery unavailable"
    return when (charging) {
        true -> "$levelPercent% · Charging"
        false -> "$levelPercent% · On battery"
        null -> "$levelPercent% · Status unavailable"
    }
}

fun formatLocalClock(instant: Instant, zoneId: ZoneId): String {
    val local = instant.atZone(zoneId)
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.US).format(local)
    val date = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US).format(local)
    return "$clock · $date · ${zoneId.id}"
}

fun formatCoordinates(latitude: Double, longitude: Double): String? {
    if (!latitude.isFinite() || !longitude.isFinite()) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}
