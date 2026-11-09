package com.robotics.urcs.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.hardware.Sensor
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.robotics.urcs.automation.AutomationAction
import com.robotics.urcs.automation.AutomationRule
import com.robotics.urcs.automation.RuleDecision
import com.robotics.urcs.automation.RuleEngine
import com.robotics.urcs.communication.ConnectionState
import com.robotics.urcs.communication.RobotCommand
import com.robotics.urcs.communication.TcpTransport
import com.robotics.urcs.device.AndroidPhoneStatusRepository
import com.robotics.urcs.logging.SessionLogger
import com.robotics.urcs.robotics.ControllerCommandGate
import com.robotics.urcs.sensors.AndroidSensorRepository
import com.robotics.urcs.service.UrcsForegroundService
import com.robotics.urcs.settings.AppSettings
import com.robotics.urcs.settings.AppSettingsRepository
import kotlinx.coroutines.launch

private val Paper = Color(0xFFF5F5F5)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UrcsApp() {
    val context = LocalContext.current
    val settingsRepository = remember { AppSettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val sensors = remember { AndroidSensorRepository(context) }
    val phoneStatus = remember { AndroidPhoneStatusRepository(context) }
    val commandGate = remember { ControllerCommandGate() }
    val activity = context as? Activity
    val sensorReadings by sensors.readings.collectAsState()
    val transport = remember { TcpTransport() }
    val connection by transport.state.collectAsState()
    val logger = remember { SessionLogger(context) }
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(AppSection.DASHBOARD) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var monitoring by rememberSaveable { mutableStateOf(false) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var emergencyStopped by rememberSaveable { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf("192.168.4.1") }
    var port by rememberSaveable { mutableStateOf("8080") }
    val terminalSessions = remember { TerminalSessionsState() }
    val lastLoggedSensorEvent = remember { mutableMapOf<Int, Long>() }
    val automationRules = remember { mutableStateListOf<AutomationRule>() }
    var logRows by remember { mutableStateOf<List<String>>(emptyList()) }

    val dark = when (settings.theme) {
        "Dark" -> true
        "Light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val selectedSensorTypes = settings.selectedSensorTypes ?: sensors.availableSensors.map { it.type }.toSet()
    val originalScreenBrightness = remember(activity) {
        activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    val initiallyKeepsScreenOn = remember(activity) {
        activity?.window?.let { it.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 } ?: false
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFFE5E5E5), onPrimary = Color(0xFF171717), primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFF4F4F4),
            secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF171717), secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFE8E8E8),
            tertiary = Color(0xFFAAAAAA), onTertiary = Color(0xFF171717), tertiaryContainer = Color(0xFF3A3A3A), onTertiaryContainer = Color(0xFFF0F0F0),
            background = Color(0xFF101010), onBackground = Color(0xFFF2F2F2), surface = Color(0xFF191919), onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF2B2B2B), onSurfaceVariant = Color(0xFFBDBDBD), outline = Color(0xFF777777), outlineVariant = Color(0xFF444444),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
        ) else lightColorScheme(
            primary = Color(0xFF202020), onPrimary = Color.White, primaryContainer = Color(0xFFE0E0E0), onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF555555), onSecondary = Color.White, secondaryContainer = Color(0xFFE5E5E5), onSecondaryContainer = Color(0xFF222222),
            tertiary = Color(0xFF444444), onTertiary = Color.White, tertiaryContainer = Color(0xFFD8D8D8), onTertiaryContainer = Color(0xFF111111),
            background = Paper, onBackground = Color(0xFF111111), surface = Color.White, onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFE7E7E7), onSurfaceVariant = Color(0xFF555555), outline = Color(0xFF777777), outlineVariant = Color(0xFFD2D2D2),
            error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
        ),
        typography = MaterialTheme.typography
    ) {
        DisposableEffect(activity, settings.keepScreenAwake, settings.useSystemBrightness, settings.appBrightness) {
            val window = activity?.window
            if (window != null) {
                if (settings.keepScreenAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else if (!initiallyKeepsScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply {
                    screenBrightness = if (settings.useSystemBrightness) {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    } else {
                        settings.appBrightness.coerceIn(0.1f, 1f)
                    }
                }
            }
            onDispose {
                if (window != null) {
                    window.attributes = window.attributes.apply { screenBrightness = originalScreenBrightness }
                    if (initiallyKeepsScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        LaunchedEffect(selectedSensorTypes, monitoring) {
            if (selectedSensorTypes.isEmpty() && monitoring) monitoring = false
        }
        DisposableEffect(sensors, monitoring, selectedSensorTypes, settings.samplePeriodMillis) {
            if (monitoring && selectedSensorTypes.isNotEmpty()) {
                sensors.start(settings.samplePeriodMillis * 1_000, selectedSensorTypes)
            }
            onDispose {
                sensors.stop()
            }
        }

        // Keep app, CPU, Wi-Fi, and TCP link alive in background via Foreground Service
        LaunchedEffect(monitoring, connection, host, port) {
            if (connection == ConnectionState.CONNECTED || monitoring) {
                val statusText = when {
                    connection == ConnectionState.CONNECTED && monitoring -> "Connected to $host:$port · Monitoring ON"
                    connection == ConnectionState.CONNECTED -> "Connected to $host:$port · Standby"
                    else -> "Sensor Telemetry Active · Background ON"
                }
                UrcsForegroundService.start(context, statusText)
            } else {
                UrcsForegroundService.stop(context)
            }
        }

        // Continuous background automation rule engine
        val firedRuleAt = remember { mutableMapOf<String, Long>() }
        LaunchedEffect(automationRules.toList(), sensorReadings, connection) {
            if (automationRules.isNotEmpty() && connection == ConnectionState.CONNECTED) {
                val values = sensorReadings.mapNotNull { (type, sample) ->
                    sample.values.firstOrNull()?.let { type.toString() to it.toDouble() }
                }.toMap()
                automationRules.forEach { rule ->
                    if (RuleEngine.evaluate(rule, values, connection == ConnectionState.CONNECTED) == RuleDecision.FIRE) {
                        val now = System.currentTimeMillis()
                        if (now - (firedRuleAt[rule.id] ?: 0L) > 2_000L) {
                            firedRuleAt[rule.id] = now
                            if (rule.action == AutomationAction.STOP_ROBOT) {
                                emergencyStopped = true
                                transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(RobotCommand.Stop))
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(transport) {
            transport.incoming.collect { message ->
                terminalSessions.recordIncoming(message)
                if (recording) runCatching { logger.append(System.currentTimeMillis(), "communication", "RX $message") }
            }
        }
        LaunchedEffect(recording, sensorReadings) {
            if (recording) {
                sensorReadings.values.forEach { sample ->
                    if (lastLoggedSensorEvent[sample.sensor.type] != sample.timestampNanos) {
                        lastLoggedSensorEvent[sample.sensor.type] = sample.timestampNanos
                        runCatching { logger.append(sample.updatedAtMillis, sample.sensor.name, sample.values.joinToString(";")) }
                    }
                }
            }
        }

        LaunchedEffect(monitoring, connection) {
            if (monitoring && connection == ConnectionState.CONNECTED) {
                while (monitoring && connection == ConnectionState.CONNECTED) {
                    kotlinx.coroutines.delay(250)
                    if (!monitoring || connection != ConnectionState.CONNECTED) break
                    val battery = phoneStatus.readBattery()
                    val location = phoneStatus.readLastKnownLocation()
                    val root = JsonObject().apply {
                        addProperty("type", "phone_telemetry")
                        val battObj = JsonObject().apply {
                            battery.levelPercent?.let { addProperty("level", it) }
                            battery.charging?.let { addProperty("charging", it) }
                        }
                        add("battery", battObj)
                        if (location != null) {
                            val gpsObj = JsonObject().apply {
                                addProperty("latitude", location.latitude)
                                addProperty("longitude", location.longitude)
                                addProperty("accuracy", location.accuracyMeters)
                                addProperty("timestamp", location.timestampMillis)
                            }
                            add("gps", gpsObj)
                        }
                        val sensorsObj = JsonObject()
                        sensorReadings[Sensor.TYPE_ACCELEROMETER]?.let { r ->
                            val arr = JsonArray()
                            r.values.forEach { arr.add(it) }
                            sensorsObj.add("accelerometer", arr)
                        }
                        sensorReadings[Sensor.TYPE_GYROSCOPE]?.let { r ->
                            val arr = JsonArray()
                            r.values.forEach { arr.add(it) }
                            sensorsObj.add("gyroscope", arr)
                        }
                        sensorReadings[Sensor.TYPE_MAGNETIC_FIELD]?.let { r ->
                            val arr = JsonArray()
                            r.values.forEach { arr.add(it) }
                            sensorsObj.add("magnetic", arr)
                        }
                        add("sensors", sensorsObj)
                    }
                    transport.send(root.toString())
                }
            }
        }

        DisposableEffect(transport, sensors, phoneStatus) {
            onDispose {
                sensors.close()
                transport.close()
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text("URCS navigation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp))
                        listOf(
                            "Workspace" to listOf(AppSection.DASHBOARD, AppSection.SENSOR_LAB, AppSection.CONTROLLER),
                            "Devices & links" to listOf(AppSection.CAMERA, AppSection.DEVICE_CONTROLS, AppSection.CONNECTIONS),
                            "Tools" to listOf(AppSection.AUTOMATION, AppSection.DATA_LOGGER, AppSection.TERMINAL),
                            "System" to listOf(AppSection.SETTINGS)
                        ).forEach { (group, destinations) ->
                            Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                            destinations.forEach { destination ->
                                NavigationDrawerItem(
                                    label = { Text(destination.label) },
                                    selected = section == destination,
                                    onClick = {
                                        section = destination
                                        scope.launch { drawerState.close() }
                                    },
                                    icon = { Text(destination.glyph) },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        ) {
        Scaffold(
            topBar = {
                TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.semantics { contentDescription = "Open navigation" }
                            ) {
                                Text("☰", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Image(
                                    painter = painterResource(com.robotics.urcs.R.drawable.urcs_wordmark),
                                    contentDescription = "URCS",
                                    modifier = Modifier.width(92.dp).height(40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Text("UNIVERSAL ROBOTICS CONTROL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        actions = {
                            val (status, statusColor) = when (connection) {
                                ConnectionState.CONNECTED -> "TCP LIVE" to if (dark) Color(0xFF70D694) else Color(0xFF168044)
                                ConnectionState.CONNECTING -> "CONNECTING" to if (dark) Color(0xFFFFD166) else Color(0xFF9A6700)
                                ConnectionState.ERROR -> "TCP ERROR" to if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
                                ConnectionState.DISCONNECTED -> "OFFLINE" to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(Modifier.padding(end = 12.dp).background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(status, color = statusColor, style = MaterialTheme.typography.labelMedium)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
            }
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
                when (section) {
                    AppSection.DASHBOARD -> DashboardScreen(
                        sensors = sensors.availableSensors,
                        readings = sensorReadings,
                        selectedSensorTypes = selectedSensorTypes,
                        samplePeriodMillis = settings.samplePeriodMillis,
                        cameraAvailable = context.packageManager.hasSystemFeature("android.hardware.camera.any"),
                        cameraEnabled = settings.cameraEnabled,
                        cameraQuality = settings.cameraQuality,
                        mirrorCamera = settings.mirrorCamera,
                        flipHorizontal = settings.flipHorizontal,
                        flipVertical = settings.flipVertical,
                        cameraOrientation = settings.cameraOrientation,
                        timeFormat24Hours = settings.timeFormat24Hours,
                        dashboardControlsEnabled = settings.dashboardControlsEnabled,
                        connection = connection,
                        emergencyStopped = emergencyStopped,
                        transport = transport,
                        commandGate = commandGate,
                        phoneStatus = phoneStatus,
                        sensitivity = settings.sensitivity,
                        maxMotorPercent = settings.maxMotorPercent,
                        onSensitivityChange = { value -> scope.launch { settingsRepository.setSensitivity(value) } },
                        monitoring = monitoring,
                        onMonitoringChange = { monitoring = it },
                        onEmergencyStop = {
                            emergencyStopped = true
                            scope.launch { transport.send(com.robotics.urcs.communication.ProtocolCodec.encode(com.robotics.urcs.communication.RobotCommand.Stop)) }
                        }
                    )
                    AppSection.SENSOR_LAB -> SensorLabScreen(
                        sensors = sensors.availableSensors,
                        readings = sensorReadings,
                        monitoring = monitoring,
                        samplingMillis = settings.samplePeriodMillis,
                        onMonitoringChange = { monitoring = it }
                    )
                    AppSection.CONTROLLER -> ControllerScreen(
                        transport = transport,
                        commandGate = commandGate,
                        connected = connection == ConnectionState.CONNECTED,
                        emergencyStopped = emergencyStopped,
                        sensitivity = settings.sensitivity,
                        maxMotorPercent = settings.maxMotorPercent,
                        customButtons = settings.customButtons,
                        onCustomButtonsChange = { buttons -> scope.launch { settingsRepository.setCustomButtons(buttons) } },
                        onSensitivityChange = { value -> scope.launch { settingsRepository.setSensitivity(value) } },
                        onEmergencyStop = { emergencyStopped = true },
                        onResumeControl = { emergencyStopped = false }
                    )
                    AppSection.CONNECTIONS -> ConnectionsScreen(
                        transport = transport,
                        state = connection,
                        host = host,
                        port = port,
                        onHostChange = { host = it },
                        onPortChange = { port = it }
                    )
                    AppSection.DEVICE_CONTROLS -> DeviceControlsScreen(
                        repository = phoneStatus,
                        settings = settings,
                        settingsRepository = settingsRepository
                    )
                    AppSection.CAMERA -> CameraScreen(
                        cameraEnabled = settings.cameraEnabled,
                        cameraQuality = settings.cameraQuality,
                        mirrorCamera = settings.mirrorCamera,
                        flipHorizontal = settings.flipHorizontal,
                        flipVertical = settings.flipVertical,
                        cameraOrientation = settings.cameraOrientation,
                        onMirrorCameraChange = { value -> scope.launch { settingsRepository.setMirrorCamera(value) } },
                        onFlipHorizontalChange = { value -> scope.launch { settingsRepository.setFlipHorizontal(value) } },
                        onFlipVerticalChange = { value -> scope.launch { settingsRepository.setFlipVertical(value) } },
                        onCameraQualityChange = { value -> scope.launch { settingsRepository.setCameraQuality(value) } },
                        onCameraOrientationChange = { value -> scope.launch { settingsRepository.setCameraOrientation(value) } }
                    )
                    AppSection.AUTOMATION -> AutomationScreen(
                        sensors = sensors.availableSensors,
                        readings = sensorReadings,
                        connection = connection,
                        transport = transport,
                        rules = automationRules,
                        onRulesChange = { rules -> automationRules.clear(); automationRules.addAll(rules) }
                    )
                    AppSection.DATA_LOGGER -> DataLoggerScreen(
                        sensors = sensors.availableSensors,
                        readings = sensorReadings,
                        monitoring = monitoring,
                        recording = recording,
                        logRows = logRows,
                        logger = logger,
                        onStart = { recording = true; monitoring = true },
                        onStop = { recording = false; scope.launch { logger.flush() }; scope.launch { logRows = logger.readRecent() } }
                    )
                    AppSection.TERMINAL -> MultiTerminalScreen(transport, connection, terminalSessions)
                    AppSection.SETTINGS -> SettingsScreen(
                        settings = settings,
                        repository = settingsRepository,
                        sensors = sensors.availableSensors,
                        host = host,
                        port = port,
                        onConnectionDetailsChange = { newHost, newPort -> host = newHost; port = newPort },
                        onOpenControllerOptions = { section = AppSection.CONTROLLER }
                    )
                }
            }
        }
        }
    }
}
