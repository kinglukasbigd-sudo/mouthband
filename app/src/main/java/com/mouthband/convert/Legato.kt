package com.mouthband.convert

import com.mouthband.model.NoteEvent

/**
 * Makes a hummed bass or melody line flow the way it was sung: consecutive notes of the same pitch
 * with only a short break (a consonant, a breath, a pitch wobble that crossed the split
 * threshold) become one held note, and every other short gap closes so a note lasts until the
 * next one starts. Breaks of at least [maxGap] frames are real rests and stay. Lines are
 * monophonic: an overlap is trimmed to the next start. The loop is cyclic, so the last note may
 * flow into the first one of the next pass.
 */
object Legato {
    fun apply(notes: List<NoteEvent>, loopFrames: Int, maxGap: Int): List<NoteEvent> {
        if (notes.size < 2) return notes
        val merged = mergeRepeats(notes.sortedBy { it.startFrame }, maxGap)
        return merged.mapIndexed { i, note ->
            val next = merged.getOrNull(i + 1)
            val nextStart = next?.startFrame ?: (merged.first().startFrame + loopFrames)
            val gap = nextStart - note.endFrame
            val end = if (gap in 0 until maxGap || gap < 0) nextStart else note.endFrame
            note.copy(endFrame = maxOf(end, note.startFrame + 1))
        }
    }

    /** Joins neighbours with the same MIDI pitch separated by less than [maxGap]. */
    private fun mergeRepeats(sorted: List<NoteEvent>, maxGap: Int): List<NoteEvent> {
        val out = ArrayList<NoteEvent>(sorted.size)
        for (note in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.midi == note.midi && note.startFrame - last.endFrame < maxGap) {
                out[out.size - 1] = last.copy(endFrame = maxOf(last.endFrame, note.endFrame))
            } else {
                out += note
            }
        }
        return out
    }
}
