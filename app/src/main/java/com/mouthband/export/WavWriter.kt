package com.mouthband.export

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * 16-bit PCM WAV writer (spec §1 Export: WAV 16-bit, 44.1 kHz stereo). Writes a placeholder
 * header, streams samples, then patches the RIFF and data sizes on [close].
 */
class WavWriter(file: File, private val sampleRate: Int, private val channels: Int = STEREO) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private val buffer = ByteBuffer.allocate(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(header(sampleRate, channels, 0))
    }

    /** Appends [frames] interleaved float frames, clipped to ±1. */
    fun write(interleaved: FloatArray, frames: Int) {
        for (i in 0 until frames * channels) {
            if (buffer.remaining() < BYTES_PER_SAMPLE) flush()
            buffer.putShort(toPcm16(interleaved[i]))
        }
    }

    private fun flush() {
        raf.write(buffer.array(), 0, buffer.position())
        dataBytes += buffer.position()
        buffer.clear()
    }

    override fun close() {
        try {
            flush()
            raf.seek(0)
            raf.write(header(sampleRate, channels, dataBytes))
        } finally {
            raf.close()
        }
    }

    companion object {
        const val STEREO = 2
        const val HEADER_BYTES = 44
        const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_BYTES = 64 * 1024
        private const val PCM_MAX = 32767f
        private const val FMT_CHUNK_SIZE = 16
        private const val FORMAT_PCM: Short = 1
        private const val BITS_PER_SAMPLE: Short = 16
        private const val RIFF_EXTRA = 36

        fun toPcm16(v: Float): Short = (v.coerceIn(-1f, 1f) * PCM_MAX).roundToInt().toShort()

        /** The canonical 44-byte RIFF/WAVE header for 16-bit PCM. */
        fun header(sampleRate: Int, channels: Int, dataBytes: Long): ByteArray {
            val b = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray(Charsets.US_ASCII))
            b.putInt((RIFF_EXTRA + dataBytes).toInt())
            b.put("WAVE".toByteArray(Charsets.US_ASCII))
            b.put("fmt ".toByteArray(Charsets.US_ASCII))
            b.putInt(FMT_CHUNK_SIZE)
            b.putShort(FORMAT_PCM)
            b.putShort(channels.toShort())
            b.putInt(sampleRate)
            b.putInt(sampleRate * channels * BYTES_PER_SAMPLE)
            b.putShort((channels * BYTES_PER_SAMPLE).toShort())
            b.putShort(BITS_PER_SAMPLE)
            b.put("data".toByteArray(Charsets.US_ASCII))
            b.putInt(dataBytes.toInt())
            return b.array()
        }
    }
}
