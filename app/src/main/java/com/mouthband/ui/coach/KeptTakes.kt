package com.mouthband.ui.coach

import com.mouthband.audio.AudioLog
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Editing a take after it was kept: its events go back into Review with the raw loop for A/B,
 * and saving writes the edited events, sound and timing to the same track (no new recording,
 * no new take file). Autosaved like every other edit.
 */
object KeptTakes {
    suspend fun candidateOf(track: Track, song: Song, deps: StepDeps): Candidate {
        val raw = withContext(Dispatchers.IO) {
            val path = track.takeFilePath ?: return@withContext FloatArray(0)
            try {
                deps.takes.read(path).samples
            } catch (e: IOException) {
                AudioLog.w("read kept take", e)
                FloatArray(0)
            }
        }
        return Candidate(
            rawLoop = raw,
            events = track.events,
            problem = null,
            instrument = track.instrument,
            kit = song.kit,
            quantize = track.quantizeStrength,
        )
    }

    /** Writes [candidate] to the [type] track; false when the track row is missing. */
    suspend fun save(
        song: Song,
        type: TrackType,
        candidate: Candidate,
        tracks: List<Track>,
        deps: StepDeps,
    ): Boolean {
        val track = tracks.firstOrNull { it.type == type } ?: return false
        val edited = track.copy(
            events = candidate.events,
            instrument = candidate.instrument,
            quantizeStrength = candidate.quantize,
        )
        deps.tracks.update(edited)
        if (type == TrackType.BEAT && candidate.kit != song.kit) {
            deps.repository.updateSong(song.copy(kit = candidate.kit))
        }
        return true
    }
}
