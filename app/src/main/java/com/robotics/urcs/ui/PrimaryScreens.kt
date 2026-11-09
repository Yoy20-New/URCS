package com.robotics.urcs.ui

import android.hardware.Sensor
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import com.robotics.urcs.communication.ConnectionState
import com.robotics.urcs.communication.RobotCommand
import com.robotics.urcs.communication.TcpTransport
import com.robotics.urcs.sensors.SensorReading
import com.robotics.urcs.sensors.sensorAccuracyLabel
import com.robotics.urcs.sensors.sensorUnit
import com.robotics.urcs.device.AndroidPhoneStatusRepository
import com.robotics.urcs.device.NetworkTransport
import com.robotics.urcs.device.batterySummary
import com.robotics.urcs.device.networkSummary
import com.robotics.urcs.robotics.ControllerCommandGate
import com.robotics.urcs.robotics.CustomRobotButton
import com.robotics.urcs.robotics.CustomRobotButtonCodec
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun DashboardScreen(
    sensors: List<Sensor>,
    readings: Map<Int, SensorReading>,
    selectedSensorTypes: Set<Int>,
    monitoring: Boolean,
    samplePeriodMillis: Int,
    cameraAvailable: Boolean,
    cameraEnabled: Boolean,
    cameraQuality: String,
    mirrorCamera: Boolean,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cameraOrientation: String,
    timeFormat24Hours: Boolean,
    dashboardControlsEnabled: Boolean,
    sensitivity: Float,
    maxMotorPercent: Float,
    connection: ConnectionState,
    emergencyStopped: Boolean,
    transport: TcpTransport,
    commandGate: ControllerCommandGate,
    phoneStatus: AndroidPhoneStatusRepository,
    onMonitoringChange: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onEmergencyStop: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedSensors = sensors.filter {
        it.type in selectedSensorTypes && it.type in setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GYROSCOPE)
    }.distinctBy { it.type }
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successStatus = if (darkSurface) Color(0xFF70D694) else Color(0xFF168044)
    val errorStatus = if (darkSurface) Color(0xFFFF8A80) else Color(0xFFB3261E)
    val driveSpeed = 120
    var commandMessage by remember { mutableStateOf("") }
    var batteryStatus by remember { mutableStateOf("Checking") }
    var networkStatus by remember { mutableStateOf("Checking") }
    var bluetoothStatus by remember { mutableStateOf("Unknown") }
    var bluetoothPermissionDenied by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(LocalTime.now()) }
    val controlsReady = dashboardControlsEnabled && connection == ConnectionState.CONNECTED && !emergencyStopped
    val motorScale = sensitivity.coerceIn(0f, 1f) * (maxMotorPercent.coerceIn(0f, 100f) / 100f)
    val clockFormatter = remember(timeFormat24Hours) {
        DateTimeFormatter.ofPattern(if (timeFormat24Hours) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    val requestBluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothPermissionDenied = !granted
        bluetoothStatus = if (granted) readBluetoothStatus(context) else "Permission denied"
    }

    LaunchedEffect(phoneStatus) {
        while (true) {
            val battery = phoneStatus.readBattery()
            batteryStatus = batterySummary(battery.levelPercent, battery.charging)
            val network = phoneStatus.readNetwork()
            networkStatus = networkSummary(network.transport, network.hasInternet)
            bluetoothStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
                bluetoothPermissionDenied
            ) "Permission denied" else readBluetoothStatus(context)
            clock = LocalTime.now()
            kotlinx.coroutines.delay(5_000)
        }
    }

    LaunchedEffect(emergencyStopped) {
        if (emergencyStopped) commandGate.latchStop() else commandGate.resume()
    }

    suspend fun sendDrive(left: Int, right: Int) {
        if (!controlsReady) return
        var failed = false
        commandGate.drive(
            sendLeft = {
                if (transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Motor(1, (left * motorScale).toInt()))).isFailure) failed = true
            },
            sendRight = {
                if (transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Motor(2, (right * motorScale).toInt()))).isFailure) failed = true
            }
        )
        commandMessage = if (failed) "Command failed" else "Command sent"
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Dashboard", "Phone status and robotics controls")
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Phone status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LabeledValue("Network", networkStatus)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bluetooth", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bluetoothStatus, style = MaterialTheme.typography.bodyMedium)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) {
                            TextButton(onClick = { requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                                Text("Check", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                LabeledValue("Battery", batteryStatus)
                LabeledValue("Local time", clock.format(clockFormatter))
                LabeledValue("Communication", when {
                    connection == ConnectionState.CONNECTED && networkStatus.startsWith("Wi-Fi") -> "Wi-Fi TCP"
                    connection == ConnectionState.CONNECTED -> "TCP connected"
                    else -> "Offline"
                })
            }
        }
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Sensors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${selectedSensors.size} selected / ${samplePeriodMillis} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (monitoring) "Monitoring" else "Stopped",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (monitoring) successStatus else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = monitoring,
                            onCheckedChange = onMonitoringChange,
                            enabled = selectedSensors.isNotEmpty(),
                            modifier = Modifier.semantics { contentDescription = "Enable selected sensor monitoring" }
                        )
                    }
                }
                if (selectedSensors.isEmpty()) {
                    Text("No sensors selected. Choose sensors in Settings to begin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    selectedSensors.forEach { sensor ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val reading = readings[sensor.type]?.takeIf { monitoring }
                            Column(Modifier.weight(1f)) {
                                Text(sensor.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    reading?.values?.joinToString("  ") { String.format(Locale.US, "%.2f", it) + sensorUnit(sensor.type) }
                                        ?: if (monitoring) "Waiting for sample" else "Monitoring stopped",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            SensorSparkline(reading?.history.orEmpty(), Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Robot controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PhoneCameraStreamPreview(
                    cameraEnabled = cameraEnabled,
                    cameraQuality = cameraQuality,
                    mirrorCamera = mirrorCamera,
                    flipHorizontal = flipHorizontal,
                    flipVertical = flipVertical,
                    cameraOrientation = cameraOrientation,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(if (!cameraAvailable) "No camera hardware detected" else if (cameraEnabled) "Phone camera preview" else "Camera disabled in Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when { emergencyStopped -> "Emergency stop active"; connection == ConnectionState.CONNECTED -> "TCP connected"; else -> "TCP offline" },
                    style = MaterialTheme.typography.bodySmall,
                    color = when { emergencyStopped -> errorStatus; connection == ConnectionState.CONNECTED -> successStatus; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                )
                Text("Sensitivity ${(sensitivity * 100).toInt()}% · motor cap ${maxMotorPercent.toInt()}%", style = MaterialTheme.typography.labelMedium)
                if (dashboardControlsEnabled) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(236.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { scope.launch { sendDrive(driveSpeed, driveSpeed) } }, enabled = controlsReady, modifier = Modifier.size(width = 84.dp, height = 56.dp), shape = CircleShape) { Text("↑\nForward") }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { scope.launch { sendDrive(-driveSpeed, driveSpeed) } }, enabled = controlsReady, modifier = Modifier.size(width = 72.dp, height = 62.dp), shape = CircleShape) { Text("←\nLeft") }
                                    OutlinedButton(
                                        onClick = {
                                            onEmergencyStop()
                                            commandGate.latchStop()
                                            commandMessage = "Emergency stop requested"
                                        },
                                        enabled = connection == ConnectionState.CONNECTED && !emergencyStopped,
                                        modifier = Modifier.size(width = 72.dp, height = 62.dp),
                                        shape = CircleShape,
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("STOP", style = MaterialTheme.typography.labelMedium) }
                                    OutlinedButton(onClick = { scope.launch { sendDrive(driveSpeed, -driveSpeed) } }, enabled = controlsReady, modifier = Modifier.size(width = 72.dp, height = 62.dp), shape = CircleShape) { Text("→\nRight") }
                                }
                                OutlinedButton(onClick = { scope.launch { sendDrive(-driveSpeed, -driveSpeed) } }, enabled = controlsReady, modifier = Modifier.size(width = 84.dp, height = 56.dp), shape = CircleShape) { Text("↓\nBack") }
                            }
                        }
                    }
                } else {
                    Text("Robot controls are disabled in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (commandMessage.isNotBlank()) Text(commandMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun readBluetoothStatus(context: Context): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return "Permission unknown"
    val adapter = runCatching {
        context.getSystemService(BluetoothManager::class.java)?.adapter
    }.getOrNull() ?: return "Unavailable"
    return runCatching { if (adapter.isEnabled) "Enabled" else "Off" }.getOrDefault("Permission unknown")
}

@Composable
fun SensorLabScreen(
    sensors: List<Sensor>,
    readings: Map<Int, SensorReading>,
    monitoring: Boolean,
    samplingMillis: Int,
    onMonitoringChange: (Boolean) -> Unit
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Sensor lab", "Only sensors reported by this device are listed")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onMonitoringChange(!monitoring) }, modifier = Modifier.weight(1f)) {
                Text(if (monitoring) "Stop monitoring" else "Start monitoring")
            }
            ValuePill("${samplingMillis} ms")
        }
        if (sensors.isEmpty()) {
            Text("This device reports no available sensors.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sensors, key = { "${it.type}-${it.name}" }) { sensor ->
                    val reading = readings[sensor.type]
                    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(sensor.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                ValuePill(if (reading == null) "WAITING" else sensorAccuracyLabel(reading.accuracy))
                            }
                            Text("${sensor.vendor} • type ${sensor.type} • v${sensor.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                reading?.values?.joinToString("   ") { "${String.format(Locale.US, "%.3f", it)} ${sensorUnit(sensor.type)}" }
                                    ?: if (monitoring) "Waiting for a hardware sample…" else "Monitoring is stopped",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            SensorSparkline(reading?.history.orEmpty())
                            LabeledValue("Range / resolution", "${sensor.maximumRange} / ${sensor.resolution} ${sensorUnit(sensor.type)}")
                            LabeledValue("Sampling delay", if (sensor.minDelay > 0) "${sensor.minDelay} µs minimum" else "On change / unspecified")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ControllerScreen(
    transport: TcpTransport,
    commandGate: ControllerCommandGate,
    connected: Boolean,
    emergencyStopped: Boolean,
    sensitivity: Float,
    maxMotorPercent: Float,
    customButtons: List<CustomRobotButton>,
    onCustomButtonsChange: (List<CustomRobotButton>) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onEmergencyStop: () -> Unit,
    onResumeControl: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("Commands use the URCS JSON line protocol.") }
    var speed by remember { mutableStateOf(120f) }
    var stopPending by remember { mutableStateOf(false) }
    var customButtonLabel by remember { mutableStateOf("") }
    var customButtonCommand by remember { mutableStateOf("{\"type\":\"motor\",\"id\":1,\"speed\":120}") }
    var editingButtonId by remember { mutableStateOf<String?>(null) }
    var editingButtonLabel by remember { mutableStateOf("") }
    val enabled = connected && !emergencyStopped
    val motorScale = sensitivity.coerceIn(0f, 1f) * (maxMotorPercent.coerceIn(0f, 100f) / 100f)
    val motorCapScale = maxMotorPercent.coerceIn(0f, 100f) / 100f
    suspend fun sendPair(left: Int, right: Int) {
        if (!enabled) return
        val scaledLeft = (left * motorScale).toInt()
        val scaledRight = (right * motorScale).toInt()
        var failure: Throwable? = null
        commandGate.drive(
            sendLeft = {
                transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Motor(1, scaledLeft)))
                    .onFailure { failure = it }
            },
            sendRight = {
                transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Motor(2, scaledRight)))
                    .onFailure { failure = it }
            }
        )
        message = if (failure == null) "Motion command sent" else "Command failed: ${failure?.message}"
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Robot controller", "Commands are sent only over a live TCP connection")
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Speed · ${speed.toInt()}", style = MaterialTheme.typography.titleMedium)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0f..255f, enabled = enabled)
                Text("Input sensitivity · ${(sensitivity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0.1f..1f)
            }
        }
        Button(onClick = { scope.launch { sendPair(speed.toInt(), speed.toInt()) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("▲  FORWARD") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { scope.launch { sendPair(-speed.toInt(), speed.toInt()) } }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("◀  LEFT") }
            OutlinedButton(onClick = { scope.launch { sendPair(speed.toInt(), -speed.toInt()) } }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("RIGHT  ▶") }
        }
        OutlinedButton(onClick = { scope.launch { sendPair(-speed.toInt(), -speed.toInt()) } }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("▼  REVERSE") }
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom robot buttons", style = MaterialTheme.typography.titleMedium)
                Text("Saved buttons send validated motor, servo, or stop JSON over TCP.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(customButtonLabel, { customButtonLabel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Button label (max 20 characters)") }, singleLine = true)
                OutlinedTextField(customButtonCommand, { customButtonCommand = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Robot command JSON") }, minLines = 2)
                Button(onClick = {
                    val button = CustomRobotButton(UUID.randomUUID().toString(), customButtonLabel.trim(), customButtonCommand.trim())
                    val updated = customButtons + button
                    runCatching { CustomRobotButtonCodec.encode(updated) }
                        .onSuccess {
                            onCustomButtonsChange(updated)
                            customButtonLabel = ""
                            message = "Custom button saved"
                        }
                        .onFailure { message = "Button not saved: ${it.message}" }
                }, enabled = customButtons.size < 8) { Text("Add custom button") }
                customButtons.forEach { customButton ->
                    val parsed = CustomRobotButtonCodec.parseCommand(customButton.commandJson).getOrNull()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val command = CustomRobotButtonCodec.parseCommand(customButton.commandJson).getOrElse {
                                    message = "Saved button is invalid: ${it.message}"
                                    return@Button
                                }
                                val cappedCommand = if (command is RobotCommand.Motor) {
                                    command.copy(speed = (command.speed * motorCapScale).toInt())
                                } else command
                                val commandJson = com.robotics.urcs.communication.ProtocolCodec.encode(cappedCommand)
                                if (command == RobotCommand.Stop) {
                                    commandGate.latchStop()
                                    stopPending = true
                                    onEmergencyStop()
                                    scope.launch {
                                        commandGate.emergencyStop {
                                            transport.send(commandJson).fold(
                                                { message = "${customButton.label}: stop sent" },
                                                { message = "Stop command failed: ${it.message}" }
                                            )
                                        }
                                        stopPending = false
                                    }
                                } else if (enabled) {
                                    scope.launch {
                                        commandGate.command {
                                            transport.send(commandJson).fold(
                                                { message = "${customButton.label} sent" },
                                                { message = "Command failed: ${it.message}" }
                                            )
                                        }
                                    }
                                }
                            },
                            enabled = connected && (parsed == RobotCommand.Stop || !emergencyStopped),
                            modifier = Modifier.weight(1f)
                        ) { Text(customButton.label) }
                        TextButton(onClick = {
                            editingButtonId = customButton.id
                            editingButtonLabel = customButton.label
                        }) { Text("Rename") }
                        TextButton(onClick = { onCustomButtonsChange(customButtons.filterNot { it.id == customButton.id }) }) { Text("Remove") }
                    }
                    if (editingButtonId == customButton.id) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(editingButtonLabel, { editingButtonLabel = it.take(20) }, modifier = Modifier.weight(1f), label = { Text("New label") }, singleLine = true)
                            TextButton(onClick = {
                                if (editingButtonLabel.isBlank()) message = "Button label cannot be empty"
                                else {
                                    onCustomButtonsChange(customButtons.map { if (it.id == customButton.id) it.copy(label = editingButtonLabel.trim()) else it })
                                    editingButtonId = null
                                    message = "Button renamed"
                                }
                            }) { Text("Save") }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                commandGate.latchStop()
                stopPending = true
                onEmergencyStop()
                scope.launch {
                    commandGate.emergencyStop {
                        transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Stop))
                            .onFailure { message = "Stop command failed: ${it.message}" }
                    }
                    stopPending = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text(if (emergencyStopped) "EMERGENCY STOP ACTIVE" else "EMERGENCY STOP") }
        if (emergencyStopped) Button(
            onClick = { commandGate.resume(); onResumeControl() },
            enabled = !stopPending,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (stopPending) "Sending stop command…" else "Resume manual control") }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Best-effort phone command only. Critical machinery requires an independent hardware emergency stop.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ConnectionsScreen(
    transport: TcpTransport,
    state: ConnectionState,
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SectionHeader("Connections", "TCP client is implemented; other transports are clearly marked unavailable")
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Wi-Fi · TCP client", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(host, onHostChange, modifier = Modifier.fillMaxWidth(), label = { Text("Robot IP / host") }, singleLine = true)
                OutlinedTextField(port, onPortChange, modifier = Modifier.fillMaxWidth(), label = { Text("Port") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { message = transport.connect(host, port.toIntOrNull() ?: 0).fold({ "TCP connected" }, { "Connection failed: ${it.message}" }) } },
                        enabled = state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING
                    ) { Text("Connect") }
                    OutlinedButton(onClick = { scope.launch { transport.disconnect(); message = "Disconnected" } }, enabled = state == ConnectionState.CONNECTED) { Text("Disconnect") }
                }
                Text("State: ${state.name.lowercase().replace('_', ' ')}${if (message.isNotBlank()) " · $message" else ""}", style = MaterialTheme.typography.bodySmall)
            }
        }
        listOf("Bluetooth Classic", "Bluetooth Low Energy", "UDP", "TCP server", "USB OTG serial").forEach { label ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    ValuePill("Not configured")
                }
            }
        }
        Text("No paired-device, BLE scan, UDP, server, or USB serial adapter is exposed in this build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
