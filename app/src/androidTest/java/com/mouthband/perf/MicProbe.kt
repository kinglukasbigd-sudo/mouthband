package com.mouthband.perf

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.MouthBandApp
import com.mouthband.audio.CaptureConsumer
import com.mouthband.audio.CaptureSink
import com.mouthband.audio.Recorder
import com.mouthband.audio.RecorderStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records what the phone's real microphone hears, through the app's own capture path, into
 * `files/mic-probe.wav` (16-bit mono): a fixture for tuning the live analysis against a real room
 * (`tools/phone_mic_test.sh --probe`). Does nothing unless the run asks (`-e micProbe SECONDS`).
 */
@RunWith(AndroidJUnit4::class)
class MicProbe {
    private val app = ApplicationProvider.getApplicationContext<MouthBandApp>()
    private val seconds get() = InstrumentationRegistry.getArguments().getString("micProbe")?.toIntOrNull()

    @Test
    fun recordTheRoom() {
        val length = seconds
        assumeTrue("only when asked", length != null)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        val rate = app.container.engine.sampleRate
        val recorder = Recorder(app, rate)
        val consumer = CaptureConsumer(recorder)
        val heard = FloatArray(rate * requireNotNull(length))
        var seen = 0
        val sink = CaptureSink { samples, count ->
            val n = minOf(count, heard.size - seen)
            if (n > 0) System.arraycopy(samples, 0, heard, seen, n)
            seen += count
        }
        assertEquals(RecorderStart.OK, recorder.start(null))
        runBlocking {
            val job = CoroutineScope(Dispatchers.Default).launch { consumer.drain(listOf(sink)) }
            delay(length * MS_PER_SECOND)
            recorder.stop()
            delay(SETTLE_MS)
            job.cancelAndJoin()
        }
        writeWav(File(app.filesDir, "mic-probe.wav"), heard, rate, minOf(seen, heard.size))
    }

    private fun writeWav(file: File, samples: FloatArray, rate: Int, count: Int) {
        val data = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) data.putShort((samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(HEADER - 8 + count * 2).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(count * 2)
        RandomAccessFile(file, "rw").use {
            it.setLength(0)
            it.write(header.array())
            it.write(data.array())
        }
    }

    private companion object {
        const val HEADER = 44
        const val MS_PER_SECOND = 1000L
        const val SETTLE_MS = 300L
    }
}
