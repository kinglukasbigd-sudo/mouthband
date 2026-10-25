package com.mouthband.synth

import com.mouthband.audio.LoopMath
import com.mouthband.convert.MusicTheory
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import com.mouthband.model.Instrument
import com.mouthband.model.MusicalKey
import com.mouthband.model.NoteEvent
import com.mouthband.model.Song
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackType
import kotlin.math.roundToInt

/**
 * Built-in note and drum patterns for "Hear an example first" (spec §1): synthesized by the
 * app in the song's tempo and key, so no audio assets are needed.
 */
object DemoPatterns {
    private const val DEMO_VELOCITY = 0.85f
    private const val BASS_OCTAVE = 36
    private const val MELODY_OCTAVE = 60
    private const val BASS_NOTE_STEPS = 3
    private const val PICKUP_STEP = 10

    /** boom · tss · pff · tss on 8th notes. */
    private val beatBar = listOf(
        0 to DrumClass.KICK,
        2 to DrumClass.HIHAT,
        4 to DrumClass.SNARE,
        6 to DrumClass.HIHAT,
        8 to DrumClass.KICK,
        10 to DrumClass.HIHAT,
        12 to DrumClass.SNARE,
        14 to DrumClass.HIHAT,
    )

    /** Bass: tonic, tonic, fifth, sixth — one note per beat. Degrees per beat. */
    private val bassDegrees = listOf(0, 0, 4, 5)

    /** Chords: I, vi, IV, V (one per bar). */
    private val chordDegrees = listOf(0, 5, 3, 4)

    /** Melody hook in 16th steps: (step, degree, length). */
    private val hook = listOf(Triple(0, 4, 2), Triple(2, 2, 2), Triple(4, 0, 4), Triple(10, 2, 2), Triple(12, 4, 4))

    /** 16th-step to frame mapping of one loop. */
    private class Grid(song: Song, val sampleRate: Int) {
        val key: MusicalKey = song.key
        val bars = 0 until song.loopBars
        val loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, sampleRate)
        private val step = LoopMath.framesPerStep(song.bpm, sampleRate)

        fun frame(steps: Int): Int = (steps * step).roundToInt()

        fun barFrame(bar: Int, steps: Int): Int = frame(bar * LoopMath.STEPS_PER_BAR + steps)

        fun note(midi: Int, start: Int, steps: Int) = NoteEvent(midi, start, start + frame(steps), DEMO_VELOCITY)
    }

    /** The demo's events for [song] and the instrument to play them with. */
    fun demo(type: TrackType, song: Song, sampleRate: Int, preferred: Instrument): Pair<TrackEvents, Instrument> {
        val grid = Grid(song, sampleRate)
        return when (type) {
            TrackType.BEAT -> TrackEvents(sampleRate, grid.loopFrames, hits = beat(grid)) to preferred
            TrackType.BASS -> TrackEvents(sampleRate, grid.loopFrames, notes = bass(grid)) to preferred
            TrackType.CHORDS -> TrackEvents(sampleRate, grid.loopFrames, notes = chords(grid)) to preferred
            TrackType.MELODY, TrackType.VOCALS_VERSE, TrackType.VOCALS_CHORUS -> {
                // Vocal demos are sung by the flute: the app has no recorded voice to play.
                val instrument = if (type == TrackType.MELODY) preferred else Instrument.FLUTE
                TrackEvents(sampleRate, grid.loopFrames, notes = melody(grid)) to instrument
            }
        }
    }

    /** The bar pattern, with a pickup kick in every second bar. */
    private fun beat(grid: Grid): List<DrumHit> = grid.bars.flatMap { bar ->
        val pickup = if (bar % 2 == 1) listOf(PICKUP_STEP to DrumClass.KICK) else emptyList()
        (beatBar + pickup).map { (s, cls) -> DrumHit(cls, grid.barFrame(bar, s), DEMO_VELOCITY) }
    }

    private fun bass(grid: Grid): List<NoteEvent> = grid.bars.flatMap { bar ->
        bassDegrees.mapIndexed { beat, degree ->
            val start = grid.barFrame(bar, beat * LoopMath.STEPS_PER_BEAT)
            grid.note(MusicTheory.degreeToMidi(degree, grid.key, BASS_OCTAVE), start, BASS_NOTE_STEPS)
        }
    }

    private fun chords(grid: Grid): List<NoteEvent> = grid.bars.flatMap { bar ->
        val degree = chordDegrees[bar % chordDegrees.size]
        val root = MusicTheory.degreeToMidi(degree, grid.key, MusicTheory.CHORD_CENTER)
        val start = grid.barFrame(bar, 0)
        MusicTheory.triad(root, grid.key).map { grid.note(it, start, LoopMath.STEPS_PER_BAR) }
    }

    /** The hook, lifted a step at the start of every second bar. */
    private fun melody(grid: Grid): List<NoteEvent> = grid.bars.flatMap { bar ->
        hook.map { (s, degree, length) ->
            val lift = if (bar % 2 == 1 && s == 0) 1 else 0
            grid.note(MusicTheory.degreeToMidi(degree + lift, grid.key, MELODY_OCTAVE), grid.barFrame(bar, s), length)
        }
    }
}
