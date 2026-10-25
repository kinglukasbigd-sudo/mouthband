package com.mouthband.audio

/**
 * What the Recording screen draws while a take is captured, each sound at the loop position where
 * it was heard: a peak and a pitch per column of the loop and every live drum hit with its exact
 * frame. Written by the capture consumer, read by the UI once per display frame. Columns are single
 * floats written in place and hits are published by count after they are stored, so a frame is at
 * most one update behind and never inconsistent. Allocation-free after construction.
 */
class LiveTake(val loopFrames: Int, val columns: Int = COLUMNS) {
    /** Raw 0..1 peak per column (draw it with [displayScale]); 0 where nothing was recorded yet. */
    val peaks = FloatArray(columns)

    /** Fractional MIDI per column; NaN where nothing voiced was heard. */
    val pitch = FloatArray(columns) { Float.NaN }

    private val hitFrames = IntArray(MAX_HITS)
    private val hitClasses = IntArray(MAX_HITS)

    @Volatile
    var hitCount: Int = 0
        private set

    /** Column of a loop-relative frame, or -1 outside the loop. */
    fun column(loopFrame: Long): Int =
        if (loopFrame < 0 || loopFrame >= loopFrames) -1 else (loopFrame * columns / loopFrames).toInt()

    fun addPeak(loopFrame: Long, peak: Float) {
        val c = column(loopFrame)
        if (c >= 0 && peak > peaks[c]) peaks[c] = peak
    }

    /** Sets the pitch from [fromFrame] to [toFrame], so the line has no holes between estimates. */
    fun addPitch(fromFrame: Long, toFrame: Long, midi: Float) {
        val from = column(fromFrame.coerceIn(0, loopFrames - 1L))
        val to = column(toFrame.coerceIn(0, loopFrames - 1L))
        if (toFrame < 0 || fromFrame >= loopFrames) return
        for (c in from..to) pitch[c] = midi
    }

    fun addHit(loopFrame: Long, drumClass: Int) {
        val n = hitCount
        if (loopFrame < 0 || loopFrame >= loopFrames || n >= MAX_HITS) return
        hitFrames[n] = loopFrame.toInt()
        hitClasses[n] = drumClass
        hitCount = n + 1
    }

    /**
     * Factor that makes the loudest column of the take fill the lane: a whisper and a shout both
     * show their full shape. Read while drawing; scans [columns] floats.
     */
    fun displayScale(): Float {
        var loudest = MIN_REFERENCE
        for (p in peaks) if (p > loudest) loudest = p
        return 1f / loudest
    }

    fun hitFrame(index: Int): Int = hitFrames[index]

    fun hitClass(index: Int): Int = hitClasses[index]

    companion object {
        const val COLUMNS = 480
        const val MAX_HITS = 256

        /** The waveform is never stretched past this reference, so a silent room stays flat. */
        const val MIN_REFERENCE = 0.001f
    }
}

/** Maps a record-stream index to the loop frame where it was heard; a fun interface, so no boxing. */
fun interface LoopClock {
    fun loopFrameAt(recordIndex: Long): Long
}

/**
 * Feeds the [LiveTake] waveform: the peak of every short block, placed at the loop frame where
 * it was heard. Runs on the capture consumer.
 */
class LiveWaveformSink(
    private val take: LiveTake,
    private val clock: LoopClock,
    private val blockFrames: Int = BLOCK_FRAMES,
) : CaptureSink {
    private var index = 0L
    private var blockPeak = 0f
    private var blockFill = 0

    override fun accept(samples: FloatArray, count: Int) {
        for (i in 0 until count) {
            val v = samples[i]
            val a = if (v < 0f) -v else v
            if (a > blockPeak) blockPeak = a
            blockFill++
            if (blockFill == blockFrames) {
                val centre = index + i - blockFrames / 2
                take.addPeak(clock.loopFrameAt(centre), blockPeak)
                blockPeak = 0f
                blockFill = 0
            }
        }
        index += count
    }

    private companion object {
        const val BLOCK_FRAMES = 256
    }
}
