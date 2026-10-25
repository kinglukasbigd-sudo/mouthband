package com.mouthband.convert

import com.mouthband.audio.AlignedTake
import com.mouthband.model.KeyMode
import com.mouthband.model.MusicalKey
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

class PitchTest {
    private val frameSeconds = PitchTracker.HOP.toDouble() / PitchTracker.SAMPLE_RATE

    private fun sine(hz: Double, seconds: Double, rate: Int = PitchTracker.SAMPLE_RATE, amp: Double = 0.5) =
        FloatArray((seconds * rate).toInt()) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun midiToHz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    @Test
    fun yinTracksSinesFrom80To1000HzWithinOnePercent() {
        val tracker = YinPitchTracker()
        for (hz in listOf(80.0, 100.0, 130.0, 196.0, 261.63, 330.0, 440.0, 587.33, 800.0, 1000.0)) {
            val track = tracker.track(sine(hz, 1.0))
            // Skip the edges, where the window reaches past the signal.
            val inner = (8 until track.size - 8).filter { track.confidence[it] >= tracker.voicedThreshold }
            assertTrue("$hz Hz: most frames voiced", inner.size > (track.size - 16) * 0.9)
            inner.forEach { t ->
                val error = abs(track.hz[t] - hz) / hz
                assertTrue("$hz Hz frame $t read ${track.hz[t]}", error < 0.01)
            }
        }
    }

    @Test
    fun yinReportsSilenceAsUnvoiced() {
        val track = YinPitchTracker().track(FloatArray(PitchTracker.SAMPLE_RATE))
        assertTrue(track.confidence.all { it == 0f })
    }

    /** Frames of [midi] for [seconds] each. */
    private fun frames(vararg parts: Pair<Float, Double>): FloatArray =
        parts.flatMap { (midi, seconds) -> List((seconds / frameSeconds).toInt()) { midi } }.toFloatArray()

    @Test
    fun segmentsSteps() {
        val notes = NoteSegmenter(frameSeconds).segment(frames(60f to 0.4, 62f to 0.4, 64f to 0.4))
        assertEquals(listOf(60, 62, 64), notes.map { it.pitch.toInt() })
        assertEquals(0, notes[0].startFrame)
        assertEquals(notes[0].endFrame, notes[1].startFrame)
    }

    @Test
    fun segmentsAGlideIntoNotesNoWiderThanTheSplitThreshold() {
        // A one-second glide from MIDI 60 to 64, then a held 64.
        val glideFrames = (1.0 / frameSeconds).toInt()
        val glide = FloatArray(glideFrames) { 60f + 4f * it / glideFrames }
        val midi = glide + frames(64f to 0.5)
        val notes = NoteSegmenter(frameSeconds).segment(midi)
        assertTrue("glide splits into several notes", notes.size >= 3)
        notes.forEach { note ->
            val values = midi.copyOfRange(note.startFrame, note.endFrame)
            assertTrue(values.all { abs(it - note.pitch) <= 0.8f + 4f / glideFrames * 2 })
        }
        assertEquals(64f, notes.last().pitch, 0.01f)
        assertTrue("pitches rise", notes.zipWithNext().all { (a, b) -> b.pitch > a.pitch })
    }

    @Test
    fun shortGapsDoNotSplitAndShortNotesAreDropped() {
        val segmenter = NoteSegmenter(frameSeconds)
        // 16 ms unvoiced gap (< 20 ms) inside a note: one note.
        val bridged = segmenter.segment(frames(60f to 0.3, Float.NaN to 0.016, 60f to 0.3))
        assertEquals(1, bridged.size)
        // A 100 ms gap (> 20 ms) splits.
        val gapped = segmenter.segment(frames(60f to 0.3, Float.NaN to 0.1, 62f to 0.3))
        assertEquals(2, gapped.size)
        // A 32 ms blip (< 50 ms) disappears.
        val blip = segmenter.segment(frames(Float.NaN to 0.2, 67f to 0.032, Float.NaN to 0.2))
        assertTrue(blip.isEmpty())
    }

