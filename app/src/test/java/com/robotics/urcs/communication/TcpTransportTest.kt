package com.robotics.urcs.communication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class TcpTransportTest {
    @Test
    fun concurrentSendsStayOnSeparateProtocolLines() = runBlocking {
        val server = ServerSocket(0)
        val received = async(Dispatchers.IO) {
            server.accept().use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                List(100) { reader.readLine() }
            }
        }
        val transport = TcpTransport()
        try {
            assertTrue(transport.connect("127.0.0.1", server.localPort).isSuccess)
            (0 until 100).map { index -> async { transport.send("message-$index").getOrThrow() } }.awaitAll()
            val lines = withTimeout(5_000) { received.await() }
            assertEquals((0 until 100).map { "message-$it" }.toSet(), lines.toSet())
        } finally {
            transport.close()
            server.close()
        }
        Unit
    }

    @Test
    fun remoteSocketFailureRemainsVisibleAsError() = runBlocking {
        val server = ServerSocket(0)
        val accepted = async(Dispatchers.IO) {
            server.accept().apply { setSoLinger(true, 0) }.close()
        }
        val transport = TcpTransport()
        try {
            assertTrue(transport.connect("127.0.0.1", server.localPort).isSuccess)
            accepted.await()
            transport.send("trigger-rst")
            withTimeout(5_000) { transport.state.first { it == ConnectionState.ERROR } }
        } finally {
            transport.close()
            server.close()
        }
        Unit
    }
}
