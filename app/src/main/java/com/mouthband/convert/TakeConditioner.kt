package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.dsp.Rms
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * A take after conditioning: [take] is what the converters analyse and what is kept as the raw
 * loop; [gainDb] was applied (negative when the take was too loud); [loudDb] is how loud the
 * loud parts were as recorded; [audible] is false when nothing rose above the microphone's own
 * noise.
 */
class ConditionedTake(
    val take: AlignedTake,
    val gainDb: Float,
    val loudDb: Float,
    val clippedRuns: Int,
    val audible: Boolean,
)

/**
 * Makes any recording clear enough to convert, however it was recorded. Phones differ by 30 dB
 * in microphone sensitivity and people hum anywhere from a whisper to a shout, so before analysis
 * every take gets:
 *  1. clipped peaks rebuilt ([Declip]),
 *  2. a 40 Hz high-pass (DC, handling thumps and wind rumble below the voice),
 *  3. one gain that puts the loud parts at [TARGET_DB], capped so a mic-noise-only take is never
 *     blown up into notes, and so the loudest peak stays under [PEAK_CEILING].
 * Quiet takes become audible to the pitch tracker and the onset detector; loud and clipped takes
 * come down to a level where their sounds stay clean. A take that is already well recorded is
 * left within a dB of what it was.
 */
object TakeConditioner {
    /** Loud parts are brought to this RMS (dBFS). */
    const val TARGET_DB = -20f

    /** Below this (99th percentile of 10 ms RMS, after the high-pass) nothing was performed. */
    const val AUDIBLE_DB = -78f
    const val MAX_BOOST_DB = 60f
    const val MAX_CUT_DB = 36f
    const val PEAK_CEILING = 0.9f
    private const val HIGH_PASS_HZ = 40.0
    private const val FRAME_SECONDS = 0.010
    private const val LOUD_PERCENTILE = 0.99f
    private const val LEAVE_ALONE_DB = 1f
    private const val DB_PER_DECADE = 20f

    fun condition(take: AlignedTake): ConditionedTake {
        val repaired = Declip.repair(finite(take.samples), take.sampleRate)
        val samples = repaired.samples
        highPass(samples, take.sampleRate)
        val loudDb = loudLevelDb(samples, take.sampleRate)
        if (loudDb < AUDIBLE_DB) {
            return ConditionedTake(rebuilt(take, samples), 0f, loudDb, repaired.runs, audible = false)
        }
        val peak = Rms.peak(samples)
        var gainDb = (TARGET_DB - loudDb).coerceIn(-MAX_CUT_DB, MAX_BOOST_DB)
        if (peak > 0f) gainDb = minOf(gainDb, DB_PER_DECADE * log10(PEAK_CEILING / peak))
        if (abs(gainDb) < LEAVE_ALONE_DB) gainDb = 0f
        val gain = Rms.fromDb(gainDb)
        if (gain != 1f) for (i in samples.indices) samples[i] *= gain
        return ConditionedTake(rebuilt(take, samples), gainDb, loudDb, repaired.runs, audible = true)
    }

    /** A driver glitch can deliver NaN or infinity; one such sample would poison every filter. */
    private fun finite(samples: FloatArray): FloatArray {
        for (i in samples.indices) {
            if (!samples[i].isFinite()) return FloatArray(samples.size) { samples[it].takeIf(Float::isFinite) ?: 0f }
        }
        return samples
    }

    private fun rebuilt(take: AlignedTake, samples: FloatArray) =
        AlignedTake(samples, take.prerollFrames, take.loopFrames, take.sampleRate)

    /** RMS (dBFS) of the loud parts: the 99th percentile of 10 ms frames, so a few clicks do not set it. */
    private fun loudLevelDb(samples: FloatArray, sampleRate: Int): Float {
        val frame = (FRAME_SECONDS * sampleRate).toInt().coerceAtLeast(1)
        val count = samples.size / frame
        if (count == 0) return Rms.toDb(Rms.of(samples))
        val levels = FloatArray(count) { Rms.of(samples, it * frame, frame) }
        levels.sort()
        return Rms.toDb(levels[(LOUD_PERCENTILE * (count - 1)).toInt()])
    }

    /** Second-order Butterworth high-pass in double precision (a 40 Hz pole is too close to 1 for float). */
    private fun highPass(samples: FloatArray, sampleRate: Int) {
        val w = 2.0 * PI * HIGH_PASS_HZ / sampleRate
        val alpha = sin(w) / (2.0 * BUTTERWORTH_Q)
        val cosW = cos(w)
        val a0 = 1.0 + alpha
        val b0 = (1.0 + cosW) / 2.0 / a0
        val b1 = -(1.0 + cosW) / a0
        val a1 = -(2.0 * cosW) / a0
        val a2 = (1.0 - alpha) / a0
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        // Start from the first sample's level so a DC offset does not begin with a step.
        if (samples.isNotEmpty()) {
            x1 = samples[0].toDouble()
            x2 = x1
        }
        for (i in samples.indices) {
            val x = samples[i].toDouble()
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            samples[i] = y.toFloat()
        }
    }

    private const val BUTTERWORTH_Q = 0.7071
}
