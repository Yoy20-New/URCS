package com.robotics.urcs.logging

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.robotics.urcs.logging.CsvFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

class SessionLogger(context: Context) {
    private val file = File(context.applicationContext.filesDir, "urcs-session.csv")
    private val jsonFile = File(context.applicationContext.filesDir, "urcs-session.jsonl")
    private val mutex = Mutex()

    suspend fun append(timestampMillis: Long, source: String, value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) file.writeText("timestamp,source,value\n")
            val timestamp = Instant.ofEpochMilli(timestampMillis).toString()
            file.appendText(CsvFormatter.row(timestamp, source, value) + "\n")
            val event = JsonObject().apply {
                addProperty("timestamp", timestamp)
                addProperty("source", source)
                addProperty("value", value)
            }
            jsonFile.appendText(event.toString() + "\n")
        }
    }

    suspend fun readRecent(limit: Int = 150): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock { if (!file.exists()) emptyList() else file.readLines().takeLast(limit).asReversed() }
    }

    suspend fun readAll(): String = withContext(Dispatchers.IO) {
        mutex.withLock { if (file.exists()) file.readText() else "timestamp,source,value\n" }
    }

    suspend fun readJson(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val events = JsonArray()
            if (jsonFile.exists()) jsonFile.forEachLine { line -> runCatching { events.add(com.google.gson.JsonParser.parseString(line)) } }
            events.toString()
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
            if (jsonFile.exists()) java.io.RandomAccessFile(jsonFile, "rw").use { it.fd.sync() }
        }
    }
}
