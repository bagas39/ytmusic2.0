package com.bagas39.ytmusic2.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parseSyncedLrcSupportsFractionalPrecisionAndSorting() {
        val raw = """
            [ti:Test Song]
            [ar:Test Artist]
            [00:12.50]Line two
            [00:05.5]Line one
            [00:20:250][00:30.00]Repeated chorus
        """.trimIndent()

        val lines = LrcParser.parse(raw)
        assertEquals(4, lines.size)
        assertEquals(5500L, lines[0].timeMs)
        assertEquals("Line one", lines[0].text)
        assertEquals(12500L, lines[1].timeMs)
        assertEquals("Line two", lines[1].text)
        assertEquals(20250L, lines[2].timeMs)
        assertEquals("Repeated chorus", lines[2].text)
        assertEquals(30000L, lines[3].timeMs)
        assertEquals("Repeated chorus", lines[3].text)
    }

    @Test
    fun parseAppliesOffsetTagSafely() {
        val raw = """
            [offset:-1000]
            [00:00.50]Clamped zero
            [00:05.00]Shifted back
        """.trimIndent()

        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals(4000L, lines[1].timeMs)
    }

    @Test
    fun parsePlainStripsMetadataTagsAndEmptyLines() {
        val raw = """
            [ti:Plain]

            First line
            Second line
        """.trimIndent()

        val lines = LrcParser.parsePlain(raw)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.timeMs == -1L })
        assertEquals("First line", lines[0].text)
        assertEquals("Second line", lines[1].text)
    }

    @Test
    fun findActiveLineIndexTracksPlaybackPosition() {
        val lines = listOf(
            LyricLine(5000L, "A"),
            LyricLine(10000L, "B"),
            LyricLine(15000L, "C")
        )

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 4999L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 9999L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 10000L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 18000L))
        assertEquals(-1, LrcParser.findActiveLineIndex(emptyList(), 5000L))
    }
}
