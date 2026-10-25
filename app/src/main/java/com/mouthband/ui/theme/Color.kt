package com.mouthband.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.mouthband.model.TrackType

/** Fixed design tokens (spec §3). Nothing outside ui/theme declares a color literal. */
object MbColors {
    val Cream = Color(0xFFF2EBE6)
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    val InkSoft = Color(0xFF2C2C2C)
    val Muted = Color(0xFF5C5650)
    val Indigo = Color(0xFF160572)
    val IndigoLine = Color(0xFF3D2E9E)
    val OnIndigoMuted = Color(0xFFC9C3EC)
    val Tangerine = Color(0xFFFF7701)
    val Highlighter = Color(0xFFFFFF00)
    val Rule = Color(0xFFD8CFC6)
    val Empty = Color(0xFFB5ADA4)

    // Derived tokens: alpha variants of the fixed palette, never new hues.

    /** "White" text on Indigo, Bass and Chords surfaces. */
    val OnIndigo = Paper

    /** Overlay that makes every 4th drum grid column slightly darker on Paper. */
    val GridBeatOverlayPaper = Ink.copy(alpha = 0.07f)

    /** Overlay that makes every 4th drum grid column slightly darker on Indigo. */
    val GridBeatOverlayIndigo = Ink.copy(alpha = 0.22f)

    /** Scrim behind bottom sheets and dialogs. */
    val Scrim = Ink.copy(alpha = 0.4f)

    val Transparent = Color.Transparent

    val BeatTrack = Color(0xFFFF7701)
    val BassTrack = Color(0xFF2544A0)
    val ChordsTrack = Color(0xFF4B0F4D)
    val MelodyTrack = Color(0xFF24E3DC)
    val VocalsTrack = Color(0xFFD98199)
}

/** A track's identity color and the text color that passes contrast on it. */
@Immutable
data class TrackColors(val fill: Color, val onFill: Color)

fun TrackType.colors(): TrackColors = when (this) {
    TrackType.BEAT -> TrackColors(MbColors.BeatTrack, MbColors.Indigo)
    TrackType.BASS -> TrackColors(MbColors.BassTrack, MbColors.OnIndigo)
    TrackType.CHORDS -> TrackColors(MbColors.ChordsTrack, MbColors.OnIndigo)
    TrackType.MELODY -> TrackColors(MbColors.MelodyTrack, MbColors.Ink)
    TrackType.VOCALS_VERSE, TrackType.VOCALS_CHORUS -> TrackColors(MbColors.VocalsTrack, MbColors.Ink)
}
