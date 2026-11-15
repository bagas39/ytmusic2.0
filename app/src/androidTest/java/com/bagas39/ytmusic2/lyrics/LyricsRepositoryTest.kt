package com.bagas39.ytmusic2.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LyricsRepositoryTest {

    @Test
    fun testPrimaryProvider_Lrclib() {
        val latch = CountDownLatch(1)
        var result: LyricsResult? = null

        LyricsRepository.getLyricsAsync(
            title = "Peradaban",
            artist = ".Feast"
        ) { res ->
            result = res
            latch.countDown()
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("Request timeout", completed)
        assertNotNull("Hasil lirik LRCLIB tidak boleh null", result)
        assertTrue("Lirik harus sinkron", result!!.isSynced)
        assertTrue("Harus ada baris lirik", result!!.lines.isNotEmpty())
    }

    @Test
    fun testFallbackProvider_NicheSong_NetEase() {
        // "Bunga Maaf" by "The Jansen" tidak ada di LRCLIB, tapi tersedia di NetEase
        val latch = CountDownLatch(1)
        var result: LyricsResult? = null

        LyricsRepository.getLyricsAsync(
            title = "Bunga Maaf",
            artist = "The Jansen"
        ) { res ->
            result = res
            latch.countDown()
        }

        val completed = latch.await(15, TimeUnit.SECONDS)
        assertTrue("Request timeout", completed)
        assertNotNull("Fallback ke NetEase harus menemukan lirik Bunga Maaf", result)
        assertTrue("Lirik fallback harus memiliki baris lirik", result!!.lines.isNotEmpty())
        assertTrue("Konten lirik raw tidak boleh kosong", result!!.rawLrc.isNotBlank())
    }
}
