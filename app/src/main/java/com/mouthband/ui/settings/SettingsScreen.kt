package com.mouthband.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.Hairline
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

@Immutable
data class SettingsUi(
    val latencyText: String = "",
    val clickOn: Boolean = true,
    val countInBars: Int = 1,
    val privacyRequired: Boolean = false,
    val version: String = "",
)

@Immutable
data class SettingsActions(
    val onBack: () -> Unit,
    val onCalibrate: () -> Unit,
    val onNudge: () -> Unit,
    val onTeach: () -> Unit,
    val onClick: (Boolean) -> Unit,
    val onCountIn: (Int) -> Unit,
    val onPrivacy: () -> Unit,
    val onLicenses: () -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsUi, actions: SettingsActions, modifier: Modifier = Modifier) {
    ScreenScaffold(modifier = modifier) {
        TopBar(backLabel = stringResource(R.string.action_back), onBack = actions.onBack)
        Text(
            stringResource(R.string.settings),
            style = MbTheme.type.h1,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        MonoLabel(stringResource(R.string.settings_section_audio))
        FramedCard(Modifier.fillMaxWidth()) {
            SettingsRow(stringResource(R.string.settings_latency), state.latencyText, onClick = actions.onCalibrate)
            Hairline()
            SettingsRow(stringResource(R.string.settings_latency_rerun), null, onClick = actions.onCalibrate)
            Hairline()
            SettingsRow(stringResource(R.string.settings_latency_nudge), null, onClick = actions.onNudge)
            Hairline()
            SettingsRow(
                stringResource(R.string.settings_teach),
                stringResource(R.string.settings_teach_body),
                onClick = actions.onTeach,
            )
            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = MbDimens.TouchTarget)
                    .toggleable(value = state.clickOn, role = Role.Switch, onValueChange = actions.onClick)
                    .padding(vertical = MbDimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_click), style = MbTheme.type.label, color = MbColors.Ink)
                    Text(
                        stringResource(R.string.settings_click_body),
                        style = MbTheme.type.bodySm,
                        color = MbColors.Muted,
                    )
                }
                Switch(
                    checked = state.clickOn,
                    onCheckedChange = null,
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
            Hairline()
            Text(
                stringResource(R.string.settings_count_in),
                style = MbTheme.type.label,
                color = MbColors.Ink,
                modifier = Modifier.padding(vertical = MbDimens.GapSmall),
            )
            SegmentedToggle(
                options = listOf(stringResource(R.string.count_in_1), stringResource(R.string.count_in_2)),
                selectedIndex = if (state.countInBars == 1) 0 else 1,
                onSelect = { actions.onCountIn(it + 1) },
            )
        }
        MonoLabel(stringResource(R.string.settings_section_about))
        FramedCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                stringResource(R.string.settings_privacy),
                stringResource(
                    if (state.privacyRequired) {
                        R.string.settings_privacy_body
                    } else {
                        R.string.settings_privacy_unavailable
                    },
                ),
                onClick = actions.onPrivacy,
                enabled = state.privacyRequired,
            )
            Hairline()
            SettingsRow(stringResource(R.string.settings_licenses), null, onClick = actions.onLicenses)
            Hairline()
            SettingsRow(stringResource(R.string.settings_version, state.version), null, onClick = null)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: (() -> Unit)?, enabled: Boolean = true) {
    val click = if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MbDimens.TouchTarget)
            .then(click)
            .padding(vertical = MbDimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MbDimens.Gap),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MbTheme.type.label, color = if (enabled) MbColors.Ink else MbColors.Muted)
            if (subtitle != null) Text(subtitle, style = MbTheme.type.bodySm, color = MbColors.Muted)
        }
        if (onClick != null && enabled) Icon(MbIcons.ChevronRight, contentDescription = null, tint = MbColors.Ink)
    }
}
