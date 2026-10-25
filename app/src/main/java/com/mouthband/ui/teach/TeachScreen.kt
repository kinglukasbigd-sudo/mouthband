package com.mouthband.ui.teach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.components.Waveform
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

const val TEACH_REPEATS = 4
private val ProgressBarHeight = 6.dp
private val ProgressGap = 4.dp

/** Teach flow phase. */
enum class TeachPhase { READY, LISTENING, DONE }

@Immutable
data class TeachUi(
    val counts: List<Int> = listOf(0, 0, 0),
    val activeIndex: Int = -1,
    val phase: TeachPhase = TeachPhase.READY,
    val micDenied: Boolean = false,
)

@Immutable
data class TeachActions(
    val onBack: () -> Unit,
    val onStart: () -> Unit,
    val onDone: () -> Unit,
    val onReset: () -> Unit,
    val onOpenAppSettings: () -> Unit = {},
)

/** Beatbox calibration: say kick, snare and hi-hat 4 times each. */
@Composable
fun TeachScreen(
    state: TeachUi,
    actions: TeachActions,
    modifier: Modifier = Modifier,
    peaks: () -> FloatArray = { FloatArray(0) },
) {
    val names = listOf(R.string.teach_kick, R.string.teach_snare, R.string.teach_hihat)
        .map { stringResource(it) }
    val sayings = listOf(R.string.teach_say_kick, R.string.teach_say_snare, R.string.teach_say_hihat)
        .map { stringResource(it) }
    ScreenScaffold(
        modifier = modifier,
        bottomBar = {
            if (!state.micDenied) TeachButton(state.phase, actions)
            TextLinkButton(
                stringResource(R.string.teach_reset),
                actions.onReset,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                underline = false,
                color = MbColors.Muted,
            )
        },
    ) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = actions.onBack)
        Text(
            stringResource(R.string.teach_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.teach_reason), style = MbTheme.type.body, color = MbColors.InkSoft)
        if (state.micDenied) {
            EmptyState(
                title = stringResource(R.string.mic_denied_title),
                body = stringResource(R.string.mic_denied_body),
                icon = MbIcons.Mic,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = actions.onOpenAppSettings,
            )
            return@ScreenScaffold
        }
        names.forEachIndexed { index, name ->
            SoundCard(name, sayings[index], state.counts.getOrElse(index) { 0 }, active = index == state.activeIndex)
        }
        Waveform(
            peaks = peaks,
            description = stringResource(R.string.rec_waveform),
            color = MbColors.Indigo,
            height = 56.dp,
        )
        val prompt = when (state.phase) {
            TeachPhase.READY -> stringResource(R.string.teach_ready)
            TeachPhase.DONE -> stringResource(R.string.teach_all_done)
            TeachPhase.LISTENING -> {
                val index = state.activeIndex.coerceIn(0, sayings.lastIndex)
                stringResource(
                    R.string.teach_listening,
                    sayings[index],
                    (state.counts[index] + 1).coerceAtMost(TEACH_REPEATS),
                )
            }
        }
        Text(
            prompt,
            style = MbTheme.type.label,
            color = MbColors.Ink,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SoundCard(name: String, saying: String, count: Int, active: Boolean) {
    val description = stringResource(R.string.teach_card_description, name, count)
    FramedCard(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        active = active,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, style = MbTheme.type.h2, color = MbColors.Indigo, modifier = Modifier.weight(1f))
            MonoLabel(saying)
        }
        Spacer(Modifier.height(MbDimens.Gap))
        Row(horizontalArrangement = Arrangement.spacedBy(ProgressGap)) {
            repeat(TEACH_REPEATS) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(ProgressBarHeight)
                        .background(if (i < count) MbColors.Indigo else MbColors.Rule),
                )
            }
        }
    }
}

@Composable
private fun TeachButton(phase: TeachPhase, actions: TeachActions) {
    when (phase) {
        TeachPhase.READY -> PrimaryPillButton(
            stringResource(R.string.teach_start),
            actions.onStart,
            Modifier.fillMaxWidth(),
            showArrow = true,
        )
        TeachPhase.LISTENING -> IndigoPillButton(
            stringResource(R.string.action_cancel),
            actions.onBack,
            Modifier.fillMaxWidth(),
        )
        TeachPhase.DONE -> IndigoPillButton(
            stringResource(R.string.action_done),
            actions.onDone,
            Modifier.fillMaxWidth(),
        )
    }
}
