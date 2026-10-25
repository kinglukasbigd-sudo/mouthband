package com.mouthband.ui.coach

import com.mouthband.audio.RecordingSession

/**
 * Positions the step's live views draw once per display frame: the loop frame heard during a
 * take, and the Review playback position. Cheap and allocation-free.
 */
class StepPlayheads(
    private val session: () -> RecordingSession?,
    private val reviewing: () -> Boolean,
    private val rig: StepRig,
) {
    /** Loop-relative frame heard during a take; [Long.MIN_VALUE] when none runs. */
    fun recording(): Long = session()?.heardLoopFrame() ?: Long.MIN_VALUE

    /** Review playback position in the loop, 0..1, or -1 when not playing. */
    fun review(): Float = if (reviewing()) rig.playheadFraction() else -1f
}
