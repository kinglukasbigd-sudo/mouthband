package com.mouthband.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens

private val SegmentGap = 4.dp
private val SegmentBorder = 1.dp

/** State of one of the 8 coach step segments. */
@Immutable
sealed interface StepSegment {
    data class Done(val color: Color) : StepSegment

    data object Current : StepSegment

    data object Empty : StepSegment
}

/**
 * 8 segments: done steps filled in their track color, empty ones outlined, the current one
 * Tangerine with an Ink border.
 */
@Composable
fun StepProgressStrip(
    segments: List<StepSegment>,
    description: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    if (!animated) {
        StaticStrip(segments, description, modifier)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        segments.forEach { segment ->
            val fill by animateColorAsState(
                when (segment) {
                    is StepSegment.Done -> segment.color
                    StepSegment.Current -> MbColors.Tangerine
                    StepSegment.Empty -> MbColors.Transparent
                },
                motionTween(),
                label = "segmentFill",
            )
            val edge by animateColorAsState(
                when (segment) {
                    is StepSegment.Done -> segment.color
                    StepSegment.Current -> MbColors.Ink
                    StepSegment.Empty -> MbColors.Empty
                },
                motionTween(),
                label = "segmentEdge",
            )
            Box(Modifier.weight(1f).height(MbDimens.StripHeight).background(fill).border(SegmentBorder, edge))
        }
    }
}

/**
 * The strip drawn in one pass, without per-segment animation state: a list of song cards on Home
 * would otherwise start sixteen colour animations (and coroutines) per card on the first frame of
 * every visit, and those colours never change while the card is on screen.
 */
@Composable
private fun StaticStrip(segments: List<StepSegment>, description: String, modifier: Modifier) {
    Canvas(
        modifier.fillMaxWidth().height(MbDimens.StripHeight).semantics { contentDescription = description },
    ) {
        val gap = SegmentGap.toPx()
        val border = SegmentBorder.toPx()
        val count = segments.size.coerceAtLeast(1)
        val width = (size.width - gap * (count - 1)) / count
        segments.forEachIndexed { index, segment ->
            val (fill, edge) = when (segment) {
                is StepSegment.Done -> segment.color to segment.color
                StepSegment.Current -> MbColors.Tangerine to MbColors.Ink
                StepSegment.Empty -> MbColors.Transparent to MbColors.Empty
            }
            val topLeft = Offset(index * (width + gap), 0f)
            val box = Size(width, size.height)
            drawRect(fill, topLeft, box)
            val inset = Offset(border / 2, border / 2)
            drawRect(edge, topLeft + inset, Size(width - border, size.height - border), style = Stroke(border))
        }
    }
}
