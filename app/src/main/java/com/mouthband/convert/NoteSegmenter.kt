package com.mouthband.convert

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** A segmented note in pitch frames: `[startFrame, endFrame)`, [pitch] = median fractional MIDI. */
data class PitchNote(val startFrame: Int, val endFrame: Int, val pitch: Float)

/**
 * Note segmentation (spec §2.5, port of SwiftF0's `segment_notes` rules): split when the pitch
 * moves more than 0.8 semitones from the note's median, ignore unvoiced gaps shorter than
 * 20 ms, drop notes shorter than 50 ms, take each note's median pitch, and merge adjacent notes
 * that round to the same MIDI pitch.
 */
class NoteSegmenter(frameSeconds: Double) {
    private val graceFrames = (GRACE_SECONDS / frameSeconds).toInt()
    private val minFrames = ceil(MIN_NOTE_SECONDS / frameSeconds).toInt()

    private class Building(val start: Int) {
        val values = ArrayList<Float>()
        var lastVoiced = start
    }

    private class Finished(val note: PitchNote, val values: List<Float>)

    /** [midi] per frame, NaN where unvoiced. */
    fun segment(midi: FloatArray): List<PitchNote> {
        val run = Run()
        for (t in midi.indices) run.feed(t, midi[t])
        run.finish()
        return merge(run.finished)
    }

    /** One pass over the frames: opens, extends and closes notes. */
    private inner class Run {
        val finished = ArrayList<Finished>()
        private var building: Building? = null

        fun feed(t: Int, m: Float) {
            val open = building
            when {
                m.isNaN() -> if (open != null && t - open.lastVoiced > graceFrames) close(open, open.lastVoiced + 1)
                open == null -> start(t, m)
                abs(m - median(open.values)) > SPLIT_SEMITONES -> {
                    close(open, t)
                    start(t, m)
                }
                else -> {
                    open.values += m
                    open.lastVoiced = t
                }
            }
        }

        fun finish() {
            building?.let { close(it, it.lastVoiced + 1) }
        }

        private fun start(t: Int, m: Float) {
            building = Building(t).also { it.values += m }
        }

        private fun close(b: Building, end: Int) {
            if (end - b.start >= minFrames) finished += Finished(PitchNote(b.start, end, median(b.values)), b.values)
            building = null
        }
    }

    /** Joins neighbours with the same rounded MIDI pitch when the gap between them is within the grace time. */
    private fun merge(notes: List<Finished>): List<PitchNote> {
        val result = ArrayList<PitchNote>()
        val pooled = ArrayList<MutableList<Float>>()
        for (item in notes) {
            val previous = result.lastOrNull()
            val joins = previous != null &&
                previous.pitch.roundToInt() == item.note.pitch.roundToInt() &&
                item.note.startFrame - previous.endFrame <= graceFrames
            if (previous != null && joins) {
                pooled.last().addAll(item.values)
                result[result.size - 1] = previous.copy(endFrame = item.note.endFrame, pitch = median(pooled.last()))
            } else {
                result += item.note
                pooled += item.values.toMutableList()
            }
        }
        return result
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    companion object {
        const val SPLIT_SEMITONES = 0.8f
        const val MIN_NOTE_SECONDS = 0.050
        const val GRACE_SECONDS = 0.020
    }
}
