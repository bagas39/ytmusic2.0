package com.example.ytmusic2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun testFormatDurationZeroAndNegative() {
        assertEquals("00:00", FormatUtils.formatDuration(0))
        assertEquals("00:00", FormatUtils.formatDuration(-500))
    }

    @Test
    fun testFormatDurationMinutesSeconds() {
        assertEquals("00:05", FormatUtils.formatDuration(5000))
        assertEquals("01:05", FormatUtils.formatDuration(65000))
        assertEquals("12:34", FormatUtils.formatDuration((12 * 60 + 34) * 1000))
    }

    @Test
    fun testFormatDurationHours() {
        assertEquals("1:00:00", FormatUtils.formatDuration(3600 * 1000))
        assertEquals("1:05:09", FormatUtils.formatDuration((3600 + 309) * 1000))
    }

    @Test
    fun testFormatFileSizeMb() {
        val bytes = 1024L * 1024L * 5 // 5 MB
        val formatted = FormatUtils.formatFileSizeMb(bytes)
        // Format could use comma or period depending on locale, verify MB is present
        assert(formatted.contains("5") && formatted.endsWith("MB"))
    }
}
