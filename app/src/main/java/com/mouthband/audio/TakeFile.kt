package com.mouthband.audio

import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Raw take files (`.f32`): an 8-byte header ("MBF1" + sample rate, little endian) followed by
 * mono 32-bit float samples. Writes go to a temp file that is renamed into place, so a take is
 * either complete or absent (spec §4 atomic writes).
 */
object TakeFile {
    private const val MAGIC = 0x3146424D // "MBF1" little endian
    const val HEADER_BYTES = 8

    /** A damaged header or size is refused instead of allocating gigabytes. */
    private const val MIN_RATE = 8_000
    private const val MAX_RATE = 192_000
    private const val MAX_SAMPLES = 192_000 * 120

    data class Audio(val sampleRate: Int, val samples: FloatArray)

    /** Writes [samples] atomically to [target]. */
    @Throws(IOException::class)
    fun write(target: File, sampleRate: Int, samples: FloatArray, count: Int = samples.size) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        TakeWriter(temp, sampleRate).use { it.append(samples, count) }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("rename failed: ${target.name}")
        }
    }

    @Throws(IOException::class)
    fun read(file: File): Audio {
        val bytes = file.length() - HEADER_BYTES
        if (bytes < 0) throw IOException("truncated take: ${file.name}")
        DataInputStream(file.inputStream().buffered()).use { input ->
            val header = ByteArray(HEADER_BYTES)
            input.readFully(header)
            val rate = sampleRateOf(header, file.name)
            val count = (bytes / Float.SIZE_BYTES).toInt()
            if (count > MAX_SAMPLES) throw IOException("take too long: ${file.name}")
            val raw = ByteArray(count * Float.SIZE_BYTES)
            input.readFully(raw)
            val samples = FloatArray(count)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples)
            return Audio(rate, samples)
        }
    }

    /** The sample rate in [header], or an [IOException] when it is not a take file or the rate is damaged. */
    private fun sampleRateOf(header: ByteArray, name: String): Int {
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) throw IOException("not a take file: $name")
        val rate = buffer.int
        if (rate !in MIN_RATE..MAX_RATE) throw IOException("bad sample rate $rate: $name")
        return rate
    }

    /** Streams floats into a take file; used by the capture consumer while recording. */
    class TakeWriter(file: File, sampleRate: Int) : Closeable {
        private val channel: FileChannel = openCreatingFolder(file)
        private val buffer = ByteBuffer.allocateDirect(CHUNK_BYTES).order(ByteOrder.LITTLE_ENDIAN)

        init {
            buffer.putInt(MAGIC).putInt(sampleRate)
            flush()
        }

        fun append(samples: FloatArray, count: Int) {
            var i = 0
            while (i < count) {
                if (buffer.remaining() < Float.SIZE_BYTES) flush()
                buffer.putFloat(samples[i])
                i++
            }
        }

        private fun flush() {
            buffer.flip()
            while (buffer.hasRemaining()) channel.write(buffer)
            buffer.clear()
        }

        override fun close() {
            try {
                flush()
                channel.force(false)
            } finally {
                channel.close()
            }
        }

        private companion object {
            const val CHUNK_BYTES = 64 * 1024
        }
    }
}

/** Opens [file] for writing, creating its folder first (a new song has none yet). */
private fun openCreatingFolder(file: File): FileChannel {
    file.parentFile?.mkdirs()
    return FileOutputStream(file).channel
}
