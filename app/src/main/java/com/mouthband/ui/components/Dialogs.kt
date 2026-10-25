package com.mouthband.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mouthband.R
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbShapes
import com.mouthband.ui.theme.MbTheme

private const val MAX_TITLE_LENGTH = 40
private val SheetTopShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)

/** Framed dialog shell (no elevation shadow). */
@Composable
fun MbDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        FramedCard(modifier = Modifier.fillMaxWidth(), contentPadding = MbDimens.ScreenPadding, content = content)
    }
}

/** Yes/no confirmation, e.g. before deleting a song. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MbDialog(onDismiss) {
        Text(title, style = MbTheme.type.h2, color = MbColors.Indigo)
        Spacer(Modifier.height(MbDimens.Gap))
        Text(body, style = MbTheme.type.body, color = MbColors.InkSoft)
        Spacer(Modifier.height(MbDimens.ScreenPadding))
        DialogButtons(confirmLabel, dismissLabel, onConfirm, onDismiss)
    }
}

/** Single-line text entry, e.g. renaming a song. Confirm is disabled for a blank value. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    MbDialog(onDismiss) {
        Text(title, style = MbTheme.type.h2, color = MbColors.Indigo)
        Spacer(Modifier.height(MbDimens.Gap))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(MAX_TITLE_LENGTH) },
            singleLine = true,
            textStyle = MbTheme.type.body.copy(color = MbColors.Ink),
            cursorBrush = SolidColor(MbColors.Indigo),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .border(MbDimens.Border, MbColors.Ink, MbShapes.Card)
                .padding(MbDimens.Gap),
        )
        Spacer(Modifier.height(MbDimens.ScreenPadding))
        DialogButtons(
            confirmLabel,
            dismissLabel,
            { onConfirm(text.trim()) },
            onDismiss,
            confirmEnabled = text.isNotBlank(),
        )
    }
}

@Composable
private fun DialogButtons(
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall)) {
        SecondaryPillButton(dismissLabel, onDismiss, Modifier.weight(1f))
        IndigoPillButton(confirmLabel, onConfirm, Modifier.weight(1f), enabled = confirmEnabled)
    }
}

/** Paper bottom sheet with a framed top edge and a short Ink handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MbBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val handleLabel = stringResource(R.string.sheet_drag_handle)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = SheetTopShape,
        containerColor = MbColors.Paper,
        contentColor = MbColors.Ink,
        tonalElevation = 0.dp,
        scrimColor = MbColors.Scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = MbDimens.Gap)
                    .width(40.dp)
                    .height(4.dp)
                    .background(MbColors.Ink)
                    .semantics { contentDescription = handleLabel },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MbDimens.ScreenPadding, vertical = MbDimens.GapLarge)
                .navigationBarsPadding(),
            content = content,
        )
    }
}
