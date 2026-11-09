package com.robotics.urcs.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.robotics.urcs.camera.MjpegServer
import com.robotics.urcs.device.CameraBindingPolicy
import com.robotics.urcs.device.CameraFacing
import com.robotics.urcs.device.TorchControlState
import com.robotics.urcs.device.beginTorchChange
import com.robotics.urcs.device.finishTorchChange
import com.robotics.urcs.device.selectCameraFacing
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private fun selectedQuality(quality: String): Quality = when (quality.uppercase()) {
    "SD" -> Quality.SD
    "FHD" -> Quality.FHD
    else -> Quality.HD
}

private fun selectedRotation(orientation: String): Int =
    if (orientation.equals("Landscape", ignoreCase = true)) Surface.ROTATION_90 else Surface.ROTATION_0

private fun xTransform(mirror: Boolean, flipHorizontal: Boolean) = if (mirror.xor(flipHorizontal)) -1f else 1f
private fun yTransform(flipVertical: Boolean) = if (flipVertical) -1f else 1f

/** Local phone-camera stream used by the Dashboard. Feeds live frames to both UI preview and [MjpegServer]. */
@Composable
fun PhoneCameraStreamPreview(
    cameraEnabled: Boolean,
    cameraQuality: String,
    mirrorCamera: Boolean,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
    cameraOrientation: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var message by remember { mutableStateOf("") }
    val screenActive = remember { AtomicBoolean(true) }
    val generation = remember { AtomicInteger(0) }
    val cameraExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val latestProvider by rememberUpdatedState(provider)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(available, cameraEnabled) {
        if (cameraEnabled && available && !granted) permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(granted, previewView, cameraEnabled, cameraQuality, cameraOrientation) {
        val currentGeneration = generation.incrementAndGet()
        val view = previewView ?: return@LaunchedEffect
        if (!cameraEnabled || !available || !granted) {
            provider?.unbindAll()
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val nextProvider = future.get()
                if (!CameraBindingPolicy.shouldBind(screenActive.get(), currentGeneration, generation.get())) return@addListener
                val selector = when {
                    nextProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    nextProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> error("No phone camera is available")
                }
                val preview = Preview.Builder().setTargetRotation(selectedRotation(cameraOrientation)).build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    MjpegServer.instance.processImageProxy(proxy)
                }
                nextProvider.unbindAll()
                try {
                    nextProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                } catch (_: Exception) {
                    nextProvider.unbindAll()
                    nextProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                }
                provider = nextProvider
                message = ""
            }.onFailure { message = it.message ?: "Camera stream unavailable" }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        onDispose {
            screenActive.set(false)
            generation.incrementAndGet()
            cameraExecutor.shutdown()
            latestProvider?.unbindAll()
        }
    }

    when {
        !cameraEnabled -> CameraNotice("Camera is disabled in Settings.", modifier)
        !available -> CameraNotice("This phone has no camera hardware.", modifier)
        !granted -> CameraNotice("Camera permission is required for the local stream.", modifier)
        else -> {
            AndroidView(
                factory = { viewContext -> PreviewView(viewContext).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } },
                modifier = modifier.scale(xTransform(mirrorCamera, flipHorizontal), yTransform(flipVertical))
            )
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CameraNotice(message: String, modifier: Modifier) {
    ElevatedCard(modifier) { Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
fun CameraScreen(
    cameraEnabled: Boolean,
    cameraQuality: String,
    mirrorCamera: Boolean = false,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    cameraOrientation: String = "Portrait",
    onMirrorCameraChange: (Boolean) -> Unit = {},
    onFlipHorizontalChange: (Boolean) -> Unit = {},
    onFlipVerticalChange: (Boolean) -> Unit = {},
    onCameraQualityChange: (String) -> Unit = {},
    onCameraOrientationChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var status by remember { mutableStateOf("Camera opens only after permission is granted.") }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var frontRequested by remember { mutableStateOf(false) }
    var frontActive by remember { mutableStateOf(false) }
    var frontAvailable by remember { mutableStateOf(false) }
    var backAvailable by remember { mutableStateOf(false) }
    var flashAvailable by remember { mutableStateOf(false) }
    var torchState by remember { mutableStateOf(TorchControlState()) }
    var recordingActive by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    val screenActive = remember { AtomicBoolean(true) }
    val generation = remember { AtomicInteger(0) }
    val cameraExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val latestProvider by rememberUpdatedState(provider)
    val latestRecording by rememberUpdatedState(recording)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        status = if (it) "Permission granted" else "Camera permission denied"
    }

    LaunchedEffect(available, cameraEnabled) {
        if (cameraEnabled && available && !granted) permission.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(granted, previewView, frontRequested, cameraEnabled, cameraQuality, cameraOrientation) {
        val currentGeneration = generation.incrementAndGet()
        val view = previewView ?: return@LaunchedEffect
        if (!cameraEnabled || !available || !granted) {
            provider?.unbindAll(); camera = null; imageCapture = null; videoCapture = null
            recording?.stop(); recording = null; recordingActive = false
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val nextProvider = future.get()
                if (!CameraBindingPolicy.shouldBind(screenActive.get(), currentGeneration, generation.get())) return@addListener
                val hasFront = nextProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val hasBack = nextProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                frontAvailable = hasFront; backAvailable = hasBack
                val facing = selectCameraFacing(if (frontRequested) CameraFacing.FRONT else CameraFacing.BACK, hasFront, hasBack)
                    ?: error("This device has no available camera lens")
                val selector = if (facing == CameraFacing.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                frontActive = facing == CameraFacing.FRONT
                val rotation = selectedRotation(cameraOrientation)
                val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = view.surfaceProvider }
                val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(rotation).build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy ->
                    MjpegServer.instance.processImageProxy(proxy)
                }
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(selectedQuality(cameraQuality), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
                val video = VideoCapture.withOutput(recorder).also { it.targetRotation = rotation }
                recording?.stop(); recording = null; recordingActive = false
                nextProvider.unbindAll()
                var videoAvailable = true
                val bound = try {
                    nextProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis, video)
                } catch (_: Exception) {
                    try {
                        videoAvailable = false
                        nextProvider.unbindAll()
                        nextProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                    } catch (_: Exception) {
                        videoAvailable = false
                        nextProvider.unbindAll()
                        nextProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }
                }
                provider = nextProvider; camera = bound; imageCapture = capture
                videoCapture = if (videoAvailable) video else null
                flashAvailable = bound.cameraInfo.hasFlashUnit(); torchState = TorchControlState()
                status = if (videoAvailable) "Local camera ready" else "Local camera ready · video unavailable for this lens"
            }.onFailure { status = "Camera unavailable: ${it.message ?: "device error"}" }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(Unit) {
        onDispose {
            screenActive.set(false)
            generation.incrementAndGet()
            cameraExecutor.shutdown()
            runCatching { latestRecording?.stop() }
            latestProvider?.unbindAll()
        }
    }

    fun startVideo() {
        val capture = videoCapture ?: run { status = "Video capture is unavailable on this camera"; return }
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        directory.mkdirs()
        val file = File(directory, "URCS-${System.currentTimeMillis()}.mp4")
        runCatching {
            capture.output.prepareRecording(context, FileOutputOptions.Builder(file).build()).start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { recordingActive = true; status = "Recording local video · ${file.name}" }
                    is VideoRecordEvent.Finalize -> { recording = null; recordingActive = false; status = if (event.hasError()) "Video failed: ${event.cause?.message ?: event.error}" else "Saved ${file.name} in app movie storage" }
                    else -> Unit
                }
            }
        }.onSuccess { recording = it; recordingActive = true; status = "Starting local video recording…" }
            .onFailure { status = "Could not start video: ${it.message}" }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Camera", "Local phone camera preview, stream server and capture controls")
        if (!cameraEnabled) Text("Camera is disabled in Settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (!available) Text("This device does not report camera hardware.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (granted) {
            AndroidView(
                factory = { viewContext -> PreviewView(viewContext).also { it.scaleType = PreviewView.ScaleType.FILL_CENTER; previewView = it } },
                modifier = Modifier.fillMaxWidth().height(if (cameraOrientation.equals("Landscape", true)) 220.dp else 300.dp).scale(xTransform(mirrorCamera, flipHorizontal), yTransform(flipVertical))
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (backAvailable) FilterChip(selected = !frontActive, onClick = { frontRequested = false }, enabled = !torchState.isPending, label = { Text("Back") })
                if (frontAvailable) FilterChip(selected = frontActive, onClick = { frontRequested = true }, enabled = !torchState.isPending, label = { Text("Front") })
                OutlinedButton(onClick = {
                    val wanted = !torchState.isOn
                    val pending = beginTorchChange(torchState, flashAvailable) ?: return@OutlinedButton
                    torchState = pending
                    val requestedGeneration = generation.get()
                    val future = runCatching { camera?.cameraControl?.enableTorch(wanted) }.getOrNull()
                    if (future == null) { torchState = finishTorchChange(pending, wanted, false); status = "Flash control failed: camera unavailable" }
                    else future.addListener({
                        val result = runCatching { future.get() }
                        if (CameraBindingPolicy.shouldBind(screenActive.get(), requestedGeneration, generation.get())) {
                            torchState = finishTorchChange(pending, wanted, result.isSuccess)
                            status = if (result.isSuccess) if (wanted) "Flash on" else "Flash off" else "Flash control failed"
                        } else {
                            torchState = finishTorchChange(pending, wanted, false)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }, enabled = flashAvailable && !torchState.isPending) { Text(if (torchState.isPending) "Setting flash…" else if (torchState.isOn) "Flash on" else "Flash off") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val capture = imageCapture ?: return@Button
                    isCapturing = true
                    val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                    directory.mkdirs(); val file = File(directory, "URCS-${System.currentTimeMillis()}.jpg")
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                            isCapturing = false
                            status = "Saved ${file.name} in app picture storage"
                        }
                        override fun onError(exception: ImageCaptureException) {
                            isCapturing = false
                            status = "Capture failed: ${exception.message}"
                        }
                    })
                }, enabled = imageCapture != null && !isCapturing, modifier = Modifier.weight(1f)) { Text(if (isCapturing) "Saving…" else "Capture photo") }
                Button(onClick = { if (recordingActive) recording?.stop() else startVideo() }, enabled = videoCapture != null || recordingActive, modifier = Modifier.weight(1f)) { Text(if (recordingActive) "Stop video" else "Record video") }
            }
        } else {
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Camera permission is required for local preview and capture.")
                TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Grant camera permission") }
            } }
        }

        // --- Inbuilt Wi-Fi Camera Stream Information Card ---
        val localIp = remember { MjpegServer.getLocalIpAddress() ?: "127.0.0.1" }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Inbuilt Wi-Fi Camera Stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "PORT 8081 · ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text("Stream URL: http://$localIp:8081/video", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Snapshot URL: http://$localIp:8081/snapshot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Direct stream for Python monitor, OpenCV, and web browsers. No 3rd-party apps needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        CameraPreferenceControls(cameraQuality, mirrorCamera, flipHorizontal, flipVertical, cameraOrientation, onMirrorCameraChange, onFlipHorizontalChange, onFlipVerticalChange, onCameraQualityChange, onCameraOrientationChange)
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("This is the phone camera. Remote robot-video streaming needs a robot video protocol before it can be shown here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CameraPreferenceControls(
    quality: String, mirror: Boolean, flipHorizontal: Boolean, flipVertical: Boolean, orientation: String,
    onMirror: (Boolean) -> Unit, onFlipHorizontal: (Boolean) -> Unit, onFlipVertical: (Boolean) -> Unit,
    onQuality: (String) -> Unit, onOrientation: (String) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Preview settings", style = MaterialTheme.typography.titleMedium)
            CameraSwitch("Mirror camera", mirror, onMirror)
            CameraSwitch("Flip horizontally", flipHorizontal, onFlipHorizontal)
            CameraSwitch("Flip vertically", flipVertical, onFlipVertical)
            Text("Quality", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("SD", "HD", "FHD").forEach { choice -> FilterChip(selected = quality.equals(choice, true), onClick = { onQuality(choice) }, label = { Text(choice) }) } }
            Text("Preview orientation", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Portrait", "Landscape").forEach { choice -> FilterChip(selected = orientation.equals(choice, true), onClick = { onOrientation(choice) }, label = { Text(choice) }) } }
        }
    }
}

@Composable
private fun CameraSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.width(190.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
