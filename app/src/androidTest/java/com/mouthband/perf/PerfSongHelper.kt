package com.mouthband.perf

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.MouthBandApp
import com.mouthband.data.reachStep
import com.mouthband.model.CoachStep
import com.mouthband.model.Mood
import com.mouthband.model.TrackType
import com.mouthband.ui.gallery.DemoSongSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Setup and teardown for `tools/phone_record_perf.sh`: a throwaway song for measuring the recording
 * screen on a real phone without touching the owner's songs. They do nothing unless the run asks
 * for them (`-e perfSong seed` / `-e perfSong remove`), so the normal suite is unaffected.
 */
@RunWith(AndroidJUnit4::class)
class PerfSongHelper {
    private val container get() = ApplicationProvider.getApplicationContext<MouthBandApp>().container
    private val mode get() = InstrumentationRegistry.getArguments().getString("perfSong")

    private suspend fun removeAll() {
        container.repository.overviews().first().filter { it.song.title == TITLE }.forEach {
            container.repository.delete(it.song.id)
        }
    }

    @Test
    fun seedForPerfRun() {
        assumeTrue("only when asked", mode == "seed")
        runBlocking {
            removeAll()
            DemoSongSeeder(
                container.repository,
                container.trackRepository,
                container.loopRenderer,
                container.engine.sampleRate,
            ).create(TITLE)
        }
    }

    /**
     * A song with no kept takes that opens at the Bass (or Beat) step: nothing plays but the click,
     * so the microphone only hears the room.
     */
    @Test
    fun seedEmptySong() {
        assumeTrue("only when asked", mode == "seedEmpty")
        val beat = InstrumentationRegistry.getArguments().getString("perfStep") == "BEAT"
        val step = if (beat) CoachStep.BEAT else CoachStep.BASS
        runBlocking {
            removeAll()
            val id = container.repository.createSong(TITLE, Mood.POP)
            container.repository.reachStep(id, step)
        }
    }

    @Test
    fun removeAfterPerfRun() {
        assumeTrue("only when asked", mode == "remove")
        runBlocking { removeAll() }
    }

    /** Prints what a real-microphone take became (`tools/phone_mic_test.sh`), one line per note or hit. */
    @Test
    fun dumpKeptTakes() {
        assumeTrue("only when asked", mode == "dump")
        runBlocking {
            val song = container.repository.overviews().first().first { it.song.title == TITLE }.song
            Log.i(TAG, "song bpm=${song.bpm} bars=${song.loopBars} key=${song.key}")
            container.trackRepository.getAll(song.id).filter { it.type == TrackType.BASS || it.type == TrackType.BEAT }
                .forEach { track ->
                    val events = track.events ?: return@forEach
                    Log.i(TAG, "${track.type} loopFrames=${events.loopFrames} rate=${events.sampleRate} " +
                        "notes=${events.notes.size} hits=${events.hits.size}")
                    events.notes.forEach { Log.i(TAG, "  note midi=${it.midi} ${it.startFrame}..${it.endFrame}") }
                    events.hits.forEach { Log.i(TAG, "  hit ${it.cls} at ${it.frameInLoop}") }
                }
        }
    }

    private companion object {
        const val TAG = "PerfDump"
        const val TITLE = "Perf song"
    }
}
