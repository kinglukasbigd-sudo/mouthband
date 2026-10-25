package com.mouthband.ui.recording

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.audio.LiveTake
import com.mouthband.ui.components.BeatCounter
import com.mouthband.ui.components.LiveDrumLane
import com.mouthband.ui.components.LivePitchLane
import com.mouthband.ui.components.LiveWaveform
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.RecordButton
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.easeOut
import com.mouthband.ui.components.motionMs
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private val RecDotSize = 10.dp
private val SideSlotWidth = 96.dp
private const val HEADLINE_FADE_MS = 180

/** What the live lane under the waveform shows. */
@Immutable
sealed interface LiveLane {
    data class Drums(val rowLabels: List<String>) : LiveLane

    data object Pitch : LiveLane

    data object None : LiveLane
}

/**
 * The take being heard: [take] fills in as the user performs, [playheadFrame] returns the
 * loop-relative frame being heard. Both are read while drawing, once per display frame.
 */
@Immutable
class LiveView(val take: LiveTake?, val loopBars: Int, val playheadFrame: () -> Long)

@Immutable
data class RecordingUi(
    val trackLabel: String,
    val bar: Int,
    val totalBars: Int,
    val beat: Int,
    val countIn: Boolean,
    val lastBar: Boolean,
    val lane: LiveLane,
    val elapsed: String,
    val processing: Boolean = false,
)

/** Full-Indigo recording screen: bar counter, beat boxes, live waveform and live lane. */
@Composable
fun RecordingScreen(
    state: RecordingUi,
    live: LiveView,
    onCancel: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MbColors.Indigo), contentAlignment = Alignment.TopCenter) {
        RecordingContent(state, live, onCancel, onStop)
    }
}

@Composable
private fun RecordingContent(state: RecordingUi, live: LiveView, onCancel: () -> Unit, onStop: () -> Unit) {
    Column(
        Modifier
            .widthIn(max = MbDimens.ContentMaxWidth)
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = MbDimens.ScreenPadding, vertical = MbDimens.GapLarge),
        verticalArrangement = Arrangement.spacedBy(MbDimens.GapLarge),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(RecDotSize).clip(MbShapes.Circle).background(MbColors.Tangerine))
            Spacer(Modifier.width(MbDimens.GapSmall))
            MonoLabel(
                stringResource(R.string.rec_label, state.trackLabel),
                color = MbColors.OnIndigo,
                bold = true,
                modifier = Modifier.weight(1f),
            )
            if (!state.countIn) {
                MonoLabel(
                    stringResource(R.string.rec_bar_counter, state.bar, state.totalBars),
                    color = MbColors.OnIndigoMuted,
                    bold = true,
                )
            }
        }
        val headline = when {
            state.processing -> stringResource(R.string.rec_processing)
            state.countIn -> stringResource(R.string.rec_count_in)
            state.lastBar -> stringResource(R.string.rec_last_bar)
            else -> stringResource(R.string.rec_keep_going)
        }
        val fade = motionMs(HEADLINE_FADE_MS)
        AnimatedContent(
            targetState = headline,
            transitionSpec = { fadeIn(easeOut(fade)) togetherWith fadeOut(easeOut(fade)) },
            label = "headline",
        ) { text ->
            Text(
                text,
                style = MbTheme.type.h1,
                color = MbColors.OnIndigo,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        BeatCounter(
            currentBeat = state.beat,
            description = stringResource(R.string.rec_beat_description, state.beat + 1),
        )
        LiveWaveform(live.take, live.playheadFrame, stringResource(R.string.rec_waveform))
        LiveLaneView(state.lane, live)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(SideSlotWidth), contentAlignment = Alignment.CenterStart) {
                TextLinkButton(
                    stringResource(R.string.action_cancel),
                    onCancel,
                    color = MbColors.OnIndigo,
                    underline = false,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RecordButton(
                    recording = true,
                    contentDescription = stringResource(R.string.rec_stop),
                    onClick = onStop,
                    ringColor = MbColors.Cream,
                    enabled = !state.processing,
                )
            }
            Box(Modifier.width(SideSlotWidth), contentAlignment = Alignment.CenterEnd) {
                val timerLabel = stringResource(R.string.rec_timer_description, state.elapsed)
                MonoLabel(
                    state.elapsed,
                    color = MbColors.OnIndigo,
                    bold = true,
                    modifier = Modifier.semantics { contentDescription = timerLabel },
                )
            }
        }
        Spacer(Modifier.height(MbDimens.Grid))
    }
}

@Composable
private fun LiveLaneView(lane: LiveLane, live: LiveView) {
    when (lane) {
        is LiveLane.Drums -> LiveDrumLane(
            take = live.take,
            rowLabels = lane.rowLabels,
            loopBars = live.loopBars,
            playheadFrame = live.playheadFrame,
            description = stringResource(R.string.rec_live_hits),
        )
        LiveLane.Pitch -> LivePitchLane(
            take = live.take,
            loopBars = live.loopBars,
            playheadFrame = live.playheadFrame,
            description = stringResource(R.string.rec_live_notes),
        )
        LiveLane.None -> Unit
    }
}
