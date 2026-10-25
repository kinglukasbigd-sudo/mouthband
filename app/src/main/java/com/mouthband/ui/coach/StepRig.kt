package com.mouthband.ui.coach

import com.mouthband.audio.AudioEngine
import com.mouthband.audio.LoopMath
import com.mouthband.audio.LoopPlayer
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import com.mouthband.synth.RenderCache

/**
 * The loops a recording step plays: one lane per earlier track (backing) plus a "candidate"
 * lane for the new take, the A/B raw take or the example demo. Built off the main thread;
 * lanes are swapped by reference so the render thread never waits.
 */
class StepRig(private val engine: AudioEngine, private val cache: RenderCache) {
    val player = LoopPlayer(TrackType.entries.size + 1)
    private val candidateLane get() = player.lanes[TrackType.entries.size]

    /** Loads every other kept instrument track; vocals never play under a new take. */
    fun loadBacking(song: Song, tracks: List<Track>, recording: TrackType) {
        player.loopFrames = LoopMath.loopFrames(song.loopBars, song.bpm, engine.sampleRate)
        for (track in tracks) {
            val lane = player.lanes[track.type.ordinal]
            val use = track.type != recording && !track.type.isVocals && track.hasContent
            lane.buffer = if (use) cache.loopOf(song, track) else null
            lane.setGainPan(track.gainDb, track.pan)
            lane.muted = track.muted
        }
    }

    fun setCandidate(buffer: FloatArray?) {
        candidateLane.buffer = buffer
    }

    /** Starts looping from bar 1 (review, examples). */
    fun play(): Boolean {
        if (!engine.start()) return false
        player.loopStartFrame = 0
        player.endFrame = Long.MAX_VALUE
        engine.metronome.plan = null
        engine.source = player
        engine.resetTransport(0)
        return true
    }

    fun stop() {
        if (engine.source === player) engine.source = null
    }

    /** Position in the loop being heard, 0..1, or -1 when the rig is not playing. */
    fun playheadFraction(): Float {
        val loop = player.loopFrames
        if (engine.source !== player || loop <= 0) return -1f
        val heard = engine.writtenTransportFrame() - engine.framesPerBuffer * HEARD_BUFFERS
        if (heard < 0) return -1f
        return (heard % loop).toFloat() / loop
    }

    private companion object {
        const val HEARD_BUFFERS = 2
    }
}
