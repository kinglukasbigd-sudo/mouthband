package com.mouthband.convert

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * YIN pitch estimator (de Cheveigné & Kawahara, 2002), the fallback tracker (spec §2.5):
 * 2048-sample window, 256-sample hop at 16 kHz, absolute threshold 0.15, parabolic
 * interpolation, search range 46.875–2093.75 Hz. Pure Kotlin; also used in unit tests.
 */
class YinPitchTracker : PitchTracker {
    override val name: String = "YIN"
    override val voicedThreshold: Float = 1f - THRESHOLD

    private val tauMin = floor(PitchTracker.SAMPLE_RATE / PitchTracker.MAX_HZ).toInt().coerceAtLeast(2)
    private val tauMax = ceil(PitchTracker.SAMPLE_RATE / PitchTracker.MIN_HZ).toInt()
    private val width = WINDOW - tauMax
    private val difference = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)
    private val frame = FloatArray(WINDOW)

    override fun track(audio16k: FloatArray): PitchTrack {
        val frames = maxOf(1, audio16k.size / PitchTracker.HOP)
        val hz = FloatArray(frames)
        val confidence = FloatArray(frames)
        for (t in 0 until frames) {
            // Window centred on the frame's hop.
            val start = t * PitchTracker.HOP + PitchTracker.HOP / 2 - WINDOW / 2
            var peak = 0f
            for (i in 0 until WINDOW) {
                val idx = start + i
                frame[i] = if (idx in audio16k.indices) audio16k[idx] else 0f
            }
            val hopStart = t * PitchTracker.HOP
            for (i in hopStart until minOf(hopStart + PitchTracker.HOP, audio16k.size)) {
                peak = maxOf(peak, abs(audio16k[i]))
            }
            if (peak < PitchTracker.SILENCE_PEAK) continue
            estimate(frame)?.let { (f0, c) ->
                hz[t] = f0
                confidence[t] = c
            }
        }
        return PitchTrack(hz, confidence)
    }

    /** (f0, confidence) of one window, or null when no candidate lies in range. */
    fun estimate(x: FloatArray): Pair<Float, Float>? {
        differenceFunction(x)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += difference[tau]
            cmnd[tau] = if (running > 0f) difference[tau] * tau / running else 1f
        }
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (cmnd[tau] < THRESHOLD) {
                while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) {
            // No dip under the threshold: report the best candidate as low-confidence.
            found = (tauMin..tauMax).minBy { cmnd[it] }
        }
        val refined = parabolic(found)
        if (refined <= 0f) return null
        val f0 = PitchTracker.SAMPLE_RATE / refined
        if (f0 !in PitchTracker.MIN_HZ..PitchTracker.MAX_HZ) return null
        return f0 to (1f - cmnd[found]).coerceIn(0f, 1f)
    }

    private fun differenceFunction(x: FloatArray) {
        // d(τ) = Σ x_j² + Σ x_{j+τ}² − 2 Σ x_j x_{j+τ}, with running sums of squares.
        var energy0 = 0f
        for (j in 0 until width) energy0 += x[j] * x[j]
        var energyTau = energy0
        difference[0] = 0f
        for (tau in 1..tauMax) {
            energyTau += x[width + tau - 1] * x[width + tau - 1] - x[tau - 1] * x[tau - 1]
            var cross = 0f
            for (j in 0 until width) cross += x[j] * x[j + tau]
            difference[tau] = (energy0 + energyTau - 2f * cross).coerceAtLeast(0f)
        }
    }

    private fun parabolic(tau: Int): Float {
        if (tau <= 1 || tau >= tauMax) return tau.toFloat()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        val denominator = s0 - 2f * s1 + s2
        if (abs(denominator) < EPSILON) return tau.toFloat()
        return tau + (s0 - s2) / (2f * denominator)
    }

    companion object {
        const val WINDOW = 2048
        const val THRESHOLD = 0.15f
        private const val EPSILON = 1e-9f
    }
}
