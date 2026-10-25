package com.mouthband.ui.coach

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.R
import com.mouthband.model.CoachTarget
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import com.mouthband.ui.common.MicPermissionState
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.common.rememberFrameSample
import com.mouthband.ui.common.rememberMicPermission
import com.mouthband.ui.components.easeOut
import com.mouthband.ui.recording.LiveLane
import com.mouthband.ui.recording.LiveView
import com.mouthband.ui.recording.RecordingScreen
import com.mouthband.ui.recording.RecordingUi
import com.mouthband.ui.review.ReviewActions
import com.mouthband.ui.review.ReviewPattern
import com.mouthband.ui.review.ReviewScreen
import com.mouthband.ui.review.ReviewUi
import com.mouthband.ui.theme.LocalReduceMotion

/** The three screens of a step; recording and processing share the Recording screen. */
private enum class StepScreen { COACH, RECORDING, REVIEW }

private val StepPhase.screen: StepScreen
    get() = when (this) {
        StepPhase.COACH -> StepScreen.COACH
        StepPhase.RECORDING, StepPhase.PROCESSING -> StepScreen.RECORDING
        StepPhase.REVIEW -> StepScreen.REVIEW
    }

/**
 * Coach steps 2–6: coach card → recording → review, driven by [StepViewModel]. With [edit] the
 * kept take opens straight in Review for editing. [fromArrange] returns to Arrange after a kept
 * take instead of moving on to the next coach step.
 */
@Composable
fun StepDestination(
    songId: Long,
    type: TrackType,
    edit: Boolean,
    fromArrange: Boolean,
    onNext: (CoachTarget) -> Unit,
    onTeach: () -> Unit,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "step-$songId-${type.name}-$edit-$fromArrange") { c, _ ->
        StepViewModel(songId, type, StepDeps(c), editOnOpen = edit, fromArrange = fromArrange)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val permission = rememberMicPermission()
    var bluetoothDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.navigation.collect { event ->
            when (event) {
                is StepEvent.Next -> onNext(event.target)
                StepEvent.OpenTeach -> onTeach()
                StepEvent.Close -> onBack()
            }
        }
    }
    val metering = state.meters(permission.access)
    LifecycleResumeEffect(metering) {
        vm.setMetering(metering)
        onPauseOrDispose { vm.setMetering(false) }
    }
    BackHandler(enabled = state.phase != StepPhase.COACH) { vm.back(state.phase) }
    if (state.route.bluetooth && !bluetoothDismissed) BluetoothSheet(onDismiss = { bluetoothDismissed = true })
    if (state.opening) {
        ScreenScaffold {}
        return
    }
    val instant = LocalReduceMotion.current
    AnimatedContent(
        targetState = state.phase.screen,
        transitionSpec = { stepTransition(initialState, targetState, instant) },
        label = "step",
    ) { screen ->
        when (screen) {
            StepScreen.COACH -> CoachContent(vm, state, permission, onBack, onTeach)
            StepScreen.RECORDING -> RecordingContent(vm, state)
            StepScreen.REVIEW -> ReviewContent(vm, state)
        }
    }
}

/** The coach card meters the mic once it is on screen and allowed; never while the editor opens. */
private fun StepState.meters(access: MicAccess): Boolean =
    phase == StepPhase.COACH && !opening && access == MicAccess.GRANTED

/** System back inside a step: cancels a recording or leaves Review; the coach card pops the route. */
private fun StepViewModel.back(phase: StepPhase) {
    when (phase) {
        StepPhase.RECORDING -> cancelRecording()
        StepPhase.REVIEW -> leaveReview()
        StepPhase.PROCESSING, StepPhase.COACH -> Unit
    }
}

private fun stepTransition(from: StepScreen, to: StepScreen, instant: Boolean): ContentTransform {
    val duration = if (instant) 0 else PHASE_MS
    val enter = when (to) {
        // The Indigo recording screen grows in from the record button's calm, the Review slides up.
        StepScreen.RECORDING -> fadeIn(easeOut(duration)) + scaleIn(easeOut(duration), initialScale = RECORDING_SCALE)
        StepScreen.REVIEW -> fadeIn(easeOut(duration)) + slideInVertically(easeOut(duration)) { it / REVIEW_SLIDE }
        StepScreen.COACH -> fadeIn(easeOut(duration))
    }
    val exit = fadeOut(easeOut(if (from == StepScreen.RECORDING) duration / 2 else duration))
    return enter togetherWith exit
}

