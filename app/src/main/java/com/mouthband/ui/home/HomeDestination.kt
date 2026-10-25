package com.mouthband.ui.home

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.R
import com.mouthband.data.SongOverview
import com.mouthband.model.CoachStep
import com.mouthband.model.CoachTarget
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.doneSteps
import com.mouthband.ui.common.nameRes
import com.mouthband.ui.common.stepSegments

/** Home with real data (spec §1 Projects and home). */
@Composable
fun HomeDestination(
    onOpen: (songId: Long, target: CoachTarget) -> Unit,
    onSettings: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    banner: (@Composable () -> Unit)?,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c.repository) }
    val songs by vm.songs.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    LaunchedEffect(vm) { vm.navigation.collect { (id, target) -> onOpen(id, target) } }
    val cards = songs.orEmpty().map { it.toCard() }
    HomeScreen(
        state = HomeUiState(songs = cards, loading = songs == null),
        actions = HomeActions(
            onNewSong = { vm.newSong { n -> resources.getString(R.string.song_default_title, n) } },
            onOpenSong = vm::open,
            onRename = vm::rename,
            onDuplicate = { id -> vm.duplicate(id) { title -> resources.getString(R.string.song_copy_title, title) } },
            onDelete = vm::delete,
            onSettings = onSettings,
            onOpenGallery = onOpenGallery,
        ),
        banner = banner,
    )
}

@Composable
private fun SongOverview.toCard(): SongCardUi {
    val exported = lastExportAt != null
    val done = doneSteps(song, tracks, exported)
    val status = when {
        lastExportAt != null -> {
            val now = System.currentTimeMillis()
            if (now - lastExportAt < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.status_exported_now)
            } else {
                val ago = DateUtils.getRelativeTimeSpanString(lastExportAt, now, DateUtils.MINUTE_IN_MILLIS)
                stringResource(R.string.status_exported, ago.toString())
            }
        }
        song.coachStep == CoachStep.EXPORT -> stringResource(R.string.status_ready_export)
        else -> stringResource(
            R.string.status_next_step,
            song.coachStep.number,
            stringResource(song.coachStep.nameRes()),
        )
    }
    return SongCardUi(
        id = song.id,
        title = song.title,
        meta = stringResource(R.string.bpm_key_format, song.bpm, song.key.label),
        segments = stepSegments(done, if (exported) null else song.coachStep),
        status = status,
        progressDescription = stringResource(R.string.step_progress_description, song.coachStep.number),
    )
}
