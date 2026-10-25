package com.mouthband.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

private const val MS_PER_SECOND = 1000.0

/** One-pole smoothing coefficient for a time constant in ms. */
internal fun timeCoefficient(ms: Float, sampleRate: Int): Float =
    if (ms <= 0f) 0f else exp(-1.0 / (ms / MS_PER_SECOND * sampleRate)).toFloat()

/**
 * Feed-forward compressor with an RMS detector (spec §2.3). Stereo-linked when used through
 * [processStereo]. Allocation-free.
 */
class Compressor(
    sampleRate: Int,
    var thresholdDb: Float,
    var ratio: Float,
    attackMs: Float,
    releaseMs: Float,
    var makeupDb: Float = 0f,
    rmsWindowMs: Float = DEFAULT_RMS_MS,
) {
    private val attack = timeCoefficient(attackMs, sampleRate)
    private val release = timeCoefficient(releaseMs, sampleRate)
    private val rmsCoefficient = timeCoefficient(rmsWindowMs, sampleRate)
    private var meanSquare = 0f
    private var gainReductionDb = 0f

    /** Current gain reduction in dB (≥ 0). */
    val reductionDb: Float get() = gainReductionDb

    /** Returns the gain to apply for a detector input of [level] (linear, e.g. max of channels). */
    fun gainFor(level: Float): Float {
        meanSquare = rmsCoefficient * meanSquare + (1f - rmsCoefficient) * level * level
        val levelDb = Rms.toDb(sqrt(meanSquare))
        val over = levelDb - thresholdDb
        val target = if (over > 0f) over * (1f - 1f / ratio) else 0f
        val coefficient = if (target > gainReductionDb) attack else release
        gainReductionDb = coefficient * gainReductionDb + (1f - coefficient) * target
        return Rms.fromDb(makeupDb - gainReductionDb)
    }

    fun process(x: Float): Float = x * gainFor(abs(x))

    /** Processes an interleaved stereo block in place, stereo-linked. */
    fun processStereo(block: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val l = block[2 * i]
            val r = block[2 * i + 1]
            val g = gainFor(max(abs(l), abs(r)))
            block[2 * i] = l * g
            block[2 * i + 1] = r * g
        }
    }

    fun reset() {
        meanSquare = 0f
        gainReductionDb = 0f
    }

    private companion object {
        const val DEFAULT_RMS_MS = 10f
    }
}

/**
 * Lookahead brick-wall limiter (spec §2.3), stereo-linked. Output is delayed by the lookahead;
 * the gain reaches its minimum exactly when the peak leaves the delay line, so no sample
 * exceeds [ceilingDb]. Uses a monotonic queue for the windowed minimum and a box filter for a
 * smooth attack. Allocation-free after construction.
 */
class Limiter(
    sampleRate: Int,
    ceilingDb: Float = DEFAULT_CEILING_DB,
    lookaheadMs: Float = DEFAULT_LOOKAHEAD_MS,
    releaseMs: Float = DEFAULT_RELEASE_MS,
) {
    private val ceiling = Rms.fromDb(ceilingDb)
    private val lookahead = maxOf(1, (lookaheadMs * sampleRate / MS_PER_SECOND).toInt())
    private val release = timeCoefficient(releaseMs, sampleRate)
    private val delayLeft = FloatArray(lookahead + 1)
    private val delayRight = FloatArray(lookahead + 1)
    private val gains = FloatArray(lookahead + 1) { 1f }
    private val queueValues = FloatArray(lookahead + 2)
    private val queueIndex = LongArray(lookahead + 2)
    private var queueHead = 0
    private var queueSize = 0
    private var position = 0L
    private var boxSum = (lookahead + 1).toDouble()
    private var released = 1f

    /** Output delay in frames. */
    val latencyFrames: Int get() = lookahead

    fun processStereo(block: FloatArray, frames: Int) {
        val slots = lookahead + 1
        for (i in 0 until frames) {
            val l = block[2 * i]
            val r = block[2 * i + 1]
            val peak = max(abs(l), abs(r))
            val target = if (peak > ceiling) ceiling / peak else 1f
            pushMin(target)
            val windowMin = queueValues[queueHead]
            // Release slowly towards higher gains, jump down immediately.
            released = if (windowMin < released) windowMin else release * released + (1f - release) * windowMin
            val slot = (position % slots).toInt()
            boxSum += released - gains[slot]
            gains[slot] = released
            val smooth = (boxSum / slots).toFloat()
            val outLeft = delayLeft[slot]
            val outRight = delayRight[slot]
            delayLeft[slot] = l
            delayRight[slot] = r
            val g = minOf(smooth, gainForDelayed(outLeft, outRight))
            block[2 * i] = outLeft * g
            block[2 * i + 1] = outRight * g
            position++
        }
    }

    /** Safety: the exact gain the delayed sample needs (guards rounding in the box filter). */
    private fun gainForDelayed(l: Float, r: Float): Float {
        val peak = max(abs(l), abs(r))
        return if (peak > ceiling) ceiling / peak else 1f
    }

    private fun pushMin(value: Float) {
        val capacity = queueValues.size
        // Drop larger values from the back, then expired values from the front.
        while (queueSize > 0 && queueValues[(queueHead + queueSize - 1) % capacity] >= value) queueSize--
        val tail = (queueHead + queueSize) % capacity
        queueValues[tail] = value
        queueIndex[tail] = position
        queueSize++
        while (queueIndex[queueHead] <= position - lookahead - 1) {
            queueHead = (queueHead + 1) % capacity
            queueSize--
        }
    }

    fun reset() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        gains.fill(1f)
        queueHead = 0
        queueSize = 0
        position = 0
        boxSum = (lookahead + 1).toDouble()
        released = 1f
    }

    companion object {
        const val DEFAULT_CEILING_DB = -1f
        const val DEFAULT_LOOKAHEAD_MS = 5f
        const val DEFAULT_RELEASE_MS = 80f
    }
}
