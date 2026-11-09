package com.robotics.urcs.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Managed client connection holding both the raw TCP socket and buffered output stream.
 * Ensures proper file descriptor and native socket cleanup on disconnection.
 */
data class StreamClient(val socket: Socket, val out: BufferedOutputStream) : Closeable {
    override fun close() {
        runCatching { out.close() }
        runCatching { socket.close() }
    }
}

/**
 * Built-in HTTP MJPEG streaming server.
 * Streams live CameraX frames to any browser, OpenCV script, or VLC client over Wi-Fi.
 *
 * Endpoints:
 * - GET /video or GET /mjpeg : Continuous MJPEG multipart stream (compatible with cv2.VideoCapture)
 * - GET /snapshot or GET /frame.jpg : Current single JPEG snapshot
 * - GET /status : JSON server info
 */
class MjpegServer(val port: Int = 8081) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    val isRunning = AtomicBoolean(false)
    val connectedClientsCount = AtomicInteger(0)

    private val latestFrame = AtomicReference<ByteArray?>(null)
    private val activeClients = CopyOnWriteArrayList<StreamClient>()

    private val lastFrameTime = AtomicLong(0L)
    private val minFrameIntervalMillis = 33L // ~30 FPS max when clients are connected

    companion object {
        /** Global shared instance of the inbuilt MJPEG stream server */
        val instance: MjpegServer by lazy {
            MjpegServer(8081).apply { start() }
        }

        /** Discovers the phone's local IPv4 Wi-Fi address, prioritizing physical WLAN interfaces */
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
                val ifaceList = Collections.list(interfaces)
                
                // First pass: look specifically for Wi-Fi interfaces (wlan*, eth*)
                for (iface in ifaceList) {
                    if (iface.isLoopback || !iface.isUp) continue
                    val name = iface.name.lowercase()
                    if (name.startsWith("wlan") || name.startsWith("eth")) {
                        for (addr in Collections.list(iface.inetAddresses)) {
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                return addr.hostAddress
                            }
                        }
                    }
                }
                
                // Second pass: fallback to any non-virtual IPv4
                for (iface in ifaceList) {
                    if (iface.isLoopback || !iface.isUp) continue
                    val name = iface.name.lowercase()
                    if (name.startsWith("rmnet") || name.startsWith("tun") || name.startsWith("dummy") || name.startsWith("p2p")) continue
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        scope.launch {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = server
                while (isActive && isRunning.get()) {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    scope.launch { handleClient(client) }
                }
            } catch (_: Exception) {
                // Socket closed or server stopped
            } finally {
                stopInternal()
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        stopInternal()
    }

    private fun stopInternal() {
        isRunning.set(false)
        activeClients.forEach { it.close() }
        activeClients.clear()
        connectedClientsCount.set(0)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun close() {
        stop()
        scope.cancel()
    }

    /**
     * Converts a CameraX ImageProxy frame into a compressed JPEG byte array
     * and broadcasts it to all active stream clients.
     *
     * Automatically throttles to ~2 FPS when no clients are streaming to save phone battery & CPU.
     */
    fun processImageProxy(imageProxy: ImageProxy, quality: Int = 75, targetWidth: Int = 640) {
        val now = System.currentTimeMillis()
        val interval = if (activeClients.isNotEmpty()) minFrameIntervalMillis else 500L
        val prevTime = lastFrameTime.get()
        if (now - prevTime < interval) {
            imageProxy.close()
            return
        }
        if (!lastFrameTime.compareAndSet(prevTime, now)) {
            imageProxy.close()
            return
        }

        try {
            // CameraX 1.3+ built-in bitmap conversion
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            // Apply rotation and scale if needed
            val processedBitmap = if (rotationDegrees != 0 || bitmap.width > targetWidth) {
                val matrix = Matrix()
                if (rotationDegrees != 0) {
                    matrix.postRotate(rotationDegrees.toFloat())
                }
                if (bitmap.width > targetWidth) {
                    val scale = targetWidth.toFloat() / bitmap.width.toFloat()
                    matrix.postScale(scale, scale)
                }
                val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                scaled
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream(65536)
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            val jpegBytes = baos.toByteArray()

            latestFrame.set(jpegBytes)
            broadcastFrame(jpegBytes)
        } catch (_: Exception) {
            // Drop frame on processing error
        } finally {
            imageProxy.close()
        }
    }

    private fun broadcastFrame(jpegBytes: ByteArray) {
        if (activeClients.isEmpty()) return
        val header = ("--frame\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${jpegBytes.size}\r\n\r\n").toByteArray(Charsets.US_ASCII)
        val footer = "\r\n".toByteArray(Charsets.US_ASCII)

        val deadClients = mutableListOf<StreamClient>()
        for (client in activeClients) {
            try {
                client.out.write(header)
                client.out.write(jpegBytes)
                client.out.write(footer)
                client.out.flush()
            } catch (_: Exception) {
                deadClients.add(client)
            }
        }
        if (deadClients.isNotEmpty()) {
            activeClients.removeAll(deadClients)
            deadClients.forEach { it.close() }
            connectedClientsCount.updateAndGet { maxOf(0, it - deadClients.size) }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            val requestLine = reader.readLine() ?: return@withContext
            
            // Read and discard remaining HTTP headers
            var headerLine: String?
            while (true) {
                headerLine = reader.readLine()
                if (headerLine.isNullOrEmpty()) break
            }

            val out = BufferedOutputStream(socket.getOutputStream())
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase() ?: "GET"
            val path = parts.getOrNull(1) ?: "/"

            if (method == "OPTIONS") {
                val cors = ("HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                out.write(cors)
                out.flush()
                socket.close()
                return@withContext
            }

            if (path.startsWith("/snapshot") || path.endsWith(".jpg")) {
                // Single snapshot JPEG response
                val frame = latestFrame.get()
                if (frame != null) {
                    val response = ("HTTP/1.1 200 OK\r\n" +
                            "Server: URCS-InbuiltCamera\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                    out.write(response)
                    out.write(frame)
                    out.flush()
                } else {
                    val notReady = "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    out.write(notReady.toByteArray(Charsets.US_ASCII))
                    out.flush()
                }
                socket.close()
            } else if (path.startsWith("/status")) {
                // Status JSON
                val body = "{\"status\":\"running\",\"port\":$port,\"clients\":${connectedClientsCount.get()}}"
                val resp = ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json\r\n" +
                        "Content-Length: ${body.length}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n$body").toByteArray(Charsets.US_ASCII)
                out.write(resp)
                out.flush()
                socket.close()
            } else if (path.startsWith("/video") || path.startsWith("/mjpeg") || path == "/" || path.startsWith("/stream")) {
                // Continuous MJPEG stream
                val mjpegHeader = ("HTTP/1.1 200 OK\r\n" +
                        "Server: URCS-InbuiltCamera\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                        "Pragma: no-cache\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
                out.write(mjpegHeader)
                out.flush()

                socket.soTimeout = 0 // Disable read timeout for continuous stream
                val client = StreamClient(socket, out)
                activeClients.add(client)
                connectedClientsCount.incrementAndGet()
            } else {
                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                out.write(notFound.toByteArray(Charsets.US_ASCII))
                out.flush()
                socket.close()
            }
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }
}
