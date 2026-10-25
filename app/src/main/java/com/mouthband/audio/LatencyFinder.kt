package com.mouthband.audio

import kotlin.math.sqrt

/**
 * Round-trip latency from a calibration recording (spec §2.2). For each emitted burst, the
 * normalized cross-correlation between the burst and the recording is searched within
 * ±[windowMs] of where the burst would sit with zero latency; the peak lag is that burst's
 * latency. The result is the median lag, rejected when fewer than [minAccepted] bursts reach
 * a correlation of [minCorrelation].
 */
object LatencyFinder {
    const val DEFAULT_WINDOW_MS = 400.0
    const val MIN_CORRELATION = 0.3f
    const val MIN_ACCEPTED = 5

    data class BurstResult(val lagFrames: Int, val correlation: Float)

    data class Result(val latencyFrames: Int?, val bursts: List<BurstResult>) {
        val accepted: Int get() = bursts.count { it.correlation >= MIN_CORRELATION }
    }

    /**
     * [recording] index 0 is transport frame [recordingStartFrame]; [schedule] holds each
     * burst and the transport frame where it began.
     */
    fun find(
        schedule: BurstSource.Schedule,
        recording: Recording,
        recordingStartFrame: Long,
        sampleRate: Int,
        windowMs: Double = DEFAULT_WINDOW_MS,
        minCorrelation: Float = MIN_CORRELATION,
        minAccepted: Int = MIN_ACCEPTED,
    ): Result {
        val window = LoopMath.msToFrames(windowMs, sampleRate)
        val results = schedule.bursts.indices.map { k ->
            val expected = schedule.frames[k] - recordingStartFrame
            peakLag(schedule.bursts[k], recording, expected, window)
        }
        val good = results.filter { it.correlation >= minCorrelation }.map { it.lagFrames }.sorted()
        val latency = if (good.size >= minAccepted) median(good) else null
        return Result(latency, results)
    }

    /** Best normalized cross-correlation of [burst] around [expected] in the recording. */
    fun peakLag(burst: FloatArray, recording: Recording, expected: Long, window: Int): BurstResult {
        val samples = recording.samples
        val size = recording.size
        val length = burst.size
        var burstEnergy = 0.0
        for (v in burst) burstEnergy += v * v
        if (burstEnergy == 0.0) return BurstResult(0, 0f)
        val first = maxOf(expected - window, 0L).toInt()
        val last = minOf(expected + window, (size - length).toLong()).toInt()
        if (last < first) return BurstResult(0, 0f)
        var windowEnergy = 0.0
        for (i in first until first + length) windowEnergy += samples[i] * samples[i]
        var bestLag = 0
        var best = -1.0
        for (start in first..last) {
            if (start > first) {
                val out = samples[start - 1]
                val inn = samples[start + length - 1]
                windowEnergy += inn * inn - out * out
            }
            if (windowEnergy > ENERGY_EPSILON) {
                var dot = 0.0
                for (n in 0 until length) dot += burst[n] * samples[start + n]
                val ncc = dot / sqrt(burstEnergy * windowEnergy)
                if (ncc > best) {
                    best = ncc
                    bestLag = (start - expected).toInt()
                }
            }
        }
        return BurstResult(bestLag, best.toFloat().coerceAtLeast(0f))
    }

    private fun median(sorted: List<Int>): Int {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private const val ENERGY_EPSILON = 1e-12
}

/** Deterministic white-noise bursts: a different seed per burst so neighbours never correlate. */
object NoiseBurst {
    const val BURST_MS = 10.0
    const val COUNT = 8
    const val INTERVAL_MS = 500.0
    private const val AMPLITUDE = 0.8f
    private const val SEED_BASE = 0x5EED

    fun create(index: Int, sampleRate: Int): FloatArray {
        val random = java.util.Random((SEED_BASE + index).toLong())
        val length = LoopMath.msToFrames(BURST_MS, sampleRate)
        return FloatArray(length) { (random.nextFloat() * 2f - 1f) * AMPLITUDE }
    }
}
