package com.mouthband.audio

/**
 * Plays pre-rendered stereo loop buffers in sync with the transport (coach steps, review A/B,
 * examples). Loop position 0 sits at [loopStartFrame]; before it the lanes are silent.
 * All configuration is swapped by reference or written to volatile fields from the control
 * thread; [render] only reads.
 */
class LoopPlayer(laneCount: Int) : RenderSource {
    /** One lane: an interleaved stereo buffer of exactly loopFrames × 2 samples. */
    class Lane {
        @Volatile
        var buffer: FloatArray? = null

        @Volatile
        var gainLeft: Float = 1f

        @Volatile
        var gainRight: Float = 1f

        @Volatile
        var muted: Boolean = false

        /** Sets gain (dB) and pan (-1..1) with an equal-power law. */
        fun setGainPan(gainDb: Float, pan: Float) {
            gainLeft = PanLaw.left(gainDb, pan)
            gainRight = PanLaw.right(gainDb, pan)
        }
    }

    val lanes: Array<Lane> = Array(laneCount) { Lane() }

    @Volatile
    var loopFrames: Int = 0

    @Volatile
    var loopStartFrame: Long = 0L

    /** Stop playing at this transport frame (exclusive). */
    @Volatile
    var endFrame: Long = Long.MAX_VALUE

    override fun render(out: FloatArray, frames: Int, transportFrame: Long) {
        val length = loopFrames
        if (length <= 0) return
        for (lane in lanes) mixLane(out, frames, transportFrame, lane, length)
    }

    private fun mixLane(out: FloatArray, frames: Int, transportFrame: Long, lane: Lane, length: Int) {
        val buffer = lane.buffer
        if (buffer == null || lane.muted || buffer.size < length * 2) return
        val start = loopStartFrame
        val end = endFrame
        val gl = lane.gainLeft
        val gr = lane.gainRight
        for (i in 0 until frames) {
            val frame = transportFrame + i
            if (frame in start until end) {
                val pos = ((frame - start) % length).toInt()
                out[2 * i] += buffer[2 * pos] * gl
                out[2 * i + 1] += buffer[2 * pos + 1] * gr
            }
        }
    }

    fun clear() {
        lanes.forEach {
            it.buffer = null
            it.muted = false
            it.setGainPan(0f, 0f)
        }
        endFrame = Long.MAX_VALUE
    }

}

/** Plays a mono burst at scheduled transport frames (latency calibration). */
class BurstSource : RenderSource {
    class Schedule(val bursts: Array<FloatArray>, val frames: LongArray)

    @Volatile
    var schedule: Schedule? = null

    override fun render(out: FloatArray, frames: Int, transportFrame: Long) {
        val s = schedule ?: return
        for (k in s.frames.indices) mixBurst(out, frames, transportFrame, s.bursts[k], s.frames[k])
    }

    private fun mixBurst(out: FloatArray, frames: Int, transportFrame: Long, burst: FloatArray, begin: Long) {
        if (begin >= transportFrame + frames || begin + burst.size <= transportFrame) return
        for (i in 0 until frames) {
            val idx = transportFrame + i - begin
            if (idx >= 0 && idx < burst.size) {
                val v = burst[idx.toInt()]
                out[2 * i] += v
                out[2 * i + 1] += v
            }
        }
    }
}

/** Mixes several sources into one (e.g. a loop player plus a song mixer during review). */
class MixSource(private val sources: Array<RenderSource>) : RenderSource {
    override fun render(out: FloatArray, frames: Int, transportFrame: Long) {
        for (s in sources) s.render(out, frames, transportFrame)
    }
}
