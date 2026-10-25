package com.mouthband.ui.settings

/** Callbacks Settings needs from the app shell (navigation and the consent form). */
class SettingsNavigation(
    val onBack: () -> Unit,
    val onCalibrate: () -> Unit,
    val onNudge: () -> Unit,
    val onTeach: () -> Unit,
    val onLicenses: () -> Unit,
    val onPrivacy: () -> Unit,
)
