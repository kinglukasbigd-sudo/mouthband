package com.mouthband.ui.gallery

import com.mouthband.audio.LoopMath
import com.mouthband.data.KeptTake
import com.mouthband.data.SongRepository
import com.mouthband.data.TrackRepository
import com.mouthband.data.reachStep
import com.mouthband.model.CoachStep
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import com.mouthband.synth.DemoPatterns
import com.mouthband.synth.LoopRenderer
import com.mouthband.synth.RenderSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug tool behind the component gallery: makes a complete song from the built-in demo
 * patterns (vocal takes are the hook rendered as audio), so Arrange, mixing and Export can be
 * tried on an emulator, which has no working microphone. Goes through the same repository calls
 * as a kept take.
 */
class DemoSongSeeder(
    private val songs: SongRepository,
    private val tracks: TrackRepository,
    private val renderer: LoopRenderer,
    private val sampleRate: Int,
) {
    suspend fun create(title: String): Long = withContext(Dispatchers.Default) {
        val id = songs.createSong(title, Mood.POP)
        val song = requireNotNull(songs.getSong(id))
        val loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, sampleRate)
        tracks.getAll(id).forEach { track ->
            val (events, instrument) = DemoPatterns.demo(track.type, song, sampleRate, track.instrument)
            val take = if (track.type.isVocals) {
                val spec = RenderSpec(TrackType.MELODY, events, null, instrument, song.kit, 1f, song.bpm, loopFrames)
                KeptTake(toMono(renderer.render(spec)), sampleRate, null, Instrument.VOICE, 0f)
            } else {
                KeptTake(null, sampleRate, events, instrument, Track.DEFAULT_QUANTIZE)
            }
            tracks.keepTake(id, track.type, take)
        }
        songs.reachStep(id, CoachStep.ARRANGE)
        id
    }

    private fun toMono(stereo: FloatArray?): FloatArray {
        val s = stereo ?: return FloatArray(0)
        return FloatArray(s.size / 2) { i -> HALF * (s[2 * i] + s[2 * i + 1]) }
    }

    private companion object {
        const val HALF = 0.5f
    }
}
