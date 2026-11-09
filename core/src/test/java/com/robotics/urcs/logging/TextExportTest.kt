package com.robotics.urcs.logging

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class TextExportTest {
    @Test
    fun writesTheWholeExportBeforeReturning() {
        val output = ByteArrayOutputStream()

        TextExport.write(output, "timestamp,source\n1,sensor\n")

        assertEquals("timestamp,source\n1,sensor\n", output.toString(Charsets.UTF_8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nullDocumentStreamIsAnExportFailure() {
        TextExport.write(null, "must not report success")
    }
}
