package com.mouthband.ui.vibe

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.mouthband.R
import com.mouthband.model.KeyMode
import com.mouthband.model.Mood
import com.mouthband.model.MusicalKey
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.ChipRow
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.MbSlider
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.MonoPill
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.components.motionTween
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private const val GRID_COLUMNS = 2
private const val STEP_NUMBER = 1

@Immutable
data class VibeUiState(
    val songTitle: String = "",
    val segments: List<StepSegment> = emptyList(),
    val progressDescription: String = "",
    val selectedMood: Mood? = null,
    val bpm: Int = Mood.POP.bpm,
    val key: MusicalKey = Mood.POP.key,
    val loopBars: Int = 2,
    val locked: Boolean = false,
    val tapCount: Int = 0,
)

@Immutable
data class VibeActions(
    val onBack: () -> Unit,
    val onMood: (Mood) -> Unit,
    val onTap: () -> Unit,
    val onBpm: (Int) -> Unit,
    val onKeyRoot: (Int) -> Unit,
    val onKeyMode: (KeyMode) -> Unit,
    val onLoopBars: (Int) -> Unit,
    val onNext: () -> Unit,
)

/** Coach step 1: five mood cards, tap tempo and manual tempo/key/loop pickers. */
@Composable
fun VibeScreen(state: VibeUiState, actions: VibeActions, modifier: Modifier = Modifier) {
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            PrimaryPillButton(
                stringResource(R.string.vibe_next),
                actions.onNext,
                Modifier.fillMaxWidth(),
                showArrow = true,
            )
        },
    ) {
        TopBar(
            backLabel = stringResource(R.string.action_back),
            onBack = actions.onBack,
            title = state.songTitle,
            trailing = { MonoPill(stringResource(R.string.bpm_key_format, state.bpm, state.key.label)) },
        )
        StepProgressStrip(state.segments, state.progressDescription)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
        ) {
            HighlighterTag(stringResource(R.string.step_tag, STEP_NUMBER))
            MonoLabel(stringResource(R.string.step_name_vibe), color = MbColors.Ink, bold = true)
        }
        Text(
            stringResource(R.string.vibe_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.vibe_reason), style = MbTheme.type.body, color = MbColors.InkSoft)
        MoodGrid(state, actions)
        if (state.locked) {
            Text(stringResource(R.string.vibe_locked), style = MbTheme.type.bodySm, color = MbColors.Muted)
        } else {
            TempoControls(state, actions)
            KeyControls(state, actions)
            MonoLabel(stringResource(R.string.vibe_loop))
            SegmentedToggle(
                options = listOf(stringResource(R.string.vibe_loop_2), stringResource(R.string.vibe_loop_4)),
                selectedIndex = if (state.loopBars == 2) 0 else 1,
                onSelect = { actions.onLoopBars(if (it == 0) 2 else 4) },
            )
            // Said up front: these settings cannot change once a take is kept.
            Text(stringResource(R.string.vibe_lock_hint), style = MbTheme.type.bodySm, color = MbColors.Muted)
        }
    }
}

@Composable
private fun MoodGrid(state: VibeUiState, actions: VibeActions) {
    val selectedWord = stringResource(R.string.vibe_selected)
    Mood.entries.chunked(GRID_COLUMNS).forEach { rowMoods ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap)) {
            rowMoods.forEach { mood ->
                val selected = mood == state.selectedMood
                val fill by animateColorAsState(
                    if (selected) MbColors.Indigo else MbColors.Paper,
                    motionTween(),
                    label = "mood",
                )
                val title by animateColorAsState(
                    if (selected) MbColors.OnIndigo else MbColors.Indigo,
                    motionTween(),
                    label = "moodTitle",
                )
                val meta by animateColorAsState(
                    if (selected) MbColors.OnIndigoMuted else MbColors.Muted,
                    motionTween(),
                    label = "moodMeta",
                )
                FramedCard(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { if (selected) stateDescription = selectedWord },
                    active = selected,
                    background = fill,
                    onClick = if (state.locked) null else ({ actions.onMood(mood) }),
                ) {
                    Text(stringResource(mood.labelRes()), style = MbTheme.type.h2, color = title)
                    Spacer(Modifier.height(MbDimens.Grid))
                    MonoLabel(
                        stringResource(R.string.bpm_key_format, mood.bpm, mood.key.label),
                        color = meta,
                        small = true,
                    )
                }
            }
            if (rowMoods.size < GRID_COLUMNS) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TempoControls(state: VibeUiState, actions: VibeActions) {
    MonoLabel(stringResource(R.string.vibe_tempo))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap)) {
        IconCircleButton(MbIcons.Back, stringResource(R.string.vibe_slower), { actions.onBpm(state.bpm - 1) })
        Text(
            stringResource(R.string.bpm_format, state.bpm),
            style = MbTheme.type.h2,
            color = MbColors.Indigo,
            modifier = Modifier.weight(1f),
        )
        IconCircleButton(MbIcons.ArrowRight, stringResource(R.string.vibe_faster), { actions.onBpm(state.bpm + 1) })
    }
    MbSlider(
        value = state.bpm.toFloat(),
        onValueChange = { actions.onBpm(it.toInt()) },
        description = stringResource(R.string.vibe_tempo),
        valueText = stringResource(R.string.bpm_format, state.bpm),
        range = Mood.MIN_BPM.toFloat()..Mood.MAX_BPM.toFloat(),
        fillColor = MbColors.Indigo,
    )
    SecondaryPillButton(
        text = stringResource(R.string.vibe_tap_tempo),
        onClick = actions.onTap,
        modifier = Modifier.fillMaxWidth(),
        icon = MbIcons.Wave,
    )
    Text(stringResource(R.string.vibe_tap_hint), style = MbTheme.type.bodySm, color = MbColors.Muted)
}

@Composable
private fun KeyControls(state: VibeUiState, actions: VibeActions) {
    MonoLabel(stringResource(R.string.vibe_key))
    ChipRow(
        items = (0 until MusicalKey.SEMITONES).map { ChipItem(it.toString(), MusicalKey.rootName(it)) },
        selectedId = state.key.root.toString(),
        onSelect = { actions.onKeyRoot(it.toInt()) },
    )
    SegmentedToggle(
        options = listOf(stringResource(R.string.vibe_major), stringResource(R.string.vibe_minor)),
        selectedIndex = if (state.key.mode == KeyMode.MAJOR) 0 else 1,
        onSelect = { actions.onKeyMode(if (it == 0) KeyMode.MAJOR else KeyMode.MINOR) },
    )
}
