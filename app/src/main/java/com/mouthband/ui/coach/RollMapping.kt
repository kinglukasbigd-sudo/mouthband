package com.mouthband.ui.coach

import com.mouthband.audio.LoopGrid
import com.mouthband.audio.LoopMath
import com.mouthband.convert.MusicTheory
import com.mouthband.convert.Quantizer
import com.mouthband.model.MusicalKey
import com.mouthband.model.TrackType
import com.mouthband.ui.components.RollModel
import com.mouthband.ui.components.RollNote
import kotlin.math.abs
import kotlin.math.roundToInt

/** Builds the mini piano roll pages: rows are scale tones around the take's notes. */
object RollMapping {
    private const val PADDING_DEGREES = 2
    private const val MIN_ROWS = 8
    private const val MAX_ROWS = 15
    private const val OCTAVE_OFFSET = 1

    fun bars(candidate: Candidate, type: TrackType, key: MusicalKey, grid: LoopGrid): List<RollModel> {
        val notes = candidate.events?.notes.orEmpty()
        val rows = rowPitches(notes.map { it.midi }, type, key)
        val step = grid.stepFrames
        val placed = notes.mapIndexed { index, note ->
            val q = Quantizer.notes(listOf(note), step, candidate.quantize, grid.loopFrames).first()
            val start = Quantizer.cell(q.startFrame, step, grid.totalSteps)
            val length = ((q.endFrame - q.startFrame) / step).roundToInt().coerceAtLeast(1)
            Triple(index, start, length) to nearestRow(rows, note.midi)
        }
        return List(grid.bars) { bar ->
            val inBar = placed.filter { (pos, _) -> pos.second / LoopMath.STEPS_PER_BAR == bar }
            val rollNotes = inBar.map { (pos, row) ->
                val startInBar = pos.second % LoopMath.STEPS_PER_BAR
                RollNote(
                    row = row,
                    startStep = startInBar,
                    lengthSteps = minOf(pos.third, LoopMath.STEPS_PER_BAR - startInBar),
                    id = pos.first,
                )
            }
            val descriptions = inBar.map { (pos, _) -> noteName(notes[pos.first].midi) }
            RollModel(rows, LoopMath.STEPS_PER_BAR, rollNotes, descriptions)
        }
    }

    /** Scale tones from highest to lowest, covering the notes plus a little room to add more. */
    fun rowPitches(midis: List<Int>, type: TrackType, key: MusicalKey): IntArray {
        val default = when (type) {
            TrackType.BASS -> MusicTheory.BASS_RANGE.first + MusicTheory.SEMITONES / 2
            TrackType.MELODY -> MusicTheory.MELODY_RANGE.first + MusicTheory.SEMITONES / 2
            else -> MusicTheory.CHORD_CENTER
        }
        var low = MusicTheory.scaleToneAtOrBelow(midis.minOrNull() ?: default, key)
        var high = MusicTheory.scaleToneAtOrAbove(midis.maxOrNull() ?: default, key)
        repeat(PADDING_DEGREES) {
            low = MusicTheory.scaleToneAtOrBelow(low - 1, key)
            high = MusicTheory.nthScaleToneAbove(high, 1, key)
        }
        var rows = (low..high).filter { key.contains(it) }
        while (rows.size < MIN_ROWS) rows = (rows + MusicTheory.nthScaleToneAbove(rows.last(), 1, key))
        if (rows.size > MAX_ROWS) rows = rows.takeLast(MAX_ROWS)
        return rows.sortedDescending().toIntArray()
    }

    private fun nearestRow(rows: IntArray, midi: Int): Int = rows.indices.minBy { abs(rows[it] - midi) }

    fun noteName(midi: Int): String =
        MusicalKey.rootName(midi) + (Math.floorDiv(midi, MusicTheory.SEMITONES) - OCTAVE_OFFSET)
}

/** Peak envelope of a take for the vocal waveform. */
object WaveformPeaks {
    private const val BUCKETS = 96

    fun of(samples: FloatArray, buckets: Int = BUCKETS): FloatArray {
        if (samples.isEmpty()) return FloatArray(buckets)
        val size = (samples.size + buckets - 1) / buckets
        val peaks = FloatArray(buckets) { b ->
            var p = 0f
            for (i in b * size until minOf(samples.size, (b + 1) * size)) p = maxOf(p, abs(samples[i]))
            p
        }
        val max = peaks.max().takeIf { it > 0f } ?: 1f
        return FloatArray(buckets) { peaks[it] / max }
    }
}
