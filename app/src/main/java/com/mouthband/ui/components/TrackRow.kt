package com.mouthband.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private val ColorChipSize = 14.dp
private val ChevronSize = 18.dp

/**
 * Mixer row: color chip, track name, sound name, volume bar and a 44 dp mute button.
 * [volume] is 0..1 of the fader travel.
 */
@Composable
fun TrackRow(
    color: Color,
    name: String,
    soundName: String,
    volume: Float,
    volumeText: String,
    muted: Boolean,
    muteLabel: String,
    unmuteLabel: String,
    volumeLabel: String,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onVolumeChangeFinished: () -> Unit = {},
    editLabel: String = "",
    onEdit: (() -> Unit)? = null,
    openLabel: String = "",
    onOpen: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = MbDimens.Grid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
    ) {
        Box(Modifier.size(ColorChipSize).background(color))
        Column(Modifier.weight(1f)) {
            Row(
                modifier = if (onOpen != null) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MbDimens.TouchTarget)
                        .clickable(onClickLabel = "$openLabel $name", role = Role.Button, onClick = onOpen)
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, style = MbTheme.type.label, color = if (enabled) MbColors.Ink else MbColors.Muted)
                Box(Modifier.weight(1f))
                MonoLabel(soundName, small = true)
                if (onOpen != null) {
                    Icon(
                        MbIcons.ChevronRight,
                        contentDescription = null,
                        tint = MbColors.Muted,
                        modifier = Modifier.size(ChevronSize),
                    )
                }
            }
            MbSlider(
                value = volume,
                onValueChange = onVolumeChange,
                onValueChangeFinished = onVolumeChangeFinished,
                description = "$volumeLabel $name",
                valueText = volumeText,
                fillColor = if (muted) MbColors.Empty else color,
            )
        }
        if (onEdit != null) {
            IconCircleButton(
                icon = MbIcons.Edit,
                contentDescription = "$editLabel $name",
                onClick = onEdit,
                size = MbDimens.MuteButton,
                tint = MbColors.Ink,
                background = MbColors.Transparent,
                modifier = Modifier.padding(MbDimens.Grid / 2),
            )
        }
        IconCircleButton(
            icon = if (muted) MbIcons.VolumeOff else MbIcons.Volume,
            contentDescription = if (muted) "$unmuteLabel $name" else "$muteLabel $name",
            onClick = onMuteToggle,
            size = MbDimens.MuteButton,
            tint = if (muted) MbColors.OnIndigo else MbColors.Ink,
            background = if (muted) MbColors.Indigo else MbColors.Transparent,
            framed = !muted,
            modifier = Modifier.padding(MbDimens.Grid / 2),
        )
    }
}
