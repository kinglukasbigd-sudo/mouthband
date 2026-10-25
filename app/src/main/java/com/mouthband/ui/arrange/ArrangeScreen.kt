package com.mouthband.ui.arrange

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.model.TrackType
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.Hairline
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.MonoPill
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.components.SectionBlock
import com.mouthband.ui.components.SectionStrip
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.components.TrackRow
import com.mouthband.ui.components.motionTween
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private const val STEP_NUMBER = 7

/** One mixer row. [volume] is fader travel 0..1. */
@Immutable
data class TrackMixUi(
    val type: TrackType,
    val name: String,
    val sound: String,
    val volume: Float,
    val gainText: String,
    val muted: Boolean,
    val hasContent: Boolean,
    val color: Color,
)

/** Sound / pan / hear / re-record sheet of one track. */
@Immutable
data class TrackSheetUi(
    val type: TrackType,
    val title: String,
    val color: Color,
    val sounds: List<ChipItem>,
    val selectedSound: String?,
    val pan: Float,
    val panText: String,
    val hearing: Boolean,
    val preparing: Boolean,
    val hasContent: Boolean,
    val canEditNotes: Boolean,
)

/** Section toggle sheet content. */
@Immutable
data class SectionSheetUi(val index: Int, val title: String, val tracks: List<SectionToggleUi>)

@Immutable
data class SectionToggleUi(
    val type: TrackType,
    val name: String,
    val color: Color,
    val on: Boolean,
    val enabled: Boolean,
)

@Immutable
data class ArrangeUi(
    val songTitle: String = "",
    val meta: String = "",
    val segments: List<StepSegment> = emptyList(),
    val progressDescription: String = "",
    val blocks: List<SectionBlock> = emptyList(),
    val tracks: List<TrackMixUi> = emptyList(),
    val autoMixOn: Boolean = false,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val playingSection: Int = -1,
    val sheet: SectionSheetUi? = null,
    val trackSheet: TrackSheetUi? = null,
    val empty: Boolean = false,
    val message: String? = null,
)

@Immutable
data class ArrangeActions(
    val onBack: () -> Unit,
    val onSectionClick: (Int) -> Unit,
    val onDismissSheet: () -> Unit,
    val onToggleTrack: (section: Int, type: TrackType, on: Boolean) -> Unit,
    val onVolume: (TrackType, Float) -> Unit,
    val onVolumeDone: (TrackType) -> Unit,
    val onMute: (TrackType) -> Unit,
    val onAutoMix: () -> Unit,
    val onExport: () -> Unit,
    val onPlayToggle: () -> Unit,
    val onEditTrack: (TrackType) -> Unit = {},
    val onRecordTrack: (TrackType) -> Unit = {},
    val onOpenTrack: (TrackType) -> Unit = {},
    val onDismissTrack: () -> Unit = {},
    val onSelectSound: (TrackType, String) -> Unit = { _, _ -> },
    val onHearTrack: (TrackType) -> Unit = {},
    val onStopHearing: () -> Unit = {},
    val onPan: (TrackType, Float) -> Unit = { _, _ -> },
    val onPanDone: (TrackType) -> Unit = {},
)

/** Coach step 7: section strip, mixer and auto-mix. */
@Composable
fun ArrangeScreen(state: ArrangeUi, actions: ArrangeActions, modifier: Modifier = Modifier) {
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            if (!state.empty) {
                SecondaryPillButton(
                    text = stringResource(
                        if (state.autoMixOn) R.string.arrange_auto_mix_on else R.string.arrange_auto_mix,
                    ),
                    onClick = actions.onAutoMix,
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (state.autoMixOn) MbIcons.Check else MbIcons.Wave,
                )
                PrimaryPillButton(
                    stringResource(R.string.arrange_export),
                    actions.onExport,
                    Modifier.fillMaxWidth(),
                    showArrow = true,
                )
            }
        },
    ) {
        TopBar(
            backLabel = stringResource(R.string.action_back),
            onBack = actions.onBack,
            title = state.songTitle,
            trailing = { MonoPill(state.meta) },
        )
        StepProgressStrip(state.segments, state.progressDescription)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
        ) {
            HighlighterTag(stringResource(R.string.step_tag, STEP_NUMBER))
            MonoLabel(stringResource(R.string.step_name_arrange), color = MbColors.Ink, bold = true)
        }
        Text(
            stringResource(R.string.step_name_arrange),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.arrange_reason), style = MbTheme.type.body, color = MbColors.InkSoft)
        if (state.empty) {
            EmptyState(
                title = stringResource(R.string.arrange_empty_title),
                body = stringResource(R.string.arrange_empty_body),
                actionLabel = stringResource(R.string.arrange_empty_action),
                onAction = actions.onBack,
            )
            return@ScreenScaffold
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            MonoLabel(stringResource(R.string.arrange_sections), modifier = Modifier.weight(1f))
            if (state.preparing) MonoLabel(stringResource(R.string.arrange_preparing), small = true)
            IconCircleButton(
                icon = if (state.playing) MbIcons.Stop else MbIcons.Play,
                contentDescription = stringResource(
                    if (state.playing) R.string.arrange_stop_song else R.string.arrange_play_song,
                ),
                onClick = actions.onPlayToggle,
                background = MbColors.Tangerine,
                tint = MbColors.Indigo,
                framed = false,
            )
        }
        SectionStrip(
            blocks = state.blocks,
            playingIndex = state.playingSection,
            onSectionClick = actions.onSectionClick,
        )
        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(motionTween()),
            exit = fadeOut(motionTween()),
        ) {
            Text(
                state.message.orEmpty(),
                style = MbTheme.type.label,
                color = MbColors.Indigo,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        MonoLabel(stringResource(R.string.arrange_tracks))
        FramedCard(Modifier.fillMaxWidth()) {
            state.tracks.forEachIndexed { index, track ->
                if (index > 0) Hairline()
                TrackRow(
                    color = track.color,
                    name = track.name,
                    soundName = if (track.hasContent) track.sound else stringResource(R.string.track_not_recorded),
                    volume = track.volume,
                    volumeText = track.gainText,
                    muted = track.muted,
                    muteLabel = stringResource(R.string.mute),
                    unmuteLabel = stringResource(R.string.unmute),
                    volumeLabel = stringResource(R.string.volume),
                    onVolumeChange = { actions.onVolume(track.type, it) },
                    onVolumeChangeFinished = { actions.onVolumeDone(track.type) },
                    onMuteToggle = { actions.onMute(track.type) },
                    enabled = track.hasContent,
                    openLabel = stringResource(R.string.arrange_open_track),
                    onOpen = { actions.onOpenTrack(track.type) },
                    editLabel = stringResource(R.string.arrange_edit_track),
                    onEdit = if (track.hasContent && !track.type.isVocals) {
                        { actions.onEditTrack(track.type) }
                    } else {
                        null
                    },
                )
            }
        }
    }
    state.sheet?.let { SectionSheet(it, actions) }
    state.trackSheet?.let { TrackSheet(it, actions) }
}
