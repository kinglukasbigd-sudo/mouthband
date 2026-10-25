package com.mouthband.synth

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decodes RIFF/WAVE (PCM 16/24/32-bit or 32-bit float, any channel count) to mono float. */
object WavReader {
    class Decoded(val sampleRate: Int, val samples: FloatArray)

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE
    private const val PCM16_SCALE = 32768f
    private const val PCM24_SCALE = 8388608f
    private const val PCM32_SCALE = 2147483648f
    private const val BITS_16 = 16
    private const val BITS_24 = 24
    private const val BITS_32 = 32
    private const val BITS_PER_BYTE = 8
    private const val RIFF_HEADER = 12
    private const val CHUNK_HEADER = 8
    private const val BYTE_MASK = 0xFF
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val EXTENSIBLE_FMT_SIZE = 26
    private const val SUBFORMAT_OFFSET = 24
    private const val ID_BYTES = 4
    private const val WAVE_ID_OFFSET = 8
    private const val SHORT_MASK = 0xFFFF
    private const val FMT_CHANNELS_OFFSET = 2
    private const val FMT_RATE_OFFSET = 4
    private const val FMT_BITS_OFFSET = 14

    @Throws(IOException::class)
    fun read(input: InputStream): Decoded {
        val bytes = input.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (!isWave(bytes)) throw IOException("not a WAVE file")
        var pos = RIFF_HEADER
        var fmt: Fmt? = null
        var decoded: Decoded? = null
        while (decoded == null && pos + CHUNK_HEADER <= bytes.size) {
            val id = String(bytes, pos, ID_BYTES)
            val size = buffer.getInt(pos + ID_BYTES)
            val body = pos + CHUNK_HEADER
            when (id) {
                "fmt " -> fmt = readFmt(buffer, body, size)
                "data" -> decoded = decode(buffer, body, minOf(size, bytes.size - body), fmt)
            }
            pos = body + size + (size and 1)
        }
        return decoded ?: throw IOException("no data chunk")
    }

    private fun isWave(bytes: ByteArray): Boolean = bytes.size >= RIFF_HEADER &&
        String(bytes, 0, ID_BYTES) == "RIFF" &&
        String(bytes, WAVE_ID_OFFSET, ID_BYTES) == "WAVE"

    private class Fmt(val code: Int, val channels: Int, val rate: Int, val bits: Int)

    private fun readFmt(buffer: ByteBuffer, body: Int, size: Int): Fmt {
        var code = buffer.getShort(body).toInt() and SHORT_MASK
        // WAVE_FORMAT_EXTENSIBLE: the real format code starts the sub-format GUID.
        if (code == FORMAT_EXTENSIBLE && size >= EXTENSIBLE_FMT_SIZE) {
            code = buffer.getShort(body + SUBFORMAT_OFFSET).toInt() and SHORT_MASK
        }
        return Fmt(
            code = code,
            channels = buffer.getShort(body + FMT_CHANNELS_OFFSET).toInt(),
            rate = buffer.getInt(body + FMT_RATE_OFFSET),
            bits = buffer.getShort(body + FMT_BITS_OFFSET).toInt(),
        )
    }

    private fun decode(buffer: ByteBuffer, offset: Int, length: Int, fmt: Fmt?): Decoded {
        val supported = fmt != null && (fmt.code == FORMAT_PCM || fmt.code == FORMAT_FLOAT)
        if (fmt == null || !supported) throw IOException("missing or unsupported fmt chunk")
        if (fmt.channels <= 0 || fmt.bits <= 0) throw IOException("bad fmt chunk")
        val bytesPerSample = fmt.bits / BITS_PER_BYTE
        val frames = length / (bytesPerSample * fmt.channels)
        val isFloat = fmt.code == FORMAT_FLOAT
        val samples = FloatArray(frames) { f ->
            var sum = 0f
            for (c in 0 until fmt.channels) {
                val at = offset + (f * fmt.channels + c) * bytesPerSample
                sum += sample(buffer, at, fmt.bits, isFloat)
            }
            sum / fmt.channels
        }
        return Decoded(fmt.rate, samples)
    }

    private fun sample(buffer: ByteBuffer, at: Int, bits: Int, isFloat: Boolean): Float = when {
        isFloat -> buffer.getFloat(at)
        bits == BITS_16 -> buffer.getShort(at) / PCM16_SCALE
        bits == BITS_24 -> {
            val v = (buffer.get(at).toInt() and BYTE_MASK) or
                ((buffer.get(at + 1).toInt() and BYTE_MASK) shl SHIFT_8) or
                (buffer.get(at + 2).toInt() shl SHIFT_16)
            v / PCM24_SCALE
        }
        bits == BITS_32 -> buffer.getInt(at) / PCM32_SCALE
        else -> throw IOException("unsupported bit depth $bits")
    }
}
