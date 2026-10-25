package com.mouthband.model

import kotlinx.serialization.Serializable

@Serializable
enum class SectionType { INTRO, VERSE, CHORUS, OUTRO }

/** One block of the song: its length in bars and which tracks play in it. */
@Serializable
data class Section(
    val type: SectionType,
    val bars: Int,
    val active: Set<TrackType>,
)

/**
 * The song structure, stored as JSON in SongEntity.arrangementJson. [autoMix] turns on the
 * auto-mix processing (bass low-pass, chord width, vocal ducking, master compressor).
 */
@Serializable
data class Arrangement(
    val sections: List<Section>,
    val autoMix: Boolean = false,
) {
    val totalBars: Int get() = sections.sumOf { it.bars }

    fun withSectionTrack(index: Int, track: TrackType, on: Boolean): Arrangement {
        val section = sections[index]
        val active = if (on) section.active + track else section.active - track
        return copy(sections = sections.toMutableList().also { it[index] = section.copy(active = active) })
    }

    companion object {
        private const val SHORT = 4
        private const val LONG = 8

        private val introOutro = setOf(TrackType.CHORDS, TrackType.MELODY)
        private val verse = setOf(TrackType.BEAT, TrackType.BASS, TrackType.CHORDS, TrackType.VOCALS_VERSE)
        private val chorus = setOf(
            TrackType.BEAT,
            TrackType.BASS,
            TrackType.CHORDS,
            TrackType.MELODY,
            TrackType.VOCALS_CHORUS,
        )

        /** Intro 4, Verse 8, Chorus 8, Verse 8, Chorus 8, Outro 4 (spec §1). */
        val Default = Arrangement(
            sections = listOf(
                Section(SectionType.INTRO, SHORT, introOutro),
                Section(SectionType.VERSE, LONG, verse),
                Section(SectionType.CHORUS, LONG, chorus),
                Section(SectionType.VERSE, LONG, verse),
                Section(SectionType.CHORUS, LONG, chorus),
                Section(SectionType.OUTRO, SHORT, introOutro),
            ),
        )
    }
}
