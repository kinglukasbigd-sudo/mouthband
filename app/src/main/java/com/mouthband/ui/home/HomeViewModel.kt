package com.mouthband.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mouthband.data.SongOverview
import com.mouthband.data.SongRepository
import com.mouthband.data.rename
import com.mouthband.model.CoachStep
import com.mouthband.model.CoachTarget
import com.mouthband.model.Mood
import com.mouthband.model.TrackType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Home: the song list with rename, duplicate and delete (spec §1 Projects and home). */
class HomeViewModel(private val repository: SongRepository) : ViewModel() {
    val songs: StateFlow<List<SongOverview>?> = repository.overviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val opened = Channel<Pair<Long, CoachTarget>>(Channel.BUFFERED)

    /** Song id + where its coach should resume. */
    val navigation = opened.receiveAsFlow()

    /** Creates "Song N" with Pop defaults and opens the Vibe step. */
    fun newSong(defaultTitle: (Int) -> String) {
        viewModelScope.launch {
            val id = repository.createSong(defaultTitle(repository.nextDefaultNumber()), Mood.POP)
            opened.send(id to CoachTarget.Vibe)
        }
    }

    fun open(songId: Long) {
        val overview = songs.value?.firstOrNull { it.song.id == songId } ?: return
        viewModelScope.launch { opened.send(songId to resumeTarget(overview.song.coachStep)) }
    }

    fun rename(songId: Long, title: String) {
        viewModelScope.launch { repository.rename(songId, title) }
    }

    fun duplicate(songId: Long, copyTitle: (String) -> String) {
        val title = songs.value?.firstOrNull { it.song.id == songId }?.song?.title ?: return
        viewModelScope.launch { repository.duplicate(songId, copyTitle(title)) }
    }

    fun delete(songId: Long) {
        viewModelScope.launch { repository.delete(songId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The coach screen a song resumes at; a finished song opens in Arrange, where it can be edited and mixed. */
fun resumeTarget(step: CoachStep): CoachTarget = when (step) {
    CoachStep.VIBE -> CoachTarget.Vibe
    CoachStep.BEAT -> CoachTarget.Record(TrackType.BEAT)
    CoachStep.BASS -> CoachTarget.Record(TrackType.BASS)
    CoachStep.CHORDS -> CoachTarget.Record(TrackType.CHORDS)
    CoachStep.MELODY -> CoachTarget.Record(TrackType.MELODY)
    CoachStep.VOCALS -> CoachTarget.Record(TrackType.VOCALS_VERSE)
    CoachStep.ARRANGE -> CoachTarget.Arrange
    CoachStep.EXPORT -> CoachTarget.Arrange
}
