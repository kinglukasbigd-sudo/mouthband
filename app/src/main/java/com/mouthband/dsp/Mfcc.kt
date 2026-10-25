package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Mel-frequency cepstral coefficients (spec §2.3): 1024-sample Hann frame, 26 triangular mel
 * bands from 0 Hz to Nyquist, natural-log energies, DCT-II, 13 coefficients. Also exposes the
 * frame's power spectrum for spectral features. Preallocated; not thread-safe.
 */
class Mfcc(private val sampleRate: Int) {
    private val fft = Fft(FRAME)
    private val window = Window.hann(FRAME)
    private val frame = FloatArray(FRAME)
    private val re = FloatArray(fft.binCount)
    private val im = FloatArray(fft.binCount)
    private val bandEnergy = FloatArray(BANDS)
    private val filterStart = IntArray(BANDS)
    private val filterWeights = Array(BANDS) { FloatArray(0) }
    private val dct = Array(COEFFICIENTS) { k ->
        FloatArray(BANDS) { n -> cos(PI / BANDS * (n + 0.5) * k).toFloat() }
    }

    /** Power spectrum of the last analysed frame (bins 0..FRAME/2). */
    val power = FloatArray(fft.binCount)

    init {
        buildFilterBank()
    }

    /** Analyses [FRAME] samples of [samples] from [offset] (zero-padded) and writes 13 MFCCs. */
    fun compute(samples: FloatArray, offset: Int, out: FloatArray) {
        for (i in 0 until FRAME) {
            val idx = offset + i
            frame[i] = if (idx in samples.indices) samples[idx] * window[i] else 0f
        }
        fft.forward(frame, re, im)
        for (k in power.indices) power[k] = re[k] * re[k] + im[k] * im[k]
        for (b in 0 until BANDS) {
            var sum = 0f
            val weights = filterWeights[b]
            val start = filterStart[b]
            for (j in weights.indices) sum += weights[j] * power[start + j]
            bandEnergy[b] = ln(sum + LOG_FLOOR)
        }
        for (k in 0 until COEFFICIENTS) {
            var sum = 0f
            val basis = dct[k]
            for (n in 0 until BANDS) sum += basis[n] * bandEnergy[n]
            out[k] = sum
        }
    }

    /** Spectral centroid (Hz) of [power]. */
    fun centroidHz(): Float {
        var weighted = 0.0
        var total = 0.0
        for (k in power.indices) {
            weighted += k * power[k]
            total += power[k]
        }
        return if (total > 0) (weighted / total * binHz()).toFloat() else 0f
    }

    /** Share of [power] below [hz]. */
    fun energyBelow(hz: Float): Float {
        val limit = (hz / binHz()).toInt().coerceIn(0, power.size - 1)
        var low = 0.0
        var total = 0.0
        for (k in power.indices) {
            total += power[k]
            if (k <= limit) low += power[k]
        }
        return if (total > 0) (low / total).toFloat() else 0f
    }

    /** Share of [power] within ±[widthHz] of [hz]. */
    fun energyNear(hz: Float, widthHz: Float): Float {
        val lo = ((hz - widthHz) / binHz()).toInt().coerceIn(0, power.size - 1)
        val hi = ((hz + widthHz) / binHz()).toInt().coerceIn(0, power.size - 1)
        var band = 0.0
        var total = 0.0
        for (k in power.indices) {
            total += power[k]
            if (k in lo..hi) band += power[k]
        }
        return if (total > 0) (band / total).toFloat() else 0f
    }

    private fun binHz(): Float = sampleRate.toFloat() / FRAME

    private fun buildFilterBank() {
        val maxMel = hzToMel(sampleRate / 2f)
        val edges = FloatArray(BANDS + 2) { melToHz(maxMel * it / (BANDS + 1)) / binHz() }
        for (b in 0 until BANDS) {
            val left = edges[b]
            val center = edges[b + 1]
            val right = edges[b + 2]
            val start = floor(left).toInt().coerceAtLeast(0)
            val end = minOf(floor(right).toInt() + 1, power.size - 1)
            filterStart[b] = start
            filterWeights[b] = FloatArray(maxOf(0, end - start + 1)) { j ->
                val bin = (start + j).toFloat()
                when {
                    bin < left || bin > right -> 0f
                    bin <= center -> if (center > left) (bin - left) / (center - left) else 1f
                    else -> if (right > center) (right - bin) / (right - center) else 1f
                }
            }
        }
    }

    companion object {
        const val FRAME = 1024
        const val BANDS = 26
        const val COEFFICIENTS = 13
        private const val LOG_FLOOR = 1e-10f
        private const val MEL_SCALE = 2595f
        private const val MEL_BREAK_HZ = 700f

        fun hzToMel(hz: Float): Float = MEL_SCALE * log10(1f + hz / MEL_BREAK_HZ)

        fun melToHz(mel: Float): Float = MEL_BREAK_HZ * (10f.pow(mel / MEL_SCALE) - 1f)

        /** Euclidean norm helper used by feature code. */
        fun norm(values: FloatArray): Float = sqrt(values.fold(0f) { acc, v -> acc + v * v })
    }
}
