package com.example.ytmusic2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun testIsAudioValidExtensions() {
        assertTrue(FileUtils.isAudio("song.mp3"))
        assertTrue(FileUtils.isAudio("song.m4a"))
        assertTrue(FileUtils.isAudio("song.webm"))
        assertTrue(FileUtils.isAudio("song.opus"))
        assertTrue(FileUtils.isAudio("song.aac"))
        assertTrue(FileUtils.isAudio("song.ogg"))
        assertTrue(FileUtils.isAudio("song.flac"))
        assertTrue(FileUtils.isAudio("song.wav"))
    }

    @Test
    fun testIsAudioCaseInsensitive() {
        assertTrue(FileUtils.isAudio("SONG.MP3"))
        assertTrue(FileUtils.isAudio("Track.M4A"))
        assertTrue(FileUtils.isAudio("Audio.FLAC"))
    }

    @Test
    fun testIsAudioInvalidExtensions() {
        assertFalse(FileUtils.isAudio("video.mp4"))
        assertFalse(FileUtils.isAudio("document.pdf"))
        assertFalse(FileUtils.isAudio("archive.zip"))
        assertFalse(FileUtils.isAudio("image.png"))
        assertFalse(FileUtils.isAudio("no_extension"))
        assertFalse(FileUtils.isAudio(""))
    }
}
