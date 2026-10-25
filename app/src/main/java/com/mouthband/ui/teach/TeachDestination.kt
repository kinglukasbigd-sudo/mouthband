package com.mouthband.ui.teach

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.ui.coach.MicAccess
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.rememberMicPermission

/** Teach your sounds: from the Beat step (automatically before the first take) or Settings. */
@Composable
fun TeachDestination(onClose: () -> Unit) {
    val vm = appViewModel { c, _ -> TeachViewModel(c.micMonitor, c.templates, c.settings, c.engine.sampleRate) }
    val state by vm.state.collectAsStateWithLifecycle()
    val peaks = vm.peaks.collectAsStateWithLifecycle()
    val permission = rememberMicPermission { granted -> if (granted) vm.start() }
    LifecycleResumeEffect(Unit) {
        onPauseOrDispose { vm.stop() }
    }
    TeachScreen(
        state = state.copy(micDenied = permission.access == MicAccess.DENIED),
        actions = TeachActions(
            onBack = {
                vm.stop()
                onClose()
            },
            onStart = { if (permission.access == MicAccess.GRANTED) vm.start() else permission.request() },
            onDone = onClose,
            onReset = vm::reset,
            onOpenAppSettings = permission.openSettings,
        ),
        peaks = { peaks.value },
    )
}
