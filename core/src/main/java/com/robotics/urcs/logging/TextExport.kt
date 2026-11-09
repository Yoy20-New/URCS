package com.robotics.urcs.logging

import java.io.OutputStream

object TextExport {
    fun write(destination: OutputStream?, text: String) {
        requireNotNull(destination) { "Could not open export destination" }.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }
}
