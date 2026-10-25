package com.mouthband.audio

import java.util.concurrent.atomic.AtomicInteger

/**
 * Lock-free single-producer / single-consumer ring buffer of floats. The producer (record
 * thread) only moves [writeIndex]; the consumer only moves [readIndex]. Indices are free-running
 * ints; their difference stays correct across overflow because the capacity is a power of two
 * far below 2^31. No allocation after construction.
 */
class FloatRingBuffer(capacity: Int) {
    init {
        require(capacity > 0 && capacity and (capacity - 1) == 0) { "capacity must be a power of two" }
    }

    private val buffer = FloatArray(capacity)
    private val mask = capacity - 1
    private val writeIndex = AtomicInteger(0)
    private val readIndex = AtomicInteger(0)
    val capacity: Int get() = buffer.size

    fun availableToRead(): Int = writeIndex.get() - readIndex.get()

    fun availableToWrite(): Int = buffer.size - availableToRead()

    /** Producer side. Writes as much as fits and returns the count; the rest is dropped. */
    fun write(src: FloatArray, offset: Int, count: Int): Int {
        val w = writeIndex.get()
        val n = minOf(count, buffer.size - (w - readIndex.get()))
        for (i in 0 until n) {
            buffer[(w + i) and mask] = src[offset + i]
        }
        writeIndex.lazySet(w + n)
        return n
    }

    /** Consumer side. Reads up to [count] samples and returns how many were read. */
    fun read(dst: FloatArray, offset: Int, count: Int): Int {
        val r = readIndex.get()
        val n = minOf(count, writeIndex.get() - r)
        for (i in 0 until n) {
            dst[offset + i] = buffer[(r + i) and mask]
        }
        readIndex.lazySet(r + n)
        return n
    }

    /** Consumer side: drop everything currently buffered. */
    fun clear() {
        readIndex.set(writeIndex.get())
    }
}
