package com.mouthband.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import java.util.Arrays
import java.util.concurrent.atomic.AtomicLong

/**
 * The single output stream (spec §2.1): one low-latency float stereo [AudioTrack] fed by one
 * urgent-audio render thread. The thread mixes the active [source] and the [metronome] into a
 * preallocated block and advances a sample-accurate transport.
 */
class AudioEngine(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val sampleRate: Int = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        ?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE

    val framesPerBuffer: Int = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        ?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_FRAMES_PER_BUFFER

    val metronome = Metronome(sampleRate)

    /** What the render thread plays besides the metronome. Swapped by reference. */
    @Volatile
    var source: RenderSource? = null

    /** Transport position of the next block to be rendered. */
    @Volatile
    var transportFrame: Long = 0L
        private set

    /** Frames handed to the AudioTrack since it started. */
    @Volatile
    var totalWritten: Long = 0L
        private set

    /** [totalWritten] value at which the transport was 0: transport = trackFrame − origin. */
    @Volatile
    var transportOrigin: Long = 0L
        private set

    @Volatile
    private var running = false
    private val transportRequest = AtomicLong(NO_REQUEST)
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val timestamp = AudioTimestamp()

    val isRunning: Boolean get() = running

    /** Opens the output stream if needed. Returns false when the device refuses it. */
    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val t = createTrack() ?: return false
        track = t
        totalWritten = 0L
        transportOrigin = 0L
        transportFrame = 0L
        running = true
        thread = Thread({ renderLoop(t) }, "MouthBand-render").also { it.start() }
        return true
    }

    /** Stops and releases the stream; safe to call repeatedly. */
    @Synchronized
    fun stop() {
        if (!running && track == null) return
        running = false
        val t = track
        try {
            t?.pause()
            t?.flush()
            thread?.join(JOIN_TIMEOUT_MS)
        } catch (e: IllegalStateException) {
            AudioLog.w("AudioTrack stop", e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            AudioLog.w("render join interrupted", e)
        } finally {
            t?.release()
            track = null
            thread = null
            source = null
            metronome.plan = null
        }
    }

    /** Moves the transport to [frame] at the start of the next block. */
    fun resetTransport(frame: Long = 0L) {
        transportRequest.set(frame)
    }

    /** Current transport position as seen by a control thread (frames already written). */
    fun writtenTransportFrame(): Long = totalWritten - transportOrigin

    /**
     * Presentation timestamp of the output: transport frame being played at [out].nanoTime.
     * Returns false before the stream reports one.
     */
    @Synchronized
    fun presentationTimestamp(out: LongArray): Boolean {
        val t = track ?: return false
        if (!t.getTimestamp(timestamp)) return false
        out[0] = timestamp.framePosition - transportOrigin
        out[1] = timestamp.nanoTime
        return true
    }

    private fun createTrack(): AudioTrack? {
        val t = buildTrack() ?: return null
        if (t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return null
        }
        t.bufferSizeInFrames = framesPerBuffer * START_BUFFERS
        return try {
            t.play()
            t
        } catch (e: IllegalStateException) {
            AudioLog.w("AudioTrack play", e)
            t.release()
            null
        }
    }

    private fun buildTrack(): AudioTrack? {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val capacityBytes = maxOf(minBytes, framesPerBuffer * CAPACITY_BUFFERS * BYTES_PER_FRAME)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(capacityBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: UnsupportedOperationException) {
            AudioLog.w("AudioTrack build", e)
            null
        } catch (e: IllegalArgumentException) {
            AudioLog.w("AudioTrack build", e)
            null
        }
    }

    private fun renderLoop(t: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frames = framesPerBuffer
        val block = FloatArray(frames * 2)
        var frame = 0L
        var written = 0L
        var blocks = 0
        var lastUnderruns = t.underrunCount
        var raised = false
        // RT-LOOP-BEGIN: no allocation, locks or logging below.
        while (running) {
            val request = transportRequest.getAndSet(NO_REQUEST)
            if (request != NO_REQUEST) {
                frame = request
                transportOrigin = written - request
            }
            Arrays.fill(block, 0f)
            val s = source
            s?.render(block, frames, frame)
            metronome.render(block, frames, frame)
            for (i in block.indices) {
                val v = block[i]
                if (v > 1f) block[i] = 1f else if (v < -1f) block[i] = -1f
            }
            val n = t.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
            if (n < 0) break
            written += frames
            frame += frames
            totalWritten = written
            transportFrame = frame
            blocks++
            if (!raised && blocks % UNDERRUN_CHECK_BLOCKS == 0) {
                val underruns = t.underrunCount
                if (underruns > lastUnderruns) {
                    t.bufferSizeInFrames = framesPerBuffer * RAISED_BUFFERS
                    raised = true
                }
                lastUnderruns = underruns
            }
        }
        // RT-LOOP-END
        running = false
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        const val DEFAULT_FRAMES_PER_BUFFER = 256
        private const val BYTES_PER_FRAME = 8
        private const val START_BUFFERS = 2
        private const val RAISED_BUFFERS = 4
        private const val CAPACITY_BUFFERS = 8
        private const val UNDERRUN_CHECK_BLOCKS = 32
        private const val JOIN_TIMEOUT_MS = 500L
        private const val NO_REQUEST = Long.MIN_VALUE
    }
}
