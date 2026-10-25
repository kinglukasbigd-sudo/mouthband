package com.mouthband.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private val UnderlineThickness = 2.dp
private val TagHorizontal = 8.dp
private val TagVertical = 4.dp
private val PillHorizontal = 12.dp
private val PillVertical = 6.dp

/** Paper card with a 1.5 dp Ink frame (3 dp when active). No shadow, ever. */
@Composable
fun FramedCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    background: Color = MbColors.Paper,
    borderColor: Color = MbColors.Ink,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = MbDimens.GapLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(MbShapes.Card)
            .background(background)
            .border(if (active) MbDimens.BorderActive else MbDimens.Border, borderColor, MbShapes.Card)
            .then(clickModifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Highlighter step tag: black mono text on yellow, square corners. */
@Composable
fun HighlighterTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MbShapes.Tag)
            .background(MbColors.Highlighter)
            .padding(horizontal = TagHorizontal, vertical = TagVertical),
    ) {
        Text(text = text.uppercase(), style = MbTheme.type.monoBold, color = MbColors.Ink)
    }
}

/** Uppercase mono text for BPM, key, bar counters, tags and eyebrows. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MbColors.Muted,
    bold: Boolean = false,
    small: Boolean = false,
) {
    val style: TextStyle = when {
        bold -> MbTheme.type.monoBold
        small -> MbTheme.type.monoSmall
        else -> MbTheme.type.mono
    }
    Text(text = text.uppercase(), style = style, color = color, modifier = modifier)
}

/** Framed pill holding mono text, e.g. "110 BPM · C MAJ". */
@Composable
fun MonoPill(text: String, modifier: Modifier = Modifier, color: Color = MbColors.Ink) {
    Box(
        modifier = modifier
            .clip(MbShapes.Pill)
            .border(MbDimens.Border, color, MbShapes.Pill)
            .padding(horizontal = PillHorizontal, vertical = PillVertical),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text.uppercase(), style = MbTheme.type.monoBold, color = color)
    }
}

/** 1.5 dp rule under section headings. */
@Composable
fun HeavyRule(modifier: Modifier = Modifier, color: Color = MbColors.Ink) {
    Box(modifier.fillMaxWidth().height(MbDimens.RuleThickness).background(color))
}

/** Hairline divider. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = MbColors.Rule) {
    Box(modifier.fillMaxWidth().height(MbDimens.Hairline).background(color))
}

/** Section heading: H2 with a count on the right and a heavy rule beneath. */
@Composable
fun SectionHeading(title: String, trailing: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = MbDimens.Grid * 2),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, style = MbTheme.type.h2, color = MbColors.Indigo, modifier = Modifier.weight(1f))
            if (trailing != null) MonoLabel(trailing)
        }
        HeavyRule()
    }
}

/** Draws a Tangerine underline below text, used for inline links. */
fun Modifier.tangerineUnderline(): Modifier = drawBehind {
    val y = size.height + UnderlineThickness.toPx()
    drawLine(
        color = MbColors.Tangerine,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = UnderlineThickness.toPx(),
    )
}
