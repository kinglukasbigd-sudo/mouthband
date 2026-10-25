package com.mouthband.mix

import com.mouthband.audio.AudioEngine
import com.mouthband.audio.AudioSession
import com.mouthband.model.Song
import com.mouthband.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Plays a whole song through a [SongMixer] on the shared engine (Arrange and Export screens).
 * Loading renders off the main thread and only swaps references, so it may run while playing.
 */
class SongPlayer(
    private val engine: AudioEngine,
    private val session: AudioSession,
    private val loader: MixLoader,
) {
    val mixer = SongMixer(engine.sampleRate)
    private val loadLock = Mutex()

    val isPlaying: Boolean get() = engine.source === mixer

    /** Loads [song] into the mixer; false when nothing would be heard. */
    suspend fun load(song: Song, tracks: List<Track>): Boolean = withContext(Dispatchers.Default) {
        loadLock.withLock { loader.load(mixer, song, tracks) }
    }

    fun updateLevels(tracks: List<Track>) = loader.updateLevels(mixer, tracks)

    /** Starts from the top of the song. [onInterrupt] runs after focus loss or a noisy route stopped it. */
    fun start(onInterrupt: () -> Unit): Boolean {
        val began = session.begin {
            detach()
            onInterrupt()
        }
        if (!began) return false
        if (!engine.start()) {
            session.end()
            return false
        }
        if (engine.source !== mixer) mixer.reset()
        engine.metronome.plan = null
        engine.resetTransport(0)
        engine.source = mixer
        return true
    }

    /** Stops this player only; another screen's playback and audio focus are left alone. */
    fun stop() {
        if (engine.source !== mixer) return
        detach()
        session.end()
    }

    private fun detach() {
        if (engine.source === mixer) engine.source = null
    }

    /** Transport frame reaching the speaker now (approximately), for the section indicator. */
    fun heardFrame(): Long = engine.writtenTransportFrame() - engine.framesPerBuffer * HEARD_BUFFERS

    /** True once the song and its reverb tail have played out. */
    fun finished(): Boolean {
        val layout = mixer.layout ?: return true
        return heardFrame() >= layout.totalFrames + TAIL_SECONDS * engine.sampleRate
    }

    private companion object {
        const val HEARD_BUFFERS = 2
        const val TAIL_SECONDS = 2L
    }
}
