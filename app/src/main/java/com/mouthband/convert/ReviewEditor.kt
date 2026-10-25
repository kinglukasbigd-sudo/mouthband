package com.mouthband.convert

import com.mouthband.audio.LoopMath
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import com.mouthband.model.MusicalKey
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackEvents
import kotlin.math.roundToInt

/**
 * The Review screen's simple editing (spec §1 Note editing): tap a grid step to toggle a hit,
 * tap a note to delete it, tap an empty cell to add a one-16th note. Works on performed events;
 * cells are compared at the current quantize strength.
 */
object ReviewEditor {
    private const val ADDED_VELOCITY = 0.8f

    /** Toggles [cls] at absolute 16th [cell]. */
    fun toggleHit(events: TrackEvents, cls: DrumClass, cell: Int, step: Double, totalSteps: Int): TrackEvents {
        val existing = events.hits.filter { it.cls == cls && Quantizer.cell(it.frameInLoop, step, totalSteps) == cell }
        val hits = if (existing.isNotEmpty()) {
            events.hits - existing.toSet()
        } else {
            events.hits + DrumHit(cls, (cell * step).roundToInt(), ADDED_VELOCITY)
        }
        return events.copy(hits = hits.sortedBy { it.frameInLoop })
    }

    /** Deletes note [index]; on a chord track the whole chord starting with it goes. */
    fun deleteNote(events: TrackEvents, index: Int, wholeChord: Boolean): TrackEvents {
        val target = events.notes.getOrNull(index) ?: return events
        val notes = if (wholeChord) {
            events.notes.filter { it.startFrame != target.startFrame }
        } else {
            events.notes - target
        }
        return events.copy(notes = notes)
    }

    /** Adds a one-16th note at [cell]. */
    fun addNote(events: TrackEvents, midi: Int, cell: Int, step: Double): TrackEvents {
        val start = (cell * step).roundToInt()
        val note = NoteEvent(midi, start, start + step.roundToInt(), ADDED_VELOCITY)
        return events.copy(notes = (events.notes + note).sortedBy { it.startFrame })
    }

    /** Adds a triad on [root] from [cell] to the next chord or the end of the bar. */
    fun addChord(events: TrackEvents, root: Int, key: MusicalKey, cell: Int, step: Double): TrackEvents {
        val start = (cell * step).roundToInt()
        val barEnd = ((cell / LoopMath.STEPS_PER_BAR + 1) * LoopMath.STEPS_PER_BAR * step).roundToInt()
        val next = events.notes.map { it.startFrame }.filter { it > start }.minOrNull() ?: Int.MAX_VALUE
        val end = minOf(barEnd, next)
        val chord = MusicTheory.triad(MusicTheory.scaleToneAtOrBelow(root, key), key)
            .map { NoteEvent(it, start, end, ADDED_VELOCITY) }
        return events.copy(notes = (events.notes + chord).sortedBy { it.startFrame })
    }
}
