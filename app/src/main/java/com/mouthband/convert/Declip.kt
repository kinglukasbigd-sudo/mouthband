package com.mouthband.convert

import kotlin.math.abs

/**
 * Rebuilds peaks that hit full scale. A loud voice clips the microphone for a few milliseconds
 * per cycle; the flat tops add harmonics that confuse pitch tracking and drum features. Each
 * short clipped run is replaced by a cubic Hermite curve through the last good samples on both
 * sides, which restores the rounded peak the voice really had. Runs too long to be a peak (a
 * flat line or a dead microphone) are left as they are.
 */
object Declip {
    /** A sample at or above this magnitude counts as clipped. */
    private const val CLIP_LEVEL = 0.99f
    private const val MIN_RUN = 3
    private const val MAX_RUN_SECONDS = 0.003
    private const val CUBIC = 3f

    /** The rebuilt overshoot never exceeds this, so a bad guess cannot blow up the level. */
    private const val MAX_OVERSHOOT = 2.5f

    /** [samples] after repair (a copy) and how many clipped runs were rebuilt. */
    class Repaired(val samples: FloatArray, val runs: Int)

    fun repair(input: FloatArray, sampleRate: Int): Repaired {
        val out = input.copyOf()
        val maxRun = (MAX_RUN_SECONDS * sampleRate).toInt()
        var runs = 0
        var i = 0
        while (i < input.size) {
            if (abs(input[i]) < CLIP_LEVEL) {
                i++
                continue
            }
            val sign = if (input[i] > 0f) 1f else -1f
            var end = i
            while (end + 1 < input.size && input[end + 1] * sign >= CLIP_LEVEL) end++
            if (end - i + 1 in MIN_RUN..maxRun && rebuild(input, out, i, end)) runs++
            i = end + 1
        }
        return Repaired(out, runs)
    }

    /** Hermite curve over `[start, end]` from the neighbours; false when they do not exist. */
    private fun rebuild(x: FloatArray, out: FloatArray, start: Int, end: Int): Boolean {
        val left = start - 1
        val right = end + 1
        if (left < 1 || right + 1 >= x.size) return false
        val span = (right - left).toFloat()
        val slopeLeft = (x[left] - x[left - 1]) * span
        val slopeRight = (x[right + 1] - x[right]) * span
        for (n in start..end) {
            val t = (n - left) / span
            val u = 1f - t
            // Hermite basis: value at the left/right anchor and the slope at each.
            val value = (1f + 2f * t) * u * u * x[left] +
                t * u * u * slopeLeft +
                t * t * (CUBIC - 2f * t) * x[right] -
                t * t * u * slopeRight
            out[n] = value.coerceIn(-MAX_OVERSHOOT, MAX_OVERSHOOT)
        }
        return true
    }
}
