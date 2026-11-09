package com.robotics.urcs.ui

enum class AppSection(val label: String, val glyph: String) {
    DASHBOARD("Dashboard", "▦"),
    SENSOR_LAB("Sensor Lab", "⌁"),
    CONTROLLER("Controller", "◎"),
    CONNECTIONS("Connections", "⇄"),
    DEVICE_CONTROLS("Phone Controls", "▤"),
    CAMERA("Camera", "▣"),
    AUTOMATION("Automation", "⌘"),
    DATA_LOGGER("Data Logger", "⊟"),
    TERMINAL("Terminal", ">_"),
    SETTINGS("Settings", "⚙")
}

class SectionSelection(initial: AppSection = AppSection.DASHBOARD) {
    @Volatile
    var selected: AppSection = initial
        private set

    fun select(section: AppSection) {
        selected = section
    }
}
