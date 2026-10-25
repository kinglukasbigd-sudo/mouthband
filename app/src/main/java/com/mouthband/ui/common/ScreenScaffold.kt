package com.mouthband.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mouthband.model.CoachStep
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.colors

/**
 * Standard screen frame: Cream background, edge-to-edge insets, 24 dp side padding, a
 * scrolling body and a fixed bottom area (buttons stay reachable at large font sizes).
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    background: Color = MbColors.Cream,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = MbDimens.ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MbDimens.ScreenPadding, vertical = MbDimens.GapLarge),
            verticalArrangement = Arrangement.spacedBy(MbDimens.Gap),
            content = content,
        )
        if (bottomBar != null) {
            Column(
                modifier = Modifier
                    .widthIn(max = MbDimens.ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = MbDimens.ScreenPadding, vertical = MbDimens.GapLarge),
                verticalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
                content = bottomBar,
            )
        }
    }
}

/** Identity color of a finished coach step in the progress strip. */
fun CoachStep.doneColor(): Color = when (this) {
    CoachStep.BEAT -> TrackType.BEAT.colors().fill
    CoachStep.BASS -> TrackType.BASS.colors().fill
    CoachStep.CHORDS -> TrackType.CHORDS.colors().fill
    CoachStep.MELODY -> TrackType.MELODY.colors().fill
    CoachStep.VOCALS -> TrackType.VOCALS_VERSE.colors().fill
    CoachStep.VIBE, CoachStep.ARRANGE, CoachStep.EXPORT -> MbColors.Indigo
}

/** Coach steps that have content, for the progress strip. */
fun doneSteps(song: Song, tracks: List<Track>, exported: Boolean = false): Set<CoachStep> = buildSet {
    if (song.coachStep.number > CoachStep.VIBE.number) add(CoachStep.VIBE)
    tracks.filter { it.hasContent }.forEach { add(it.type.coachStep) }
    if (song.coachStep.number > CoachStep.ARRANGE.number) add(CoachStep.ARRANGE)
    if (exported) add(CoachStep.EXPORT)
}

/**
 * Builds the 8 strip segments. [done] holds the steps with content; [current] is highlighted
 * (null when no step is current, e.g. on the Home card of a finished song).
 */
fun stepSegments(done: Set<CoachStep>, current: CoachStep?): List<StepSegment> =
    CoachStep.entries.map { step ->
        when {
            step == current -> StepSegment.Current
            step in done -> StepSegment.Done(step.doneColor())
            else -> StepSegment.Empty
        }
    }
