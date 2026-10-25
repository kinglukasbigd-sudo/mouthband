package com.mouthband.mix

import com.mouthband.audio.PanLaw
import com.mouthband.audio.RenderSource
import com.mouthband.dsp.Biquad
import com.mouthband.dsp.Compressor
import com.mouthband.dsp.Limiter
import com.mouthband.dsp.Reverb
import com.mouthband.dsp.Rms
import com.mouthband.dsp.timeCoefficient
import com.mouthband.model.TrackType

/**
 * The song mixer (spec §2.8, §2.9): plays the pre-rendered loop of every lane through the
 * arrangement with per-lane gain, pan and mute, the vocal reverb send, and — with auto-mix —
 * the bass low-pass, vocal ducking and the 2:1 bus compressor; the −1 dBFS limiter always runs.
 * The same class renders playback (on the audio thread) and export (offline), so both sound
 * identical. Allocation-free after construction.
 */
class SongMixer(private val sampleRate: Int) : RenderSource {
    class Lane {
        @Volatile
        var buffer: FloatArray? = null

        @Volatile
        var gainLeft = 1f

        @Volatile
        var gainRight = 1f

        @Volatile
        var muted = false

        fun setGainPan(gainDb: Float, pan: Float) {
            gainLeft = PanLaw.left(gainDb, pan)
            gainRight = PanLaw.right(gainDb, pan)
        }
    }

    val lanes = Array(TrackType.entries.size) { Lane() }

    @Volatile
    var layout: SongLayout? = null

    @Volatile
    var autoMix: Boolean = false

    private val bassLowLeft = Biquad().apply { coefficients = Biquad.lowPass(sampleRate, BASS_LOW_PASS_HZ) }
    private val bassLowRight = Biquad().apply { coefficients = Biquad.lowPass(sampleRate, BASS_LOW_PASS_HZ) }
    private val reverb = Reverb(sampleRate)
    private val busCompressor = Compressor(sampleRate, BUS_THRESHOLD_DB, BUS_RATIO, BUS_ATTACK_MS, BUS_RELEASE_MS)
    private val limiter = Limiter(sampleRate)
    private val duckDetect = timeCoefficient(DUCK_DETECT_MS, sampleRate)
    private val duckAttack = timeCoefficient(DUCK_ATTACK_MS, sampleRate)
    private val duckRelease = timeCoefficient(DUCK_RELEASE_MS, sampleRate)
    private val duckThreshold = Rms.fromDb(DUCK_THRESHOLD_DB).let { it * it }
    private val duckFloor = Rms.fromDb(DUCK_DB)
    private val block = FloatArray(MAX_BLOCK * 2)
    private val reverbIn = FloatArray(MAX_BLOCK)
    private var vocalPower = 0f
    private var duckGain = 1f
    private var othersL = 0f
    private var othersR = 0f
    private var vocalsL = 0f
    private var vocalsR = 0f

    override fun render(out: FloatArray, frames: Int, transportFrame: Long) {
        val l = layout ?: return
        var done = 0
        while (done < frames) {
            val n = minOf(MAX_BLOCK, frames - done)
            mixBlock(l, n, transportFrame + done)
            for (i in 0 until n * 2) out[done * 2 + i] += block[i]
            done += n
        }
    }

    /** Index of the section playing at [frame], or -1 past the end. */
    fun sectionPlaying(frame: Long): Int {
        val l = layout ?: return -1
        return if (frame < 0 || frame >= l.totalFrames) -1 else l.sectionAt(frame)
    }

    fun reset() {
        bassLowLeft.reset()
        bassLowRight.reset()
        reverb.reset()
        busCompressor.reset()
        limiter.reset()
        vocalPower = 0f
        duckGain = 1f
    }

    private fun mixBlock(l: SongLayout, n: Int, start: Long) {
        val auto = autoMix
        var section = l.sectionAt(start.coerceAtLeast(0))
        var sectionEnd = l.sectionEnd(section)
        for (i in 0 until n) {
            val frame = start + i
            while (frame >= sectionEnd && section + 1 < l.sectionCount) {
                section++
                sectionEnd = l.sectionEnd(section)
            }
            mixFrame(l, i, frame, section, auto)
        }
        reverb.processAdd(reverbIn, block, n, 1f)
        if (auto) busCompressor.processStereo(block, n)
        limiter.processStereo(block, n)
    }

    private fun mixFrame(l: SongLayout, i: Int, frame: Long, section: Int, auto: Boolean) {
        othersL = 0f
        othersR = 0f
        vocalsL = 0f
        vocalsR = 0f
        if (frame >= 0 && frame < l.totalFrames) {
            val mask = l.activeMasks[section]
            val pos = ((frame - l.sectionStarts[section]) % l.loopFrames).toInt()
            for (k in lanes.indices) {
                if (mask and (1 shl k) != 0) addLane(k, pos, l.loopFrames, auto)
            }
        }
        val vocalMono = 0.5f * (vocalsL + vocalsR)
        vocalPower = duckDetect * vocalPower + (1f - duckDetect) * vocalMono * vocalMono
        val target = if (auto && vocalPower > duckThreshold) duckFloor else 1f
        val coefficient = if (target < duckGain) duckAttack else duckRelease
        duckGain = coefficient * duckGain + (1f - coefficient) * target
        block[2 * i] = othersL * duckGain + vocalsL
        block[2 * i + 1] = othersR * duckGain + vocalsR
        reverbIn[i] = vocalMono * VocalChain.REVERB_SEND
    }

    /** Adds lane [k] at loop position [pos] to the vocal or the instrument sum. */
    private fun addLane(k: Int, pos: Int, loopFrames: Int, auto: Boolean) {
        val lane = lanes[k]
        val buffer = lane.buffer
        if (lane.muted || buffer == null || buffer.size < loopFrames * 2) return
        var sl = buffer[2 * pos] * lane.gainLeft
        var sr = buffer[2 * pos + 1] * lane.gainRight
        if (k == BASS && auto) {
            sl = bassLowLeft.process(sl)
            sr = bassLowRight.process(sr)
        }
        if (k == VERSE_VOCALS || k == CHORUS_VOCALS) {
            vocalsL += sl
            vocalsR += sr
        } else {
            othersL += sl
            othersR += sr
        }
    }

    companion object {
        const val MAX_BLOCK = 1024
        const val BASS_LOW_PASS_HZ = 2000f
        const val DUCK_THRESHOLD_DB = -30f
        const val DUCK_DB = -2f
        const val BUS_THRESHOLD_DB = -12f
        const val BUS_RATIO = 2f
        private const val BUS_ATTACK_MS = 20f
        private const val BUS_RELEASE_MS = 200f
        private const val DUCK_DETECT_MS = 50f
        private const val DUCK_ATTACK_MS = 20f
        private const val DUCK_RELEASE_MS = 250f
        private val BASS = TrackType.BASS.ordinal
        private val VERSE_VOCALS = TrackType.VOCALS_VERSE.ordinal
        private val CHORUS_VOCALS = TrackType.VOCALS_CHORUS.ordinal
    }
}
