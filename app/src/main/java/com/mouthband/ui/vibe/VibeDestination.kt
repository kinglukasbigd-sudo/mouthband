package com.mouthband.ui.vibe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mouthband.R
import com.mouthband.model.CoachStep
import com.mouthband.model.Mood
import com.mouthband.ui.common.appViewModel
import com.mouthband.ui.common.stepSegments

@Composable
fun VibeDestination(songId: Long, onNext: () -> Unit, onBack: () -> Unit) {
    val vm = appViewModel(key = "vibe-$songId") { c, _ -> VibeViewModel(songId, c.repository, c.trackRepository) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.proceed.collect { onNext() } }
    val song = state.song
    VibeScreen(
        state = VibeUiState(
            songTitle = song?.title.orEmpty(),
            segments = stepSegments(emptySet(), CoachStep.VIBE),
            progressDescription = stringResource(R.string.step_progress_description, CoachStep.VIBE.number),
            selectedMood = state.mood,
            bpm = song?.bpm ?: Mood.POP.bpm,
            key = song?.key ?: Mood.POP.key,
            loopBars = song?.loopBars ?: 2,
            locked = state.locked,
            tapCount = state.taps,
        ),
        actions = VibeActions(
            onBack = onBack,
            onMood = vm::selectMood,
            onTap = vm::tap,
            onBpm = vm::setBpm,
            onKeyRoot = vm::setKeyRoot,
            onKeyMode = vm::setKeyMode,
            onLoopBars = vm::setLoopBars,
            onNext = vm::next,
        ),
    )
}
