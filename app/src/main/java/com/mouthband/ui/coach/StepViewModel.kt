package com.mouthband.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.audio.AlignedTake
import com.mouthband.audio.AudioLog
import com.mouthband.audio.LiveTake
import com.mouthband.audio.LoopMath
import com.mouthband.audio.MicLevel
import com.mouthband.audio.MicVerdict
import com.mouthband.audio.RecorderStart
import com.mouthband.audio.RecordingSession
import com.mouthband.convert.TakeProcessor
import com.mouthband.data.KeptTake
import com.mouthband.data.prefs.AudioSettings
import com.mouthband.data.prefs.LastTake
import com.mouthband.data.reachStep
import com.mouthband.model.CoachStep
import com.mouthband.model.CoachTarget
import com.mouthband.model.Instrument
import com.mouthband.model.Track
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * One coach recording step (spec §1, steps 2–6): coach card with live meter and example,
 * recording over the earlier tracks, conversion, then Review with A/B, editing, sound and
 * timing. Keeping a take autosaves it immediately.
 */
class StepViewModel(
    private val songId: Long,
    val type: TrackType,
    private val deps: StepDeps,
    private val editOnOpen: Boolean = false,
    private val fromArrange: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(StepState(opening = editOnOpen))
    val state: StateFlow<StepState> = _state.asStateFlow()
    private val events = Channel<StepEvent>(Channel.BUFFERED)
    val navigation = events.receiveAsFlow()

    val sampleRate: Int get() = deps.engine.sampleRate
    private val rig = StepRig(deps.engine, deps.cache)
    /** Review interactions (A/B, sound, timing) and [ReviewController.edits]. */
    val review = ReviewController(_state, rig, deps, type, viewModelScope)

    /** Positions the live views draw once per frame. */
    val playheads = StepPlayheads({ session }, { _state.value.reviewPlaying }, rig)
    private var recordingJob: Job? = null
    private var session: RecordingSession? = null
    private var editOpened = false

    /** The mic level for the coach card's meter, read by the meter once per frame. */
    val micLevel: StateFlow<MicLevel> = deps.micMonitor.level

    /** Too quiet / Good / Too loud: changes rarely, so the coach card only recomposes then. */
    val micVerdict: StateFlow<MicVerdict> = deps.micMonitor.level.map { it.verdict }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MicLevel.Silent.verdict)

    init {
        viewModelScope.launch {
            combine(deps.repository.song(songId), deps.tracks.observe(songId)) { s, t -> s to t }
                .collect { (song, tracks) ->
                    _state.update { it.copy(song = song, tracks = tracks) }
                    if (shouldOpenEditor(song != null && tracks.isNotEmpty())) {
                        editOpened = true
                        editKept()
                    }
                }
        }
        viewModelScope.launch { deps.outputDevices.route.collect { route -> _state.update { it.copy(route = route) } } }
    }

    private fun shouldOpenEditor(loaded: Boolean): Boolean = editOnOpen && !editOpened && loaded

    /** Coach card visible with mic access: open the mic for the level meter. */
    fun setMetering(on: Boolean) {
        val coach = _state.value.phase == StepPhase.COACH
        if (on && coach && !deps.micMonitor.isRunning) {
            if (deps.micMonitor.start(viewModelScope) == RecorderStart.UNAVAILABLE) setError(StepError.MIC_BUSY)
        } else if (!on || !coach) {
            deps.micMonitor.stop()
        }
    }

    fun toggleExample() {
        if (_state.value.examplePlaying) {
            stopPlayback()
            return
        }
        val song = _state.value.song ?: return
        viewModelScope.launch {
            val buffer = withContext(Dispatchers.Default) {
                rig.loadBacking(song, _state.value.tracks, type)
                review.renderDemo(song)
            }
            rig.setCandidate(buffer)
            if (deps.session.begin { stopPlayback() } && rig.play()) {
                _state.update { it.copy(examplePlaying = true) }
            } else {
                setError(StepError.AUDIO_BUSY)
            }
        }
    }

    fun record() {
        if (recordingJob?.isActive == true) return
        val song = _state.value.song ?: return
        _state.update { it.copy(error = null) }
        recordingJob = viewModelScope.launch {
            if (type == TrackType.BEAT && shouldTeachFirst()) {
                events.send(StepEvent.OpenTeach)
                return@launch
            }
            stopPlayback()
            deps.micMonitor.stop()
            val tracks = _state.value.tracks
            withContext(Dispatchers.Default) { rig.loadBacking(song, tracks, type) }
            rig.setCandidate(null)
            if (!deps.session.begin { recordingJob?.cancel() }) {
                setError(StepError.AUDIO_BUSY)
                return@launch
            }
            val audio = deps.settings.audioNow()
            deps.loadLiveClassifier()
            val take = try {
                capture(song.bpm, song.loopBars, audio)
            } finally {
                deps.session.end()
                _state.update { it.copy(progress = null) }
            }
            if (take != null) process(take, audio.clickOn)
        }
        recordingJob?.invokeOnCompletion { cause ->
            if (cause != null && _state.value.phase == StepPhase.RECORDING) {
                _state.update { it.copy(phase = StepPhase.COACH) }
            }
        }
    }

    private suspend fun shouldTeachFirst(): Boolean {
        if (deps.templates.current() != null || deps.settings.flagsNow().teachPromptShown) return false
        deps.settings.setTeachPromptShown()
        return true
    }

    private suspend fun capture(bpm: Int, loopBars: Int, audio: AudioSettings): AlignedTake? {
        val recording = deps.newSession().also { session = it }
        val live = LiveTake(LoopMath.loopFrames(loopBars, bpm, deps.engine.sampleRate))
        _state.update { it.copy(phase = StepPhase.RECORDING, error = null, live = live) }
        // Progress only changes on a new beat or second, so the screen recomposes rarely; the
        // live lanes draw from [live] every frame without recomposition.
        val progressJob = viewModelScope.launch {
            recording.progress.collect { p -> _state.update { it.copy(progress = p ?: it.progress) } }
        }
        val request = RecordingSession.Request(
            bpm,
            loopBars,
            rig.player,
            audio,
            deps.takes.recordingTemp(songId),
            liveSinks(type, recording::loopFrameAt, live, deps.engine.sampleRate, deps::classifierFor),
        )
        val result = try {
            recording.record(request)
        } finally {
            progressJob.cancel()
            session = null
        }
        return when (result) {
            is RecordingSession.Result.Captured -> result.take
            is RecordingSession.Result.Failed -> {
                _state.update { it.copy(phase = StepPhase.COACH) }
                setError(if (result.reason == RecorderStart.UNAVAILABLE) StepError.MIC_BUSY else StepError.AUDIO_BUSY)
                null
            }
            RecordingSession.Result.StorageFailed -> {
                _state.update { it.copy(phase = StepPhase.COACH) }
                setError(StepError.STORAGE)
                null
            }
        }
    }

    private suspend fun process(take: AlignedTake, clickOn: Boolean) {
        val song = _state.value.song ?: return
        _state.update { it.copy(phase = StepPhase.PROCESSING) }
        val context = TakeProcessor.Context(
            bpm = song.bpm,
            key = song.key,
            templates = deps.templates.current(),
            clicksAudible = clickOn && !_state.value.route.wiredHeadphones,
        )
        val converted = withContext(Dispatchers.Default) { deps.processor.process(type, take, context) }
        val instrument = _state.value.tracks.firstOrNull { it.type == type }?.instrument ?: Instrument.VOICE
        val candidate = Candidate(
            rawLoop = converted.loop,
            events = converted.events,
            problem = converted.problem,
            instrument = instrument,
            kit = song.kit,
            quantize = _state.value.tracks.firstOrNull { it.type == type }?.quantizeStrength ?: Track.DEFAULT_QUANTIZE,
            abConverted = type.kind != TrackKind.RAW,
        )
        _state.update { it.copy(phase = StepPhase.REVIEW, candidate = candidate, live = null) }
        if (converted.problem == null) review.refresh(autoPlay = true)
    }

    fun stopRecording() {
        session?.requestStop()
    }

    fun cancelRecording() {
        recordingJob?.cancel()
    }

    fun tryAgain() {
        stopPlayback()
        _state.update { it.copy(phase = StepPhase.COACH, candidate = null, editingKept = false) }
    }

    /**
     * Back from Review drops the take or the unsaved edit. An editor opened from Arrange closes
     * back to Arrange; everything else returns to the coach card.
     */
    fun leaveReview() {
        if (!(_state.value.editingKept && editOnOpen)) {
            tryAgain()
            return
        }
        stopPlayback()
        viewModelScope.launch { events.send(StepEvent.Close) }
    }

    /** Opens the kept take in Review to edit its notes, chords or hits without recording again. */
    fun editKept() {
        val song = _state.value.song
        val tracks = _state.value.tracks
        val track = tracks.firstOrNull { it.type == type }?.takeIf { it.hasContent && !type.isVocals }
        if (song == null || track == null || _state.value.phase != StepPhase.COACH) {
            _state.update { it.copy(opening = false) }
            return
        }
        viewModelScope.launch {
            stopPlayback()
            deps.micMonitor.stop()
            val candidate = KeptTakes.candidateOf(track, song, deps)
            // Show the editor at once; the backing loops for playback render behind it.
            _state.update {
                it.copy(phase = StepPhase.REVIEW, candidate = candidate, editingKept = true, opening = false)
            }
            withContext(Dispatchers.Default) { rig.loadBacking(song, tracks, type) }
            review.refresh()
        }
    }

    fun keep() {
        val song = _state.value.song ?: return
        val candidate = _state.value.candidate ?: return
        if (_state.value.editingKept) {
            viewModelScope.launch {
                stopPlayback()
                val saved = KeptTakes.save(song, type, candidate, _state.value.tracks, deps)
                when {
                    !saved -> setError(StepError.STORAGE)
                    // Saved: back to Arrange when it opened the editor, else back to the coach card.
                    editOnOpen -> events.send(StepEvent.Close)
                    else -> tryAgain()
                }
            }
            return
        }
        viewModelScope.launch {
            stopPlayback()
            try {
                val kept = deps.tracks.keepTake(
                    songId,
                    type,
                    KeptTake(
                        candidate.rawLoop,
                        deps.engine.sampleRate,
                        candidate.events,
                        candidate.instrument,
                        candidate.quantize,
                    ),
                )
                if (type == TrackType.BEAT && candidate.kit != song.kit) {
                    deps.repository.updateSong(song.copy(kit = candidate.kit))
                }
                kept.takeFilePath?.let { deps.settings.setLastTake(LastTake(it, song.bpm, song.loopBars)) }
                finishStep()
            } catch (e: IOException) {
                AudioLog.w("keep take", e)
                setError(StepError.STORAGE)
            }
        }
    }

    fun skip() {
        viewModelScope.launch { finishStep() }
    }

    /** After a kept or skipped step: back to Arrange when it opened this step, else on in the coach. */
    private suspend fun finishStep() {
        if (fromArrange) events.send(StepEvent.Close) else advance(type.nextAfterKeep)
    }

    private suspend fun advance(target: CoachTarget) {
        val step = when (target) {
            is CoachTarget.Record -> target.track.coachStep
            CoachTarget.Arrange -> CoachStep.ARRANGE
            CoachTarget.Export -> CoachStep.EXPORT
            CoachTarget.Vibe -> CoachStep.VIBE
        }
        deps.repository.reachStep(songId, step)
        events.send(StepEvent.Next(target))
    }

    private fun stopPlayback() {
        rig.stop()
        deps.session.end()
        _state.update { it.copy(examplePlaying = false, reviewPlaying = false) }
    }

    private fun setError(error: StepError) {
        _state.update { it.copy(error = error) }
    }

    override fun onCleared() {
        recordingJob?.cancel()
        rig.stop()
        deps.micMonitor.stop()
        deps.session.end()
    }
}
