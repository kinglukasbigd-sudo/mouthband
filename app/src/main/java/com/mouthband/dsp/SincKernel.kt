package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 16-tap Blackman-windowed sinc of the resamplers (spec §2.3), tabulated at [PHASES]
 * fractional offsets with linear interpolation in between, so resampling costs multiply-adds
 * instead of three trig calls per tap. [cutoff] is relative to the input Nyquist rate.
 */
class SincKernel(cutoff: Double) {
    private val table = FloatArray((PHASES + 1) * TAPS)

    init {
        for (p in 0..PHASES) {
            val frac = p.toDouble() / PHASES
            for (j in 0 until TAPS) table[p * TAPS + j] = kernel(frac + HALF - 1 - j, cutoff).toFloat()
        }
    }

    /**
     * Weights of taps `k = center − 7 … center + 8` (index 0 … 15) for an output that lies
     * [frac] (0 ≤ frac < 1) past input sample `center`.
     */
    fun weights(frac: Double, out: FloatArray) {
        val position = frac * PHASES
        val p = position.toInt().coerceIn(0, PHASES - 1)
        val a = (position - p).toFloat()
        val base = p * TAPS
        for (j in 0 until TAPS) {
            val w0 = table[base + j]
            out[j] = w0 + a * (table[base + TAPS + j] - w0)
        }
    }

    companion object {
        const val TAPS = 16
        const val HALF = TAPS / 2
        const val PHASES = 512
        private const val BLACKMAN_A0 = 0.42
        private const val BLACKMAN_A1 = 0.5
        private const val BLACKMAN_A2 = 0.08

        /** Windowed sinc at distance [x] (in input samples) from the output position. */
        fun kernel(x: Double, cutoff: Double): Double {
            val sinc = if (x == 0.0) 1.0 else sin(PI * cutoff * x) / (PI * cutoff * x)
            val position = (x + HALF) / TAPS // 0..1 across the kernel
            if (position < 0.0 || position > 1.0) return 0.0
            val phase = 2 * PI * position
            return sinc * (BLACKMAN_A0 - BLACKMAN_A1 * cos(phase) + BLACKMAN_A2 * cos(2 * phase))
        }
    }
}
