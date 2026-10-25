package com.mouthband.convert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Rms
import com.mouthband.model.KeyMode
import com.mouthband.model.MusicalKey
import com.mouthband.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The real pitch model (SwiftF0) must find the hummed notes at every recording level, from a
 * whisper to a clipped shout, through the same [TakeProcessor] the app uses.
 */
@RunWith(AndroidJUnit4::class)
class ClarityInstrumentedTest {
    private val rate = 48_000

    private fun midiToHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** A voice-like hum (12 harmonics, light vibrato) of [midi] for [seconds], starting at [start] s. */
    private fun addHum(out: FloatArray, midi: Int, start: Double, seconds: Double, offset: Int) {
        val hz = midiToHz(midi)
        var phase = 0.0
        val from = offset + (start * rate).toInt()
        val n = (seconds * rate).toInt()
        for (i in 0 until n) {
            phase += 2 * PI * hz * (1 + VIBRATO_DEPTH * sin(2 * PI * VIBRATO_HZ * i / rate)) / rate
            var v = 0.0
            for (k in 1..HARMONICS) v += sin(k * phase) / k
            val env = minOf(1.0, i / (ATTACK * rate), (n - i) / (ATTACK * rate))
            out[from + i] += (env * v).toFloat()
        }
    }

    private fun take(peakDb: Float): AlignedTake {
        val preroll = rate / 10
        val loop = 4 * rate
        val samples = FloatArray(preroll + loop + rate / 4)
        listOf(48 to 0.0, 55 to 1.0, 57 to 2.0).forEach { (midi, start) -> addHum(samples, midi, start, 0.5, preroll) }
        val loudest = Rms.peak(samples)
        val random = Random(2)
        // Room noise 40 dB under the hum, at whatever level the phone recorded it.
        val noise = Rms.fromDb(peakDb - NOISE_BELOW_DB)
        val gain = Rms.fromDb(peakDb) / loudest
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain + random.nextGaussian().toFloat() * noise).coerceIn(-1f, 1f)
        }
        return AlignedTake(samples, preroll, loop, rate)
    }

    @Test
    fun theModelKeepsTheThreeNotesFromWhisperToClipping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tracker = SwiftF0PitchTracker.load(context.assets)
        assertNotNull("model loads", tracker)
        tracker?.use { model ->
            val processor = TakeProcessor(rate, BeatboxConverter(rate), PitchConverter(rate, primary = { model }))
            val key = MusicalKey(0, KeyMode.MAJOR)
            val context48 = TakeProcessor.Context(120, key, templates = null, clicksAudible = false)
            for (peakDb in listOf(-66f, -55f, -40f, -25f, -12f, -3f, 0f, 6f)) {
                val result = processor.process(TrackType.BASS, take(peakDb), context48)
                assertNull("$peakDb dBFS: ${result.problem}", result.problem)
                val midis = result.events?.notes?.map { it.midi }.orEmpty().sorted()
                assertEquals("$peakDb dBFS", listOf(33, 36, 43), midis)
            }
        }
    }

    private companion object {
        const val HARMONICS = 12
        const val VIBRATO_DEPTH = 0.004
        const val VIBRATO_HZ = 5.5
        const val ATTACK = 0.01
        const val NOISE_BELOW_DB = 40f
    }
}
