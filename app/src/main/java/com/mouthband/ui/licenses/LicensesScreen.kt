package com.mouthband.ui.licenses

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenScaffold(modifier = modifier) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = onBack)
        Text(
            stringResource(R.string.licenses_title),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.licenses_body), style = MbTheme.type.body, color = MbColors.InkSoft)
        BundledLicenses.forEach { entry ->
            val open = expanded == entry.asset
            FramedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { expanded = if (open) null else entry.asset },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(stringResource(entry.name), style = MbTheme.type.label, color = MbColors.Ink)
                        MonoLabel(stringResource(entry.license), small = true)
                    }
                    Icon(
                        if (open) MbIcons.ChevronDown else MbIcons.ChevronRight,
                        contentDescription = null,
                        tint = MbColors.Ink,
                    )
                }
                if (open) {
                    Spacer(Modifier.height(MbDimens.Gap))
                    LicenseText(entry.asset)
                }
            }
        }
    }
}

@Composable
private fun LicenseText(asset: String) {
    val context = LocalContext.current
    val text by produceState(initialValue = "", asset) { value = readAsset(context, asset) }
    Text(text, style = MbTheme.type.bodySm, color = MbColors.InkSoft)
}

private suspend fun readAsset(context: Context, path: String): String = withContext(Dispatchers.IO) {
    try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.message.orEmpty()
    }
}
