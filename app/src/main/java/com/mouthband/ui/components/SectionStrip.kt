package com.mouthband.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private val BlockGap = 3.dp
private val TrackBarHeight = 3.dp
private val BlockMinHeight = 76.dp
private val BlockPadding = 4.dp
private val LabelMinSize = 9.sp
private val LabelStep = 0.5.sp

/** One block of the arrangement strip. [trackColors] shows which tracks play in it. */
@Immutable
data class SectionBlock(
    val label: String,
    val bars: Int,
    val trackColors: List<Color>,
    val description: String,
)

/** Intro/Verse/Chorus blocks with widths proportional to their bars. Tap opens the section. */
@Composable
fun SectionStrip(
    blocks: List<SectionBlock>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    playingIndex: Int = -1,
    onSectionClick: (Int) -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
        blocks.forEachIndexed { index, block ->
            val playing = index == playingIndex
            val fill by animateColorAsState(
                if (playing) MbColors.Indigo else MbColors.Paper,
                motionTween(),
                label = "section",
            )
            val ink by animateColorAsState(
                if (playing) MbColors.OnIndigo else MbColors.Ink,
                motionTween(),
                label = "sectionInk",
            )
            Column(
                modifier = Modifier
                    .weight(block.bars.toFloat().coerceAtLeast(1f))
                    .heightIn(min = BlockMinHeight)
                    .background(fill)
                    .border(if (index == selectedIndex) MbDimens.BorderActive else MbDimens.Border, MbColors.Ink)
                    .clickable(role = Role.Button, onClick = { onSectionClick(index) })
                    .semantics { contentDescription = block.description }
                    .padding(BlockPadding),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Short sections are narrow (widths follow bars): the label shrinks to fit.
                BasicText(
                    block.label,
                    style = MbTheme.type.label.copy(color = ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = LabelMinSize,
                        maxFontSize = MbTheme.type.label.fontSize,
                        stepSize = LabelStep,
                    ),
                )
                MonoLabel(
                    "${block.bars}",
                    small = true,
                    color = if (playing) MbColors.OnIndigoMuted else MbColors.Muted,
                )
                Box(Modifier.weight(1f, fill = false))
                block.trackColors.forEach { color ->
                    Box(Modifier.fillMaxWidth().height(TrackBarHeight).background(color))
                }
            }
        }
    }
}
