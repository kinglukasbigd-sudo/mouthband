package com.mouthband.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Window functions. */
object Window {
    /** Periodic Hann window (for STFT frames). */
    fun hann(size: Int): FloatArray = FloatArray(size) { n -> (0.5 - 0.5 * cos(2.0 * PI * n / size)).toFloat() }

    /** Multiplies [frame] by [window] in place. */
    fun apply(frame: FloatArray, window: FloatArray) {
        for (i in window.indices) frame[i] *= window[i]
    }
}

/** Level helpers. */
object Rms {
    private const val DB_PER_DECADE = 20.0
    private const val MIN_LEVEL = 1e-9

    /** Root mean square of [length] samples starting at [offset]. */
    fun of(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        val end = minOf(samples.size, offset + length)
        for (i in maxOf(0, offset) until end) sum += samples[i] * samples[i]
        return sqrt(sum / length).toFloat()
    }

    fun toDb(linear: Float): Float = (DB_PER_DECADE * log10(max(linear.toDouble(), MIN_LEVEL))).toFloat()

    fun fromDb(db: Float): Float = 10.0.pow(db / DB_PER_DECADE).toFloat()

    /** Highest absolute sample value in the range. */
    fun peak(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Float {
        var p = 0f
        val end = minOf(samples.size, offset + length)
        for (i in maxOf(0, offset) until end) p = max(p, abs(samples[i]))
        return p
    }
}

/** Zero-crossing rate. */
object ZeroCrossing {
    /** Sign changes per sample over [length] samples from [offset] (0..1). */
    fun rate(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): Float {
        val end = minOf(samples.size, offset + length)
        val start = maxOf(0, offset)
        if (end - start < 2) return 0f
        var crossings = 0
        for (i in start + 1 until end) {
            if ((samples[i - 1] >= 0f) != (samples[i] >= 0f)) crossings++
        }
        return crossings.toFloat() / (end - start - 1)
    }
}
