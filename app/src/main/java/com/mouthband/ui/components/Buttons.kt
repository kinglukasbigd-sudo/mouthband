package com.mouthband.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private const val DISABLED_ALPHA = 0.4f
private val ArrowEndInset = 7.dp
private val ButtonHorizontalPadding = 24.dp

/** Tangerine pill: the one primary action on a screen. Its label is always Indigo. */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = false,
    enabled: Boolean = true,
) {
    PillSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = MbDimens.PrimaryButtonHeight,
        background = MbColors.Tangerine,
        border = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = ButtonHorizontalPadding,
                end = if (showArrow) ArrowEndInset else ButtonHorizontalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showArrow) Arrangement.SpaceBetween else Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MbTheme.type.button,
                color = MbColors.Indigo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (showArrow) {
                Box(
                    modifier = Modifier.size(MbDimens.ArrowCircle).clip(MbShapes.Circle).background(MbColors.Indigo),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MbIcons.ArrowRight, contentDescription = null, tint = MbColors.OnIndigo)
                }
            }
        }
    }
}

/** Transparent pill with a 1.5 dp Ink frame. */
@Composable
fun SecondaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = MbColors.Ink,
) {
    PillSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = MbDimens.SecondaryButtonHeight,
        background = MbColors.Transparent,
        border = contentColor,
    ) {
        PillLabel(text = text, icon = icon, color = contentColor)
    }
}

/** Indigo filled pill: secondary filled action (e.g. "Done"). */
@Composable
fun IndigoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    PillSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        height = MbDimens.SecondaryButtonHeight,
        background = MbColors.Indigo,
        border = null,
    ) {
        PillLabel(text = text, icon = icon, color = MbColors.OnIndigo)
    }
}

@Composable
private fun PillLabel(text: String, icon: ImageVector?, color: Color) {
    Row(
        modifier = Modifier.padding(horizontal = ButtonHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(MbDimens.IconSmall))
            Spacer(Modifier.width(MbDimens.GapSmall))
        }
        Text(text = text, style = MbTheme.type.button, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PillSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    height: Dp,
    background: Color,
    border: Color?,
    content: @Composable () -> Unit,
) {
    val framed = if (border != null) Modifier.border(MbDimens.Border, border, MbShapes.Pill) else Modifier
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MbDimens.TouchTarget)
            .height(height)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(MbShapes.Pill)
            .background(background)
            .then(framed)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Inline text action, underlined in Tangerine (e.g. "Teach my sounds", "Skip step"). */
@Composable
fun TextLinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MbColors.Ink,
    underline: Boolean = true,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = MbDimens.TouchTarget, minHeight = MbDimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MbDimens.Grid),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MbTheme.type.label,
            color = color,
            textAlign = TextAlign.Center,
            modifier = if (underline) Modifier.tangerineUnderline() else Modifier,
        )
    }
}

/** Circular framed icon button with a 48 dp touch target. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MbColors.Ink,
    background: Color = MbColors.Transparent,
    framed: Boolean = true,
    size: Dp = MbDimens.TouchTarget,
) {
    val frame = if (framed) Modifier.border(MbDimens.Border, tint, MbShapes.Circle) else Modifier
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(MbShapes.Circle)
            .background(background)
            .then(frame)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(MbDimens.IconSize))
    }
}
