package com.mouthband.model

/** Domain view of a song row. */
data class Song(
    val id: Long,
    val title: String,
    val bpm: Int,
    val key: MusicalKey,
    val loopBars: Int,
    val kit: DrumKit,
    val createdAt: Long,
    val updatedAt: Long,
    val coachStep: CoachStep,
    val arrangement: Arrangement,
)

/** Domain view of a track row. A track without a kept take has no file and no events. */
data class Track(
    val id: Long,
    val songId: Long,
    val type: TrackType,
    val takeFilePath: String?,
    val events: TrackEvents?,
    val instrument: Instrument,
    val quantizeStrength: Float,
    val gainDb: Float,
    val pan: Float,
    val muted: Boolean,
    val takeVersion: Int,
) {
    /** True once a take was kept (vocals keep only audio; other tracks keep events). */
    val hasContent: Boolean
        get() = if (type.isVocals) takeFilePath != null else events?.isEmpty == false

    companion object {
        const val DEFAULT_QUANTIZE = 0.8f
        const val LOOP_BARS_SHORT = 2
        const val LOOP_BARS_LONG = 4
        const val BEATS_PER_BAR = 4
        const val STEPS_PER_BEAT = 4
    }
}

/** A finished export of a song. */
data class ExportRecord(
    val id: Long,
    val songId: Long,
    val uri: String,
    val format: ExportFormat,
    val durationMs: Long,
    val createdAt: Long,
)

enum class ExportFormat(val extension: String, val mimeType: String) {
    WAV("wav", "audio/wav"),
    M4A("m4a", "audio/mp4"),
}
