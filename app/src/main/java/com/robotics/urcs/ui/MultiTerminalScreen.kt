package com.robotics.urcs.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import com.robotics.urcs.communication.ConnectionState
import com.robotics.urcs.communication.TcpTransport
import kotlinx.coroutines.launch

/**
 * In-memory terminal tabs for the single shared TCP transport. The state intentionally
 * remains in the composition owner rather than the transport: closing or creating a tab
 * does not create or disconnect a socket.
 */
class TerminalSessionsState {
    private var nextId = 1
    val sessions = mutableStateListOf(TerminalSession(id = nextId, label = "Terminal 1"))
    var selectedSessionId by mutableStateOf(nextId)
        private set

    val activeSession: TerminalSession
        get() = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.first()

    fun createSession(): TerminalSession {
        nextId += 1
        return TerminalSession(id = nextId, label = "Terminal $nextId").also {
            sessions += it
            selectedSessionId = it.id
        }
    }

    fun selectSession(id: Int) {
        if (sessions.any { it.id == id }) selectedSessionId = id
    }

    fun closeSession(id: Int) {
        if (sessions.size == 1) return
        val removedIndex = sessions.indexOfFirst { it.id == id }
        if (removedIndex < 0) return
        sessions.removeAt(removedIndex)
        if (selectedSessionId == id) {
            selectedSessionId = sessions.getOrNull((removedIndex - 1).coerceAtLeast(0))?.id ?: sessions.first().id
        }
    }

    fun clearActiveSession() {
        activeSession.lines.clear()
        activeSession.status = "Terminal cleared"
    }

    /** Incoming TCP data is visible in every open terminal tab. */
    fun recordIncoming(line: String) {
        sessions.forEach { it.append("RX  $line") }
    }

    fun recordSent(line: String) {
        activeSession.append("TX  $line")
        activeSession.draft = ""
    }
}

class TerminalSession internal constructor(
    val id: Int,
    val label: String
) {
    val lines = mutableStateListOf<String>()
    var draft by mutableStateOf("")
    var mode by mutableStateOf("Text")
    var status by mutableStateOf("")

    internal fun append(line: String) {
        lines += line
        if (lines.size > 250) lines.removeAt(0)
    }
}

@Composable
fun MultiTerminalScreen(
    transport: TcpTransport,
    connection: ConnectionState,
    sessions: TerminalSessionsState
) {
    val scope = rememberCoroutineScope()
    val active = sessions.activeSession
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val statusColor = when (connection) {
        ConnectionState.CONNECTED -> if (dark) Color(0xFF70D694) else Color(0xFF168044)
        ConnectionState.CONNECTING -> if (dark) Color(0xFFFFD166) else Color(0xFF9A6700)
        ConnectionState.ERROR -> if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        Modifier.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader("Terminals", "Multiple views over one shared TCP connection")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                connection.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
            OutlinedButton(
                onClick = { sessions.createSession() },
                modifier = Modifier.semantics { contentDescription = "Create a new terminal" }
            ) { Text("New terminal") }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.sessions.forEach { session ->
                FilterChip(
                    selected = session.id == sessions.selectedSessionId,
                    onClick = { sessions.selectSession(session.id) },
                    label = { Text(session.label) },
                    trailingIcon = if (sessions.sessions.size > 1) {
                        {
                            TextButton(
                                onClick = { sessions.closeSession(session.id) },
                                modifier = Modifier.semantics { contentDescription = "Close ${session.label}" }
                            ) { Text("×") }
                        }
                    } else null
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Text", "ASCII", "JSON").forEach { mode ->
                FilterChip(selected = active.mode == mode, onClick = { active.mode = mode }, label = { Text(mode) })
            }
        }
        LazyColumn(
            Modifier.weight(1f).heightIn(min = 180.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (active.lines.isEmpty()) {
                item { Text("No terminal messages yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                itemsIndexed(active.lines, key = { index, line -> "$index:$line" }) { _, line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = active.draft,
                onValueChange = { active.draft = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") },
                singleLine = true
            )
            Button(
                onClick = {
                    val outgoing = active.draft.trim()
                    when {
                        outgoing.isBlank() -> active.status = "Enter a message first"
                        active.mode == "ASCII" && outgoing.any { it.code > 127 } -> active.status = "ASCII mode accepts characters 0–127 only"
                        active.mode == "JSON" && runCatching { JsonParser.parseString(outgoing) }.isFailure -> active.status = "Invalid JSON"
                        else -> scope.launch {
                            val sent = transport.send(outgoing)
                            if (sent.isSuccess) {
                                sessions.recordSent(outgoing)
                                active.status = "Sent"
                            } else {
                                active.status = "Send failed: ${sent.exceptionOrNull()?.message ?: "connection error"}"
                            }
                        }
                    }
                },
                enabled = connection == ConnectionState.CONNECTED
            ) { Text("Send") }
        }
        if (active.status.isNotBlank()) Text(active.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { sessions.clearActiveSession() }) { Text("Clear this terminal") }
    }
}
