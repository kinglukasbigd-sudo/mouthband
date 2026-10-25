package com.mouthband.ui.coach

import com.mouthband.audio.LoopMath
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Song
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import com.mouthband.synth.DemoPatterns
import com.mouthband.synth.RenderSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Review interactions (spec §1 Review screen): A/B between the raw take and the converted
 * loop, sound picker, timing slider and simple pattern editing. Every change re-renders the
 * candidate loop off the main thread and swaps it into the playing rig.
 */
class ReviewController(
    private val state: MutableStateFlow<StepState>,
    private val rig: StepRig,
    private val deps: StepDeps,
    private val type: TrackType,
    private val scope: CoroutineScope,
) {
    private var renderJob: Job? = null

    /** Note and hit editing with selection and Undo. */
    val edits = ReviewEdits(state, deps.engine.sampleRate, type) { refresh() }

    /** Example loop for the coach card's "Hear an example first". */
    fun renderDemo(song: Song): FloatArray? {
        val rate = deps.engine.sampleRate
        val preferred = state.value.tracks.firstOrNull { it.type == type }?.instrument ?: Instrument.VOICE
        val (events, instrument) = DemoPatterns.demo(type, song, rate, preferred)
        val spec = RenderSpec(
            type = if (type.isVocals) TrackType.MELODY else type,
            events = events,
            rawLoop = null,
            instrument = instrument,
            kit = song.kit,
            quantize = 1f,
            bpm = song.bpm,
            loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, rate),
        )
        return deps.renderer.render(spec)
    }

    /** Re-renders the candidate (converted or raw, per the A/B toggle) and optionally starts playback. */
    fun refresh(autoPlay: Boolean = false) {
        val song = state.value.song ?: return
        val candidate = state.value.candidate ?: return
        renderJob?.cancel()
        renderJob = scope.launch {
            val loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, deps.engine.sampleRate)
            val buffer = withContext(Dispatchers.Default) {
                if (candidate.abConverted && candidate.events != null) {
                    deps.renderer.render(
                        RenderSpec(
                            type,
                            candidate.events,
                            null,
                            candidate.instrument,
                            candidate.kit,
                            candidate.quantize,
                            song.bpm,
                            loopFrames,
                        ),
                    )
                } else {
                    val spec = RenderSpec(
                        type,
                        null,
                        candidate.rawLoop,
                        Instrument.VOICE,
                        candidate.kit,
                        0f,
                        song.bpm,
                        loopFrames,
                        vocalReverb = true,
                    )
                    if (type.isVocals) deps.renderer.render(spec) else deps.renderer.rawStereo(
                        candidate.rawLoop,
                        loopFrames,
                    )
                }
            }
            rig.setCandidate(buffer)
            if (autoPlay) play()
        }
    }

    fun togglePlay() {
        if (state.value.reviewPlaying) {
            rig.stop()
            deps.session.end()
            state.update { it.copy(reviewPlaying = false) }
        } else {
            play()
        }
    }

    private fun play() {
        if (deps.session.begin { togglePlayOff() } && rig.play()) state.update { it.copy(reviewPlaying = true) }
    }

    private fun togglePlayOff() {
        rig.stop()
        state.update { it.copy(reviewPlaying = false) }
    }

    fun setAb(converted: Boolean) = edit(rerender = true) { it.copy(abConverted = converted) }

    fun selectBar(bar: Int) = edit(rerender = false) { it.copy(selectedBar = bar, selectedNote = -1) }

    fun setQuantize(value: Float) = edit(rerender = false) { it.copy(quantize = value.coerceIn(0f, 1f)) }

    /** Sound chip: a drum kit for the beat, an instrument preset otherwise. */
    fun selectSound(id: String) = edit(rerender = true) { candidate ->
        if (type.kind == TrackKind.DRUMS) {
            DrumKit.entries.firstOrNull { it.name == id }?.let { candidate.copy(kit = it) } ?: candidate
        } else {
            Instrument.entries.firstOrNull { it.name == id }?.let { candidate.copy(instrument = it) } ?: candidate
        }
    }

    private fun edit(rerender: Boolean, change: (Candidate) -> Candidate) {
        state.update { s -> s.candidate?.let { s.copy(candidate = change(it)) } ?: s }
        if (rerender) refresh()
    }
}
