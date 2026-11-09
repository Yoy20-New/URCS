/*
 * URCS settings UI exploration template (reference only).
 *
 * This file is intentionally outside app/src/main: it is a visual/template proposal,
 * not wired to navigation, DataStore, Android settings, or device controls. The approved
 * single-page implementation lives in app/src/main/java/com/robotics/urcs/ui/DataScreens.kt.
 * Values here remain preview state only.
 */
package com.robotics.urcs.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SettingsTemplateSection(
    val title: String,
    val description: String,
    val previewSummary: String
) {
    SENSORS("Sensors", "Sampling and individual sensor streams", "20 available · 50 ms"),
    CAMERA("Camera", "Lens and capture preferences", "Back lens · HD video"),
    LOCATION("Location and GPS", "Foreground location and coordinate format", "While in use · decimal degrees"),
    DISPLAY("Display", "Theme and URCS-only brightness", "System theme · system brightness"),
    CONTROLS("Robot controls", "Drive limits, sensitivity, and custom buttons", "70% sensitivity · 100% speed limit"),
    CONNECTION("Connectivity", "TCP profile and phone network status", "Wi-Fi · 192.168.4.1:8080"),
    PERMISSIONS("Permissions", "Review phone permissions and Android settings", "Camera and location access")
}

enum class AndroidSettingsTarget { APP_PERMISSIONS, LOCATION, DISPLAY, NETWORK }

data class SettingsTemplateDraft(
    val availableSensors: List<String> = listOf("Accelerometer", "Gyroscope", "Magnetometer", "Orientation"),
    val enabledSensors: Set<String> = setOf("Accelerometer", "Gyroscope", "Magnetometer", "Orientation"),
    val sampleIntervalMs: Float = 50f,
    val cameraLens: String = "Back",
    val photoQuality: String = "High",
    val videoQuality: String = "HD",
    val locationIntervalSeconds: Float = 2f,
    val coordinateFormat: String = "Decimal",
    val theme: String = "System",
    val appBrightness: Float = 1f,
    val useSystemBrightness: Boolean = true,
    val controlSensitivity: Float = 0.7f,
    val maxMotorPercent: Float = 100f,
    val robotHost: String = "192.168.4.1",
    val robotPort: String = "8080",
    val activeTransport: String = "Wi-Fi",
    val cameraPermission: String = "Granted",
    val locationPermission: String = "Granted"
)

/** Template A: settings landing page with one clear entry per category. */
@Composable
fun SettingsHubTemplate(
    onOpenSection: (SettingsTemplateSection) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Configure phone features and robot controls",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
        items(SettingsTemplateSection.entries.toList()) { section ->
            Card(
                onClick = { onOpenSection(section) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                ListItem(
                    headlineContent = { Text(section.title) },
                    supportingContent = { Text(section.description) },
                    trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) }
                )
                Text(
                    section.previewSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                )
            }
        }
        item {
            Text(
                "Battery, current time, and active network are status information. Android owns mobile-data, system-time, and global-brightness controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Template B: shared detail-page shell; category controls below are draft examples only. */
@Composable
fun SettingsDetailTemplate(
    section: SettingsTemplateSection,
    state: SettingsTemplateDraft,
    onStateChange: (SettingsTemplateDraft) -> Unit,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit,
    onOpenController: () -> Unit,
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(section.title, style = MaterialTheme.typography.headlineMedium)
        Text(section.description, color = MaterialTheme.colorScheme.onSurfaceVariant)

        when (section) {
            SettingsTemplateSection.SENSORS -> SensorSettingsTemplate(state, onStateChange)
            SettingsTemplateSection.CAMERA -> CameraSettingsTemplate(state, onStateChange, onOpenAndroidSettings)
            SettingsTemplateSection.LOCATION -> LocationSettingsTemplate(state, onStateChange, onOpenAndroidSettings)
            SettingsTemplateSection.DISPLAY -> DisplaySettingsTemplate(state, onStateChange, onOpenAndroidSettings)
            SettingsTemplateSection.CONTROLS -> RobotControlsSettingsTemplate(state, onStateChange, onOpenController)
            SettingsTemplateSection.CONNECTION -> ConnectivitySettingsTemplate(state, onStateChange, onOpenAndroidSettings, onOpenConnections)
            SettingsTemplateSection.PERMISSIONS -> PermissionSettingsTemplate(state, onOpenAndroidSettings)
        }
    }
}

@Composable
private fun SensorSettingsTemplate(state: SettingsTemplateDraft, onChange: (SettingsTemplateDraft) -> Unit) {
    PreferenceCard("Sampling rate", "Choose how often URCS reads active phone sensors.") {
        Text("${state.sampleIntervalMs.toInt()} ms")
        Slider(state.sampleIntervalMs, { onChange(state.copy(sampleIntervalMs = it)) }, valueRange = 20f..500f)
    }
    PreferenceCard("Available sensors", "Only sensors reported by this phone can be enabled.") {
        state.availableSensors.forEach { name ->
            PreferenceSwitch(name, name in state.enabledSensors) { checked ->
                val next = if (checked) state.enabledSensors + name else state.enabledSensors - name
                onChange(state.copy(enabledSensors = next))
            }
        }
    }
}

@Composable
private fun CameraSettingsTemplate(
    state: SettingsTemplateDraft,
    onChange: (SettingsTemplateDraft) -> Unit,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit
) {
    PreferenceCard("Default lens", "The camera screen can still switch lenses while open.") {
        ChoiceRow(listOf("Back", "Front"), state.cameraLens) { onChange(state.copy(cameraLens = it)) }
    }
    PreferenceCard("Photo quality", "Choose a capture preset.") {
        ChoiceRow(listOf("Standard", "High"), state.photoQuality) { onChange(state.copy(photoQuality = it)) }
    }
    PreferenceCard("Video quality", "Fallback is used when this phone does not support the selected quality.") {
        ChoiceRow(listOf("SD", "HD", "Full HD"), state.videoQuality) { onChange(state.copy(videoQuality = it)) }
    }
    PreferenceCard("Media storage", "Photos and videos remain in URCS app-specific storage.") { Text("App storage") }
    OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.APP_PERMISSIONS) }) {
        Text("Camera permission · ${state.cameraPermission}")
    }
}

