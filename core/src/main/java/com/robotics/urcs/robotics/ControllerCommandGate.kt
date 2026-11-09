package com.robotics.urcs.robotics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes each paired drive command with stop and rejects any motion after stop is latched. */
class ControllerCommandGate {
    private val commandMutex = Mutex()
    private val stopped = AtomicBoolean(false)

    fun latchStop() {
        stopped.set(true)
    }

    suspend fun drive(sendLeft: suspend () -> Unit, sendRight: suspend () -> Unit) {
        commandMutex.withLock {
            if (stopped.get()) return
            sendLeft()
            if (stopped.get()) return
            sendRight()
        }
    }

    suspend fun command(send: suspend () -> Unit) {
        commandMutex.withLock {
            if (stopped.get()) return
            send()
        }
    }

    suspend fun emergencyStop(sendStop: suspend () -> Unit) {
        latchStop()
        commandMutex.withLock { sendStop() }
    }

    fun resume() {
        stopped.set(false)
    }
}
