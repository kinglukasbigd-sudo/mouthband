package com.mouthband.convert

import com.mouthband.model.MusicalKey
import com.mouthband.model.TrackType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2

/** Scale snapping, octave ranges and diatonic triads (spec §2.5 Musical cleanup). */
object MusicTheory {
    const val SEMITONES = 12
    private const val TIE_EPSILON = 0.1f

    /** Spec §2.5: bass 28–43, melody 55–84 (MIDI). */
    val BASS_RANGE = 28..43
    val MELODY_RANGE = 55..84

    /** Chords are voiced around C4. */
    const val CHORD_CENTER = 60

    /**
     * Nearest scale tone to the fractional MIDI [pitch]. When two scale tones are (nearly)
     * equally close, round toward the direction the melody is moving from [previous]; with no
     * previous note (or no movement), round down.
     */
    fun snapToScale(pitch: Float, key: MusicalKey, previous: Int?): Int {
        val below = scaleToneAtOrBelow(floor(pitch).toInt(), key)
        val above = scaleToneAtOrAbove(below + 1, key)
        val dBelow = pitch - below
        val dAbove = above - pitch
        return when {
            abs(dBelow - dAbove) <= TIE_EPSILON -> if (previous != null && pitch > previous) above else below
            dBelow < dAbove -> below
            else -> above
        }
    }

    fun scaleToneAtOrBelow(midi: Int, key: MusicalKey): Int {
        var m = midi
        while (!key.contains(m)) m--
        return m
    }

    fun scaleToneAtOrAbove(midi: Int, key: MusicalKey): Int {
        var m = midi
        while (!key.contains(m)) m++
        return m
    }

    /** Transposes by whole octaves into [range]; pitches already inside stay put. */
    fun foldIntoRange(midi: Int, range: IntRange): Int {
        var m = midi
        while (m < range.first) m += SEMITONES
        while (m > range.last) m -= SEMITONES
        return if (m < range.first) m + SEMITONES else m
    }

    fun rangeFor(type: TrackType): IntRange? = when (type) {
        TrackType.BASS -> BASS_RANGE
        TrackType.MELODY -> MELODY_RANGE
        else -> null
    }

    private const val THIRD_DEGREES = 2
    private const val FIFTH_DEGREES = 4

    /**
     * Diatonic triad on [root] (a scale tone) in [key]: root, 3rd and 5th taken from the scale,
     * voiced as the inversion closest to [CHORD_CENTER].
     */
    fun triad(root: Int, key: MusicalKey): IntArray {
        val third = nthScaleToneAbove(root, THIRD_DEGREES, key)
        val fifth = nthScaleToneAbove(root, FIFTH_DEGREES, key)
        val classes = intArrayOf(root, third, fifth).map { Math.floorMod(it, SEMITONES) }
        // Every chord tone goes into the octave around the centre; sorted, that is the inversion nearest C4.
        return classes.map { pc ->
            val base = CHORD_CENTER - SEMITONES / 2
            base + Math.floorMod(pc - base, SEMITONES)
        }.sorted().toIntArray()
    }

    /** The scale tone [steps] scale degrees above [midi] (which must be a scale tone). */
    fun nthScaleToneAbove(midi: Int, steps: Int, key: MusicalKey): Int {
        var m = midi
        repeat(steps) { m = scaleToneAtOrAbove(m + 1, key) }
        return m
    }

    /** MIDI of scale [degree] (0 = tonic, may be negative or ≥ 7) in the octave starting at [octaveRoot]. */
    fun degreeToMidi(degree: Int, key: MusicalKey, octaveRoot: Int): Int {
        val size = key.mode.intervals.size
        val octave = Math.floorDiv(degree, size)
        val index = Math.floorMod(degree, size)
        return octaveRoot + key.root + octave * SEMITONES + key.mode.intervals[index]
    }

    /** Fractional MIDI of a frequency. */
    fun hzToMidi(hz: Float): Float = A4_MIDI + SEMITONES * log2(hz / A4_HZ)

    private const val A4_MIDI = 69f
    private const val A4_HZ = 440f
}
