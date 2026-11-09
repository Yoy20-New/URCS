package com.robotics.urcs.ui

import android.content.Context
import android.hardware.Sensor
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.robotics.urcs.automation.AutomationAction
import com.robotics.urcs.automation.AutomationRule
import com.robotics.urcs.automation.Comparison
import com.robotics.urcs.automation.RuleDecision
import com.robotics.urcs.automation.RuleEngine
import com.robotics.urcs.communication.ConnectionState
import com.robotics.urcs.communication.ProtocolCodec
import com.robotics.urcs.communication.RobotCommand
import com.robotics.urcs.communication.TcpTransport
import com.robotics.urcs.logging.SessionLogger
import com.robotics.urcs.logging.TextExport
import com.robotics.urcs.profiles.ProfileCodec
import com.robotics.urcs.profiles.ProfileDecodeResult
import com.robotics.urcs.profiles.RobotProfile
import com.robotics.urcs.sensors.SensorReading
import com.robotics.urcs.settings.AppSettings
import com.robotics.urcs.settings.AppSettingsRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AutomationScreen(
    sensors: List<android.hardware.Sensor>,
    readings: Map<Int, SensorReading>,
    connection: ConnectionState,
    transport: TcpTransport,
    rules: List<AutomationRule>,
    onRulesChange: (List<AutomationRule>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ruleSnapshot = rules.toList()
    val firedAt = remember { mutableStateMapOf<String, Long>() }
    var selectedType by remember(sensors) { mutableStateOf(sensors.firstOrNull()?.type ?: -1) }
    var threshold by remember { mutableStateOf("15") }
    var message by remember { mutableStateOf("Rules are evaluated only from live phone-sensor values.") }
    LaunchedEffect(readings, ruleSnapshot, connection) {
        val values = readings.mapNotNull { (type, sample) -> sample.values.firstOrNull()?.let { type.toString() to it.toDouble() } }.toMap()
        ruleSnapshot.forEach { rule ->
            if (RuleEngine.evaluate(rule, values, connection == ConnectionState.CONNECTED) == RuleDecision.FIRE) {
                val now = System.currentTimeMillis()
                if (now - (firedAt[rule.id] ?: 0L) > 2_000L) {
                    firedAt[rule.id] = now
                    val result = transport.send(ProtocolCodec.encode(RobotCommand.Stop))
                    message = if (result.isSuccess) "${rule.id}: stop command sent" else "${rule.id}: command failed"
                }
            }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Automation", "Create a threshold rule that sends a stop command")
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("WHEN sensor value is below", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = false, onClick = { selectedType = sensors.firstOrNull()?.type ?: -1 }, label = { Text(sensors.firstOrNull { it.type == selectedType }?.name ?: "Select sensor") })
                    OutlinedTextField(threshold, { threshold = it.filter { char -> char.isDigit() || char == '.' || char == '-' } }, label = { Text("Threshold") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                if (sensors.isNotEmpty()) {
                    sensors.forEach { sensor ->
                        FilterChip(selected = selectedType == sensor.type, onClick = { selectedType = sensor.type }, label = { Text(sensor.name) })
                    }
                }
                Button(
                    onClick = {
                        val parsed = threshold.toDoubleOrNull()
                        if (selectedType < 0 || parsed == null || !parsed.isFinite()) {
                            message = "Choose an available sensor and enter a finite threshold"
                        } else {
                            val rule = AutomationRule("rule-${System.currentTimeMillis()}", selectedType.toString(), Comparison.BELOW, parsed, AutomationAction.STOP_ROBOT, true)
                            onRulesChange(rules + rule)
                            message = "Rule added. It stays inactive until its sensor has a live value and the robot is connected."
                        }
                    },
                    enabled = sensors.isNotEmpty()
                ) { Text("Add stop rule") }
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rules.isEmpty()) Text("No automation rules yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        rules.forEach { rule ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sensors.firstOrNull { it.type.toString() == rule.sensorId }?.name ?: "Sensor ${rule.sensorId}"} < ${rule.threshold}", fontWeight = FontWeight.Medium)
                        Text("Action: stop robot · ${if (connection == ConnectionState.CONNECTED) "connected" else "waiting for hardware"}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRulesChange(rules.filterNot { it.id == rule.id }) }) { Text("Remove") }
                }
            }
        }
        Text("Current implementation supports one live sensor threshold per rule. AND/OR groups, timer triggers, and cooldown configuration remain outstanding.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DataLoggerScreen(
    sensors: List<android.hardware.Sensor>,
    readings: Map<Int, SensorReading>,
    monitoring: Boolean,
    recording: Boolean,
    logRows: List<String>,
    logger: SessionLogger,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMessage by remember { mutableStateOf("") }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching { TextExport.write(context.contentResolver.openOutputStream(uri), logger.readAll()) }
                .onSuccess { exportMessage = "CSV exported" }.onFailure { exportMessage = "Export failed: ${it.message}" }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { TextExport.write(context.contentResolver.openOutputStream(uri), logger.readJson()) }
                .onSuccess { exportMessage = "JSON exported" }.onFailure { exportMessage = "Export failed: ${it.message}" }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Data logger", "Timestamped sensor and communication events in app-private storage")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, enabled = !recording) { Text("Start logging") }
            OutlinedButton(onClick = onStop, enabled = recording) { Text("Stop") }
            ValuePill(if (recording) "RECORDING" else "STOPPED")
        }
        Text("Sensors: ${sensors.size} discovered · live samples: ${readings.size} · monitoring ${if (monitoring) "on" else "off"}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csvLauncher.launch("urcs-session.csv") }) { Text("Export CSV") }
            OutlinedButton(onClick = { jsonLauncher.launch("urcs-session.json") }) { Text("Export JSON") }
        }
        Text(exportMessage, style = MaterialTheme.typography.bodySmall)
        Text("Recent entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (logRows.isEmpty()) Text("No session entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        logRows.take(40).forEach { row ->
            Text(row, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
fun TerminalScreen(
    transport: TcpTransport,
    state: ConnectionState,
    rows: List<String>,
    onSent: (String) -> Unit,
    onClear: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Text") }
    var status by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Communication terminal", "UTF-8 newline-framed TCP send and receive")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Text", "ASCII", "JSON").forEach { option ->
                FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option) })
            }
            ValuePill(state.name.lowercase().replace('_', ' '))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f), label = { Text("Message") }, singleLine = true)
            Button(onClick = {
                val outgoing = draft.trim()
                if (outgoing.isBlank()) status = "Enter a message first"
                else if (mode == "JSON" && runCatching { JsonParser.parseString(outgoing) }.isFailure) status = "Invalid JSON"
                else scope.launch {
                    val sent = transport.send(outgoing)
                    status = sent.fold({ "Sent" }, { "Send failed: ${it.message}" })
                    if (sent.isSuccess) onSent(outgoing)
                }
            }, enabled = state == ConnectionState.CONNECTED) { Text("Send") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).heightIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(rows.takeLast(250)) { row ->
                Text(row, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onClear) { Text("Clear terminal") }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    repository: AppSettingsRepository,
    sensors: List<Sensor>,
    host: String,
    port: String,
    onConnectionDetailsChange: (String, String) -> Unit,
    onOpenControllerOptions: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profileName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var samplePeriod by remember(settings.samplePeriodMillis) { mutableStateOf(settings.samplePeriodMillis.toFloat()) }
    var sensitivity by remember(settings.sensitivity) { mutableStateOf(settings.sensitivity) }
    var maxMotorPercent by remember(settings.maxMotorPercent) { mutableStateOf(settings.maxMotorPercent) }
    var appBrightness by remember(settings.appBrightness) { mutableStateOf(settings.appBrightness) }
    var gpsInterval by remember(settings.gpsUpdateIntervalSeconds) { mutableStateOf(settings.gpsUpdateIntervalSeconds.toFloat()) }
    var applicationExpanded by remember { mutableStateOf(true) }
    var sensorsExpanded by remember { mutableStateOf(false) }
    var cameraExpanded by remember { mutableStateOf(false) }
    var robotControlsExpanded by remember { mutableStateOf(false) }
    var gpsExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }
    var connectionExpanded by remember { mutableStateOf(false) }
    val availableSensorTypes = remember(sensors) { sensors.distinctBy { it.type }.sortedBy { it.name.lowercase(Locale.ROOT) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val selected = settings.profiles.firstOrNull { it.name == settings.activeProfileName }
        if (uri != null && selected != null) runCatching {
            TextExport.write(context.contentResolver.openOutputStream(uri), ProfileCodec.encode(selected))
        }.onSuccess { message = "Profile exported" }.onFailure { message = "Export failed: ${it.message}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.fold({ text ->
            when (val result = ProfileCodec.decode(text)) {
                is ProfileDecodeResult.Success -> scope.launch {
                    repository.saveProfile(result.profile)
                    repository.setActiveProfile(result.profile.name)
                    onConnectionDetailsChange(result.profile.host, result.profile.port.toString())
                    message = "Imported ${result.profile.name}"
                }
                is ProfileDecodeResult.Error -> message = result.reason
            }
        }, { message = "Import failed: ${it.message}" })
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Settings", "Configure sensors, camera, robot controls, and profiles")
        SettingsGroup("Application", applicationExpanded, { applicationExpanded = !applicationExpanded }) {
            Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEach { theme ->
                    FilterChip(selected = settings.theme == theme, onClick = { scope.launch { repository.setTheme(theme) } }, label = { Text(theme) })
                }
            }
            SettingsToggle("Keep screen awake", "Prevent the display from sleeping while URCS is in use.", settings.keepScreenAwake) { scope.launch { repository.setKeepScreenAwake(it) } }
            SettingsToggle("Use system brightness", "Use Android's current display brightness.", settings.useSystemBrightness) { scope.launch { repository.setUseSystemBrightness(it) } }
            Text("App brightness · ${(appBrightness * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(value = appBrightness, onValueChange = { appBrightness = it }, onValueChangeFinished = { scope.launch { repository.setAppBrightness(appBrightness) } }, valueRange = 0.1f..1f, enabled = !settings.useSystemBrightness)
            Text("Brightness applies to the URCS app window; Android system brightness is unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup("Sensors", sensorsExpanded, { sensorsExpanded = !sensorsExpanded }) {
        Text("Sensor sample interval · ${samplePeriod.toInt()} ms", style = MaterialTheme.typography.titleSmall)
        Slider(value = samplePeriod, onValueChange = { samplePeriod = it }, onValueChangeFinished = { scope.launch { repository.setSamplePeriod(samplePeriod.toInt()) } }, valueRange = 20f..500f)
        Text("Choose which phone sensor types URCS monitors. All available includes sensors added by the device; None disables sensor monitoring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = settings.selectedSensorTypes == null, onClick = { scope.launch { repository.setSelectedSensorTypes(null) } }, label = { Text("All available") })
            FilterChip(selected = settings.selectedSensorTypes?.isEmpty() == true, onClick = { scope.launch { repository.setSelectedSensorTypes(emptySet()) } }, label = { Text("None") })
        }
        if (availableSensorTypes.isEmpty()) {
            Text("No Android sensors are available on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availableSensorTypes.forEach { sensor ->
                    val isSelected = settings.selectedSensorTypes?.contains(sensor.type) ?: true
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val current = settings.selectedSensorTypes ?: availableSensorTypes.map { it.type }.toSet()
                            val updated = if (sensor.type in current) current - sensor.type else current + sensor.type
                            scope.launch { repository.setSelectedSensorTypes(updated) }
                        },
                        label = { Text(sensor.name) }
                    )
                }
            }
        }
        }
        SettingsGroup("Camera", cameraExpanded, { cameraExpanded = !cameraExpanded }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Enable phone camera", style = MaterialTheme.typography.titleSmall)
                Text("Allow the local camera controls to be used.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.cameraEnabled, onCheckedChange = { enabled -> scope.launch { repository.setCameraEnabled(enabled) } })
        }
        Text("Camera quality", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("SD", "HD", "FHD").forEach { quality ->
                FilterChip(
                    selected = settings.cameraQuality == quality,
                    onClick = { scope.launch { repository.setCameraQuality(quality) } },
                    enabled = settings.cameraEnabled,
                    label = { Text(quality) }
                )
            }
        }
        SettingsToggle("Mirror camera", "Mirror the preview and saved image.", settings.mirrorCamera) { scope.launch { repository.setMirrorCamera(it) } }
        SettingsToggle("Flip horizontally", "Apply a horizontal flip to camera output.", settings.flipHorizontal) { scope.launch { repository.setFlipHorizontal(it) } }
        SettingsToggle("Flip vertically", "Apply a vertical flip to camera output.", settings.flipVertical) { scope.launch { repository.setFlipVertical(it) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Portrait", "Landscape").forEach { orientation ->
                FilterChip(selected = settings.cameraOrientation == orientation, onClick = { scope.launch { repository.setCameraOrientation(orientation) } }, enabled = settings.cameraEnabled, label = { Text(orientation) })
            }
        }
        }
        SettingsGroup("Robot controls", robotControlsExpanded, { robotControlsExpanded = !robotControlsExpanded }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Enable dashboard controls", style = MaterialTheme.typography.titleSmall)
                Text("Show the dashboard controls for the connected robot.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = settings.dashboardControlsEnabled, onCheckedChange = { enabled -> scope.launch { repository.setDashboardControlsEnabled(enabled) } })
        }
        Text("Control sensitivity · ${(sensitivity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
        Slider(value = sensitivity, onValueChange = { sensitivity = it }, onValueChangeFinished = { scope.launch { repository.setSensitivity(sensitivity) } }, valueRange = 0.1f..1f)
        Text("Maximum motor output · ${maxMotorPercent.toInt()}%", style = MaterialTheme.typography.titleSmall)
        Slider(value = maxMotorPercent, onValueChange = { maxMotorPercent = it }, onValueChangeFinished = { scope.launch { repository.setMaxMotorPercent(maxMotorPercent) } }, valueRange = 1f..100f, enabled = settings.dashboardControlsEnabled)
        OutlinedButton(onClick = onOpenControllerOptions) { Text("Custom buttons and controller options") }
        }
        SettingsGroup("GPS", gpsExpanded, { gpsExpanded = !gpsExpanded }) {
            SettingsToggle("Enable GPS controls", "A location fix starts only after you request one on Phone controls.", settings.gpsEnabled) { scope.launch { repository.setGpsEnabled(it) } }
            Text("Update interval · ${gpsInterval.toInt()} seconds", style = MaterialTheme.typography.titleSmall)
            Slider(value = gpsInterval, onValueChange = { gpsInterval = it }, onValueChangeFinished = { scope.launch { repository.setGpsUpdateIntervalSeconds(gpsInterval.toInt()) } }, valueRange = 1f..60f, steps = 58, enabled = settings.gpsEnabled)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Decimal", "DMS").forEach { format ->
                    FilterChip(selected = settings.coordinateFormat == format, onClick = { scope.launch { repository.setCoordinateFormat(format) } }, enabled = settings.gpsEnabled, label = { Text(format) })
                }
            }
        }
        SettingsGroup("Time", timeExpanded, { timeExpanded = !timeExpanded }) {
            SettingsToggle("24-hour time", "Display local time using a 24-hour or 12-hour clock.", settings.timeFormat24Hours) { scope.launch { repository.setTimeFormat24Hours(it) } }
            Text("System time and mobile-data changes remain managed by Android.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { runCatching { context.startActivity(android.content.Intent(Settings.ACTION_DATE_SETTINGS)) }.onFailure { message = "Could not open Date & time Settings" } }) { Text("Date & time settings") }
        }
        SettingsGroup("Connection and profiles", connectionExpanded, { connectionExpanded = !connectionExpanded }) {
        OutlinedTextField(profileName, { profileName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Profile name") }, singleLine = true)
        OutlinedTextField(host, { onConnectionDetailsChange(it, port) }, modifier = Modifier.fillMaxWidth(), label = { Text("TCP host") }, singleLine = true)
        OutlinedTextField(port, { onConnectionDetailsChange(host, it) }, modifier = Modifier.fillMaxWidth(), label = { Text("TCP port") }, singleLine = true)
        Button(onClick = {
            val parsedPort = port.toIntOrNull()
            if (profileName.isBlank() || parsedPort !in 1..65535) message = "Profile name and port 1–65535 are required"
            else scope.launch {
                val profile = RobotProfile(profileName.trim(), host.trim(), parsedPort!!, "TCP")
                repository.saveProfile(profile)
                repository.setActiveProfile(profile.name)
                profileName = ""
                message = "Saved ${profile.name}"
            }
        }) { Text("Save current TCP profile") }
        settings.profiles.forEach { profile ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text("${profile.host}:${profile.port} · ${profile.protocol}", style = MaterialTheme.typography.bodySmall)
                    }
                    FilterChip(selected = settings.activeProfileName == profile.name, onClick = {
                        scope.launch {
                            repository.setActiveProfile(profile.name)
                            onConnectionDetailsChange(profile.host, profile.port.toString())
                        }
                    }, label = { Text("Use") })
                    TextButton(onClick = { scope.launch { repository.deleteProfile(profile.name) } }) { Text("Delete") }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("urcs-profile.json") }, enabled = settings.profiles.any { it.name == settings.activeProfileName }) { Text("Export active") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Import profile") }
        }
        }
        Text(message, style = MaterialTheme.typography.bodySmall)
        Text("Bluetooth and USB preference pages are not active until those transport adapters are implemented.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsGroup(title: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onToggle) { Text(if (expanded) "Collapse" else "Expand") }
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun SettingsToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
