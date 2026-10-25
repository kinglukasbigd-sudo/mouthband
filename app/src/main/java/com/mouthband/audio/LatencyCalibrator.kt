package com.mouthband.audio

import com.mouthband.data.prefs.AudioSettings
import com.mouthband.data.prefs.LatencySource
import com.mouthband.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Latency calibration (spec §2.2): plays 8 × 10 ms white-noise bursts 500 ms apart on the
 * speaker, records them, and stores the median lag. On failure it stores the 150 ms fallback.
 */
class LatencyCalibrator(
    private val engine: AudioEngine,
    private val recorder: Recorder,
    private val settings: SettingsStore,
) {
    sealed interface Outcome {
        data class Measured(val latencyMs: Int, val source: LatencySource) : Outcome

        data class TooQuiet(val fallbackMs: Int) : Outcome

        data object NoPermission : Outcome

        data object AudioUnavailable : Outcome
    }

    suspend fun run(onProgress: (Float) -> Unit): Outcome = withContext(Dispatchers.Default) {
        val rate = engine.sampleRate
        val startedEngine = !engine.isRunning
        if (!engine.start()) return@withContext Outcome.AudioUnavailable
        val bursts = BurstSource()
        engine.metronome.plan = null
        engine.source = bursts
        engine.resetTransport(0)
        delay(SETTLE_MS)
        val sink = MemorySink(rate * CAPTURE_SECONDS)
        val consumer = CaptureConsumer(recorder)
        when (recorder.start(engine)) {
            RecorderStart.NO_PERMISSION -> return@withContext cleanup(startedEngine, Outcome.NoPermission)
            RecorderStart.UNAVAILABLE -> return@withContext cleanup(startedEngine, Outcome.AudioUnavailable)
            RecorderStart.OK -> Unit
        }
        val signals = Array(NoiseBurst.COUNT) { NoiseBurst.create(it, rate) }
        val interval = LoopMath.msToFrames(NoiseBurst.INTERVAL_MS, rate)
        val firstEmit = engine.writtenTransportFrame() + LoopMath.msToFrames(LEAD_MS, rate)
        val emits = LongArray(NoiseBurst.COUNT) { firstEmit + it.toLong() * interval }
        val schedule = BurstSource.Schedule(signals, emits)
        val stamps = StartFrameSampler(engine, recorder)
        try {
            coroutineScope {
                val drain = launch { consumer.drain(listOf(sink)) }
                bursts.schedule = schedule
                val end = emits.last() + LoopMath.msToFrames(TAIL_MS, rate)
                while (engine.writtenTransportFrame() < end) {
                    stamps.sample()
                    val done = (engine.writtenTransportFrame() - firstEmit).toFloat() / (end - firstEmit)
                    onProgress(done.coerceIn(0f, 1f))
                    delay(POLL_MS)
                }
                drain.cancel()
            }
        } finally {
            recorder.stop()
            bursts.schedule = null
        }
        consumer.drainOnce(listOf(sink))
        val timestampStart = stamps.median()
        val source = if (timestampStart != null) LatencySource.TIMESTAMP else LatencySource.COUNTER
        val start = timestampStart ?: recorder.counterStartFrame
        val result = LatencyFinder.find(schedule, Recording(sink.samples, sink.size), start, rate)
        val frames = result.latencyFrames
        AudioLog.d("calibration: ${result.bursts} via $source → $frames frames")
        val outcome = if (frames != null) {
            val ms = LoopMath.framesToMs(frames.toLong(), rate).roundToInt()
            settings.setLatency(ms, source)
            Outcome.Measured(ms, source)
        } else {
            settings.setLatency(AudioSettings.DEFAULT_LATENCY_MS, LatencySource.FALLBACK)
            Outcome.TooQuiet(AudioSettings.DEFAULT_LATENCY_MS)
        }
        cleanup(startedEngine, outcome)
    }

    private fun cleanup(stopEngine: Boolean, outcome: Outcome): Outcome {
        engine.source = null
        if (stopEngine) engine.stop()
        return outcome
    }

    private companion object {
        const val SETTLE_MS = 150L
        const val LEAD_MS = 400.0
        const val TAIL_MS = 700.0
        const val POLL_MS = 30L
        const val CAPTURE_SECONDS = 8
    }
}

/**
 * Collects several timestamp-based estimates of where record frame 0 sits on the transport and
 * returns their median (a single reading can be jittery on some devices).
 */
class StartFrameSampler(private val engine: AudioEngine, private val recorder: Recorder) {
    private val play = LongArray(2)
    private val rec = LongArray(2)
    private val estimates = ArrayList<Long>()

    fun sample() {
        if (estimates.size >= MAX_SAMPLES) return
        if (!engine.presentationTimestamp(play) || !recorder.captureTimestamp(rec)) return
        if (play[1] <= 0L || rec[1] <= 0L || rec[0] <= 0L) return
        estimates += Alignment.startFrameFromTimestamps(play[0], play[1], rec[0], rec[1], engine.sampleRate)
    }

    fun median(): Long? {
        if (estimates.size < MIN_SAMPLES) return null
        val sorted = estimates.sorted()
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val MAX_SAMPLES = 15
        const val MIN_SAMPLES = 3
    }
}
