package com.mouthband.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process

/** Why the microphone could not be opened. */
enum class RecorderStart { OK, NO_PERMISSION, UNAVAILABLE }

/**
 * Mono float capture at the engine's sample rate (spec §2.1). A dedicated urgent-audio thread
 * reads the [AudioRecord] into a preallocated chunk and pushes it into [ring]; a consumer
 * coroutine drains the ring. Gain control, noise suppression and echo cancelling are disabled.
 */
class Recorder(context: Context, val sampleRate: Int) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val ring = FloatRingBuffer(RING_CAPACITY)

    @Volatile
    var framesCaptured: Long = 0L
        private set

    /** Engine transport (frames written) right after capture started — the counter mapping. */
    @Volatile
    var counterStartFrame: Long = 0L
        private set

    /** Last negative AudioRecord.read() result (0 = none), for diagnostics after a stall. */
    @Volatile
    var readError: Int = 0
        private set

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val effects = CaptureEffects()
    private val timestamp = AudioTimestamp()

    val isRunning: Boolean get() = running

    /** Opens the mic and starts capturing. [engine] (optional) supplies the counter mapping. */
    @Synchronized
    fun start(engine: AudioEngine?): RecorderStart {
        if (running) return RecorderStart.OK
        return when (val opened = openRecord()) {
            is Opened.Ok -> launchCapture(opened.record, engine)
            Opened.NoPermission -> RecorderStart.NO_PERMISSION
            Opened.Failed -> RecorderStart.UNAVAILABLE
        }
    }

    private fun launchCapture(r: AudioRecord, engine: AudioEngine?): RecorderStart {
        if (!beginCapture(r)) {
            releaseRecord(r)
            return RecorderStart.UNAVAILABLE
        }
        counterStartFrame = engine?.writtenTransportFrame() ?: 0L
        record = r
        running = true
        thread = Thread({ captureLoop(r) }, "MouthBand-record").also { it.start() }
        return RecorderStart.OK
    }

    private fun beginCapture(r: AudioRecord): Boolean {
        effects.disableAll(r.audioSessionId)
        ring.clear()
        framesCaptured = 0L
        readError = 0
        return try {
            r.startRecording()
            r.recordingState == AudioRecord.RECORDSTATE_RECORDING
        } catch (e: IllegalStateException) {
            AudioLog.w("startRecording", e)
            false
        }
    }

    @Synchronized
    fun stop() {
        val r = record ?: return
        running = false
        try {
            r.stop()
            thread?.join(JOIN_TIMEOUT_MS)
        } catch (e: IllegalStateException) {
            AudioLog.w("AudioRecord stop", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            AudioLog.w("record join interrupted", e)
        } finally {
            releaseRecord(r)
            record = null
            thread = null
        }
    }

    /**
     * Hardware capture timestamp: record frame [out]`[0]` was captured at [out]`[1]` ns
     * (CLOCK_MONOTONIC). False when the device does not provide one.
     */
    @Synchronized
    fun captureTimestamp(out: LongArray): Boolean {
        val r = record ?: return false
        val status = r.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        if (status != AudioRecord.SUCCESS) return false
        out[0] = timestamp.framePosition
        out[1] = timestamp.nanoTime
        return true
    }

    private sealed interface Opened {
        data class Ok(val record: AudioRecord) : Opened

        data object NoPermission : Opened

        data object Failed : Opened
    }

    /** Tries UNPROCESSED (when supported), then VOICE_RECOGNITION, then MIC. */
    private fun openRecord(): Opened {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBytes <= 0) return Opened.Failed
        val bytes = maxOf(minBytes * 2, sampleRate / BUFFER_DIVISOR * Float.SIZE_BYTES)
        for (source in sourcePreference()) {
            val opened = tryOpen(source, bytes)
            if (opened != Opened.Failed) return opened
        }
        return Opened.Failed
    }

    private fun tryOpen(source: Int, bytes: Int): Opened = try {
        val r = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bytes)
        if (r.state == AudioRecord.STATE_INITIALIZED) {
            Opened.Ok(r)
        } else {
            r.release()
            Opened.Failed
        }
    } catch (e: SecurityException) {
        AudioLog.w("record permission", e)
        Opened.NoPermission
    } catch (e: IllegalArgumentException) {
        AudioLog.w("record source $source", e)
        Opened.Failed
    }

    private fun sourcePreference(): List<Int> {
        val unprocessed =
            audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
    }

    private fun releaseRecord(r: AudioRecord) {
        effects.release()
        r.release()
    }

    private fun captureLoop(r: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val chunk = FloatArray(CHUNK_FRAMES)
        var captured = 0L
        // RT-LOOP-BEGIN: no allocation, locks or logging below.
        while (running) {
            val n = r.read(chunk, 0, CHUNK_FRAMES, AudioRecord.READ_BLOCKING)
            if (n < 0) {
                readError = n
                break
            }
            if (n > 0) {
                ring.write(chunk, 0, n)
                captured += n
                framesCaptured = captured
            }
        }
        // RT-LOOP-END
    }

    companion object {
        /** 2^19 floats ≈ 10.9 s at 48 kHz: far more than the consumer ever lags. */
        private const val RING_CAPACITY = 1 shl 19
        private const val CHUNK_FRAMES = 256
        private const val BUFFER_DIVISOR = 10
        private const val JOIN_TIMEOUT_MS = 500L
    }
}

/** Turns off automatic gain control, noise suppression and echo cancelling (spec §2.1). */
private class CaptureEffects {
    private val effects = mutableListOf<AudioEffect>()

    fun disableAll(sessionId: Int) {
        if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.let { effects += it }
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.let { effects += it }
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.let { effects += it }
        effects.forEach { effect ->
            try {
                effect.enabled = false
            } catch (e: IllegalStateException) {
                AudioLog.w("disable ${effect.javaClass.simpleName}", e)
            }
        }
    }

    fun release() {
        effects.forEach { it.release() }
        effects.clear()
    }
}
