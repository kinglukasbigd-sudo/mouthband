package com.mouthband.ui.arrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.audio.AudioEngine
import com.mouthband.audio.AudioSession
import com.mouthband.audio.LoopMath
import com.mouthband.data.KeptTake
import com.mouthband.data.SongRepository
import com.mouthband.data.TakeStore
import com.mouthband.data.TrackRepository
import com.mouthband.data.db.MouthBandDatabase
import com.mouthband.mix.MixLoader
import com.mouthband.mix.SongPlayer
import com.mouthband.model.DrumClass
import com.mouthband.model.DrumHit
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.NoteEvent
import com.mouthband.model.TrackEvents
import com.mouthband.model.TrackType
import com.mouthband.synth.InstrumentLibrary
import com.mouthband.synth.LoopRenderer
import com.mouthband.synth.RenderCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The per-track sheet of Arrange on a device, against the real song player and repositories:
 * choosing a sound saves it and plays the part alone, a muted part can still be heard, closing the
 * sheet ends the audition, and pan is clamped and saved.
 */
@RunWith(AndroidJUnit4::class)
class TrackSheetControllerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File
    private lateinit var db: MouthBandDatabase
    private lateinit var songs: SongRepository
    private lateinit var tracks: TrackRepository
    private lateinit var engine: AudioEngine
    private lateinit var player: SongPlayer
    private lateinit var scope: CoroutineScope
    private lateinit var state: MutableStateFlow<ArrangeState>
    private lateinit var controller: TrackSheetController
    private var ticking = 0
    private var songId = 0L

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DB)
        folder = File(context.cacheDir, "sheet-test").apply { deleteRecursively() }
        val takes = TakeStore(File(folder, "files"))
        db = MouthBandDatabase.create(context, DB)
        songs = SongRepository(db, takes)
        tracks = TrackRepository(db, takes)
        engine = AudioEngine(context)
        val rate = engine.sampleRate
        val cache = RenderCache(LoopRenderer(InstrumentLibrary(context.assets, rate), rate), takes, rate)
        player = SongPlayer(engine, AudioSession(context), MixLoader(cache, rate))
        songId = songs.createSong("Sheet", Mood.POP)
        val song = requireNotNull(songs.getSong(songId))
        val loop = LoopMath.loopFrames(song.loopBars, song.bpm, rate)
        tracks.keepTake(songId, TrackType.BEAT, KeptTake(null, rate, beat(rate, loop), Instrument.VOICE, 0.8f))
        tracks.keepTake(songId, TrackType.BASS, KeptTake(null, rate, bass(rate, loop), Instrument.SYNTH_BASS, 0.8f))
        state = MutableStateFlow(ArrangeState(song = song, tracks = tracks.getAll(songId)))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        controller = TrackSheetController(
            state,
            songs,
            tracks,
            player,
            scope,
            TrackSheetController.Hooks(stopAll = {}, interrupted = {}, ticking = { ticking++ }, levels = {}),
        )
    }

    @After
    fun tearDown() {
        player.stop()
        scope.cancel()
        db.close()
        context.deleteDatabase(DB)
        folder.deleteRecursively()
    }

    private fun bass(rate: Int, loop: Int) = TrackEvents(
        rate,
        loop,
        notes = listOf(NoteEvent(36, 0, loop / 4, 0.9f), NoteEvent(43, loop / 2, loop * 3 / 4, 0.8f)),
    )

    private fun beat(rate: Int, loop: Int) = TrackEvents(
        rate,
        loop,
        hits = listOf(DrumHit(DrumClass.KICK, 0, 1f), DrumHit(DrumClass.SNARE, loop / 4, 0.9f)),
    )

    private suspend fun until(what: String, condition: () -> Boolean) {
        withTimeout(TIMEOUT_MS) { while (!condition()) delay(POLL_MS) }
        assertTrue(what, condition())
    }

    private fun bassTrack() = state.value.tracks.first { it.type == TrackType.BASS }

    @Test
    fun choosingASoundSavesItAndPlaysThePartAlone() = runBlocking {
        controller.open(TrackType.BASS)
        assertEquals(TrackType.BASS, state.value.trackSheet)
        controller.selectSound(TrackType.BASS, Instrument.DEEP_SUB.name)
        assertEquals("the choice shows at once", Instrument.DEEP_SUB, bassTrack().instrument)
        until("the part is auditioned") { state.value.auditioning == TrackType.BASS && !state.value.preparing }
        assertTrue("the ticker was started for the audition", ticking >= 1)
        assertTrue(player.isPlaying)
        until("the choice is saved") {
            runBlocking { tracks.getAll(songId).first { it.type == TrackType.BASS }.instrument } == Instrument.DEEP_SUB
        }
    }

    @Test
    fun aDrumKitBelongsToTheSongAndIsSaved() = runBlocking {
        controller.selectSound(TrackType.BEAT, DrumKit.TRAP.name)
        assertEquals(DrumKit.TRAP, state.value.song?.kit)
        until("the kit is saved") { runBlocking { songs.getSong(songId)?.kit } == DrumKit.TRAP }
        until("the beat is auditioned") { state.value.auditioning == TrackType.BEAT && !state.value.preparing }
    }

    @Test
    fun aSoundThatDoesNotBelongToThePartIsRefused() = runBlocking {
        controller.selectSound(TrackType.BASS, Instrument.PIANO.name)
        controller.selectSound(TrackType.BASS, "NOT_A_SOUND")
        controller.selectSound(TrackType.BEAT, "NOT_A_KIT")
        assertEquals(Instrument.SYNTH_BASS, bassTrack().instrument)
        assertNull(state.value.auditioning)
        assertFalse(player.isPlaying)
    }

    @Test
    fun closingTheSheetEndsTheAudition() = runBlocking {
        controller.open(TrackType.BASS)
        controller.audition(TrackType.BASS)
        until("the audition plays") { state.value.auditioning == TrackType.BASS && !state.value.preparing }
        controller.close()
        assertNull(state.value.trackSheet)
        assertNull(state.value.auditioning)
        assertFalse("nothing keeps playing", player.isPlaying)
    }

    @Test
    fun aMutedPartCanStillBeHeardAndStaysMuted() = runBlocking {
        val muted = bassTrack().copy(muted = true)
        tracks.update(muted)
        state.value = state.value.copy(tracks = tracks.getAll(songId))
        controller.audition(TrackType.BASS)
        until("the muted part is auditioned") { state.value.auditioning == TrackType.BASS && !state.value.preparing }
        assertTrue("the saved track is still muted", bassTrack().muted)
    }

    @Test
    fun panIsClampedHeardAndSavedOnRelease() = runBlocking {
        controller.setPan(TrackType.BASS, 5f)
        assertEquals(1f, state.value.pans.getValue(TrackType.BASS), 0f)
        controller.setPan(TrackType.BASS, -0.4f)
        assertEquals(-0.4f, state.value.pans.getValue(TrackType.BASS), 1e-6f)
        controller.panDone(TrackType.BASS)
        assertTrue("the drag is over", state.value.pans.isEmpty())
        assertEquals(-0.4f, bassTrack().pan, 1e-6f)
        until("the pan is saved") {
            runBlocking { tracks.getAll(songId).first { it.type == TrackType.BASS }.pan } == -0.4f
        }
    }

    private companion object {
        const val DB = "sheet-test.db"
        const val TIMEOUT_MS = 30_000L
        const val POLL_MS = 50L
    }
}
