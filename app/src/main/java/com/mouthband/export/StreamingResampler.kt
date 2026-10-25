package com.mouthband.export

import com.mouthband.dsp.Resampler
import com.mouthband.dsp.SincKernel
import kotlin.math.floor

/**
 * Chunked stereo version of the 16-tap windowed-sinc [Resampler], so a whole
 * song can be converted to 44.1 kHz without holding it in memory. Feed interleaved stereo with
 * [push]; converted interleaved frames go to the sink. Call [finish] once at the end.
 */
class StreamingResampler(
    private val fromRate: Int,
    private val toRate: Int,
    private val sink: (FloatArray, Int) -> Unit,
) {
    private val ratio = fromRate.toDouble() / toRate
    private val kernel = SincKernel(Resampler.cutoff(fromRate, toRate))
    private val weights = FloatArray(TAPS)
    private var history = FloatArray(INITIAL_CAPACITY * 2)
    private var historyFrames = 0

    /** Absolute input frame index of history[0]. */
    private var historyStart = 0L
    private var nextOut = 0L
    private val out = FloatArray(OUT_CHUNK * 2)
    private var outFill = 0

    fun push(interleaved: FloatArray, frames: Int) {
        if (fromRate == toRate) {
            sink(interleaved, frames)
            return
        }
        ensureCapacity(historyFrames + frames)
        System.arraycopy(interleaved, 0, history, historyFrames * 2, frames * 2)
        historyFrames += frames
        produce(final = false)
    }

    fun finish() {
        if (fromRate == toRate) return
        produce(final = true)
        flushOut()
    }

    private fun produce(final: Boolean) {
        val available = historyStart + historyFrames
        // Without more input, an output needs the whole kernel; at the end, its centre suffices.
        val reach = if (final) 0 else HALF
        while (floor(nextOut * ratio).toLong() + reach < available) {
            val t = nextOut * ratio
            val center = floor(t).toLong()
            kernel.weights(t - center, weights)
            var l = 0.0
            var r = 0.0
            var weight = 0.0
            for (i in 0 until TAPS) {
                val w = weights[i]
                weight += w
                val idx = (center - HALF + 1 + i - historyStart).toInt()
                if (idx in 0 until historyFrames) {
                    l += history[2 * idx] * w
                    r += history[2 * idx + 1] * w
                }
            }
            out[2 * outFill] = if (weight != 0.0) (l / weight).toFloat() else 0f
            out[2 * outFill + 1] = if (weight != 0.0) (r / weight).toFloat() else 0f
            outFill++
            if (outFill == OUT_CHUNK) flushOut()
            nextOut++
        }
        discardConsumed()
    }

    private fun discardConsumed() {
        val keepFrom = floor(nextOut * ratio).toLong() - HALF
        val drop = (keepFrom - historyStart).toInt().coerceIn(0, historyFrames)
        if (drop > 0) {
            System.arraycopy(history, drop * 2, history, 0, (historyFrames - drop) * 2)
            historyFrames -= drop
            historyStart += drop
        }
    }

    private fun flushOut() {
        if (outFill > 0) sink(out, outFill)
        outFill = 0
    }

    private fun ensureCapacity(frames: Int) {
        if (frames * 2 > history.size) history = history.copyOf(maxOf(frames * 2, history.size * 2))
    }

    private companion object {
        const val TAPS = SincKernel.TAPS
        const val HALF = SincKernel.HALF
        const val INITIAL_CAPACITY = 8192
        const val OUT_CHUNK = 4096
    }
}
