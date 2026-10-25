package com.mouthband.ui.arrange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mouthband.R
import com.mouthband.ui.components.ChipRow
import com.mouthband.ui.components.MbBottomSheet
import com.mouthband.ui.components.MbSlider
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

private val ChipSize = 14.dp

/**
 * Bottom sheet for one part of a finished song: hear it on its own, try the other sounds (tapping
 * a chip applies it and plays it), set its pan, edit its notes or record it again.
 */
@Composable
internal fun TrackSheet(sheet: TrackSheetUi, actions: ArrangeActions) {
    MbBottomSheet(onDismiss = actions.onDismissTrack) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            Box(Modifier.size(ChipSize).background(sheet.color))
            Text(sheet.title, style = MbTheme.type.h2, color = MbColors.Indigo)
        }
        if (sheet.hasContent) {
            if (sheet.sounds.isNotEmpty()) {
                Spacer(Modifier.height(MbDimens.Gap))
                MonoLabel(stringResource(R.string.arrange_sound))
                Spacer(Modifier.height(MbDimens.GapSmall))
                ChipRow(
                    items = sheet.sounds,
                    selectedId = sheet.selectedSound,
                    onSelect = { actions.onSelectSound(sheet.type, it) },
                )
                Spacer(Modifier.height(MbDimens.GapSmall))
                Text(
                    stringResource(R.string.arrange_sound_hint),
                    style = MbTheme.type.bodySm,
                    color = MbColors.InkSoft,
                )
            }
            Spacer(Modifier.height(MbDimens.Gap))
            MonoLabel(stringResource(R.string.arrange_pan))
            Spacer(Modifier.height(MbDimens.GapSmall))
            MbSlider(
                value = sheet.pan,
                onValueChange = { actions.onPan(sheet.type, it) },
                onValueChangeFinished = { actions.onPanDone(sheet.type) },
                description = stringResource(R.string.arrange_pan_description, sheet.title),
                valueText = sheet.panText,
                range = -1f..1f,
                centered = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MbDimens.Gap))
            SecondaryPillButton(
                text = stringResource(if (sheet.hearing) R.string.arrange_stop_hearing else R.string.arrange_hear_part),
                onClick = { if (sheet.hearing) actions.onStopHearing() else actions.onHearTrack(sheet.type) },
                modifier = Modifier.fillMaxWidth(),
                icon = if (sheet.hearing) MbIcons.Stop else MbIcons.Play,
                enabled = !sheet.preparing,
            )
            if (sheet.canEditNotes) {
                Spacer(Modifier.height(MbDimens.GapSmall))
                SecondaryPillButton(
                    text = stringResource(R.string.arrange_edit_notes),
                    onClick = { actions.onEditTrack(sheet.type) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = MbIcons.Edit,
                )
            }
        }
        Spacer(Modifier.height(MbDimens.GapSmall))
        val record = if (sheet.hasContent) R.string.arrange_record_again else R.string.arrange_record_part
        SecondaryPillButton(
            text = stringResource(record),
            onClick = { actions.onRecordTrack(sheet.type) },
            modifier = Modifier.fillMaxWidth(),
            icon = MbIcons.Mic,
        )
        Spacer(Modifier.height(MbDimens.Gap))
    }
}
