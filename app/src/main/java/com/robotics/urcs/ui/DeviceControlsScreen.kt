package com.robotics.urcs.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.robotics.urcs.device.AndroidPhoneStatusRepository
import com.robotics.urcs.device.PhoneLocationReading
import com.robotics.urcs.device.batterySummary
import com.robotics.urcs.device.networkSummary
import com.robotics.urcs.settings.AppSettings
import com.robotics.urcs.settings.AppSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun DeviceControlsScreen(repository: AndroidPhoneStatusRepository, settings: AppSettings, settingsRepository: AppSettingsRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    var battery by remember { mutableStateOf("Reading battery…") }
    var network by remember { mutableStateOf("Checking connection…") }
    var clock by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<PhoneLocationReading?>(null) }
    var locationMessage by remember { mutableStateOf("Location is off until you request a fix.") }
    var tracking by remember { mutableStateOf(false) }
    var fineAllowed by remember { mutableStateOf(repository.hasLocationPermission()) }
    var brightness by remember(settings.appBrightness) { mutableStateOf(settings.appBrightness) }
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        fineAllowed = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineAllowed) {
            tracking = true
            locationMessage = "Waiting for a phone location fix…"
        } else {
            tracking = false
            locationMessage = "Location permission was denied. Coordinates are unavailable."
        }
    }

    LaunchedEffect(repository) {
        while (true) {
            val reading = repository.readBattery()
            battery = batterySummary(reading.levelPercent, reading.charging)
            val connection = repository.readNetwork()
            network = networkSummary(connection.transport, connection.hasInternet)
            delay(5_000)
        }
    }
    LaunchedEffect(settings.timeFormat24Hours) {
        while (true) {
            clock = formatClock(Instant.now(), ZoneId.systemDefault(), settings.timeFormat24Hours)
            delay(1_000)
        }
    }
    LaunchedEffect(settings.gpsEnabled) {
        if (!settings.gpsEnabled) {
            tracking = false
            locationMessage = "GPS controls are disabled in Settings."
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        fineAllowed = repository.hasLocationPermission()
    }
    DisposableEffect(tracking, fineAllowed, settings.gpsEnabled, settings.gpsUpdateIntervalSeconds) {
        val listener = if (tracking && fineAllowed && settings.gpsEnabled) runCatching {
            repository.startLocationUpdates(settings.gpsUpdateIntervalSeconds * 1_000L) { fix ->
                location = fix
                locationMessage = "Phone location updated"
            }
        }.onFailure { error ->
            tracking = false
            locationMessage = error.message ?: "Location is unavailable"
        }.getOrNull() else null
        onDispose { listener?.let(repository::stopLocationUpdates) }
    }
    DisposableEffect(activity, settings.appBrightness, settings.useSystemBrightness, settings.keepScreenAwake) {
        val window = activity?.window
        val originalFlags = window?.attributes?.flags ?: 0
        window?.let {
            val params = it.attributes
            params.screenBrightness = if (settings.useSystemBrightness) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.appBrightness
            it.attributes = params
            if (settings.keepScreenAwake) it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let { current ->
                current.attributes = current.attributes.apply { screenBrightness = originalBrightness }
                if (originalFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) current.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else current.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    fun setWindowBrightness(value: Float?) {
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = value ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
        if (value != null) {
            brightness = value
            scope.launch {
                settingsRepository.setUseSystemBrightness(false)
                settingsRepository.setAppBrightness(value)
            }
        } else {
            scope.launch { settingsRepository.setUseSystemBrightness(true) }
        }
    }

    fun openSettings(action: String, failure: String) {
        runCatching { context.startActivity(Intent(action)) }
            .onFailure { locationMessage = failure }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Phone & device controls", "Live phone status and Android-owned settings")
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phone status", style = MaterialTheme.typography.titleMedium)
                LabeledValue("Battery", battery)
                LabeledValue("Network", network)
                LabeledValue("Local time", clock)
                LabeledValue("Time zone", ZoneId.systemDefault().id)
            }
        }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phone GPS coordinates", style = MaterialTheme.typography.titleMedium)
                LabeledValue("Coordinates", location?.let { formatCoordinatesFor(it.latitude, it.longitude, settings.coordinateFormat) } ?: "No location fix")
                LabeledValue("Accuracy", location?.let { "±${"%.1f".format(java.util.Locale.US, it.accuracyMeters)} m" } ?: "Unavailable")
                LabeledValue("Fix time", location?.let { formatClock(Instant.ofEpochMilli(it.timestampMillis), ZoneId.systemDefault(), settings.timeFormat24Hours) } ?: "Unavailable")
                Text(locationMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (repository.hasLocationPermission()) {
                            if (repository.isLocationEnabled()) {
                                tracking = !tracking
                                locationMessage = if (tracking) "Waiting for a phone location fix…" else "Location updates stopped"
                            } else {
                                locationMessage = "Turn on Location in Android Settings to get coordinates."
                            }
                        } else {
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        }
                    }, enabled = settings.gpsEnabled || tracking) { Text(if (tracking) "Stop location" else "Get coordinates") }
                    OutlinedButton(onClick = { openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Could not open Location Settings") }) { Text("Location settings") }
                }
                val formatted = location?.let { formatCoordinatesFor(it.latitude, it.longitude, settings.coordinateFormat) }
                if (formatted != null) OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Phone GPS coordinates", formatted))
                    locationMessage = "Phone coordinates copied"
                }) { Text("Copy coordinates") }
            }
        }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("App display brightness", style = MaterialTheme.typography.titleMedium)
                Text("This slider affects the URCS screen only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = brightness, onValueChange = { setWindowBrightness(it) }, valueRange = 0.1f..1f, enabled = !settings.useSystemBrightness)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { setWindowBrightness(null) }) { Text("Use system brightness") }
                    OutlinedButton(onClick = { openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Could not open Display Settings") }) { Text("Display settings") }
                }
                if (settings.useSystemBrightness) Text("Using the system brightness setting.", style = MaterialTheme.typography.bodySmall)
            }
        }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Android settings", style = MaterialTheme.typography.titleMedium)
                Text("URCS can report the active network, but Android controls mobile data and system time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openSettings(Settings.ACTION_WIRELESS_SETTINGS, "Could not open Network Settings") }) { Text("Mobile network settings") }
                    OutlinedButton(onClick = { openSettings(Settings.ACTION_DATE_SETTINGS, "Could not open Date & time Settings") }) { Text("Date & time settings") }
                }
            }
        }
        Text("Phone coordinates are not robot coordinates. Mobile-data and system-clock changes must be made in Android Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatClock(instant: Instant, zone: ZoneId, is24Hour: Boolean): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm:ss" else "h:mm:ss a", Locale.getDefault())
        .format(instant.atZone(zone))

private fun formatCoordinatesFor(latitude: Double, longitude: Double, format: String): String? =
    if (format == "DMS") "${toDms(latitude, true)}  ${toDms(longitude, false)}"
    else com.robotics.urcs.device.formatCoordinates(latitude, longitude)

private fun toDms(value: Double, latitude: Boolean): String {
    val absolute = abs(value)
    val degrees = floor(absolute).toInt()
    val minuteValue = (absolute - degrees) * 60.0
    val minutes = floor(minuteValue).toInt()
    val seconds = (minuteValue - minutes) * 60.0
    val hemisphere = if (latitude) if (value < 0) "S" else "N" else if (value < 0) "W" else "E"
    return "$degrees° ${minutes}′ ${"%.1f".format(Locale.US, seconds)}″ $hemisphere"
}
