package com.robotics.urcs.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvFormatterTest {
    @Test
    fun fieldsWithCommasQuotesAndNewlinesAreEscaped() {
        assertEquals("\"sensor,one\",\"x\"\"y\",\"line1\nline2\"", CsvFormatter.row("sensor,one", "x\"y", "line1\nline2"))
    }

    @Test
    fun ordinaryFieldsStayReadable() {
        assertEquals("2026-11-08,accel,1.25", CsvFormatter.row("2026-11-08", "accel", "1.25"))
    }
}
