package com.mouthband.ui.navigation

import com.mouthband.model.TrackType
import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data class VibeRoute(val songId: Long)

/**
 * Coach steps 2–6: one route holds the coach card, recording and review phases. [edit] opens the
 * kept take straight in Review; [fromArrange] means Arrange opened it, so keeping or leaving
 * returns there instead of continuing through the coach.
 */
@Serializable
data class StepRoute(
    val songId: Long,
    val track: TrackType,
    val edit: Boolean = false,
    val fromArrange: Boolean = false,
)

@Serializable
data class TeachRoute(val thenRecordSongId: Long = NO_SONG)

@Serializable
data class ArrangeRoute(val songId: Long)

@Serializable
data class ExportRoute(val songId: Long)

@Serializable
data object SettingsRoute

@Serializable
data object CalibrationRoute

@Serializable
data object NudgeRoute

@Serializable
data object LicensesRoute

@Serializable
data object GalleryRoute

const val NO_SONG = -1L
