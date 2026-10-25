package com.mouthband.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private const val FULL_CIRCLE = 360f
private const val TOP_ANGLE = -90f
private val RingStroke = 6.dp
private val StatusChipPadding = 12.dp
private val StatusChipVertical = 6.dp
private val EmptyIconSize = 32.dp

/** Friendly empty or error state: icon, headline, explanation and an optional action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MbIcons.Alert,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onIndigo: Boolean = false,
) {
    FramedCard(
        modifier = modifier.fillMaxWidth(),
        background = if (onIndigo) MbColors.Indigo else MbColors.Paper,
        borderColor = if (onIndigo) MbColors.OnIndigoMuted else MbColors.Ink,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (onIndigo) MbColors.Tangerine else MbColors.Indigo,
            modifier = Modifier.size(EmptyIconSize),
        )
        Spacer(Modifier.height(MbDimens.Gap))
        Text(title, style = MbTheme.type.h2, color = if (onIndigo) MbColors.OnIndigo else MbColors.Indigo)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(MbDimens.Grid * 2))
            Text(body, style = MbTheme.type.body, color = if (onIndigo) MbColors.OnIndigoMuted else MbColors.InkSoft)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MbDimens.GapLarge))
            if (onIndigo) {
                SecondaryPillButton(actionLabel, onAction, Modifier.fillMaxWidth(), contentColor = MbColors.OnIndigo)
            } else {
                IndigoPillButton(actionLabel, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Determinate progress ring (export). [progress] is 0..1. */
private const val RING_MS = 220

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    label: String? = null,
) {
    // The arc glides between progress updates instead of stepping.
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), motionTween(RING_MS), label = "ring")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(size)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
        ) {
            val stroke = RingStroke.toPx()
            drawCircle(MbColors.Rule, radius = this.size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
            drawArc(
                color = MbColors.Tangerine,
                startAngle = TOP_ANGLE,
                sweepAngle = FULL_CIRCLE * shown,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Butt),
            )
        }
        if (label != null) Text(label, style = MbTheme.type.h2, color = MbColors.Indigo, textAlign = TextAlign.Center)
    }
}

/** Small status pill with an icon, e.g. the "Headphones connected" chip. */
@Composable
fun StatusChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    fill: Color = MbColors.MelodyTrack,
    content: Color = MbColors.Ink,
) {
    Row(
        modifier = modifier
            .clip(MbShapes.Pill)
            .background(fill)
            .border(MbDimens.Border, fill, MbShapes.Pill)
            .padding(horizontal = StatusChipPadding, vertical = StatusChipVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(MbDimens.IconSmall))
        Spacer(Modifier.width(MbDimens.Grid * 2))
        Text(text, style = MbTheme.type.label, color = content)
    }
}

/** Top row used by every inner screen: back button, optional title block, optional trailing slot. */
@Composable
fun TopBar(
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    onIndigo: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
    ) {
        IconCircleButton(
            icon = MbIcons.Back,
            contentDescription = backLabel,
            onClick = onBack,
            tint = if (onIndigo) MbColors.OnIndigo else MbColors.Ink,
        )
        Box(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    title,
                    style = MbTheme.type.label,
                    color = if (onIndigo) MbColors.OnIndigo else MbColors.Ink,
                    maxLines = 1,
                )
            }
        }
        trailing()
    }
}

/**
 * Bottom area that hosts the Home banner. It collapses when no ad is loaded. It paints no background:
 * the banner itself lies on a layer beneath the screen and shows through.
 */
@Composable
fun AdBannerSlot(
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)?,
) {
    if (banner == null) return
    Column(modifier.fillMaxWidth()) {
        Hairline()
        Box(Modifier.fillMaxWidth().padding(vertical = MbDimens.Grid), contentAlignment = Alignment.Center) {
            banner()
        }
    }
}
