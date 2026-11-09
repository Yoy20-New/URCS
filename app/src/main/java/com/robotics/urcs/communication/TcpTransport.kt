package com.robotics.urcs.communication

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

class TcpTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    private val mutableIncoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = mutableIncoming.asSharedFlow()

    @Volatile private var socket: Socket? = null
    @Volatile private var writer: BufferedWriter? = null
    private val writeMutex = Mutex()
    private val connectMutex = Mutex()
    private var readerJob: Job? = null

    suspend fun connect(host: String, port: Int): Result<Unit> = connectMutex.withLock {
        withContext(Dispatchers.IO) {
            disconnectInternal()
            mutableState.value = ConnectionState.CONNECTING
            var candidate: Socket? = null
            try {
                require(host.isNotBlank()) { "Host is required" }
                require(port in 1..65535) { "Port must be between 1 and 65535" }
                val s = Socket()
                candidate = s
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host.trim(), port), 5_000)
                socket = s
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
                mutableState.value = ConnectionState.CONNECTED
                readerJob = scope.launch { readLines(s) }
                Result.success(Unit)
            } catch (error: Exception) {
                runCatching { candidate?.close() }
                clearSocket()
                mutableState.value = ConnectionState.ERROR
                Result.failure(error)
            }
        }
    }

    suspend fun send(line: String): Result<Unit> = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val activeWriter = writer ?: return@withLock Result.failure(IllegalStateException("Not connected"))
            val activeSocket = socket
            try {
                activeWriter.write(line)
                activeWriter.newLine()
                activeWriter.flush()
                Result.success(Unit)
            } catch (ce: CancellationException) {
                throw ce
            } catch (error: Exception) {
                if (socket === activeSocket && activeSocket != null) {
                    clearSocket()
                    mutableState.value = ConnectionState.ERROR
                }
                Result.failure(error)
            }
        }
    }

    suspend fun disconnect() = connectMutex.withLock {
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                disconnectInternal()
            }
        }
    }

    fun close() {
        scope.cancel()
        readerJob?.cancel()
        readerJob = null
        if (writeMutex.tryLock()) {
            try {
                clearSocket()
            } finally {
                writeMutex.unlock()
            }
        } else {
            clearSocket()
        }
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private suspend fun readLines(activeSocket: Socket) {
        try {
            BufferedReader(InputStreamReader(activeSocket.getInputStream(), Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    mutableIncoming.emit(line)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            if (socket === activeSocket) mutableState.value = ConnectionState.ERROR
        } finally {
            if (socket === activeSocket) {
                val failed = mutableState.value == ConnectionState.ERROR
                clearSocket()
                if (!failed) mutableState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun disconnectInternal() {
        clearSocket()
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private fun clearSocket() {
        readerJob?.cancel()
        readerJob = null
        val activeSocket = socket
        socket = null
        writer = null
        runCatching { activeSocket?.close() }
    }
}
