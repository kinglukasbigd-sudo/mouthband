package com.mouthband.ui.arrange

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.R
import com.mouthband.mix.Fader
import com.mouthband.model.CoachStep
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Section
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.doneSteps
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.common.stepSegments
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.SectionBlock
import com.mouthband.ui.theme.colors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Coach step 7 with real data: [ArrangeViewModel] behind [ArrangeScreen]. */
@Composable
fun ArrangeDestination(
    songId: Long,
    onExport: () -> Unit,
    onEditTrack: (TrackType) -> Unit,
    onRecordTrack: (TrackType) -> Unit,
    onBack: () -> Unit,
) {
    val vm = appViewModel(key = "arrange-$songId") { c, _ ->
        ArrangeViewModel(songId, c.repository, c.trackRepository, c.newSongPlayer())
    }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(vm) { onPauseOrDispose { vm.stop() } }
    ArrangeScreen(
        state = state.toUi(),
        actions = ArrangeActions(
            onBack = onBack,
            onSectionClick = vm::openSection,
            onDismissSheet = vm::closeSection,
            onToggleTrack = vm::toggleTrack,
            onVolume = vm::setVolume,
            onVolumeDone = vm::volumeDone,
            onMute = vm::toggleMute,
            onAutoMix = vm::toggleAutoMix,
            onExport = {
                vm.stop()
                onExport()
            },
            onPlayToggle = vm::togglePlay,
            onEditTrack = { type ->
                vm.stop()
                vm.trackSheet.close()
                onEditTrack(type)
            },
            onRecordTrack = { type ->
                vm.stop()
                vm.trackSheet.close()
                onRecordTrack(type)
            },
            onOpenTrack = vm.trackSheet::open,
            onDismissTrack = vm.trackSheet::close,
            onSelectSound = vm.trackSheet::selectSound,
            onHearTrack = vm.trackSheet::audition,
            onStopHearing = vm::stop,
            onPan = vm.trackSheet::setPan,
            onPanDone = vm.trackSheet::panDone,
        ),
    )
}

@Composable
private fun ArrangeState.toUi(): ArrangeUi {
    val song = song ?: return ArrangeUi()
    val recorded = tracks.filter { it.hasContent }.map { it.type }.toSet()
    return ArrangeUi(
        songTitle = song.title,
        meta = stringResource(R.string.bpm_key_format, song.bpm, song.key.label),
        segments = stepSegments(doneSteps(song, tracks), CoachStep.ARRANGE),
        progressDescription = stringResource(R.string.step_progress_description, CoachStep.ARRANGE.number),
        blocks = song.arrangement.sections.map { it.toBlock(recorded) },
        tracks = TrackType.entries.mapNotNull { type -> tracks.firstOrNull { it.type == type } }
            .map { it.toMixUi(song, faders[it.type]) },
        autoMixOn = song.arrangement.autoMix,
        playing = playing,
        preparing = preparing,
        playingSection = playingSection,
        sheet = sheetIndex?.let { index ->
            song.arrangement.sections.getOrNull(index)?.let { it.toSheet(index, recorded) }
        },
        trackSheet = trackSheet?.let { type -> toTrackSheet(type, song) },
        empty = empty,
        message = when (problem) {
            PlaybackProblem.NOTHING_AUDIBLE -> stringResource(R.string.arrange_nothing_audible)
            PlaybackProblem.AUDIO_BUSY -> stringResource(R.string.error_audio_busy)
            null -> null
        },
    )
}

@Composable
private fun Section.toBlock(recorded: Set<TrackType>): SectionBlock {
    val label = stringResource(type.labelRes())
    val playing = TrackType.entries.filter { it in active && it in recorded }
    return SectionBlock(
        label = label,
        bars = bars,
        trackColors = playing.map { it.colors().fill },
        description = stringResource(
            R.string.section_description,
            label,
            pluralStringResource(R.plurals.section_bars, bars, bars),
            pluralStringResource(R.plurals.section_parts_playing, playing.size, playing.size),
        ),
    )
}

@Composable
private fun Section.toSheet(index: Int, recorded: Set<TrackType>): SectionSheetUi = SectionSheetUi(
    index = index,
    title = stringResource(type.labelRes()),
    tracks = TrackType.entries.map { type ->
        SectionToggleUi(
            type = type,
            name = stringResource(type.labelRes()),
            color = type.colors().fill,
            on = type in active,
            enabled = type in recorded,
        )
    },
)

@Composable
private fun Track.toMixUi(song: Song, dragged: Float?): TrackMixUi {
    val position = dragged ?: Fader.toPosition(gainDb)
    val sound = if (type.kind == TrackKind.DRUMS) song.kit.labelRes() else instrument.labelRes()
    return TrackMixUi(
        type = type,
        name = stringResource(type.labelRes()),
        sound = stringResource(sound),
        volume = position,
        gainText = stringResource(R.string.gain_value, Fader.toDb(position)),
        muted = muted,
        hasContent = hasContent,
        color = type.colors().fill,
    )
}

@Composable
private fun ArrangeState.toTrackSheet(type: TrackType, song: Song): TrackSheetUi? {
    val track = tracks.firstOrNull { it.type == type } ?: return null
    val sounds = when (type.kind) {
        TrackKind.DRUMS -> DrumKit.entries.map { ChipItem(it.name, stringResource(it.labelRes())) }
        TrackKind.PITCHED -> Instrument.forTrack(type).map { ChipItem(it.name, stringResource(it.labelRes())) }
        TrackKind.RAW -> emptyList()
    }
    val selected = if (type.kind == TrackKind.DRUMS) song.kit.name else track.instrument.name
    val pan = pans[type] ?: track.pan
    return TrackSheetUi(
        type = type,
        title = stringResource(type.labelRes()),
        color = type.colors().fill,
        sounds = sounds,
        selectedSound = selected,
        pan = pan,
        panText = panText(pan),
        hearing = auditioning == type,
        preparing = preparing,
        hasContent = track.hasContent,
        canEditNotes = track.hasContent && !type.isVocals,
    )
}

@Composable
private fun panText(pan: Float): String {
    val percent = (abs(pan) * PERCENT).roundToInt()
    return when {
        percent == 0 -> stringResource(R.string.pan_centre)
        pan < 0f -> stringResource(R.string.pan_left, percent)
        else -> stringResource(R.string.pan_right, percent)
    }
}

private const val PERCENT = 100
