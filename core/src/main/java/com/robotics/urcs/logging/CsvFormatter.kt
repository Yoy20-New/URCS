package com.robotics.urcs.logging

object CsvFormatter {
    fun row(vararg fields: String): String = fields.joinToString(",") { field ->
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }
}
