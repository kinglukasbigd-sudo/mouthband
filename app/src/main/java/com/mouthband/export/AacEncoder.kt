package com.mouthband.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.mouthband.audio.AudioLog
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * M4A export (spec §2.9): `MediaCodec` AAC-LC at 192 kbps fed with 16-bit PCM, written by
 * `MediaMuxer` into an MPEG-4 container. Codec and muxer are always released in [close].
 */
class AacEncoder(
    output: File,
    private val sampleRate: Int,
    private val channels: Int = WavWriter.STEREO,
) : Closeable {
    private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private val pcm = ByteBuffer.allocate(PCM_CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private var track = -1
    private var muxing = false
    private var framesQueued = 0L
    private var finished = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PCM_CHUNK_BYTES)
        }
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (e: IllegalStateException) {
            close()
            throw IOException("AAC encoder unavailable", e)
        }
    }

    /** Encodes [frames] interleaved float frames. */
    fun write(interleaved: FloatArray, frames: Int) {
        for (i in 0 until frames * channels) {
            if (pcm.remaining() < WavWriter.BYTES_PER_SAMPLE) queuePcm(endOfStream = false)
            pcm.putShort(WavWriter.toPcm16(interleaved[i]))
        }
    }

    /** Flushes the last samples and drains the codec to end of stream. */
    fun finish() {
        queuePcm(endOfStream = true)
        while (!finished) drain(TIMEOUT_US)
    }

    private fun queuePcm(endOfStream: Boolean) {
        pcm.flip()
        var sentLast = false
        while (pcm.hasRemaining() || (endOfStream && !sentLast)) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) sentLast = fill(index, endOfStream)
            drain(0)
        }
        pcm.clear()
    }

    /** Copies pending PCM into input buffer [index]; returns true when it carried end of stream. */
    private fun fill(index: Int, endOfStream: Boolean): Boolean {
        val input = codec.getInputBuffer(index) ?: throw IOException("no input buffer")
        input.clear()
        val count = minOf(input.remaining(), pcm.remaining())
        val slice = pcm.slice()
        slice.limit(count)
        input.put(slice)
        pcm.position(pcm.position() + count)
        val presentationUs = framesQueued * MICROS_PER_SECOND / sampleRate
        framesQueued += count / (WavWriter.BYTES_PER_SAMPLE * channels)
        val last = endOfStream && !pcm.hasRemaining()
        val flags = if (last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        codec.queueInputBuffer(index, 0, count, presentationUs, flags)
        return last
    }

    private fun drain(timeoutUs: Long) {
        while (true) {
            val index = codec.dequeueOutputBuffer(info, timeoutUs)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxing = true
                }
                index >= 0 -> {
                    val output = codec.getOutputBuffer(index) ?: throw IOException("no output buffer")
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && muxing) muxer.writeSampleData(track, output, info)
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        finished = true
                        return
                    }
                }
            }
        }
    }

    override fun close() {
        try {
            codec.stop()
        } catch (e: IllegalStateException) {
            AudioLog.w("codec stop", e)
        } finally {
            codec.release()
            try {
                if (muxing) muxer.stop()
            } catch (e: IllegalStateException) {
                AudioLog.w("muxer stop", e)
            } finally {
                muxer.release()
            }
        }
    }

    private companion object {
        const val BIT_RATE = 192_000
        const val PCM_CHUNK_BYTES = 16 * 1024
        const val TIMEOUT_US = 10_000L
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
