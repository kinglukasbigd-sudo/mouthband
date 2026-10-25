package com.mouthband.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.LocalReduceMotion
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes

private const val PULSE_MS = 900
private const val PULSE_SCALE = 1.05f
private val MicIconSize = 34.dp

/**
 * 100 dp ring with a 1.5 dp border and an 84 dp Tangerine core holding an Indigo mic. While
 * recording the core turns Cream with a Tangerine rounded square, and the button pulses gently
 * (unless the system asks for no animations).
 */
@Composable
fun RecordButton(
    recording: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ringColor: Color = MbColors.Ink,
    enabled: Boolean = true,
) {
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (recording && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "recordPulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "recordPulseScale",
        ).value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .size(MbDimens.RecordOuter)
            .scale(pulse)
            .clip(MbShapes.Circle)
            .border(MbDimens.Border, ringColor, MbShapes.Circle)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(MbDimens.RecordInner)
                .clip(MbShapes.Circle)
                .background(if (recording) MbColors.Cream else MbColors.Tangerine),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) {
                Box(
                    Modifier
                        .size(MbDimens.RecordStopSquare)
                        .clip(MbShapes.Card)
                        .background(MbColors.Tangerine),
                )
            } else {
                Icon(
                    MbIcons.Mic,
                    contentDescription = null,
                    tint = MbColors.Indigo,
                    modifier = Modifier.size(MicIconSize),
                )
            }
        }
    }
}
