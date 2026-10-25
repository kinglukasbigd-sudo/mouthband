package com.mouthband.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/** How a track's take is turned into sound. */
enum class TrackKind { DRUMS, PITCHED, RAW }

/**
 * The lanes of a song. Vocals have two separate takes (verse and chorus) that the
 * arrangement switches between; both belong to coach step 6.
 */
@Keep
@Serializable
enum class TrackType(val kind: TrackKind, val coachStep: CoachStep) {
    BEAT(TrackKind.DRUMS, CoachStep.BEAT),
    BASS(TrackKind.PITCHED, CoachStep.BASS),
    CHORDS(TrackKind.PITCHED, CoachStep.CHORDS),
    MELODY(TrackKind.PITCHED, CoachStep.MELODY),
    VOCALS_VERSE(TrackKind.RAW, CoachStep.VOCALS),
    VOCALS_CHORUS(TrackKind.RAW, CoachStep.VOCALS),
    ;

    val isVocals: Boolean get() = kind == TrackKind.RAW

    /** The step that follows a kept take of this track. */
    val nextAfterKeep: CoachTarget
        get() = when (this) {
            BEAT -> CoachTarget.Record(BASS)
            BASS -> CoachTarget.Record(CHORDS)
            CHORDS -> CoachTarget.Record(MELODY)
            MELODY -> CoachTarget.Record(VOCALS_VERSE)
            VOCALS_VERSE -> CoachTarget.Record(VOCALS_CHORUS)
            VOCALS_CHORUS -> CoachTarget.Arrange
        }
}

/** The 8 Song Coach steps, always in this order. */
enum class CoachStep(val number: Int) {
    VIBE(1),
    BEAT(2),
    BASS(3),
    CHORDS(4),
    MELODY(5),
    VOCALS(6),
    ARRANGE(7),
    EXPORT(8),
    ;

    companion object {
        const val COUNT = 8

        fun fromNumber(number: Int): CoachStep = entries.firstOrNull { it.number == number } ?: VIBE
    }
}

/** Where the coach sends the user next. */
sealed interface CoachTarget {
    data object Vibe : CoachTarget

    data class Record(val track: TrackType) : CoachTarget

    data object Arrange : CoachTarget

    data object Export : CoachTarget
}
