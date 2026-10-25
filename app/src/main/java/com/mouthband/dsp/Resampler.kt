package com.mouthband.dsp

import kotlin.math.floor

/**
 * Windowed-sinc resampler with 16 taps (spec §2.3). The sinc cutoff follows the lower of the two
 * Nyquist rates, so downsampling is anti-aliased. Blackman window, see [SincKernel].
 */
object Resampler {
    const val TAPS = SincKernel.TAPS
    private const val HALF = SincKernel.HALF
    private const val CUTOFF_MARGIN = 0.95

    /** Cutoff (relative to the input Nyquist rate) for a [fromRate] → [toRate] conversion. */
    fun cutoff(fromRate: Int, toRate: Int): Double = minOf(1.0, toRate.toDouble() / fromRate) * CUTOFF_MARGIN

    private val kernels = HashMap<Long, SincKernel>()

    /** Kernels are built once per rate pair (live pitch resamples every 32 ms). */
    private fun kernelFor(fromRate: Int, toRate: Int): SincKernel = synchronized(kernels) {
        kernels.getOrPut(fromRate.toLong() shl Int.SIZE_BITS or toRate.toLong()) {
            SincKernel(cutoff(fromRate, toRate))
        }
    }

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input.copyOf()
        val ratio = fromRate.toDouble() / toRate
        val outLength = (input.size / ratio).toInt()
        val kernel = kernelFor(fromRate, toRate)
        val weights = FloatArray(TAPS)
        val out = FloatArray(outLength)
        for (j in 0 until outLength) {
            val t = j * ratio
            val center = floor(t).toInt()
            kernel.weights(t - center, weights)
            var sum = 0.0
            var weight = 0.0
            for (i in 0 until TAPS) {
                val k = center - HALF + 1 + i
                weight += weights[i]
                if (k in input.indices) sum += input[k] * weights[i]
            }
            out[j] = if (weight != 0.0) (sum / weight).toFloat() else 0f
        }
        return out
    }
}
