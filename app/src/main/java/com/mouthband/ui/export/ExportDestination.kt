package com.mouthband.ui.export

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.R
import com.mouthband.audio.AudioLog
import com.mouthband.ui.common.appViewModel

/** Coach step 8 with real data: [ExportViewModel] behind [ExportScreen]. */
@Composable
fun ExportDestination(songId: Long, onExported: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    val vm = appViewModel(key = "export-$songId") { c, _ -> ExportViewModel(songId, ExportDeps(c)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val share = rememberShareSheet(onTargetChosen = vm::shareChosen)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(state.format.mimeType),
    ) { uri -> uri?.let(vm::saveTo) }
    LaunchedEffect(vm) { vm.exported.collect { onExported() } }
    LifecycleResumeEffect(vm) { onPauseOrDispose { vm.stopPlayback() } }
    val cancelAndLeave = {
        vm.cancel()
        onBack()
    }
    BackHandler(enabled = state.stage is ExportStage.Rendering, onBack = cancelAndLeave)
    ExportScreen(
        state = state.toUi(),
        actions = ExportActions(
            onBack = cancelAndLeave,
            onCancel = cancelAndLeave,
            onPlayToggle = vm::togglePlay,
            onFormat = vm::setFormat,
            onSave = {
                vm.stopPlayback()
                launchSafely { saveLauncher.launch(vm.suggestedName()) }
            },
            onShare = {
                vm.stopPlayback()
                vm.selectedFile()?.let { file ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    vm.shareStarted(uri.toString())
                    share(uri, state.format.mimeType)
                }
            },
            onRetry = vm::start,
            onHome = {
                vm.stopPlayback()
                onHome()
            },
        ),
    )
}

@Composable
private fun ExportState.toUi(): ExportUi {
    val phase = when (val s = stage) {
        is ExportStage.Rendering -> ExportPhase.Working(s.progress, encoding = s.progress >= 1f)
        is ExportStage.Done -> {
            val seconds = s.result.durationMs / MS_PER_SECOND
            ExportPhase.Done(
                stringResource(R.string.time_format, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE),
            )
        }
        ExportStage.Failed -> ExportPhase.Failed
        ExportStage.Empty -> ExportPhase.NothingToExport
    }
    val message = when (notice) {
        ExportNotice.SAVED -> stringResource(R.string.export_saved)
        ExportNotice.SAVE_FAILED -> stringResource(R.string.export_save_failed)
        ExportNotice.PLAYBACK_FAILED -> stringResource(R.string.error_audio_busy)
        null -> null
    }
    return ExportUi(phase = phase, m4aSelected = m4a, playing = playing, message = message)
}

/** No app can handle the request (no document provider / share target): nothing happens. */
private inline fun launchSafely(launch: () -> Unit) {
    try {
        launch()
    } catch (e: ActivityNotFoundException) {
        AudioLog.w("no activity for export", e)
    }
}

private const val MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
