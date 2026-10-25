package com.mouthband.ui.coach

import com.mouthband.audio.LoopMath
import com.mouthband.convert.ReviewEditor
import com.mouthband.model.DrumClass
import com.mouthband.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Editing a take's pattern (spec §1 Note editing, extended): toggle drum hits, add notes or
 * chords, select a note (or chord) and delete it, and undo any of these. Every change keeps the
 * previous events for Undo and asks [onChanged] to rebuild the loop.
 */
class ReviewEdits(
    private val state: MutableStateFlow<StepState>,
    private val sampleRate: Int,
    private val type: TrackType,
    private val onChanged: () -> Unit,
) {
    fun toggleHit(row: Int, stepInBar: Int) = edit(rerender = true) { candidate ->
        val events = candidate.events ?: return@edit candidate
        val (step, total) = steps() ?: return@edit candidate
        val cell = candidate.selectedBar * LoopMath.STEPS_PER_BAR + stepInBar
        candidate.withEdit(ReviewEditor.toggleHit(events, DrumClass.entries[row], cell, step, total))
    }

    /** Tapping a note selects it (its whole chord on the chord track); tapping it again clears it. */
    fun tapNote(index: Int) = edit(rerender = false) { candidate ->
        candidate.copy(selectedNote = if (candidate.selectedNote == index) -1 else index)
    }

    /** Deletes the selected note, or its chord on the chord track. Undo brings it back. */
    fun deleteSelected() = deleteNote(state.value.candidate?.selectedNote ?: -1)

    fun deleteNote(index: Int) = edit(rerender = true) { candidate ->
        val events = candidate.events ?: return@edit candidate
        if (index !in events.notes.indices) return@edit candidate
        candidate.withEdit(ReviewEditor.deleteNote(events, index, wholeChord = type == TrackType.CHORDS))
    }

    /** Steps back through the edits of this take (adds, deletes, toggled hits). */
    fun undo() = edit(rerender = true) { candidate ->
        val previous = candidate.undo.lastOrNull() ?: return@edit candidate
        candidate.copy(events = previous, undo = candidate.undo.dropLast(1), selectedNote = -1)
    }

    fun addNote(midi: Int, stepInBar: Int) = edit(rerender = true) { candidate ->
        val events = candidate.events ?: return@edit candidate
        val song = state.value.song ?: return@edit candidate
        val (step, _) = steps() ?: return@edit candidate
        val cell = candidate.selectedBar * LoopMath.STEPS_PER_BAR + stepInBar
        val edited = if (type == TrackType.CHORDS) {
            ReviewEditor.addChord(events, midi, song.key, cell, step)
        } else {
            ReviewEditor.addNote(events, midi, cell, step)
        }
        candidate.withEdit(edited)
    }

    private fun steps(): Pair<Double, Int>? {
        val song = state.value.song ?: return null
        val step = LoopMath.framesPerStep(song.bpm, sampleRate)
        return step to (song.loopBars * LoopMath.STEPS_PER_BAR.toDouble()).roundToInt()
    }

    private fun edit(rerender: Boolean, change: (Candidate) -> Candidate) {
        state.update { s -> s.candidate?.let { s.copy(candidate = change(it)) } ?: s }
        if (rerender) onChanged()
    }
}
