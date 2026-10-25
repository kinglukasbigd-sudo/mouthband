package com.mouthband.audio

import kotlin.math.roundToLong

/**
 * Maps recorded frames onto the transport (spec §2.1 Alignment): recorded frame `i` belongs to
 * transport frame `startFrame + i − latencyFrames`.
 */
object Alignment {
    private const val NANOS_PER_SECOND = 1_000_000_000.0

    /**
     * Transport frame of record frame 0 from the two hardware timestamps.
     * [playFrame]/[playNanos]: transport frame presented at that time; [recFrame]/[recNanos]:
     * record frame captured at that time. Both clocks are CLOCK_MONOTONIC.
     */
    fun startFrameFromTimestamps(
        playFrame: Long,
        playNanos: Long,
        recFrame: Long,
        recNanos: Long,
        sampleRate: Int,
    ): Long {
        val recordStartNanos = recNanos - recFrame * NANOS_PER_SECOND / sampleRate
        val presented = playFrame + (recordStartNanos - playNanos) * sampleRate / NANOS_PER_SECOND
        return presented.roundToLong()
    }

    /**
     * Cuts the aligned window `[fromTransport, fromTransport + length)` out of a recording.
     * Samples outside the recording are silence.
     */
    fun extract(recording: Recording, map: RecordingMap, fromTransport: Long, length: Int): FloatArray {
        val out = FloatArray(length)
        val offset = fromTransport - map.startFrame + map.latencyFrames
        for (j in 0 until length) {
            val index = offset + j
            if (index in 0 until recording.size) out[j] = recording.samples[index.toInt()]
        }
        return out
    }
}

/** Captured mono samples; only the first [size] are valid. */
class Recording(val samples: FloatArray, val size: Int = samples.size)

/** Record frame `i` ↔ transport frame `startFrame + i − latencyFrames`. */
data class RecordingMap(val startFrame: Long, val latencyFrames: Int)

/**
 * A take aligned to the loop: [samples] covers transport `[loopStart − preroll, loopStart +
 * loopFrames + tail)`. Converters analyse the whole window; the kept raw take is the loop part.
 */
class AlignedTake(
    val samples: FloatArray,
    val prerollFrames: Int,
    val loopFrames: Int,
    val sampleRate: Int,
) {
    val tailFrames: Int get() = samples.size - prerollFrames - loopFrames

    /** Exactly one loop of audio. */
    fun loopOnly(): FloatArray = samples.copyOfRange(prerollFrames, prerollFrames + loopFrames)
}