    @Test
    fun adjacentNotesWithTheSameMidiPitchMerge() {
        // 59.6 → 60.45 moves 0.85 semitones (> 0.8, a split), but both round to MIDI 60: one note.
        val notes = NoteSegmenter(frameSeconds).segment(frames(59.6f to 0.3, 60.45f to 0.3))
        assertEquals(1, notes.size)
        assertEquals(60, Math.round(notes[0].pitch))
        // 60 → 61.3 splits into two different MIDI pitches and stays two notes.
        assertEquals(2, NoteSegmenter(frameSeconds).segment(frames(60f to 0.3, 61.3f to 0.3)).size)
    }

    @Test
    fun snapsToTheScaleInEveryKey() {
        for (key in MusicalKey.all) {
            for (midi in 40..90) {
                for (offset in listOf(-0.3f, 0f, 0.3f)) {
                    val pitch = midi + offset
                    val snapped = MusicTheory.snapToScale(pitch, key, null)
                    assertTrue("$key $pitch → $snapped in scale", key.contains(snapped))
                    val nearest = (midi - 2..midi + 2).filter { key.contains(it) }.minOf { abs(it - pitch) }
                    assertTrue("$key $pitch → $snapped is nearest", abs(snapped - pitch) <= nearest + 0.11f)
                }
            }
        }
    }

    @Test
    fun tiesRoundTowardTheMelodysDirectionInEveryKey() {
        for (key in MusicalKey.all) {
            for (midi in 48..72) {
                if (key.contains(midi)) continue
                // A non-scale semitone sits between two scale tones one semitone away.
                assertTrue(key.contains(midi - 1) && key.contains(midi + 1))
                assertEquals("$key $midi rising", midi + 1, MusicTheory.snapToScale(midi.toFloat(), key, midi - 5))
                assertEquals("$key $midi falling", midi - 1, MusicTheory.snapToScale(midi.toFloat(), key, midi + 5))
            }
        }
    }

    @Test
    fun foldsBassAndMelodyIntoTheirRanges() {
        for (midi in 0..127) {
            assertTrue(MusicTheory.foldIntoRange(midi, MusicTheory.BASS_RANGE) in 28..43)
            assertTrue(MusicTheory.foldIntoRange(midi, MusicTheory.MELODY_RANGE) in 55..84)
            assertEquals(0, Math.floorMod(MusicTheory.foldIntoRange(midi, MusicTheory.BASS_RANGE) - midi, 12))
        }
        assertEquals(40, MusicTheory.foldIntoRange(40, MusicTheory.BASS_RANGE))
    }

    @Test
    fun buildsDiatonicTriadsAroundC4() {
        val cMajor = MusicalKey(0, KeyMode.MAJOR)
        assertEquals(setOf(0, 4, 7), MusicTheory.triad(60, cMajor).map { it % 12 }.toSet())
        assertEquals(setOf(2, 5, 9), MusicTheory.triad(62, cMajor).map { it % 12 }.toSet()) // D minor
        assertEquals(setOf(11, 2, 5), MusicTheory.triad(59, cMajor).map { it % 12 }.toSet()) // B dim
        val aMinor = MusicalKey(9, KeyMode.MINOR)
        assertEquals(setOf(9, 0, 4), MusicTheory.triad(57, aMinor).map { it % 12 }.toSet())
        for (key in MusicalKey.all) {
            for (degree in 0 until 7) {
                val root = MusicTheory.degreeToMidi(degree, key, 48)
                val chord = MusicTheory.triad(root, key)
                assertEquals(3, chord.size)
                assertTrue("$key degree $degree sorted", chord.toList() == chord.sorted())
                assertTrue("$key degree $degree around C4", chord.all { it in 54..65 })
                assertTrue("$key degree $degree diatonic", chord.all { key.contains(it) })
                val pcs = key.pitchClasses
                val expected = setOf(pcs[degree], pcs[(degree + 2) % 7], pcs[(degree + 4) % 7])
                assertEquals(expected, chord.map { Math.floorMod(it, 12) }.toSet())
            }
        }
    }

