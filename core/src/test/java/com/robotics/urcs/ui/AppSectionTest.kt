package com.robotics.urcs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSectionTest {
    @Test
    fun navigationStartsOnDashboardAndPreservesRequestedSection() {
        val navigation = SectionSelection()
        assertEquals(AppSection.DASHBOARD, navigation.selected)
        navigation.select(AppSection.SENSOR_LAB)
        assertEquals(AppSection.SENSOR_LAB, navigation.selected)
    }

    @Test
    fun allPrimarySectionsHaveUserFacingLabels() {
        assertEquals(
            listOf("Dashboard", "Sensor Lab", "Controller", "Connections", "Phone Controls", "Camera", "Automation", "Data Logger", "Terminal", "Settings"),
            AppSection.entries.map(AppSection::label)
        )
    }

    @Test
    fun allPrimarySectionsHaveUniqueGlyphs() {
        val glyphs = AppSection.entries.map(AppSection::glyph)
        assertEquals(glyphs.distinct().size, glyphs.size)
    }
}