@Composable
private fun LocationSettingsTemplate(
    state: SettingsTemplateDraft,
    onChange: (SettingsTemplateDraft) -> Unit,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit
) {
    PreferenceCard("Location access", "Foreground only; URCS stops location updates when you leave the screen.") {
        Text("Permission: ${state.locationPermission}")
        OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.LOCATION) }) { Text("Open Android location settings") }
    }
    PreferenceCard("Update interval", "A requested interval is a hint; Android and the phone determine actual fix timing.") {
        Text("${state.locationIntervalSeconds.toInt()} seconds")
        Slider(state.locationIntervalSeconds, { onChange(state.copy(locationIntervalSeconds = it)) }, valueRange = 1f..30f)
    }
    PreferenceCard("Coordinate format", "Applies to the displayed and copied phone coordinates.") {
        ChoiceRow(listOf("Decimal", "Degrees / minutes / seconds"), state.coordinateFormat) {
            onChange(state.copy(coordinateFormat = it))
        }
    }
}

@Composable
private fun DisplaySettingsTemplate(
    state: SettingsTemplateDraft,
    onChange: (SettingsTemplateDraft) -> Unit,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit
) {
    PreferenceCard("Theme", "Choose how URCS is displayed.") {
        ChoiceRow(listOf("System", "Light", "Dark"), state.theme) { onChange(state.copy(theme = it)) }
    }
    PreferenceCard("App brightness", "This changes the URCS window only.") {
        PreferenceSwitch("Use system brightness", state.useSystemBrightness) {
            onChange(state.copy(useSystemBrightness = it))
        }
        if (!state.useSystemBrightness) {
            Slider(state.appBrightness, { onChange(state.copy(appBrightness = it)) }, valueRange = 0.1f..1f)
        }
        OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.DISPLAY) }) { Text("Open Android display settings") }
    }
}

@Composable
private fun RobotControlsSettingsTemplate(
    state: SettingsTemplateDraft,
    onChange: (SettingsTemplateDraft) -> Unit,
    onOpenController: () -> Unit
) {
    PreferenceCard("Control sensitivity", "Adjusts how quickly the on-screen controls respond.") {
        Text("${(state.controlSensitivity * 100).toInt()}%")
        Slider(state.controlSensitivity, { onChange(state.copy(controlSensitivity = it)) }, valueRange = 0.1f..1f)
    }
    PreferenceCard("Motor speed limit", "Caps the speed emitted by supported robot commands.") {
        Text("${state.maxMotorPercent.toInt()}%")
        Slider(state.maxMotorPercent, { onChange(state.copy(maxMotorPercent = it)) }, valueRange = 10f..100f)
    }
    PreferenceCard("On-screen buttons", "Button labels and validated command mappings stay in the Controller editor.") {
        Text("Open Controller to add, rename, or remove robot buttons.")
        OutlinedButton(onClick = onOpenController) { Text("Open Controller button editor") }
    }
    Text(
        "The emergency-stop command remains available and cannot be disabled here. Hardware safety still belongs on the robot controller.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun ConnectivitySettingsTemplate(
    state: SettingsTemplateDraft,
    onChange: (SettingsTemplateDraft) -> Unit,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit,
    onOpenConnections: () -> Unit
) {
    PreferenceCard("Phone network", "Read-only connection status; exact cellular generation may not be available to ordinary apps.") {
        Text(state.activeTransport)
        OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.NETWORK) }) { Text("Open Android network settings") }
    }
    PreferenceCard("Robot TCP connection", "Edit the endpoint used by the existing TCP client.") {
        OutlinedTextField(state.robotHost, { onChange(state.copy(robotHost = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Robot host") }, singleLine = true)
        OutlinedTextField(state.robotPort, { onChange(state.copy(robotPort = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("TCP port") }, singleLine = true)
        OutlinedButton(onClick = onOpenConnections) { Text("Open connection profile editor") }
    }
    Text(
        "Mobile data is controlled by Android. This page will open system settings instead of claiming it can switch 4G or mobile data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PermissionSettingsTemplate(
    state: SettingsTemplateDraft,
    onOpenAndroidSettings: (AndroidSettingsTarget) -> Unit
) {
    PreferenceCard("Camera", "Permission state: ${state.cameraPermission}") {
        Text("URCS asks before opening the phone camera.")
        OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.APP_PERMISSIONS) }) { Text("Open app permission settings") }
    }
    PreferenceCard("Location", "Permission state: ${state.locationPermission}") {
        Text("Location is requested only when you ask for coordinates.")
        OutlinedButton(onClick = { onOpenAndroidSettings(AndroidSettingsTarget.APP_PERMISSIONS) }) { Text("Open app permission settings") }
    }
}

@Composable
private fun PreferenceCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}
