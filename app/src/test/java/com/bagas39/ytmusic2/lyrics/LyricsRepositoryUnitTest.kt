package com.bagas39.ytmusic2.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRepositoryUnitTest {

    @Test
    fun sanitizeTitleAndArtistHandlesEnDashAndVerticalVideo() {
        val (title, artist) = LyricsMatcher.sanitizeTitleAndArtist(
            rawTitle = ".Feast – Berselancar ⧸ Kami Belum Tentu (Vertical Video) (Official Music Video)",
            rawArtist = ""
        )

        assertEquals(".Feast", artist)
        assertEquals("Berselancar ⧸ Kami Belum Tentu", title)
    }

    @Test
    fun extractTitleSearchVariantsSplitsDualTrackWithoutBreakingCompactSlashes() {
        val dualVariants = LyricsMatcher.extractTitleSearchVariants("Berselancar ⧸ Kami Belum Tentu")
        assertEquals(
            listOf("Berselancar ⧸ Kami Belum Tentu", "Berselancar", "Kami Belum Tentu"),
            dualVariants
        )

        val spaceSlashVariants = LyricsMatcher.extractTitleSearchVariants("Pemakaman / Berita Kehilangan")
        assertEquals(
            listOf("Pemakaman / Berita Kehilangan", "Pemakaman", "Berita Kehilangan"),
            spaceSlashVariants
        )

        val bandSlashVariants = LyricsMatcher.extractTitleSearchVariants("AC/DC Tribute")
        assertEquals(listOf("AC/DC Tribute"), bandSlashVariants)
    }

    @Test
    fun scoreCandidateMetadataPrefersExactDurationOverStudioMismatchAndCorruptDuration() {
        val normTitle = LyricsMatcher.normalizeForMatch("Kami Belum Tentu")
        val normArtist = LyricsMatcher.normalizeForMatch(".Feast")
        val targetDurationSec = 236.82

        val verticalVideoScore = LyricsMatcher.scoreCandidateMetadata(
            normTrack = normTitle,
            normArtist = normArtist,
            candidateDurationSec = 236.0,
            normTargetTitle = normTitle,
            normTargetArtist = normArtist,
            targetDurationSec = targetDurationSec
        )
        val studioScore = LyricsMatcher.scoreCandidateMetadata(
            normTrack = normTitle,
            normArtist = normArtist,
            candidateDurationSec = 233.0,
            normTargetTitle = normTitle,
            normTargetArtist = normArtist,
            targetDurationSec = targetDurationSec
        )
        val corruptShortScore = LyricsMatcher.scoreCandidateMetadata(
            normTrack = normTitle,
            normArtist = normArtist,
            candidateDurationSec = 4.0,
            normTargetTitle = normTitle,
            normTargetArtist = normArtist,
            targetDurationSec = targetDurationSec
        )

        assertTrue(verticalVideoScore > studioScore)
        assertTrue(studioScore > corruptShortScore)
    }

    @Test
    fun durationTagRoundTripStaysTransparentToLrcParser() {
        val sample = "[00:49.42] Tiang masih berdiri\n[03:49.61] (Namun kami belum tentu!)"
        val stamped = LyricsMatcher.formatLrcWithDurationTag(sample, 236821L)

        assertEquals(236821L, LyricsMatcher.extractDurationTagMs(stamped))
        val reStamped = LyricsMatcher.formatLrcWithDurationTag(stamped, 236000L)
        assertEquals(236000L, LyricsMatcher.extractDurationTagMs(reStamped))

        val parsed = LrcParser.parse(stamped)
        assertEquals(2, parsed.size)
        assertEquals(49420L, parsed[0].timeMs)
        assertEquals("Tiang masih berdiri", parsed[0].text)
        assertEquals(229610L, parsed[1].timeMs)
        assertEquals("(Namun kami belum tentu!)", parsed[1].text)
    }

    @Test
    fun scoreSyncedLineCadencePenalizesAnomalousEarlyFirstLineJump() {
        val erratic = listOf(
            LyricLine(41280L, "Tiang masih berdiri"),
            LyricLine(51490L, "Bendera makin tinggi"),
            LyricLine(53900L, "Berkibar tiap pagi"),
            LyricLine(55930L, "Dimakan matahari"),
            LyricLine(58460L, "Merah makin memudar")
        )
        val clean = listOf(
            LyricLine(49420L, "Tiang masih berdiri"),
            LyricLine(51590L, "Bendera makin tinggi"),
            LyricLine(53930L, "Berkibar tiap pagi"),
            LyricLine(56150L, "Dimakan matahari"),
            LyricLine(58440L, "Merah makin memudar")
        )

        assertTrue(LyricsMatcher.scoreSyncedLineCadence(clean) > LyricsMatcher.scoreSyncedLineCadence(erratic))
    }
}
