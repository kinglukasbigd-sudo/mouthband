package com.mouthband.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyMode(val intervals: IntArray, val shortLabel: String) {
    MAJOR(intArrayOf(0, 2, 4, 5, 7, 9, 11), "MAJ"),
    MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 10), "MIN"),
}

/** A song key: one of 12 roots (0 = C) in major or natural minor. */
@Serializable
data class MusicalKey(val root: Int, val mode: KeyMode) {
    init {
        require(root in 0 until SEMITONES) { "root must be a pitch class" }
    }

    /** Pitch classes of the scale, ascending from the root. */
    val pitchClasses: IntArray
        get() = IntArray(mode.intervals.size) { (root + mode.intervals[it]) % SEMITONES }

    fun contains(midi: Int): Boolean {
        val pc = Math.floorMod(midi, SEMITONES)
        return pitchClasses.any { it == pc }
    }

    /** Mono label such as "A MIN". */
    val label: String get() = "${rootName(root)} ${mode.shortLabel}"

    companion object {
        const val SEMITONES = 12
        private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun rootName(root: Int): String = NAMES[Math.floorMod(root, SEMITONES)]

        /** Every key the manual picker offers: 12 roots × 2 modes. */
        val all: List<MusicalKey> = KeyMode.entries.flatMap { mode -> (0 until SEMITONES).map { MusicalKey(it, mode) } }
    }
}
