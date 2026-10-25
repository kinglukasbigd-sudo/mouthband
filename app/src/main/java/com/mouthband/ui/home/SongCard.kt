package com.mouthband.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.mouthband.R
import com.mouthband.ui.components.ConfirmDialog
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.Hairline
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.MbBottomSheet
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.TextInputDialog
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

@Composable
internal fun SongCard(song: SongCardUi, onOpen: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    FramedCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = song.title,
                style = MbTheme.type.h2,
                color = MbColors.Indigo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = MbDimens.Grid),
            )
            IconCircleButton(
                icon = MbIcons.More,
                contentDescription = stringResource(R.string.more_actions, song.title),
                onClick = onMore,
                framed = false,
            )
        }
        MonoLabel(song.meta)
        Spacer(Modifier.height(MbDimens.Gap))
        StepProgressStrip(segments = song.segments, description = song.progressDescription, animated = false)
        Spacer(Modifier.height(MbDimens.GapSmall))
        Text(song.status, style = MbTheme.type.bodySm, color = MbColors.Muted)
    }
}

/** Rename / duplicate / delete for one song. Delete asks for confirmation first. */
@Composable
internal fun SongActionsSheet(
    song: SongCardUi,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SongDialog.NONE) }
    when (dialog) {
        SongDialog.RENAME -> TextInputDialog(
            title = stringResource(R.string.song_rename_title),
            initial = song.title,
            confirmLabel = stringResource(R.string.action_rename),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = { title ->
                onRename(title)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SongDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.song_delete_title),
            body = stringResource(R.string.song_delete_body, song.title),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                onDelete()
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SongDialog.NONE -> MbBottomSheet(onDismiss = onDismiss) {
            Text(song.title, style = MbTheme.type.h2, color = MbColors.Indigo)
            Spacer(Modifier.height(MbDimens.Gap))
            SheetAction(MbIcons.Edit, stringResource(R.string.action_rename)) { dialog = SongDialog.RENAME }
            Hairline()
            SheetAction(MbIcons.Copy, stringResource(R.string.action_duplicate)) {
                onDuplicate()
                onDismiss()
            }
            Hairline()
            SheetAction(MbIcons.Trash, stringResource(R.string.action_delete)) { dialog = SongDialog.DELETE }
        }
    }
}

private enum class SongDialog { NONE, RENAME, DELETE }

@Composable
internal fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MbDimens.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
    ) {
        Icon(icon, contentDescription = null, tint = MbColors.Ink, modifier = Modifier.size(MbDimens.IconSize))
        Text(label, style = MbTheme.type.label, color = MbColors.Ink)
    }
}
