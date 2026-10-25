package com.mouthband.ui.coach

import com.mouthband.audio.LiveTake
import com.mouthband.audio.OutputRoute
import com.mouthband.audio.RecordingSession
import com.mouthband.convert.TakeProblem
import com.mouthband.model.CoachTarget
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackEvents

enum class StepPhase { COACH, RECORDING, PROCESSING, REVIEW }

/** Something went wrong outside the user's control; shown on the coach card. */
enum class StepError { AUDIO_BUSY, MIC_BUSY, STORAGE }

/**
 * A take waiting in Review, or a kept take being edited. [events] are performed positions;
 * [rawLoop] is exactly one loop (empty when no raw audio exists). [selectedNote] indexes
 * `events.notes` (-1: none); [undo] holds earlier [events], newest last.
 */
data class Candidate(
    val rawLoop: FloatArray,
    val events: TrackEvents?,
    val problem: TakeProblem?,
    val instrument: Instrument,
    val kit: DrumKit,
    val quantize: Float,
    val abConverted: Boolean = true,
    val selectedBar: Int = 0,
    val selectedNote: Int = -1,
    val undo: List<TrackEvents> = emptyList(),
) {
    /** The candidate after an edit to [edited], remembering the current events for Undo. */
    fun withEdit(edited: TrackEvents): Candidate {
        val current = events ?: return copy(events = edited, selectedNote = -1)
        return copy(events = edited, undo = (undo + current).takeLast(MAX_UNDO), selectedNote = -1)
    }

    private companion object {
        const val MAX_UNDO = 30
    }
}

data class StepState(
    val song: Song? = null,
    val tracks: List<Track> = emptyList(),
    val phase: StepPhase = StepPhase.COACH,
    val examplePlaying: Boolean = false,
    val route: OutputRoute = OutputRoute(),
    val progress: RecordingSession.Progress? = null,
    /** Filled while a take is recorded; drawn by the Recording screen once per frame. */
    val live: LiveTake? = null,
    val candidate: Candidate? = null,
    val reviewPlaying: Boolean = false,
    /** True when Review edits a take that was already kept (opened from Arrange or the coach). */
    val editingKept: Boolean = false,
    /** Opened to edit a kept take that is still loading: show nothing rather than the coach card. */
    val opening: Boolean = false,
    val error: StepError? = null,
)

sealed interface StepEvent {
    data class Next(val target: CoachTarget) : StepEvent

    /** Editing a kept take ended (saved or dropped): go back to where it started. */
    data object Close : StepEvent

    data object OpenTeach : StepEvent
}
