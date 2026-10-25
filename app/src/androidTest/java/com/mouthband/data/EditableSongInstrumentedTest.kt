package com.mouthband.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.audio.LoopMath
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.mix.MixLoader
import com.mouthband.mix.SongMixer
import com.mouthband.model.Arrangement
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.NoteEvent
import com.mouthband.model.Section
import com.mouthband.model.SectionType
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackType
import com.mouthband.synth.InstrumentLibrary
import com.mouthband.synth.LoopRenderer
import com.mouthband.synth.RenderCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A finished song stays editable: another sound per part, pan and level, edited notes, a part
 * recorded again, a copy and a delete, all through the same repositories the app uses.
 */
@RunWith(AndroidJUnit4::class)
class EditableSongInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = 48_000
    private lateinit var folder: File
    private lateinit var db: MouthBandDatabase
    private lateinit var takes: TakeStore
    private lateinit var songs: SongRepository
    private lateinit var tracks: TrackRepository

    @Before
    fun setUp() {
        context.deleteDatabase(DB)
        folder = File(context.cacheDir, "editable-test").apply { deleteRecursively() }
        takes = TakeStore(File(folder, "files"))
        db = MouthBandDatabase.create(context, DB)
        songs = SongRepository(db, takes)
        tracks = TrackRepository(db, takes)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(DB)
        folder.deleteRecursively()
    }

    private fun bassEvents(loop: Int) = TrackEvents(
        rate,
        loop,
        notes = listOf(NoteEvent(36, 0, loop / 4, 0.9f), NoteEvent(43, loop / 2, loop / 2 + loop / 4, 0.8f)),
    )

    private fun beatEvents(loop: Int) = TrackEvents(
        rate,
        loop,
        hits = listOf(
            DrumHit(DrumClass.KICK, 0, 1f),
            DrumHit(DrumClass.SNARE, loop / 4, 0.9f),
            DrumHit(DrumClass.HIHAT, loop / 8, 0.7f),
        ),
    )

    private suspend fun finishedSong(): Long {
        val id = songs.createSong("Editable", Mood.POP)
        val song = requireNotNull(songs.getSong(id))
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        tracks.keepTake(id, TrackType.BEAT, KeptTake(FloatArray(loop), rate, beatEvents(loop), Instrument.VOICE, 0.8f))
        tracks.keepTake(
            id,
            TrackType.BASS,
            KeptTake(FloatArray(loop) { 0.1f }, rate, bassEvents(loop), Instrument.SYNTH_BASS, 0.8f),
        )
        return id
    }

    private fun cache() = RenderCache(LoopRenderer(InstrumentLibrary(context.assets, rate), rate), takes, rate)

    @Test
    fun everySoundOfAPartIsSavedAndSoundsDifferent() = runBlocking {
        val id = finishedSong()
        val song = requireNotNull(songs.getSong(id))
        val cache = cache()
        val bass = tracks.getAll(id).first { it.type == TrackType.BASS }
        val rendered = Instrument.forTrack(TrackType.BASS).map { instrument ->
            tracks.update(bass.copy(instrument = instrument))
            val saved = tracks.getAll(id).first { it.type == TrackType.BASS }
            assertEquals(instrument, saved.instrument)
            requireNotNull(cache.loopOf(song, saved)).contentHashCode()
        }
        assertEquals("every bass sound renders differently", rendered.size, rendered.toSet().size)
    }

    @Test
    fun everyDrumKitIsSavedWithTheSongAndSoundsDifferent() = runBlocking {
        val id = finishedSong()
        val cache = cache()
        val beat = tracks.getAll(id).first { it.type == TrackType.BEAT }
        val rendered = DrumKit.entries.map { kit ->
            val song = requireNotNull(songs.getSong(id)).copy(kit = kit)
            songs.updateSong(song)
            assertEquals(kit, requireNotNull(songs.getSong(id)).kit)
            requireNotNull(cache.loopOf(song, beat)).contentHashCode()
        }
        assertTrue("kits render differently: $rendered", rendered.toSet().size >= DrumKit.entries.size - 1)
    }

    /** Notes across each part's range, so a sound is judged on its low and high end too. */
    private fun rangeEvents(type: TrackType, loop: Int): TrackEvents {
        val pitches = when (type) {
            TrackType.BASS -> listOf(33, 36, 40, 43)
            TrackType.CHORDS -> listOf(52, 55, 59, 64)
            else -> listOf(57, 64, 72, 81)
        }
        val slot = loop / pitches.size
        return TrackEvents(
            rate,
            loop,
            notes = pitches.mapIndexed { i, midi -> NoteEvent(midi, i * slot, i * slot + slot * 3 / 4, 0.9f) },
        )
    }

    private fun assertClean(name: String, loop: FloatArray) {
        assertTrue("$name: every sample is a number", loop.all { it.isFinite() })
        val peak = loop.maxOf { kotlin.math.abs(it) }
        val rms = kotlin.math.sqrt(loop.sumOf { (it * it).toDouble() } / loop.size).toFloat()
        val rmsDb = 20 * kotlin.math.log10(rms.coerceAtLeast(1e-9f))
        val mean = loop.sumOf { it.toDouble() } / loop.size
        assertTrue("$name: audible (peak $peak, ${rmsDb.toInt()} dBFS)", peak > 0.02f && rmsDb > -50f)
        assertTrue("$name: sane level (peak $peak)", peak < 2f)
        assertTrue("$name: no DC offset ($mean)", kotlin.math.abs(mean) < 0.02)
    }

    @Test
    fun everySoundOfEveryPartIsAudibleCleanAndTheRightLength() = runBlocking {
        val id = finishedSong()
        val song = requireNotNull(songs.getSong(id))
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        val cache = cache()
        val rows = tracks.getAll(id)
        for (type in listOf(TrackType.BASS, TrackType.CHORDS, TrackType.MELODY)) {
            val first = Instrument.forTrack(type).first()
            tracks.keepTake(id, type, KeptTake(null, rate, rangeEvents(type, loop), first, 0.8f))
            for (instrument in Instrument.forTrack(type)) {
                val track = tracks.getAll(id).first { it.type == type }
                tracks.update(track.copy(instrument = instrument))
                val rendered = requireNotNull(cache.loopOf(song, tracks.getAll(id).first { it.type == type }))
                assertEquals("$instrument: stereo loop length", loop * 2, rendered.size)
                assertClean("$type/$instrument", rendered)
            }
        }
        val beat = rows.first { it.type == TrackType.BEAT }
        for (kit in DrumKit.entries) {
            val rendered = requireNotNull(cache.loopOf(song.copy(kit = kit), beat))
            assertEquals("$kit: stereo loop length", loop * 2, rendered.size)
            assertClean("BEAT/$kit", rendered)
        }
    }

    @Test
    fun panLevelAndMuteSurviveAndReachTheMix() = runBlocking {
        val id = finishedSong()
        val bass = tracks.getAll(id).first { it.type == TrackType.BASS }
        tracks.update(bass.copy(pan = -0.7f, gainDb = -9f, muted = false))
        val saved = tracks.getAll(id).first { it.type == TrackType.BASS }
        assertEquals(-0.7f, saved.pan, 1e-6f)
        assertEquals(-9f, saved.gainDb, 1e-6f)
        val song = requireNotNull(songs.getSong(id))
        val mixer = SongMixer(rate)
        assertTrue(MixLoader(cache(), rate).load(mixer, song, tracks.getAll(id)))
        assertNotNull(mixer.lanes[TrackType.BASS.ordinal].buffer)
        tracks.update(saved.copy(muted = true))
        assertTrue(tracks.getAll(id).first { it.type == TrackType.BASS }.muted)
    }

    @Test
    fun aMutedPartIsSilentInTheMixButCanBeAuditionedUnmuted() = runBlocking {
        val id = finishedSong()
        val song = requireNotNull(songs.getSong(id))
        val bass = tracks.getAll(id).first { it.type == TrackType.BASS }
        tracks.update(bass.copy(muted = true))
        val muted = tracks.getAll(id)
        val section = Section(SectionType.VERSE, song.loopBars * 2, setOf(TrackType.BASS))
        val solo = song.copy(arrangement = Arrangement(listOf(section)))
        val loader = MixLoader(cache(), rate)
        assertFalse("muted: nothing to hear", loader.load(SongMixer(rate), solo, muted))
        val unmutedForAudition = muted.map { if (it.type == TrackType.BASS) it.copy(muted = false) else it }
        assertTrue("unmuted for the audition: audible", loader.load(SongMixer(rate), solo, unmutedForAudition))
        assertTrue("the saved track stays muted", tracks.getAll(id).first { it.type == TrackType.BASS }.muted)
    }

    @Test
    fun editedNotesReplaceTheOldOnesAndRecordingAgainKeepsOnlyTheNewFile() = runBlocking {
        val id = finishedSong()
        val song = requireNotNull(songs.getSong(id))
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        val before = tracks.getAll(id).first { it.type == TrackType.BASS }
        val oldFile = File(requireNotNull(before.takeFilePath))
        assertTrue(oldFile.exists())
        // Delete one note (as the editor does) and save it as a new version of the same take.
        val edited = requireNotNull(before.events).copy(notes = requireNotNull(before.events).notes.drop(1))
        val saved = tracks.keepTake(
            id,
            TrackType.BASS,
            KeptTake(FloatArray(loop) { 0.2f }, rate, edited, before.instrument, before.quantizeStrength),
        )
        assertEquals(before.takeVersion + 1, saved.takeVersion)
        assertEquals(1, tracks.getAll(id).first { it.type == TrackType.BASS }.events?.notes?.size)
        assertFalse("the replaced take file is removed", oldFile.exists())
        assertTrue(File(requireNotNull(saved.takeFilePath)).exists())
        val files = takes.songDir(id).listFiles { f -> f.name.startsWith("bass") }.orEmpty()
        assertEquals("only the newest bass take stays on disk", 1, files.size)
    }

    @Test
    fun aCopyIsIndependentAndDeletingASongRemovesItsFiles() = runBlocking {
        val id = finishedSong()
        val copyId = requireNotNull(songs.duplicate(id, "Editable copy"))
        val original = tracks.getAll(id).first { it.type == TrackType.BASS }
        val copy = tracks.getAll(copyId).first { it.type == TrackType.BASS }
        assertNotEquals(original.takeFilePath, copy.takeFilePath)
        assertTrue(File(requireNotNull(copy.takeFilePath)).exists())
        tracks.update(copy.copy(instrument = Instrument.DEEP_SUB, pan = 0.5f))
        assertEquals(Instrument.SYNTH_BASS, tracks.getAll(id).first { it.type == TrackType.BASS }.instrument)

        songs.delete(id)
        assertNull(songs.getSong(id))
        assertTrue(tracks.getAll(id).isEmpty())
        assertFalse("deleted song's folder is gone", takes.songDir(id).exists())
        assertNotNull("the copy is untouched", songs.getSong(copyId))
        val keptCopy = tracks.getAll(copyId).first { it.type == TrackType.BASS }
        assertTrue(File(requireNotNull(keptCopy.takeFilePath)).exists())
    }

    private companion object {
        const val DB = "editable-test.db"
    }
}
