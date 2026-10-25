package com.mouthband.audio

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sqrt

/** Receives captured samples on the consumer coroutine (never on the record thread). */
fun interface CaptureSink {
    fun accept(samples: FloatArray, count: Int)
}

/**
 * Drains the [Recorder] ring buffer (spec §2.1): forwards samples to sinks (take file,
 * memory, live analysis) and publishes the mic level and a scrolling peak history at most
 * 30 times per second.
 */
class CaptureConsumer(private val recorder: Recorder, historyBuckets: Int = DEFAULT_HISTORY) {
    private val _level = MutableStateFlow(MicLevel.Silent)
    val level: StateFlow<MicLevel> = _level.asStateFlow()

    private val _peaks = MutableStateFlow(FloatArray(historyBuckets))
    val peaks: StateFlow<FloatArray> = _peaks.asStateFlow()

    private val chunk = FloatArray(CHUNK)
    private val history = FloatArray(historyBuckets)
    private val bucketFrames = (recorder.sampleRate * BUCKET_SECONDS).toInt()
    private var bucketPeak = 0f
    private var bucketFill = 0
    private var sumSquares = 0.0
    private var windowFrames = 0
    private var windowPeak = 0f
    private var holdDb = LevelMath.FLOOR_DB
    private var holdSetAt = 0L
    private var lastEmit = 0L

    /** Runs until the calling coroutine is cancelled. */
    suspend fun drain(sinks: List<CaptureSink>) {
        while (currentCoroutineContext().isActive) {
            if (drainOnce(sinks) == 0) delay(POLL_MS)
        }
    }

    /** Forwards whatever is buffered right now; returns the sample count. */
    fun drainOnce(sinks: List<CaptureSink>): Int {
        var total = 0
        while (true) {
            val n = recorder.ring.read(chunk, 0, chunk.size)
            if (n == 0) break
            total += n
            for (sink in sinks) sink.accept(chunk, n)
            meter(n)
        }
        publishIfDue()
        return total
    }

    private fun meter(n: Int) {
        for (i in 0 until n) {
            val v = chunk[i]
            val a = abs(v)
            sumSquares += v * v
            if (a > windowPeak) windowPeak = a
            if (a > bucketPeak) bucketPeak = a
            bucketFill++
            if (bucketFill >= bucketFrames) {
                System.arraycopy(history, 1, history, 0, history.size - 1)
                history[history.size - 1] = bucketPeak.coerceAtMost(1f)
                bucketPeak = 0f
                bucketFill = 0
            }
        }
        windowFrames += n
    }

    private fun publishIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastEmit < EMIT_INTERVAL_MS || windowFrames == 0) return
        lastEmit = now
        val rmsDb = LevelMath.amplitudeToDb(sqrt(sumSquares / windowFrames).toFloat())
        val peakDb = LevelMath.amplitudeToDb(windowPeak)
        if (rmsDb >= holdDb || now - holdSetAt > HOLD_MS) {
            holdDb = rmsDb
            holdSetAt = now
        }
        _level.value = MicLevel(rmsDb, peakDb, holdDb)
        _peaks.value = history.copyOf()
        sumSquares = 0.0
        windowFrames = 0
        windowPeak = 0f
    }

    companion object {
        const val DEFAULT_HISTORY = 96
        private const val CHUNK = 4096
        private const val POLL_MS = 10L
        private const val EMIT_INTERVAL_MS = 33L
        private const val HOLD_MS = 1500L
        private const val BUCKET_SECONDS = 0.02
    }
}

/** Collects samples into one preallocated array (calibration, teach). */
class MemorySink(capacity: Int) : CaptureSink {
    val samples = FloatArray(capacity)
    var size: Int = 0
        private set

    override fun accept(samples: FloatArray, count: Int) {
        val n = minOf(count, this.samples.size - size)
        System.arraycopy(samples, 0, this.samples, size, n)
        size += n
    }
}
