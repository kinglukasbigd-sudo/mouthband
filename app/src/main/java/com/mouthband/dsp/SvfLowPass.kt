package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.tan

/**
 * 24 dB/oct low-pass: two cascaded trapezoidal state-variable filters (Butterworth Q pair).
 * Stable under fast cutoff modulation, so the synth can sweep it every few samples. [resonance]
 * scales the second stage's Q. Allocation-free.
 */
class SvfLowPass(private val sampleRate: Int) {
    private val stageA = Stage()
    private val stageB = Stage()

    fun setCutoff(hz: Float, resonance: Float = 1f) {
        val fc = hz.coerceIn(MIN_HZ, sampleRate * NYQUIST_GUARD)
        val g = tan(PI * fc / sampleRate).toFloat()
        stageA.set(g, Q_FIRST)
        stageB.set(g, Q_SECOND * resonance.coerceAtLeast(MIN_RESONANCE))
    }

    fun process(x: Float): Float = stageB.process(stageA.process(x))

    fun reset() {
        stageA.reset()
        stageB.reset()
    }

    private class Stage {
        private var a1 = 0f
        private var a2 = 0f
        private var a3 = 0f
        private var k = 1f
        private var ic1 = 0f
        private var ic2 = 0f

        fun set(g: Float, q: Float) {
            k = 1f / q
            a1 = 1f / (1f + g * (g + k))
            a2 = g * a1
            a3 = g * a2
        }

        fun process(v0: Float): Float {
            val v3 = v0 - ic2
            val v1 = a1 * ic1 + a2 * v3
            val v2 = ic2 + a2 * ic1 + a3 * v3
            ic1 = 2f * v1 - ic1
            ic2 = 2f * v2 - ic2
            return v2
        }

        fun reset() {
            ic1 = 0f
            ic2 = 0f
        }
    }

    private companion object {
        const val MIN_HZ = 20f
        const val NYQUIST_GUARD = 0.45f
        const val Q_FIRST = 0.5412f
        const val Q_SECOND = 1.3066f
        const val MIN_RESONANCE = 0.3f
    }
}
