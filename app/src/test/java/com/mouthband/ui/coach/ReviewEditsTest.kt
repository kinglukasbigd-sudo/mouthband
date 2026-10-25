package com.mouthband.ui.coach

import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewEditsTest {
    private val rate = 48_000
    private val melody = listOf(
        NoteEvent(60, 0, 6_000, 1f),
        NoteEvent(62, 6_000, 12_000, 1f),
        NoteEvent(64, 12_000, 18_000, 1f),
    )

    // Two triads: C major on the first beat, F major on the third.
    private val chords = listOf(
        NoteEvent(48, 0, 24_000, 1f),
        NoteEvent(52, 0, 24_000, 1f),
        NoteEvent(55, 0, 24_000, 1f),
        NoteEvent(53, 24_000, 48_000, 1f),
        NoteEvent(57, 24_000, 48_000, 1f),
        NoteEvent(60, 24_000, 48_000, 1f),
    )

    private var rebuilds = 0

    private fun editsFor(type: TrackType, notes: List<NoteEvent>): Pair<MutableStateFlow<StepState>, ReviewEdits> {
        val candidate = Candidate(
            rawLoop = FloatArray(0),
            events = TrackEvents(rate, 96_000, notes = notes),
            problem = null,
            instrument = Instrument.entries.first(),
            kit = DrumKit.entries.first(),
            quantize = 0.8f,
        )
        val state = MutableStateFlow(StepState(candidate = candidate))
        return state to ReviewEdits(state, rate, type) { rebuilds++ }
    }

    private val MutableStateFlow<StepState>.notes get() = value.candidate?.events?.notes.orEmpty()
    private val MutableStateFlow<StepState>.selected get() = value.candidate?.selectedNote ?: -1

    @Test
    fun tappingANoteSelectsItAndTappingAgainClearsIt() {
        val (state, edits) = editsFor(TrackType.MELODY, melody)
        edits.tapNote(1)
        assertEquals(1, state.selected)
        edits.tapNote(1)
        assertEquals(-1, state.selected)
        assertEquals(0, rebuilds)
    }

    @Test
    fun deletingTheSelectedNoteKeepsTheOthers() {
        val (state, edits) = editsFor(TrackType.MELODY, melody)
        edits.tapNote(1)
        edits.deleteSelected()
        assertEquals(listOf(60, 64), state.notes.map { it.midi })
        assertEquals(-1, state.selected)
        assertEquals(1, rebuilds)
    }

    @Test
    fun deletingWithNothingSelectedChangesNothing() {
        val (state, edits) = editsFor(TrackType.MELODY, melody)
        edits.deleteSelected()
        assertEquals(melody, state.notes)
        assertEquals(emptyList<TrackEvents>(), state.value.candidate?.undo)
    }

    @Test
    fun onTheChordTrackDeleteRemovesTheWholeChord() {
        val (state, edits) = editsFor(TrackType.CHORDS, chords)
        edits.tapNote(4)
        edits.deleteSelected()
        assertEquals(listOf(48, 52, 55), state.notes.map { it.midi })
    }

    @Test
    fun undoBringsDeletedNotesBackOneStepAtATime() {
        val (state, edits) = editsFor(TrackType.MELODY, melody)
        edits.deleteNote(0)
        edits.deleteNote(0)
        assertEquals(listOf(64), state.notes.map { it.midi })
        edits.undo()
        assertEquals(listOf(62, 64), state.notes.map { it.midi })
        edits.undo()
        assertEquals(melody, state.notes)
        edits.undo()
        assertEquals(melody, state.notes)
    }

    @Test
    fun undoBringsAWholeChordBack() {
        val (state, edits) = editsFor(TrackType.CHORDS, chords)
        edits.deleteNote(0)
        assertEquals(3, state.notes.size)
        edits.undo()
        assertEquals(chords, state.notes)
    }

    @Test
    fun undoHistoryIsCappedAtThirtySteps() {
        val many = List(40) { NoteEvent(60, it * 2_000, it * 2_000 + 1_000, 1f) }
        val (state, edits) = editsFor(TrackType.MELODY, many)
        repeat(35) { edits.deleteNote(0) }
        assertEquals(5, state.notes.size)
        repeat(40) { edits.undo() }
        // Only the last 30 deletions can be undone.
        assertEquals(35, state.notes.size)
    }
}
