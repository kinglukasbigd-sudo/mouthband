package com.mouthband.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun defaultArrangementMatchesSpec() {
        val sections = Arrangement.Default.sections
        assertEquals(listOf(4, 8, 8, 8, 8, 4), sections.map { it.bars })
        assertEquals(
            listOf(
                SectionType.INTRO,
                SectionType.VERSE,
                SectionType.CHORUS,
                SectionType.VERSE,
                SectionType.CHORUS,
                SectionType.OUTRO,
            ),
            sections.map { it.type },
        )
        assertEquals(setOf(TrackType.CHORDS, TrackType.MELODY), sections.first().active)
        assertEquals(
            setOf(TrackType.BEAT, TrackType.BASS, TrackType.CHORDS, TrackType.VOCALS_VERSE),
            sections[1].active,
        )
        assertTrue(TrackType.VOCALS_CHORUS in sections[2].active)
        assertFalse(TrackType.VOCALS_VERSE in sections[2].active)
        assertEquals(40, Arrangement.Default.totalBars)
    }

    @Test
    fun arrangementRoundTripsThroughJson() {
        val json = Json.encodeToString(Arrangement.serializer(), Arrangement.Default.copy(autoMix = true))
        val back = Json.decodeFromString(Arrangement.serializer(), json)
        assertEquals(Arrangement.Default.copy(autoMix = true), back)
    }

    @Test
    fun sectionToggleChangesOnlyThatSection() {
        val updated = Arrangement.Default.withSectionTrack(0, TrackType.BEAT, on = true)
        assertTrue(TrackType.BEAT in updated.sections[0].active)
        assertEquals(Arrangement.Default.sections.drop(1), updated.sections.drop(1))
    }

    @Test
    fun moodsMatchSpecTable() {
        assertEquals(85, Mood.CHILL.bpm)
        assertEquals("A MIN", Mood.CHILL.key.label)
        assertEquals(DrumKit.LOFI, Mood.CHILL.kit)
        assertEquals("C MAJ", Mood.POP.key.label)
        assertEquals(140, Mood.HYPE.bpm)
        assertEquals("F MIN", Mood.HYPE.key.label)
        assertEquals(DrumKit.TRAP, Mood.HYPE.kit)
        assertEquals("D MIN", Mood.SAD.key.label)
        assertEquals("E MIN", Mood.ROCK.key.label)
        assertEquals(DrumKit.ROCK, Mood.ROCK.kit)
    }

    @Test
    fun keyContainsOnlyScaleTones() {
        val cMajor = MusicalKey(0, KeyMode.MAJOR)
        assertTrue(cMajor.contains(60))
        assertFalse(cMajor.contains(61))
        val aMinor = MusicalKey(9, KeyMode.MINOR)
        assertEquals(cMajor.pitchClasses.toSet(), aMinor.pitchClasses.toSet())
        assertEquals(24, MusicalKey.all.size)
    }

    @Test
    fun trackEventsRescaleProportionally() {
        val events = TrackEvents(
            sampleRate = 48_000,
            loopFrames = 1000,
            hits = listOf(DrumHit(DrumClass.KICK, 500, 1f)),
            notes = listOf(NoteEvent(60, 250, 1000, 0.8f)),
        )
        val scaled = events.rescaledTo(targetLoopFrames = 2000, targetRate = 96_000)
        assertEquals(1000, scaled.hits.single().frameInLoop)
        assertEquals(500, scaled.notes.single().startFrame)
        assertEquals(2000, scaled.notes.single().endFrame)
        assertEquals(96_000, scaled.sampleRate)
    }

    @Test
    fun coachOrderIsFixed() {
        assertEquals((1..8).toList(), CoachStep.entries.map { it.number })
        assertEquals(CoachTarget.Record(TrackType.BASS), TrackType.BEAT.nextAfterKeep)
        assertEquals(CoachTarget.Record(TrackType.VOCALS_CHORUS), TrackType.VOCALS_VERSE.nextAfterKeep)
        assertEquals(CoachTarget.Arrange, TrackType.VOCALS_CHORUS.nextAfterKeep)
    }
}
