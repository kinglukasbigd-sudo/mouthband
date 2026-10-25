package com.mouthband.synth

import com.mouthband.audio.AudioLog
import com.mouthband.audio.LoopMath
import com.mouthband.data.TakeStore
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackKind
import java.io.IOException

/**
 * Renders kept tracks to loop buffers and remembers the last results, so opening another step
 * or the mixer does not re-synthesize unchanged tracks. Keys include everything that affects
 * the sound (take version, instrument, kit, timing, tempo, width, reverb).
 */
class RenderCache(private val renderer: LoopRenderer, private val takes: TakeStore, private val sampleRate: Int) {
    private val cache = object : LinkedHashMap<String, FloatArray>(MAX_ENTRIES, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > MAX_ENTRIES
    }

    /** Stereo loop of a kept track, or null when it has no content. Call off the main thread. */
    fun loopOf(song: Song, track: Track, chordWidth: Float = 0f, vocalReverb: Boolean = false): FloatArray? {
        if (!track.hasContent) return null
        val spec = RenderSpec(
            type = track.type,
            events = track.events,
            rawLoop = null,
            instrument = track.instrument,
            kit = song.kit,
            quantize = track.quantizeStrength,
            bpm = song.bpm,
            loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, sampleRate),
            chordWidth = chordWidth,
            vocalReverb = vocalReverb,
        )
        val key = listOf(
            track.id,
            track.takeVersion,
            track.instrument,
            song.kit.takeIf { track.type.kind == TrackKind.DRUMS },
            track.quantizeStrength,
            song.bpm,
            spec.loopFrames,
            chordWidth,
            vocalReverb,
            track.events.hashCode(),
        ).joinToString("|")
        synchronized(cache) { cache[key] }?.let { return it }
        val raw = if (track.type.kind == TrackKind.RAW) readRaw(track) else null
        return renderer.render(spec.copy(rawLoop = raw))?.also { synchronized(cache) { cache[key] = it } }
    }

    private fun readRaw(track: Track): FloatArray? {
        val path = track.takeFilePath ?: return null
        return try {
            takes.read(path).samples
        } catch (e: IOException) {
            AudioLog.w("read take ${track.type}", e)
            null
        }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    private companion object {
        const val MAX_ENTRIES = 12
        const val LOAD_FACTOR = 0.75f
    }
}
