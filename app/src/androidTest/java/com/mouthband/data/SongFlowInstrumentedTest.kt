package com.mouthband.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.audio.AlignedTake
import com.mouthband.audio.LoopMath
import com.mouthband.convert.BeatboxConverter
import com.mouthband.convert.PitchConverter
import com.mouthband.convert.SwiftF0PitchTracker
import com.mouthband.convert.TakeProcessor
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.export.ExportRenderer
import com.mouthband.export.WavWriter
import com.mouthband.mix.MixLoader
import com.mouthband.mix.SongMixer
import com.mouthband.model.DrumClass
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.Song
import com.mouthband.model.Track
import com.mouthband.model.TrackType
import com.mouthband.synth.InstrumentLibrary
import com.mouthband.synth.LoopRenderer
import com.mouthband.synth.RenderCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Phase 5/6 end to end on a device, minus the microphone (the emulator has none): synthetic
 * beatbox, hummed and sung takes go through the real converters (SwiftF0 on ONNX Runtime), are
 * kept through the repositories, survive closing and reopening the database (process death),
 * then the whole song is rendered and encoded to WAV + M4A.
 */
@RunWith(AndroidJUnit4::class)
class SongFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = 48_000
    private lateinit var folder: File

    @Before
    fun setUp() {
        context.deleteDatabase(DB)
        folder = File(context.cacheDir, "flow-test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB)
        folder.deleteRecursively()
    }

    @Test
    fun keptTakesSurviveARestartAndTheSongExports() = runBlocking {
        val takes = TakeStore(File(folder, "files"))
        val songId = keepThreeTakes(takes)

        // "Process death": new database and repository instances read everything back.
        val db = MouthBandDatabase.create(context, DB)
        try {
            val song = requireNotNull(SongRepository(db, takes).getSong(songId))
            val tracks = TrackRepository(db, takes).getAll(songId)
            val beat = tracks.first { it.type == TrackType.BEAT }
            val bass = tracks.first { it.type == TrackType.BASS }
            val vocals = tracks.first { it.type == TrackType.VOCALS_VERSE }
            assertTrue(beat.hasContent && bass.hasContent && vocals.hasContent)
            val hits = requireNotNull(beat.events).hits
            assertEquals(setOf(DrumClass.KICK, DrumClass.SNARE, DrumClass.HIHAT), hits.map { it.cls }.toSet())
            assertTrue(requireNotNull(bass.events).notes.all { it.midi in 28..43 })
            val raw = takes.read(requireNotNull(vocals.takeFilePath))
            assertEquals(LoopMath.loopFrames(song.loopBars, song.bpm, rate), raw.samples.size)
            exportAndCheck(song, tracks, takes)
        } finally {
            db.close()
        }
    }

    private suspend fun keepThreeTakes(takes: TakeStore): Long {
        val db = MouthBandDatabase.create(context, DB)
        try {
            val songs = SongRepository(db, takes)
            val tracks = TrackRepository(db, takes)
            val id = songs.createSong("Flow", Mood.POP)
            val song = requireNotNull(songs.getSong(id))
            val processor = TakeProcessor(
                rate,
                BeatboxConverter(rate),
                PitchConverter(rate, primary = { SwiftF0PitchTracker.load(context.assets) }),
            )
            val ctx = TakeProcessor.Context(song.bpm, song.key, templates = null, clicksAudible = false)
            val beat = processor.process(TrackType.BEAT, beatboxTake(song), ctx)
            assertEquals(null, beat.problem)
            tracks.keepTake(id, TrackType.BEAT, KeptTake(null, rate, beat.events, Instrument.VOICE, 0.8f))
            val bass = processor.process(TrackType.BASS, humTake(song), ctx)
            assertEquals(null, bass.problem)
            tracks.keepTake(id, TrackType.BASS, KeptTake(null, rate, bass.events, Instrument.SYNTH_BASS, 0.8f))
            val sung = humTake(song)
            tracks.keepTake(id, TrackType.VOCALS_VERSE, KeptTake(sung.loopOnly(), rate, null, Instrument.VOICE, 0f))
            return id
        } finally {
            db.close()
        }
    }

    private suspend fun exportAndCheck(song: Song, tracks: List<Track>, takes: TakeStore) {
        val library = InstrumentLibrary(context.assets, rate)
        val cache = RenderCache(LoopRenderer(library, rate), takes, rate)
        val mixer = SongMixer(rate)
        assertTrue(MixLoader(cache, rate).load(mixer, song, tracks))
        val wav = File(folder, "song.wav")
        val m4a = File(folder, "song.m4a")
        var lastProgress = 0f
        val result = ExportRenderer(rate).render(mixer, wav, m4a) { p ->
            assertTrue("progress never goes back", p >= lastProgress)
            lastProgress = p
        }
        assertEquals(1f, lastProgress, 1e-6f)
        val songFrames = requireNotNull(mixer.layout).totalFrames
        val exact = song.arrangement.totalBars * LoopMath.framesPerBar(song.bpm, rate)
        assertTrue("render length = sum of section bars ± 1 frame", abs(songFrames - exact) <= 1.0)
        val expectedFrames = (songFrames + ExportRenderer.TAIL_SECONDS * rate) * ExportRenderer.EXPORT_RATE / rate
        val wavFrames = (wav.length() - WavWriter.HEADER_BYTES) / (2 * WavWriter.BYTES_PER_SAMPLE)
        assertTrue("wav frames $wavFrames vs $expectedFrames", abs(wavFrames - expectedFrames) <= 2)
        assertEquals(songFrames * 1000 / rate, result.durationMs)
        val head = m4a.readBytes().copyOfRange(4, 8).toString(Charsets.US_ASCII)
        assertEquals("ftyp", head)
        assertTrue("M4A at roughly 192 kbps", m4a.length() > expectedFrames / 44_100.0 * 150_000 / 8)
        assertNotNull(result)
    }

    /** boom · tss · pff · tss on 8th notes for two bars. */
    private fun beatboxTake(song: Song): AlignedTake {
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        val preroll = rate / 10
        val samples = FloatArray(preroll + loop + rate / 4)
        val eighth = LoopMath.framesPerBeat(song.bpm, rate) / 2
        val random = Random(SEED)
        for (i in 0 until song.loopBars * 8) {
            val cls = when (i % 4) {
                0 -> DrumClass.KICK
                2 -> DrumClass.SNARE
                else -> DrumClass.HIHAT
            }
            addHit(samples, preroll + (i * eighth).toInt(), cls, random)
        }
        return AlignedTake(samples, preroll, loop, rate)
    }

    private fun addHit(out: FloatArray, at: Int, cls: DrumClass, random: Random) {
        val length = (0.08 * rate).toInt()
        var hp = 0f
        var prev = 0f
        var lp = 0f
        for (n in 0 until length) {
            if (at + n >= out.size) return
            val t = n.toDouble() / rate
            val v = when (cls) {
                DrumClass.KICK -> sin(2 * PI * (60 + 90 * exp(-t * 40)) * t) * exp(-t * 25)
                DrumClass.SNARE -> {
                    lp += 0.25f * (random.nextGaussian().toFloat() - lp)
                    (lp * 1.5 + sin(2 * PI * 200 * t) * 0.3) * exp(-t * 35)
                }
                DrumClass.HIHAT -> {
                    val noise = random.nextGaussian().toFloat()
                    hp = 0.9f * (hp + noise - prev)
                    prev = noise
                    hp * exp(-t * 60)
                }
            }
            out[at + n] += (v * 0.5).toFloat()
        }
    }

    /** A hummed line, one note per beat: C3, G3, A3, F3 (harmonics + light vibrato). */
    private fun humTake(song: Song): AlignedTake {
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        val preroll = rate / 10
        val samples = FloatArray(preroll + loop + rate / 4)
        val beat = LoopMath.framesPerBeat(song.bpm, rate)
        val line = listOf(48, 55, 57, 53)
        for (i in 0 until song.loopBars * 4) {
            val hz = 440.0 * 2.0.pow((line[i % line.size] - 69) / 12.0)
            val start = preroll + (i * beat).toInt()
            val n = (beat * 0.8).toInt()
            var phase = 0.0
            for (k in 0 until n) {
                phase += 2 * PI * hz * (1 + 0.004 * sin(2 * PI * 5.5 * k / rate)) / rate
                var v = 0.0
                for (h in 1..10) v += sin(h * phase) / h
                val env = minOf(1.0, k / (0.01 * rate), (n - k) / (0.01 * rate))
                samples[start + k] += (0.2 * env * v).toFloat()
            }
        }
        return AlignedTake(samples, preroll, loop, rate)
    }

    private companion object {
        const val DB = "flow-test.db"
        const val SEED = 7L
    }
}
