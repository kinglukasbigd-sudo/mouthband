package com.mouthband.export

import com.mouthband.audio.LoopMath
import com.mouthband.dsp.Resampler
import com.mouthband.mix.AutoMix
import com.mouthband.mix.Fader
import com.mouthband.mix.SongLayout
import com.mouthband.mix.SongMixer
import com.mouthband.model.Arrangement
import com.mouthband.model.Section
import com.mouthband.model.SectionType
import com.mouthband.model.TrackType
import com.mouthband.synth.WavReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ExportTest {
    @Test
    fun wavHeaderIsCanonical16BitPcm() {
        val dataBytes = 44_100L * 4 * 3
        val b = ByteBuffer.wrap(WavWriter.header(44_100, 2, dataBytes)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(WavWriter.HEADER_BYTES, b.capacity())
        assertEquals("RIFF", ascii(b, 0))
        assertEquals(36 + dataBytes, b.getInt(4).toLong())
        assertEquals("WAVE", ascii(b, 8))
        assertEquals("fmt ", ascii(b, 12))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(2, b.getShort(22).toInt()) // stereo
        assertEquals(44_100, b.getInt(24))
        assertEquals(44_100 * 4, b.getInt(28)) // byte rate
        assertEquals(4, b.getShort(32).toInt()) // block align
        assertEquals(16, b.getShort(34).toInt()) // bits
        assertEquals("data", ascii(b, 36))
        assertEquals(dataBytes, b.getInt(40).toLong())
    }

    private fun ascii(b: ByteBuffer, at: Int) = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)

    @Test
    fun wavWriterPatchesSizesAndRoundTrips() {
        val file = File.createTempFile("export", ".wav")
        try {
            val frames = 1000
            val stereo = FloatArray(frames * 2) { if (it % 2 == 0) 0.5f else -0.25f }
            WavWriter(file, 44_100).use { it.write(stereo, frames) }
            assertEquals(WavWriter.HEADER_BYTES + frames * 4L, file.length())
            val decoded = file.inputStream().use { WavReader.read(it) }
            assertEquals(44_100, decoded.sampleRate)
            assertEquals(frames, decoded.samples.size)
            // Mono mixdown of (0.5, −0.25) is 0.125 within one 16-bit step.
            assertTrue(decoded.samples.all { abs(it - 0.125f) < 1e-4f })
        } finally {
            file.delete()
        }
    }

    @Test
    fun pcmConversionClipsAndRounds() {
        assertEquals(32767, WavWriter.toPcm16(1.5f).toInt())
        assertEquals(-32767, WavWriter.toPcm16(-2f).toInt())
        assertEquals(0, WavWriter.toPcm16(0f).toInt())
        assertEquals(16384, WavWriter.toPcm16(0.5f).toInt())
    }

    @Test
    fun renderLengthIsTheSumOfSectionBarsWithinOneFrame() {
        for (rate in listOf(44_100, 48_000)) {
            for (bpm in 60..180 step 7) {
                for (loopBars in listOf(2, 4)) {
                    val layout = SongLayout.of(Arrangement.Default, bpm, loopBars, rate)
                    val exact = Arrangement.Default.totalBars * LoopMath.framesPerBar(bpm, rate)
                    assertTrue("$rate Hz $bpm BPM", abs(layout.totalFrames - exact) <= 1.0)
                    // Every section starts on its own bar within one frame.
                    var bars = 0
                    Arrangement.Default.sections.forEachIndexed { i, section ->
                        assertTrue(abs(layout.sectionStarts[i] - bars * LoopMath.framesPerBar(bpm, rate)) <= 1.0)
                        bars += section.bars
                    }
                }
            }
        }
    }

    @Test
    fun mixerRendersExactlyTheSongThenSilence() {
        val rate = 48_000
        val mixer = loadedMixer(rate, bpm = 120)
        val layout = requireNotNull(mixer.layout)
        val total = layout.totalFrames + rate
        val out = FloatArray(total.toInt() * 2)
        var frame = 0L
        while (frame < total) {
            val n = minOf(SongMixer.MAX_BLOCK.toLong(), total - frame).toInt()
            val block = FloatArray(n * 2)
            mixer.render(block, n, frame)
            System.arraycopy(block, 0, out, frame.toInt() * 2, n * 2)
            frame += n
        }
        // Sound up to the last frame of the song; after the song only the decaying reverb/limiter.
        val lastBar = (layout.totalFrames - LoopMath.framesPerBar(120, rate).toLong()).toInt()
        assertTrue(peak(out, lastBar, layout.totalFrames.toInt()) > 0.1f)
        assertTrue(peak(out, layout.totalFrames.toInt() + rate / 2, total.toInt()) < 1e-3f)
        assertTrue("limiter ceiling −1 dBFS", peak(out, 0, total.toInt()) <= 0.8913f)
    }

    @Test
    fun sectionsTurnTracksOnAndOff() {
        val rate = 48_000
        val bpm = 120
        val arrangement = Arrangement(
            listOf(
                Section(SectionType.VERSE, 2, setOf(TrackType.BEAT)),
                Section(SectionType.CHORUS, 2, setOf(TrackType.BASS)),
            ),
        )
        val loopFrames = LoopMath.loopFrames(2, bpm, rate)
        val mixer = SongMixer(rate)
        mixer.lanes[TrackType.BEAT.ordinal].buffer = FloatArray(loopFrames * 2) { 0.1f }
        mixer.lanes[TrackType.BASS.ordinal].buffer = FloatArray(loopFrames * 2) { 0.2f }
        mixer.layout = SongLayout.of(arrangement, bpm, 2, rate)
        val out = FloatArray(loopFrames * 2 * 2)
        renderAll(mixer, out, loopFrames * 2L)
        // Verse: only the beat lane (0.1), chorus: only the bass lane (0.2).
        assertEquals(0.1f, out[2 * (loopFrames / 2)], 0.02f)
        assertEquals(0.2f, out[2 * (loopFrames + loopFrames / 2)], 0.03f)
        // Muting the bass silences the chorus.
        mixer.reset()
        mixer.lanes[TrackType.BASS.ordinal].muted = true
        val muted = FloatArray(loopFrames * 2 * 2)
        renderAll(mixer, muted, loopFrames * 2L)
        assertTrue(peak(muted, loopFrames + loopFrames / 4, 2 * loopFrames) < 0.01f)
    }

    @Test
    fun streamingResamplerMatchesTheOfflineResampler() {
        val from = 48_000
        val to = 44_100
        val mono = FloatArray(from) { (0.5 * sin(2 * PI * 997 * it / from)).toFloat() }
        val stereo = FloatArray(mono.size * 2) { mono[it / 2] }
        val collected = ArrayList<Float>()
        val resampler = StreamingResampler(from, to) { buffer, frames ->
            for (i in 0 until frames) collected += buffer[2 * i]
        }
        var offset = 0
        val chunk = 1234
        while (offset < mono.size) {
            val n = minOf(chunk, mono.size - offset)
            resampler.push(stereo.copyOfRange(offset * 2, (offset + n) * 2), n)
            offset += n
        }
        resampler.finish()
        val offline = Resampler.resample(mono, from, to)
        assertTrue("length ${collected.size} vs ${offline.size}", abs(collected.size - offline.size) <= 1)
        for (i in 0 until minOf(collected.size, offline.size)) {
            assertEquals("sample $i", offline[i], collected[i], 1e-4f)
        }
    }

    @Test
    fun autoMixTableAndFader() {
        assertEquals(0f to 0f, AutoMix.settingsFor(TrackType.BEAT))
        assertEquals(-2f to 0f, AutoMix.settingsFor(TrackType.BASS))
        assertEquals(-6f to 0f, AutoMix.settingsFor(TrackType.CHORDS))
        assertEquals(-4f to 0.1f, AutoMix.settingsFor(TrackType.MELODY))
        assertEquals(1f to 0f, AutoMix.settingsFor(TrackType.VOCALS_VERSE))
        assertEquals(1f to 0f, AutoMix.settingsFor(TrackType.VOCALS_CHORUS))
        for (db in listOf(-29f, -12f, 0f, 6f)) assertEquals(db, Fader.toDb(Fader.toPosition(db)), 1e-4f)
        // The bottom of the travel is silence.
        assertEquals(Fader.SILENT_DB, Fader.toDb(0f))
        assertEquals(0f, Fader.toPosition(Fader.SILENT_DB))
    }

    private fun loadedMixer(rate: Int, bpm: Int): SongMixer {
        val loopFrames = LoopMath.loopFrames(2, bpm, rate)
        val mixer = SongMixer(rate)
        TrackType.entries.forEachIndexed { k, _ ->
            val hz = 110.0 + 55 * k
            mixer.lanes[k].buffer = FloatArray(loopFrames * 2) { (0.2 * sin(2 * PI * hz * (it / 2) / rate)).toFloat() }
        }
        mixer.layout = SongLayout.of(Arrangement.Default, bpm, 2, rate)
        return mixer
    }

    private fun renderAll(mixer: SongMixer, out: FloatArray, frames: Long) {
        val block = FloatArray(SongMixer.MAX_BLOCK * 2)
        var frame = 0L
        while (frame < frames) {
            val n = minOf(SongMixer.MAX_BLOCK.toLong(), frames - frame).toInt()
            block.fill(0f)
            mixer.render(block, n, frame)
            System.arraycopy(block, 0, out, frame.toInt() * 2, n * 2)
            frame += n
        }
    }

    private fun peak(x: FloatArray, fromFrame: Int, toFrame: Int): Float {
        var p = 0f
        for (i in fromFrame * 2 until minOf(x.size, toFrame * 2)) p = maxOf(p, abs(x[i]))
        return p
    }

    @Test
    fun headerSizesForAnEmptyFileAreZero() {
        val b = ByteBuffer.wrap(WavWriter.header(48_000, 2, 0)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, b.getInt(4))
        assertEquals(0, b.getInt(40))
    }
}
