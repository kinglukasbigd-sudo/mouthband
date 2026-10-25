package com.mouthband.convert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/** Runs the bundled SwiftF0 model through ONNX Runtime on a device (spec §2.5). */
@RunWith(AndroidJUnit4::class)
class SwiftF0InstrumentedTest {
    /**
     * A voice-like hum: 12 harmonics with light vibrato. (The model is trained on voices; a bare
     * sine gets correct pitch but only 0.5–0.85 confidence, below the 0.9 threshold.)
     */
    private fun hum(hz: Double): FloatArray {
        val rate = PitchTracker.SAMPLE_RATE
        var phase = 0.0
        return FloatArray(rate) { n ->
            phase += 2 * PI * hz * (1 + VIBRATO_DEPTH * sin(2 * PI * VIBRATO_HZ * n / rate)) / rate
            var v = 0.0
            for (k in 1..HARMONICS) v += sin(k * phase) / k
            (HUM_GAIN * v).toFloat()
        }
    }

    @Test
    fun bundledModelTracksSines() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tracker = SwiftF0PitchTracker.load(context.assets)
        assertNotNull("model loads", tracker)
        tracker?.use {
            for (hz in listOf(110.0, 220.0, 440.0, 880.0)) {
                val track = it.track(hum(hz))
                val voiced = (4 until track.size - 4).filter { t -> track.confidence[t] >= it.voicedThreshold }
                assertTrue("$hz Hz voiced", voiced.size > (track.size - 8) * 0.8)
                val median = voiced.map { t -> track.hz[t].toDouble() }.sorted()[voiced.size / 2]
                assertEquals("$hz Hz", hz, median, hz * 0.02)
            }
        }
    }

    @Test
    fun outputsAreReadWhateverTheirElementType() {
        // The shipped model returns pitch as DOUBLE and confidence as FLOAT.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SwiftF0PitchTracker.load(context.assets)?.use {
            val track = it.track(hum(220.0))
            assertTrue(track.hz.count { f -> f > 0f } > track.size / 2)
        }
    }

    @Test
    fun silenceIsUnvoiced() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SwiftF0PitchTracker.load(context.assets)?.use {
            val track = it.track(FloatArray(PitchTracker.SAMPLE_RATE))
            assertTrue(track.confidence.all { c -> c == 0f })
        }
    }

    private companion object {
        const val HARMONICS = 12
        const val VIBRATO_DEPTH = 0.004
        const val VIBRATO_HZ = 5.5
        const val HUM_GAIN = 0.25
    }
}