@Composable
private fun CoachContent(
    vm: StepViewModel,
    state: StepState,
    permission: MicPermissionState,
    onBack: () -> Unit,
    onTeach: () -> Unit,
) {
    val verdict by vm.micVerdict.collectAsStateWithLifecycle()
    // Collected as a State object and only read inside the meter's drawing: no recomposition.
    val level = vm.micLevel.collectAsStateWithLifecycle()
    CoachStepScreen(
        state = state.toCoachUi(vm.type, permission.access, verdict),
        actions = CoachStepActions(
            onBack = onBack,
            onHearExample = vm::toggleExample,
            onRecord = vm::record,
            onSkip = vm::skip,
            onTeachSounds = onTeach,
            onRequestMic = permission.request,
            onOpenAppSettings = permission.openSettings,
            onEditKept = vm::editKept,
        ),
        meterLevel = { level.value.meterFraction },
    )
}

@Composable
private fun RecordingContent(vm: StepViewModel, state: StepState) {
    val loopBars = state.song?.loopBars ?: 2
    val live = remember(state.live, loopBars) { LiveView(state.live, loopBars, vm.playheads::recording) }
    RecordingScreen(
        state = state.toRecordingUi(vm.type),
        live = live,
        onCancel = vm::cancelRecording,
        onStop = vm::stopRecording,
    )
}

@Composable
private fun ReviewContent(vm: StepViewModel, state: StepState) {
    // While the screen fades out after "Try again" the candidate is already gone: keep the last UI.
    val last = remember { mutableStateOf<ReviewUi?>(null) }
    val ui = state.toReviewUi(vm.type, vm.sampleRate)?.also { last.value = it } ?: last.value ?: return
    val playhead = rememberFrameSample(active = state.reviewPlaying) { vm.playheads.review() }
    val review = vm.review
    val edits = review.edits
    ReviewScreen(
        state = ui,
        actions = ReviewActions(
            onBack = vm::leaveReview,
            onAb = review::setAb,
            onPlayToggle = review::togglePlay,
            onBar = review::selectBar,
            onToggleHit = edits::toggleHit,
            onTapNote = edits::tapNote,
            onTapRollCell = { row, step -> ui.rowPitch(row)?.let { edits.addNote(it, step) } },
            onDeleteNote = edits::deleteNote,
            onDeleteSelected = edits::deleteSelected,
            onUndo = edits::undo,
            onSound = review::selectSound,
            onQuantize = review::setQuantize,
            onQuantizeDone = review::refresh,
            onTryAgain = vm::tryAgain,
            onKeep = vm::keep,
        ),
        playhead = { playhead.value },
    )
}

private fun ReviewUi.rowPitch(row: Int): Int? {
    val pattern = pattern as? ReviewPattern.Notes ?: return null
    return pattern.bars.getOrNull(selectedBar)?.rowPitches?.getOrNull(row)
}

@Composable
private fun StepState.toRecordingUi(type: TrackType): RecordingUi {
    val p = progress
    val totalBars = song?.loopBars ?: 2
    val bar = p?.bar ?: 0
    val elapsed = (p?.elapsedMs ?: 0L) / MS_PER_SECOND
    val lane = when (type.kind) {
        TrackKind.DRUMS -> LiveLane.Drums(
            listOf(R.string.drum_kick, R.string.drum_snare, R.string.drum_hihat).map { stringResource(it) },
        )
        TrackKind.PITCHED -> LiveLane.Pitch
        TrackKind.RAW -> LiveLane.None
    }
    return RecordingUi(
        trackLabel = stringResource(type.labelRes()),
        bar = bar + 1,
        totalBars = totalBars,
        beat = p?.beat ?: -1,
        countIn = p?.countIn ?: true,
        lastBar = p != null && !p.countIn && bar == totalBars - 1,
        lane = lane,
        elapsed = stringResource(R.string.time_format, elapsed / SECONDS_PER_MINUTE, elapsed % SECONDS_PER_MINUTE),
        processing = phase == StepPhase.PROCESSING,
    )
}

private const val PHASE_MS = 220
private const val RECORDING_SCALE = 0.96f
private const val REVIEW_SLIDE = 12
private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
