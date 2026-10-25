package com.mouthband.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TakeFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun header(magic: Int, rate: Int): ByteArray =
        ByteBuffer.allocate(TakeFile.HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(magic).putInt(rate).array()

    private fun expectFailure(file: File, text: String) {
        try {
            TakeFile.read(file)
            fail("$text should be refused")
        } catch (expected: IOException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun aTakeReadsBackExactlyAndLeavesNoTempFile() {
        val target = File(folder.root, "songs/1/bass_1.f32")
        val samples = FloatArray(1_000) { it / 500f - 1f }
        TakeFile.write(target, 48_000, samples)
        val audio = TakeFile.read(target)
        assertEquals(48_000, audio.sampleRate)
        assertArrayEquals(samples, audio.samples, 0f)
        assertFalse(File(target.parentFile, "bass_1.f32.tmp").exists())
    }

    @Test
    fun aTruncatedOrForeignFileIsRefused() {
        val tiny = folder.newFile("tiny.f32").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        expectFailure(tiny, "a file shorter than its header")
        val foreign = folder.newFile("foreign.f32").apply { writeBytes(header(0x12345678, 48_000) + ByteArray(40)) }
        expectFailure(foreign, "a file with the wrong magic")
    }

    @Test
    fun aDamagedSampleRateIsRefused() {
        val magic = 0x3146424D
        for (rate in listOf(0, -1, 100, 7_999, 192_001, Int.MAX_VALUE)) {
            val file = folder.newFile("rate$rate.f32").apply { writeBytes(header(magic, rate) + ByteArray(400)) }
            expectFailure(file, "sample rate $rate")
        }
    }

    @Test
    fun aHalfWrittenTrailingSampleIsIgnored() {
        val file = File(folder.root, "odd.f32")
        TakeFile.write(file, 44_100, floatArrayOf(0.5f, -0.5f))
        file.appendBytes(byteArrayOf(9, 9))
        assertEquals(2, TakeFile.read(file).samples.size)
    }
}

class TakeStoreSweepTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun leftoverScratchFilesAreRemovedAndRealTakesStay() {
        val store = com.mouthband.data.TakeStore(folder.root)
        val take = store.takeFile(7, com.mouthband.model.TrackType.BASS, 1)
        TakeFile.write(take, 48_000, FloatArray(100))
        val stale = store.recordingTemp(7).apply { writeBytes(ByteArray(10)) }
        val halfWritten = File(take.parentFile, "bass_2.f32.tmp").apply { writeBytes(ByteArray(10)) }
        assertEquals(2, store.sweepTemporaryFiles())
        assertFalse(stale.exists() || halfWritten.exists())
        assertTrue(take.exists())
        assertEquals(0, store.sweepTemporaryFiles())
    }

    @Test
    fun sweepingWithoutAnySongsDoesNothing() {
        assertEquals(0, com.mouthband.data.TakeStore(folder.root).sweepTemporaryFiles())
    }
}
