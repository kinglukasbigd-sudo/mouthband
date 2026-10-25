package com.mouthband.ui.coach

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.Hairline
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.LevelMeter
import com.mouthband.ui.components.LevelStatus
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.MonoPill
import com.mouthband.ui.components.RecordButton
import com.mouthband.ui.components.StatusChip
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private val TipNumberSize = 28.dp
private val SideSlotWidth = 96.dp

/** Microphone access as seen by a recording step. */
enum class MicAccess { GRANTED, NEEDS_REQUEST, DENIED }

@Immutable
data class CoachStepUi(
    val songTitle: String,
    val meta: String,
    val segments: List<StepSegment>,
    val progressDescription: String,
    val stepNumber: Int,
    val stepName: String,
    val title: String,
    val reason: String,
    val tips: List<String>,
    val levelStatus: LevelStatus,
    val showTeachLink: Boolean,
    val headphones: Boolean,
    val examplePlaying: Boolean,
    val mic: MicAccess,
    val busy: Boolean = false,
    val error: String? = null,
    /** A take of this step was kept earlier: offer to edit its notes, chords or hits. */
    val canEditKept: Boolean = false,
)

@Immutable
data class CoachStepActions(
    val onBack: () -> Unit,
    val onHearExample: () -> Unit,
    val onRecord: () -> Unit,
    val onSkip: () -> Unit,
    val onTeachSounds: () -> Unit,
    val onRequestMic: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onEditKept: () -> Unit = {},
)

/** Coach steps 2–6 before recording: why, how, a live mic meter and the record button. */
@Composable
fun CoachStepScreen(
    state: CoachStepUi,
    actions: CoachStepActions,
    modifier: Modifier = Modifier,
    meterLevel: () -> Float = { 0f },
) {
    ScreenScaffold(modifier = modifier, bottomBar = { CoachBottomRow(state, actions) }) {
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
            HighlighterTag(stringResource(R.string.step_tag, state.stepNumber))
            MonoLabel(state.stepName, color = MbColors.Ink, bold = true)
        }
        Text(state.title, style = MbTheme.type.h1, color = MbColors.Indigo, modifier = Modifier.semantics { heading() })
        Text(state.reason, style = MbTheme.type.body, color = MbColors.InkSoft)
        HowToCard(state, actions)
        if (state.canEditKept) {
            TextLinkButton(stringResource(R.string.coach_edit_kept), actions.onEditKept)
        }
        state.error?.let { EmptyState(title = it, body = "", icon = MbIcons.Alert) }
        when (state.mic) {
            MicAccess.GRANTED -> MicLevelRow(state, meterLevel)
            MicAccess.NEEDS_REQUEST -> EmptyState(
                title = stringResource(R.string.mic_rationale_title),
                body = stringResource(R.string.mic_rationale_body),
                icon = MbIcons.Mic,
                actionLabel = stringResource(R.string.mic_allow),
                onAction = actions.onRequestMic,
            )
            MicAccess.DENIED -> EmptyState(
                title = stringResource(R.string.mic_denied_title),
                body = stringResource(R.string.mic_denied_body),
                icon = MbIcons.Mic,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = actions.onOpenAppSettings,
            )
        }
        if (state.headphones) {
            StatusChip(stringResource(R.string.coach_headphones), MbIcons.Headphones)
        }
    }
}

@Composable
private fun HowToCard(state: CoachStepUi, actions: CoachStepActions) {
    FramedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MbDimens.TouchTarget)
                .clickable(role = Role.Button, onClick = actions.onHearExample),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            Box(
                Modifier.size(MbDimens.MuteButton).border(MbDimens.Border, MbColors.Ink, MbShapes.Circle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.examplePlaying) MbIcons.Stop else MbIcons.Play,
                    contentDescription = null,
                    tint = MbColors.Ink,
                )
            }
            Text(
                stringResource(if (state.examplePlaying) R.string.coach_stop_example else R.string.coach_hear_example),
                style = MbTheme.type.label,
                color = MbColors.Ink,
            )
        }
        Hairline(Modifier.padding(vertical = MbDimens.Gap))
        MonoLabel(stringResource(R.string.coach_how_to))
        state.tips.forEachIndexed { index, tip ->
            Row(
                modifier = Modifier.padding(top = MbDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(TipNumberSize).border(MbDimens.Border, MbColors.Ink, MbShapes.Circle),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoLabel("${index + 1}", color = MbColors.Ink, bold = true)
                }
                Text(tip, style = MbTheme.type.bodySm, color = MbColors.InkSoft, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MicLevelRow(state: CoachStepUi, meterLevel: () -> Float) {
    val statusText = stringResource(
        when (state.levelStatus) {
            LevelStatus.TOO_QUIET -> R.string.level_too_quiet
            LevelStatus.GOOD -> R.string.level_good
            LevelStatus.TOO_LOUD -> R.string.level_too_loud
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap)) {
        MonoLabel(stringResource(R.string.coach_mic))
        LevelMeter(
            level = meterLevel,
            description = stringResource(R.string.level_meter_description, statusText),
            modifier = Modifier.weight(1f),
        )
        Text(
            statusText,
            style = MbTheme.type.label,
            color = if (state.levelStatus == LevelStatus.GOOD) MbColors.Ink else MbColors.Indigo,
        )
    }
}

@Composable
private fun CoachBottomRow(state: CoachStepUi, actions: CoachStepActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(SideSlotWidth), contentAlignment = Alignment.CenterStart) {
            if (state.showTeachLink) TextLinkButton(stringResource(R.string.coach_teach_link), actions.onTeachSounds)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (state.mic == MicAccess.GRANTED) {
                RecordButton(
                    recording = false,
                    contentDescription = stringResource(R.string.coach_record),
                    onClick = actions.onRecord,
                    enabled = !state.busy,
                )
            } else {
                IndigoPillButton(stringResource(R.string.mic_allow), actions.onRequestMic)
            }
        }
        Box(Modifier.width(SideSlotWidth), contentAlignment = Alignment.CenterEnd) {
            TextLinkButton(
                stringResource(R.string.coach_skip),
                actions.onSkip,
                underline = false,
                color = MbColors.Muted,
            )
        }
    }
}
