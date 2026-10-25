package com.mouthband.audio

import com.mouthband.data.prefs.AudioSettings
import com.mouthband.data.prefs.LatencySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * One take (spec §2.1): count-in clicks, backing loop, capture to a temp float file, then
 * alignment and trimming to the loop. Recording covers count-in + N bars + 250 ms tail.
 */
class RecordingSession(
    private val engine: AudioEngine,
    private val recorder: Recorder,
) {
    /** What to record. [backing] is already loaded with the earlier tracks. */
    class Request(
        val bpm: Int,
        val loopBars: Int,
        val backing: LoopPlayer,
        val settings: AudioSettings,
        val tempFile: File,
        val liveSinks: List<CaptureSink> = emptyList(),
    )

    /** Live position for the Recording screen. Bars and beats are 0-based. */
    data class Progress(
        val countIn: Boolean,
        val bar: Int,
        val beat: Int,
        val totalBars: Int,
        val elapsedMs: Long,
    )

    sealed interface Result {
        data class Captured(val take: AlignedTake) : Result

        data class Failed(val reason: RecorderStart) : Result

        /** The take could not be written to or read back from app storage. */
        data object StorageFailed : Result
    }

    /** Transport layout of one take, in frames. */
    private class Timeline(request: Request, rate: Int) {
        val sampleRate = rate
        val bars = request.loopBars
        val beat = LoopMath.framesPerBeat(request.bpm, rate)
        val lead = LoopMath.msToFrames(LEAD_MS, rate).toLong()
        val loopStart = lead + (request.settings.countInBars * LoopMath.BEATS_PER_BAR * beat).roundToInt()
        val loopFrames = LoopMath.loopFrames(request.loopBars, request.bpm, rate)
        val tail = LoopMath.msToFrames(TAIL_MS, rate)
        val preroll = LoopMath.msToFrames(PREROLL_MS, rate)
        val end = loopStart + loopFrames + tail
        val latency = LoopMath.msToFrames(request.settings.latencyMs.toDouble(), rate)
    }

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    @Volatile
    var consumer: CaptureConsumer? = null
        private set

    @Volatile
    private var stopRequested = false

    @Volatile
    private var current: Timeline? = null

    /** Loop-relative transport frame of record frame [index] in the running take (live grid). */
    fun loopFrameOf(index: Long): Long? {
        val t = current ?: return null
        return recorder.counterStartFrame + index - t.latency - t.loopStart
    }

    /** [loopFrameOf] without boxing for per-block live analysis; [Long.MIN_VALUE] before the take starts. */
    fun loopFrameAt(index: Long): Long {
        val t = current ?: return Long.MIN_VALUE
        return recorder.counterStartFrame + index - t.latency - t.loopStart
    }

    /**
     * Loop-relative transport frame reaching the speaker now (negative during count-in,
     * [Long.MIN_VALUE] when no take runs). Called once per display frame, so it does not box.
     */
    fun heardLoopFrame(): Long {
        val t = current ?: return Long.MIN_VALUE
        return engine.writtenTransportFrame() - engine.framesPerBuffer * HEARD_BUFFERS - t.loopStart
    }

    /** 16th-note length of the running take, for live displays. */
    fun stepFrames(): Double? = current?.let { it.beat / LoopMath.STEPS_PER_BEAT }

    /** Ends the take early; what was recorded so far is kept and processed. */
    fun requestStop() {
        stopRequested = true
    }

    suspend fun record(request: Request): Result = withContext(Dispatchers.Default) {
        stopRequested = false
        val timeline = Timeline(request, engine.sampleRate)
        current = timeline
        val started = startTransport(request, timeline)
        if (started != RecorderStart.OK) return@withContext Result.Failed(started)
        val writer = try {
            TakeFile.TakeWriter(request.tempFile, timeline.sampleRate)
        } catch (e: IOException) {
            AudioLog.w("take file", e)
            recorder.stop()
            stopPlayback()
            return@withContext Result.StorageFailed
        }
        val stamps = StartFrameSampler(engine, recorder)
        if (capture(request, timeline, writer, stamps)) {
            request.tempFile.delete()
            return@withContext Result.Failed(RecorderStart.UNAVAILABLE)
        }
        val useTimestamps = request.settings.latencySource == LatencySource.TIMESTAMP
        val start = stamps.median()?.takeIf { useTimestamps } ?: recorder.counterStartFrame
        align(request.tempFile, RecordingMap(start, timeline.latency), timeline)
    }

    private suspend fun startTransport(request: Request, t: Timeline): RecorderStart {
        if (!engine.start()) return RecorderStart.UNAVAILABLE
        request.backing.loopFrames = t.loopFrames
        request.backing.loopStartFrame = t.loopStart
        engine.source = request.backing
        engine.metronome.plan = Metronome.Plan(
            startFrame = t.lead,
            framesPerBeat = t.beat,
            countInEndFrame = t.loopStart,
            endFrame = t.loopStart + t.loopFrames,
            clickAfterCountIn = request.settings.clickOn,
        )
        engine.resetTransport(0)
        delay(RESET_WAIT_MS)
        val started = recorder.start(engine)
        if (started != RecorderStart.OK) stopPlayback()
        return started
    }

    /** Records until the take is complete or stopped; returns true when the mic stalled. */
    private suspend fun capture(
        request: Request,
        t: Timeline,
        writer: TakeFile.TakeWriter,
        stamps: StartFrameSampler,
    ): Boolean {
        val capture = CaptureConsumer(recorder)
        consumer = capture
        val sinks = listOf(CaptureSink { s, n -> writer.append(s, n) }) + request.liveSinks
        val watchdog = MicWatchdog()
        var canceled = false
        var stalled = false
        try {
            coroutineScope {
                val drain = launch { capture.drain(sinks) }
                while (!stopRequested && !isComplete(t) && !stalled) {
                    stamps.sample()
                    publish(t)
                    delay(POLL_MS)
                    stalled = watchdog.stalled(recorder.framesCaptured)
                }
                drain.cancel()
            }
            if (stalled) {
                AudioLog.w("mic stalled at ${recorder.framesCaptured} frames, read error ${recorder.readError}")
            }
        } catch (e: CancellationException) {
            canceled = true
            throw e
        } finally {
            withContext(NonCancellable) {
                recorder.stop()
                capture.drainOnce(sinks)
                writer.close()
                stopPlayback()
                _progress.value = null
                if (canceled) request.tempFile.delete()
            }
        }
        return stalled
    }

    /** Done once the transport passed the tail and the mic delivered audio up to it. */
    private fun isComplete(t: Timeline): Boolean {
        val needed = t.end - recorder.counterStartFrame + t.latency + MARGIN_FRAMES
        return engine.writtenTransportFrame() >= t.end && recorder.framesCaptured >= needed
    }

    private fun align(file: File, map: RecordingMap, t: Timeline): Result {
        val recording = try {
            TakeFile.read(file).samples
        } catch (e: IOException) {
            AudioLog.w("read take", e)
            return Result.StorageFailed
        }
        val window = t.preroll + t.loopFrames + t.tail
        val aligned = Alignment.extract(Recording(recording), map, t.loopStart - t.preroll, window)
        return Result.Captured(AlignedTake(aligned, t.preroll, t.loopFrames, t.sampleRate))
    }

    private fun publish(t: Timeline) {
        val heard = engine.writtenTransportFrame() - engine.framesPerBuffer * HEARD_BUFFERS
        val countIn = heard < t.loopStart
        val origin = if (countIn) t.lead else t.loopStart
        val beats = ((heard - origin).coerceAtLeast(0) / t.beat).toInt()
        _progress.value = Progress(
            countIn = countIn,
            bar = if (countIn) 0 else (beats / LoopMath.BEATS_PER_BAR).coerceAtMost(t.bars - 1),
            beat = beats % LoopMath.BEATS_PER_BAR,
            totalBars = t.bars,
            // Whole seconds only: the flow then emits on beat or second changes, not every poll.
            elapsedMs = LoopMath.framesToMs((heard - t.loopStart).coerceAtLeast(0), t.sampleRate).toLong() /
                MS_PER_SECOND * MS_PER_SECOND,
        )
    }

    private fun stopPlayback() {
        engine.metronome.plan = null
        engine.source = null
    }

    companion object {
        /** Silence before the first count-in click, so capture is running when clicks start. */
        const val LEAD_MS = 300.0
        const val TAIL_MS = 250.0

        /** Audio kept before the loop start so a slightly early downbeat is not lost. */
        const val PREROLL_MS = 100.0
        private const val RESET_WAIT_MS = 30L
        private const val POLL_MS = 30L
        private const val MARGIN_FRAMES = 512
        private const val HEARD_BUFFERS = 2
        private const val MS_PER_SECOND = 1000L
    }
}
