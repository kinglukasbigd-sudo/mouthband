package com.mouthband.ui.common

import androidx.annotation.StringRes
import com.mouthband.R
import com.mouthband.model.CoachStep
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.SectionType
import com.mouthband.model.TrackType

/** String resources for domain enums, so screens never hard-code labels. */
@StringRes
fun TrackType.labelRes(): Int = when (this) {
    TrackType.BEAT -> R.string.track_beat
    TrackType.BASS -> R.string.track_bass
    TrackType.CHORDS -> R.string.track_chords
    TrackType.MELODY -> R.string.track_melody
    TrackType.VOCALS_VERSE -> R.string.track_vocals_verse
    TrackType.VOCALS_CHORUS -> R.string.track_vocals_chorus
}

@StringRes
fun CoachStep.nameRes(): Int = when (this) {
    CoachStep.VIBE -> R.string.step_name_vibe
    CoachStep.BEAT -> R.string.step_name_beat
    CoachStep.BASS -> R.string.step_name_bass
    CoachStep.CHORDS -> R.string.step_name_chords
    CoachStep.MELODY -> R.string.step_name_melody
    CoachStep.VOCALS -> R.string.step_name_vocals
    CoachStep.ARRANGE -> R.string.step_name_arrange
    CoachStep.EXPORT -> R.string.step_name_export
}

@StringRes
fun Mood.labelRes(): Int = when (this) {
    Mood.CHILL -> R.string.mood_chill
    Mood.POP -> R.string.mood_pop
    Mood.HYPE -> R.string.mood_hype
    Mood.SAD -> R.string.mood_sad
    Mood.ROCK -> R.string.mood_rock
}

@StringRes
fun DrumKit.labelRes(): Int = when (this) {
    DrumKit.BOOM_BAP -> R.string.kit_boom_bap
    DrumKit.TRAP -> R.string.kit_trap
    DrumKit.LOFI -> R.string.kit_lofi
    DrumKit.ROCK -> R.string.kit_rock
    DrumKit.POP -> R.string.kit_pop
}

@StringRes
fun Instrument.labelRes(): Int = when (this) {
    Instrument.SYNTH_BASS -> R.string.inst_synth_bass
    Instrument.DEEP_SUB -> R.string.inst_deep_sub
    Instrument.ELECTRIC_BASS -> R.string.inst_electric_bass
    Instrument.SOFT_KEYS -> R.string.inst_soft_keys
    Instrument.WARM_PAD -> R.string.inst_warm_pad
    Instrument.ORGAN -> R.string.inst_organ
    Instrument.PIANO -> R.string.inst_piano
    Instrument.FLUTE -> R.string.inst_flute
    Instrument.BRIGHT_LEAD -> R.string.inst_bright_lead
    Instrument.BELL -> R.string.inst_bell
    Instrument.VOICE -> R.string.inst_voice
}

@StringRes
fun SectionType.labelRes(): Int = when (this) {
    SectionType.INTRO -> R.string.section_intro
    SectionType.VERSE -> R.string.section_verse
    SectionType.CHORUS -> R.string.section_chorus
    SectionType.OUTRO -> R.string.section_outro
}

/** Coach copy for a recording step: title, reason and the three numbered tips. */
data class CoachCopy(
    @StringRes val title: Int,
    @StringRes val reason: Int,
    @StringRes val tips: List<Int>,
)

fun TrackType.coachCopy(): CoachCopy = when (this) {
    TrackType.BEAT -> CoachCopy(
        R.string.coach_beat_title,
        R.string.coach_beat_reason,
        listOf(R.string.coach_beat_tip1, R.string.coach_beat_tip2, R.string.coach_beat_tip3),
    )
    TrackType.BASS -> CoachCopy(
        R.string.coach_bass_title,
        R.string.coach_bass_reason,
        listOf(R.string.coach_bass_tip1, R.string.coach_bass_tip2, R.string.coach_bass_tip3),
    )
    TrackType.CHORDS -> CoachCopy(
        R.string.coach_chords_title,
        R.string.coach_chords_reason,
        listOf(R.string.coach_chords_tip1, R.string.coach_chords_tip2, R.string.coach_chords_tip3),
    )
    TrackType.MELODY -> CoachCopy(
        R.string.coach_melody_title,
        R.string.coach_melody_reason,
        listOf(R.string.coach_melody_tip1, R.string.coach_melody_tip2, R.string.coach_melody_tip3),
    )
    TrackType.VOCALS_VERSE -> CoachCopy(
        R.string.coach_verse_title,
        R.string.coach_verse_reason,
        listOf(R.string.coach_verse_tip1, R.string.coach_verse_tip2, R.string.coach_verse_tip3),
    )
    TrackType.VOCALS_CHORUS -> CoachCopy(
        R.string.coach_chorus_title,
        R.string.coach_chorus_reason,
        listOf(R.string.coach_chorus_tip1, R.string.coach_chorus_tip2, R.string.coach_chorus_tip3),
    )
}
