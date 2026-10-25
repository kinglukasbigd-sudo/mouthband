package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Fft
import com.mouthband.dsp.Rms
import com.mouthband.dsp.Window
import kotlin.math.exp
import kotlin.math.ln

/**
 * Tells a sung take from noise. Singing and humming are tonal: their energy sits on a few
 * harmonics and the bins between them stay low. Room noise, a fan or a pressed-and-held record
 * button spreads evenly over the spectrum. The spectral flatness (geometric over arithmetic mean
 * of the power in the voice band) of the loudest moments is near 1 for noise and far below it for a
 * voice, at any loudness, so a vocal take of noise is "didn't hear anything" and is never boosted
 * into a loud hiss.
 */
object VoicePresence {
    /** Flatness at or above this is noise. White noise averages ≈ 0.9; a voice stays under 0.2. */
    const val MAX_VOICE_FLATNESS = 0.5f
    private const val FRAME = 1024
    private const val LOUDEST_SHARE = 0.2f
    private const val MIN_FRAMES = 4
    private const val BAND_LOW_HZ = 100f
    private const val BAND_HIGH_HZ = 4_000f
    private const val POWER_FLOOR = 1e-20

    /** Whether the loop part of [take] holds a voice. */
    fun hasVoice(take: AlignedTake): Boolean =
        flatness(take.samples, take.prerollFrames, take.loopFrames, take.sampleRate) < MAX_VOICE_FLATNESS

    /** Spectral flatness 0..1 of the loudest frames of `[from, from + length)`; 1 when there is nothing to judge. */
    fun flatness(samples: FloatArray, from: Int, length: Int, sampleRate: Int): Float {
        val hop = FRAME / 2
        val count = (length - FRAME) / hop + 1
        if (count < 1 || from < 0 || from + length > samples.size) return 1f
        val levels = FloatArray(count) { Rms.of(samples, from + it * hop, FRAME) }
        val keep = maxOf(MIN_FRAMES, (count * LOUDEST_SHARE).toInt()).coerceAtMost(count)
        val threshold = levels.sortedDescending()[keep - 1]
        val fft = Fft(FRAME)
        val window = Window.hann(FRAME)
        val frame = FloatArray(FRAME)
        val re = FloatArray(FRAME / 2 + 1)
        val im = FloatArray(FRAME / 2 + 1)
        val magnitude = FloatArray(FRAME / 2 + 1)
        val power = DoubleArray(FRAME / 2 + 1)
        var used = 0
        for (t in 0 until count) {
            if (levels[t] < threshold || used >= keep) continue
            System.arraycopy(samples, from + t * hop, frame, 0, FRAME)
            Window.apply(frame, window)
            fft.magnitudes(frame, re, im, magnitude)
            for (k in magnitude.indices) power[k] += magnitude[k].toDouble() * magnitude[k]
            used++
        }
        val low = (BAND_LOW_HZ * FRAME / sampleRate).toInt().coerceAtLeast(1)
        val high = (BAND_HIGH_HZ * FRAME / sampleRate).toInt().coerceAtMost(magnitude.size - 1)
        if (used == 0 || high <= low) return 1f
        var logSum = 0.0
        var sum = 0.0
        for (k in low..high) {
            val p = power[k] / used + POWER_FLOOR
            logSum += ln(p)
            sum += p
        }
        val bins = high - low + 1
        return (exp(logSum / bins) / (sum / bins)).toFloat()
    }
}
