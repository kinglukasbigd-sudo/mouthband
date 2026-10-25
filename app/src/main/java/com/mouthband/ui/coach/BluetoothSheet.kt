package com.mouthband.ui.coach

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mouthband.R
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.MbBottomSheet
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

/** Warning shown on recording steps while a Bluetooth output is connected (spec §2.1). */
@Composable
fun BluetoothSheet(onDismiss: () -> Unit) {
    MbBottomSheet(onDismiss = onDismiss) {
        Icon(
            MbIcons.Bluetooth,
            contentDescription = null,
            tint = MbColors.Indigo,
            modifier = Modifier.size(MbDimens.IconSize),
        )
        Spacer(Modifier.height(MbDimens.Gap))
        Text(stringResource(R.string.bt_title), style = MbTheme.type.h2, color = MbColors.Indigo)
        Spacer(Modifier.height(MbDimens.Grid * 2))
        Text(stringResource(R.string.bt_body), style = MbTheme.type.body, color = MbColors.InkSoft)
        Spacer(Modifier.height(MbDimens.ScreenPadding))
        IndigoPillButton(stringResource(R.string.action_ok), onDismiss, Modifier.fillMaxWidth())
    }
}