    /** A hummed take: [notes] as (midi, start s, length s) over a loop, with harmonics. */
    private fun hummedTake(rate: Int, loopSeconds: Double, notes: List<Triple<Int, Double, Double>>): AlignedTake {
        val preroll = (0.1 * rate).toInt()
        val loop = (loopSeconds * rate).toInt()
        val samples = FloatArray(preroll + loop + (0.25 * rate).toInt())
        notes.forEach { (midi, start, length) ->
            val hz = midiToHz(midi)
            val from = preroll + (start * rate).toInt()
            val n = (length * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, i / (0.01 * rate), (n - i) / (0.01 * rate))
                val v = sin(2 * PI * hz * t) + 0.4 * sin(4 * PI * hz * t) + 0.2 * sin(6 * PI * hz * t)
                samples[from + i] += (0.3 * env * v).toFloat()
            }
        }
        return AlignedTake(samples, preroll, loop, rate)
    }

    @Test
    fun convertsAHummedBassLineWithTheYinFallback() {
        val rate = 48_000
        val key = MusicalKey(0, KeyMode.MAJOR)
        // 120 BPM: one beat = 0.5 s; two bars = 4 s. Hummed an octave above the bass range.
        val take = hummedTake(rate, 4.0, listOf(Triple(48, 0.0, 0.45), Triple(55, 1.0, 0.45), Triple(57, 2.0, 0.45)))
        val result = PitchConverter(rate, primary = { null }).convert(take, TrackType.BASS, key, bpm = 120)
        assertNull(result.problem)
        assertTrue(result.notes.all { it.midi in 28..43 })
        // The loop is cyclic: a note detected a few ms before the downbeat sits at the loop end.
        val beat = rate / 2
        val loop = take.loopFrames
        fun nearBeat(frame: Int, beats: Int): Boolean {
            val d = Math.floorMod(frame - beats * beat, loop)
            return minOf(d, loop - d) < rate * 0.08
        }
        val byBeat = listOf(0 to 36, 2 to 43, 4 to 33)
        assertEquals(3, result.notes.size)
        byBeat.forEach { (beats, midi) ->
            val found = result.notes.any { it.midi == midi && nearBeat(it.startFrame, beats) }
            assertTrue("MIDI $midi on beat $beats", found)
        }
    }

    @Test
    fun hummedChordRootsBecomeTriadsHeldToTheBarEnd() {
        val rate = 48_000
        val key = MusicalKey(0, KeyMode.MAJOR)
        // Bar 1: C hummed briefly. Bar 2: three short roots (E, F, G) — G is longest and kept.
        val take = hummedTake(
            rate,
            4.0,
            listOf(Triple(60, 0.0, 0.5), Triple(64, 2.0, 0.3), Triple(65, 2.5, 0.3), Triple(67, 3.0, 0.7)),
        )
        val result = PitchConverter(rate, primary = { null }).convert(take, TrackType.CHORDS, key, bpm = 120)
        val chords = result.notes.groupBy { it.startFrame }.values.sortedBy { chord -> chord.first().midi % 12 }
        assertEquals(2, chords.size)
        val c = chords.first { chord -> chord.any { it.midi % 12 == 0 } }
        val g = chords.first { chord -> chord.any { it.midi % 12 == 7 && chord.any { n -> n.midi % 12 == 11 } } }
        assertEquals(setOf(0, 4, 7), c.map { it.midi % 12 }.toSet())
        assertEquals(setOf(7, 11, 2), g.map { it.midi % 12 }.toSet())
        // Every chord is held to the end of its bar (cyclically, for a root sung just early).
        val bar = 2 * rate
        val loop = take.loopFrames
        listOf(c to bar, g to loop).forEach { (chord, barEnd) ->
            chord.forEach { note ->
                val end = if (note.startFrame > loop - rate / 4) note.endFrame - loop else note.endFrame
                assertTrue("chord held to its bar end: $end vs $barEnd", abs(end - barEnd) <= 1)
            }
        }
    }

    @Test
    fun silentTakeReportsAProblem() {
        val take = AlignedTake(FloatArray(48_000 * 3), 4_800, 48_000 * 2, 48_000)
        val converter = PitchConverter(48_000, primary = { null })
        val result = converter.convert(take, TrackType.MELODY, MusicalKey(0, KeyMode.MAJOR), 120)
        assertEquals(TakeProblem.SILENCE, result.problem)
    }

    @Test
    fun quantizedNotesAreAtLeastOneSixteenth() {
        val step = 6000.0
        val notes = Quantizer.notes(
            listOf(NoteEvent(60, 12_100, 12_900, 1f)),
            step,
            1f,
            96_000,
        )
        assertEquals(12_000, notes[0].startFrame)
        assertEquals(18_000, notes[0].endFrame)
    }
}
