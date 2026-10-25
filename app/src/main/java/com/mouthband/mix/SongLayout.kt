package com.mouthband.mix

import com.mouthband.audio.LoopMath
import com.mouthband.model.Arrangement
import com.mouthband.model.TrackType

/**
 * The arrangement in frames: where each section starts, which lanes play in it, and the song
 * length. Section boundaries are rounded from exact bar positions, so the total length equals
 * the sum of section bars within one frame; each section restarts the loops (spec §1).
 */
class SongLayout(
    val sectionStarts: LongArray,
    val activeMasks: IntArray,
    val totalFrames: Long,
    val loopFrames: Int,
) {
    val sectionCount: Int get() = sectionStarts.size

    /** Index of the section containing [frame] (the last one for frames past the end). */
    fun sectionAt(frame: Long): Int {
        var lo = 0
        var hi = sectionStarts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (sectionStarts[mid] <= frame) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun sectionEnd(index: Int): Long = if (index + 1 < sectionStarts.size) sectionStarts[index + 1] else totalFrames

    companion object {
        fun of(arrangement: Arrangement, bpm: Int, loopBars: Int, sampleRate: Int): SongLayout {
            val starts = LongArray(arrangement.sections.size)
            var bars = 0
            arrangement.sections.forEachIndexed { i, section ->
                starts[i] = LoopMath.barStartFrame(bars, bpm, sampleRate)
                bars += section.bars
            }
            val masks = IntArray(arrangement.sections.size) { i ->
                arrangement.sections[i].active.fold(0) { mask, type -> mask or (1 shl type.ordinal) }
            }
            return SongLayout(
                starts,
                masks,
                LoopMath.barStartFrame(bars, bpm, sampleRate),
                LoopMath.loopFrames(loopBars, bpm, sampleRate),
            )
        }

        /** A single section that plays every lane forever (loop preview). */
        fun loopOnly(loopFrames: Int): SongLayout =
            SongLayout(longArrayOf(0L), intArrayOf((1 shl TrackType.entries.size) - 1), Long.MAX_VALUE, loopFrames)
    }
}
