package com.mouthband.convert

import com.mouthband.model.NoteEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class LegatoTest {
    private val loop = 96_000
    private val sixteenth = 6_000

    private fun note(midi: Int, start: Int, end: Int) = NoteEvent(midi, start, end, 1f)

    @Test
    fun shortBreaksCloseUpToTheNextNote() {
        val out = Legato.apply(listOf(note(60, 0, 20_000), note(62, 23_000, 40_000)), loop, sixteenth)
        assertEquals(23_000, out[0].endFrame)
    }

    @Test
    fun realRestsStay() {
        val out = Legato.apply(listOf(note(60, 0, 20_000), note(62, 40_000, 60_000)), loop, sixteenth)
        assertEquals(20_000, out[0].endFrame)
        assertEquals(60_000, out[1].endFrame)
    }

    @Test
    fun wobbleSplitRepeatsBecomeOneHeldNote() {
        val out = Legato.apply(
            listOf(note(60, 0, 10_000), note(60, 11_000, 20_000), note(64, 50_000, 70_000)),
            loop,
            sixteenth,
        )
        assertEquals(2, out.size)
        assertEquals(0, out[0].startFrame)
        assertEquals(20_000, out[0].endFrame)
    }

    @Test
    fun overlapsAreTrimmedToTheNextStart() {
        val out = Legato.apply(listOf(note(60, 0, 30_000), note(62, 25_000, 40_000)), loop, sixteenth)
        assertEquals(25_000, out[0].endFrame)
    }

    @Test
    fun theLastNoteFlowsIntoTheNextLoopPass() {
        val out = Legato.apply(listOf(note(60, 1_000, 40_000), note(62, 50_000, 94_000)), loop, sixteenth)
        // Next pass starts at loop + 1 000 = 97 000; the 3 000-frame break closes.
        assertEquals(97_000, out[1].endFrame)
    }

    @Test
    fun singleNotesAreUntouched() {
        val single = listOf(note(60, 0, 1_000))
        assertEquals(single, Legato.apply(single, loop, sixteenth))
    }
}
