package com.mouthband.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme
import kotlin.math.roundToInt

private val SegmentInset = 4.dp
private val ChipPadding = 16.dp

/** Pill-framed segmented control; the selected segment is Indigo. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onIndigo: Boolean = false,
) {
    val frame = if (onIndigo) MbColors.OnIndigoMuted else MbColors.Ink
    val fill = if (onIndigo) MbColors.Cream else MbColors.Indigo
    val count = options.size.coerceAtLeast(1)
    // The selected pill slides between segments instead of jumping.
    val position by animateFloatAsState(
        selectedIndex.toFloat(),
        motionTween(),
        label = "segment",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(MbDimens.TouchTarget)
            .clip(MbShapes.Pill)
            .border(MbDimens.Border, frame, MbShapes.Pill)
            .padding(SegmentInset),
    ) {
        val segment = maxWidth / count
        if (selectedIndex in options.indices) {
            Box(
                Modifier
                    .offset { IntOffset((position * segment.toPx()).roundToInt(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .clip(MbShapes.Pill)
                    .background(fill),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val text by animateColorAsState(
                    when {
                        selected && onIndigo -> MbColors.Indigo
                        selected -> MbColors.OnIndigo
                        onIndigo -> MbColors.OnIndigo
                        else -> MbColors.Ink
                    },
                    motionTween(),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MbShapes.Pill)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MbTheme.type.label,
                        color = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Immutable
data class ChipItem(val id: String, val label: String)

/** Horizontally scrolling pill chips (sound picker). The selected chip is filled. */
@Composable
fun ChipRow(
    items: List<ChipItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedFill: Color = MbColors.Indigo,
    selectedText: Color = MbColors.OnIndigo,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            val fill by animateColorAsState(
                if (selected) selectedFill else MbColors.Transparent,
                motionTween(),
                label = "chip",
            )
            val edge by animateColorAsState(
                if (selected) selectedFill else MbColors.Ink,
                motionTween(),
                label = "chipEdge",
            )
            val text by animateColorAsState(
                if (selected) selectedText else MbColors.Ink,
                motionTween(),
                label = "chipText",
            )
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = MbDimens.TouchTarget)
                    .clip(MbShapes.Pill)
                    .background(fill)
                    .border(MbDimens.Border, edge, MbShapes.Pill)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(item.id) })
                    .padding(horizontal = ChipPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.label, style = MbTheme.type.label, color = text)
            }
        }
    }
}
