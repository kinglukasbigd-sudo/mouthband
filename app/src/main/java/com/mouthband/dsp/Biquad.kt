package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * RBJ-cookbook biquad (spec §2.3): low-pass, high-pass and peaking EQ. Direct form I, one
 * instance per channel. [process] is allocation-free.
 */
class Biquad {
    /** Immutable normalized coefficients; swap a whole set at once. */
    class Coefficients(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)

    var coefficients: Coefficients = PASS
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val c = coefficients
        val y = c.b0 * x + c.b1 * x1 + c.b2 * x2 - c.a1 * y1 - c.a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    /** Filters [samples] in place. */
    fun processBlock(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset) {
        for (i in offset until offset + length) samples[i] = process(samples[i])
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }

    companion object {
        const val BUTTERWORTH_Q = 0.7071f
        private const val DB_TO_AMPLITUDE = 40.0
        val PASS = Coefficients(1f, 0f, 0f, 0f, 0f)

        fun lowPass(sampleRate: Int, cutoffHz: Float, q: Float = BUTTERWORTH_Q): Coefficients {
            val (cosW, alpha) = prewarp(sampleRate, cutoffHz, q)
            val b1 = 1.0 - cosW
            return Raw(b1 / 2, b1, b1 / 2, 1 + alpha, -(2 * cosW), 1 - alpha).normalized()
        }

        fun highPass(sampleRate: Int, cutoffHz: Float, q: Float = BUTTERWORTH_Q): Coefficients {
            val (cosW, alpha) = prewarp(sampleRate, cutoffHz, q)
            val b1 = -(1.0 + cosW)
            return Raw(-b1 / 2, b1, -b1 / 2, 1 + alpha, -(2 * cosW), 1 - alpha).normalized()
        }

        fun peak(sampleRate: Int, centerHz: Float, gainDb: Float, q: Float = 1f): Coefficients {
            val a = 10.0.pow(gainDb / DB_TO_AMPLITUDE)
            val (cosW, alpha) = prewarp(sampleRate, centerHz, q)
            val b1 = -(2 * cosW)
            return Raw(1 + alpha * a, b1, 1 - alpha * a, 1 + alpha / a, b1, 1 - alpha / a).normalized()
        }

        private fun prewarp(sampleRate: Int, hz: Float, q: Float): Pair<Double, Double> {
            val w = 2 * PI * hz.coerceIn(1f, sampleRate * NYQUIST_GUARD) / sampleRate
            return cos(w) to sin(w) / (2 * q)
        }

        /** Raw cookbook coefficients before dividing by a0. */
        private class Raw(
            val b0: Double,
            val b1: Double,
            val b2: Double,
            val a0: Double,
            val a1: Double,
            val a2: Double,
        ) {
            fun normalized() = Coefficients(
                (b0 / a0).toFloat(),
                (b1 / a0).toFloat(),
                (b2 / a0).toFloat(),
                (a1 / a0).toFloat(),
                (a2 / a0).toFloat(),
            )
        }

        private const val NYQUIST_GUARD = 0.49f
    }
}
