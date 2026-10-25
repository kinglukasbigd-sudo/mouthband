package com.mouthband.ui.coach

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mouthband.R
import com.mouthband.audio.LoopGrid
import com.mouthband.audio.LoopMath
import com.mouthband.audio.MicVerdict
import com.mouthband.convert.Quantizer
import com.mouthband.convert.TakeProblem
import com.mouthband.model.CoachStep
import com.mouthband.model.CoachTarget
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import com.mouthband.ui.common.coachCopy
import com.mouthband.ui.common.doneSteps
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.common.nameRes
import com.mouthband.ui.common.stepSegments
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.DrumGridModel
import com.mouthband.ui.components.LevelStatus
import com.mouthband.ui.review.ReviewPattern
import com.mouthband.ui.review.ReviewProblem
import com.mouthband.ui.review.ReviewUi
import com.mouthband.ui.theme.colors

/** Steps whose content exists, for the progress strip. */
fun StepState.doneSteps(): Set<CoachStep> = song?.let { doneSteps(it, tracks) }.orEmpty()

@Composable
fun StepState.toCoachUi(type: TrackType, mic: MicAccess, verdict: MicVerdict): CoachStepUi {
    val copy = type.coachCopy()
    val step = type.coachStep
    val error = when (error) {
        StepError.AUDIO_BUSY -> stringResource(R.string.error_audio_busy)
        StepError.MIC_BUSY -> stringResource(R.string.error_mic_busy)
        StepError.STORAGE -> stringResource(R.string.error_storage)
        null -> null
    }
    return CoachStepUi(
        songTitle = song?.title.orEmpty(),
        meta = song?.let { stringResource(R.string.bpm_key_format, it.bpm, it.key.label) }.orEmpty(),
        segments = stepSegments(doneSteps(), step),
        progressDescription = stringResource(R.string.step_progress_description, step.number),
        stepNumber = step.number,
        stepName = stringResource(if (type.isVocals) type.labelRes() else step.nameRes()),
        title = stringResource(copy.title),
        reason = stringResource(copy.reason),
        tips = copy.tips.map { stringResource(it) },
        levelStatus = verdict.toStatus(),
        canEditKept = tracks.any { it.type == type && it.hasContent } && !type.isVocals,
        showTeachLink = type == TrackType.BEAT,
        headphones = route.wiredHeadphones,
        examplePlaying = examplePlaying,
        mic = mic,
        busy = phase != StepPhase.COACH,
        error = error,
    )
}

/** "Keep it · next: …" label. */
@Composable
fun TrackType.nextLabel(): String = when (val next = nextAfterKeep) {
    is CoachTarget.Record -> stringResource(
        if (next.track.isVocals) next.track.labelRes() else next.track.coachStep.nameRes(),
    )
    CoachTarget.Arrange -> stringResource(R.string.step_name_arrange)
    CoachTarget.Export -> stringResource(R.string.step_name_export)
    CoachTarget.Vibe -> stringResource(R.string.step_name_vibe)
}

@Composable
fun StepState.toReviewUi(type: TrackType, sampleRate: Int): ReviewUi? {
    val song = song ?: return null
    val candidate = candidate ?: return null
    val colors = type.colors()
    val grid = LoopGrid(song.loopBars, song.bpm, sampleRate)
    val drumLabels = listOf(R.string.drum_kick, R.string.drum_snare, R.string.drum_hihat).map { stringResource(it) }
    val pattern = when (type.kind) {
        TrackKind.DRUMS -> ReviewPattern.Drums(drumBars(candidate, grid, drumLabels))
        TrackKind.PITCHED -> ReviewPattern.Notes(RollMapping.bars(candidate, type, song.key, grid))
        TrackKind.RAW -> ReviewPattern.Vocal(WaveformPeaks.of(candidate.rawLoop))
    }
    val sounds = when (type.kind) {
        TrackKind.DRUMS -> DrumKit.entries.map { ChipItem(it.name, stringResource(it.labelRes())) }
        TrackKind.PITCHED -> Instrument.forTrack(type).map { ChipItem(it.name, stringResource(it.labelRes())) }
        TrackKind.RAW -> emptyList()
    }
    return ReviewUi(
        trackLabel = stringResource(type.labelRes()),
        stepNumber = type.coachStep.number,
        trackColor = colors.fill,
        trackOnColor = colors.onFill,
        showAb = type.kind != TrackKind.RAW && candidate.rawLoop.isNotEmpty(),
        abConverted = candidate.abConverted,
        playing = reviewPlaying,
        pattern = pattern,
        bars = song.loopBars,
        selectedBar = candidate.selectedBar,
        sounds = sounds,
        selectedSound = if (type.kind == TrackKind.DRUMS) candidate.kit.name else candidate.instrument.name,
        quantize = candidate.quantize,
        nextLabel = type.nextLabel(),
        problem = candidate.problem?.toReviewProblem(),
        selectedNotes = selectedNotes(candidate, type),
        selection = selectionLabel(candidate, type),
        canUndo = candidate.undo.isNotEmpty(),
        editing = editingKept,
    )
}

/** The selected note's index, or every note of its chord on the chord track. */
private fun selectedNotes(candidate: Candidate, type: TrackType): Set<Int> {
    val notes = candidate.events?.notes.orEmpty()
    val selected = notes.getOrNull(candidate.selectedNote) ?: return emptySet()
    return if (type == TrackType.CHORDS) {
        notes.indices.filter { notes[it].startFrame == selected.startFrame }.toSet()
    } else {
        setOf(candidate.selectedNote)
    }
}

@Composable
private fun selectionLabel(candidate: Candidate, type: TrackType): String? {
    val notes = candidate.events?.notes ?: return null
    val selected = notes.getOrNull(candidate.selectedNote) ?: return null
    return if (type == TrackType.CHORDS) {
        val chord = notes.filter { it.startFrame == selected.startFrame }.sortedBy { it.midi }
        stringResource(R.string.review_chord_selected, chord.joinToString(" · ") { RollMapping.noteName(it.midi) })
    } else {
        stringResource(R.string.review_note_selected, RollMapping.noteName(selected.midi))
    }
}


private fun drumBars(candidate: Candidate, grid: LoopGrid, labels: List<String>): List<DrumGridModel> {
    val cells = List(grid.bars) { List(DrumClass.entries.size) { BooleanArray(LoopMath.STEPS_PER_BAR) } }
    val hits = candidate.events?.hits.orEmpty()
    Quantizer.hits(hits, grid.stepFrames, candidate.quantize, grid.loopFrames).forEach { hit ->
        val cell = Quantizer.cell(hit.frameInLoop, grid.stepFrames, grid.totalSteps)
        cells[cell / LoopMath.STEPS_PER_BAR][hit.cls.ordinal][cell % LoopMath.STEPS_PER_BAR] = true
    }
    return cells.map { DrumGridModel(it, labels) }
}

private fun TakeProblem.toReviewProblem(): ReviewProblem = when (this) {
    TakeProblem.SILENCE -> ReviewProblem.SILENCE
    TakeProblem.NO_HITS -> ReviewProblem.NO_HITS
    TakeProblem.NO_PITCH -> ReviewProblem.NO_PITCH
}

fun MicVerdict.toStatus(): LevelStatus = when (this) {
    MicVerdict.TOO_QUIET -> LevelStatus.TOO_QUIET
    MicVerdict.GOOD -> LevelStatus.GOOD
    MicVerdict.TOO_LOUD -> LevelStatus.TOO_LOUD
}
