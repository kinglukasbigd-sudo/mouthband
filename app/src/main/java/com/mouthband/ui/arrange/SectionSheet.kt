package com.mouthband.ui.arrange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.ui.components.Hairline
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.MbBottomSheet
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private val ChipSize = 14.dp

/** Bottom sheet with one on/off switch per track for the tapped section. */
@Composable
internal fun SectionSheet(sheet: SectionSheetUi, actions: ArrangeActions) {
    MbBottomSheet(onDismiss = actions.onDismissSheet) {
        Text(sheet.title, style = MbTheme.type.h2, color = MbColors.Indigo)
        Spacer(Modifier.height(MbDimens.Grid))
        Text(stringResource(R.string.section_sheet_body), style = MbTheme.type.bodySm, color = MbColors.InkSoft)
        Spacer(Modifier.height(MbDimens.Gap))
        sheet.tracks.forEachIndexed { index, track ->
            if (index > 0) Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MbDimens.TouchTarget)
                    .toggleable(
                        value = track.on,
                        enabled = track.enabled,
                        role = Role.Switch,
                        onValueChange = { actions.onToggleTrack(sheet.index, track.type, it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
            ) {
                Box(Modifier.size(ChipSize).background(track.color))
                Text(
                    track.name,
                    style = MbTheme.type.label,
                    color = if (track.enabled) MbColors.Ink else MbColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = track.on,
                    onCheckedChange = null,
                    enabled = track.enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MbColors.Paper,
                        checkedTrackColor = MbColors.Indigo,
                        checkedBorderColor = MbColors.Indigo,
                        uncheckedThumbColor = MbColors.Ink,
                        uncheckedTrackColor = MbColors.Paper,
                        uncheckedBorderColor = MbColors.Ink,
                    ),
                )
            }
        }
        Spacer(Modifier.height(MbDimens.GapLarge))
        IndigoPillButton(stringResource(R.string.action_done), actions.onDismissSheet, Modifier.fillMaxWidth())
    }
}
