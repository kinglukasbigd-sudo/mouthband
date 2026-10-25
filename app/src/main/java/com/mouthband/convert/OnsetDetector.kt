package com.mouthband.convert

import com.mouthband.dsp.Fft
import com.mouthband.dsp.Rms
import com.mouthband.dsp.Window
import kotlin.math.abs
import kotlin.math.max

/**
 * Spectral-flux onset detection (spec §2.4 step 1): 1024-sample FFT, 256-sample hop, adaptive
 * threshold = moving median (±8 frames) × 1.5 + δ, peak picking with ≥ 60 ms between onsets,
 * then each onset is refined to the sample by searching back up to 10 ms for the energy rise.
 */
class OnsetDetector(private val sampleRate: Int) {
    private val fft = Fft(FRAME)
    private val window = Window.hann(FRAME)
    private val frame = FloatArray(FRAME)
    private val re = FloatArray(fft.binCount)
    private val im = FloatArray(fft.binCount)
    private val magnitude = FloatArray(fft.binCount)
    private val previous = FloatArray(fft.binCount)

    /** Onset sample positions in [samples], ascending. */
    fun detect(samples: FloatArray): List<Int> {
        val flux = spectralFlux(samples)
        if (flux.isEmpty()) return emptyList()
        val peaks = pickPeaks(flux)
        val minGap = (MIN_GAP_SECONDS * sampleRate).toInt()
        // Frame t ends at t × hop: the flux peaks when a hit sits ~70 % into the window, i.e. near t × hop.
        val refined = peaks.map { refine(samples, it * HOP) }
        val result = ArrayList<Int>()
        for (onset in refined.sorted()) {
            if (result.isEmpty() || onset - result.last() >= minGap) result += onset
        }
        return result
    }

    /** Half-wave-rectified spectral flux per hop, normalized by the bin count. */
    fun spectralFlux(samples: FloatArray): FloatArray {
        val frames = if (samples.size < HOP) 0 else (samples.size + FRAME - HOP) / HOP
        val flux = FloatArray(frames)
        previous.fill(0f)
        for (t in 0 until frames) {
            val start = t * HOP - (FRAME - HOP)
            for (i in 0 until FRAME) {
                val idx = start + i
                frame[i] = if (idx in samples.indices) samples[idx] * window[i] else 0f
            }
            fft.magnitudes(frame, re, im, magnitude)
            var sum = 0f
            for (k in magnitude.indices) {
                val diff = magnitude[k] - previous[k]
                if (diff > 0f) sum += diff
                previous[k] = magnitude[k]
            }
            // The first frames are zero-padded at the front: their rise is the padding, not a hit.
            flux[t] = if (t < PADDED_FRAMES) 0f else sum / magnitude.size
        }
        return flux
    }

    private fun pickPeaks(flux: FloatArray): List<Int> {
        val maxFlux = flux.max()
        if (maxFlux <= 0f) return emptyList()
        // δ: a share of the loudest hit, but never below a few times the typical (noise) flux.
        val delta = maxOf(DELTA_FRACTION * maxFlux, NOISE_FACTOR * flux.sorted()[flux.size / 2])
        val minGapFrames = (MIN_GAP_SECONDS * sampleRate / HOP).toInt().coerceAtLeast(1)
        val window = FloatArray(2 * MEDIAN_RADIUS + 1)
        val picked = ArrayList<Int>()
        for (t in flux.indices) {
            val isPeak = (t == 0 || flux[t] >= flux[t - 1]) && (t == flux.size - 1 || flux[t] > flux[t + 1])
            if (isPeak && flux[t] > movingMedian(flux, t, window) * MEDIAN_FACTOR + delta) {
                val last = picked.lastOrNull()
                when {
                    last == null || t - last >= minGapFrames -> picked += t
                    flux[t] > flux[last] -> picked[picked.size - 1] = t
                }
            }
        }
        return picked
    }

    private fun movingMedian(flux: FloatArray, t: Int, scratch: FloatArray): Float {
        var n = 0
        for (i in t - MEDIAN_RADIUS..t + MEDIAN_RADIUS) {
            if (i in flux.indices) scratch[n++] = flux[i]
        }
        scratch.sort(0, n)
        return scratch[n / 2]
    }

    /**
     * Finds the hit's peak near [coarse], then walks back (≤ 10 ms) while the short-term
     * envelope stays above 10 % of that peak.
     */
    fun refine(samples: FloatArray, coarse: Int): Int {
        val from = (coarse - FRAME / 2).coerceIn(0, samples.size - 1)
        val to = (coarse + HOP).coerceIn(0, samples.size - 1)
        var peakIndex = from
        var peak = 0f
        for (i in from..to) {
            val a = abs(samples[i])
            if (a > peak) {
                peak = a
                peakIndex = i
            }
        }
        if (peak == 0f) return coarse.coerceIn(0, samples.size - 1)
        val limit = maxOf(0, peakIndex - (REFINE_SECONDS * sampleRate).toInt())
        // The rise must stand out from the background just before the search range.
        val noiseEnd = limit
        val noiseStart = maxOf(0, noiseEnd - (NOISE_WINDOW_SECONDS * sampleRate).toInt())
        val noise = Rms.of(samples, noiseStart, noiseEnd - noiseStart)
        val threshold = maxOf(peak * RISE_FRACTION, noise * NOISE_MARGIN)
        val envelope = maxOf(1, (ENVELOPE_SECONDS * sampleRate).toInt())
        var i = peakIndex
        while (i > limit && localMax(samples, i - envelope, i) > threshold) i--
        return i
    }

    private fun localMax(samples: FloatArray, from: Int, to: Int): Float {
        var m = 0f
        for (j in maxOf(0, from)..to) m = max(m, abs(samples[j]))
        return m
    }

    companion object {
        const val FRAME = 1024
        const val HOP = 256

        private const val MEDIAN_RADIUS = 8
        private const val MEDIAN_FACTOR = 1.5f
        private const val DELTA_FRACTION = 0.03f
        private const val NOISE_FACTOR = 2f
        private const val PADDED_FRAMES = FRAME / HOP
        private const val MIN_GAP_SECONDS = 0.060
        private const val REFINE_SECONDS = 0.010
        private const val ENVELOPE_SECONDS = 0.0005
        private const val RISE_FRACTION = 0.1f
        private const val NOISE_WINDOW_SECONDS = 0.020
        private const val NOISE_MARGIN = 4f
    }
}
