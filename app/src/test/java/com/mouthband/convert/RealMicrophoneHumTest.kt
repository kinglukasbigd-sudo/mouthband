package com.mouthband.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A real recording: a hummed line (C4, C4, G4, A4, repeating) played through a speaker and captured
 * by a phone's microphone with the app's own capture path (`MicProbe`, 48 kHz). It carries room
 * sound, speaker colouring and microphone noise that synthetic hum does not. The live line must
 * follow the notes in order, without jumps.
 */
class RealMicrophoneHumTest {
    private val estimates = ArrayList<Pair<Double, Float>>()
    private var rate = 0

    private fun runLiveSink() {
        val bytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("real_mic_hum_c4_g4_a4.wav")).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        rate = buffer.getInt(RATE_OFFSET)
        val audio = FloatArray((bytes.size - HEADER) / 2) { buffer.getShort(HEADER + 2 * it) / PCM_SCALE }
        val sink = LivePitchSink(rate) { centre, midi -> estimates.add(centre.toDouble() / rate to midi) }
        var at = 0
        while (at < audio.size) {
            val n = minOf(BLOCK, audio.size - at)
            sink.accept(audio.copyOfRange(at, at + n), n)
            at += n
        }
    }

    /** Rounded MIDI of each run of voiced estimates, ignoring runs shorter than [MIN_RUN]. */
    private fun notesHeard(): List<Int> {
        val runs = ArrayList<Pair<Int, Int>>()
        for ((_, midi) in estimates) {
            if (midi.isNaN()) {
                runs.add(NONE to 1)
                continue
            }
            val note = midi.roundToInt()
            if (runs.isNotEmpty() && runs.last().first == note) runs[runs.size - 1] = note to runs.last().second + 1
            else runs.add(note to 1)
        }
        return runs.filter { it.first != NONE && it.second >= MIN_RUN }.map { it.first }
    }

    @Test
    fun theLiveLineFollowsTheHummedNotesInOrder() {
        runLiveSink()
        // The recording starts inside the first C4; after the A4 the pattern repeats.
        assertEquals(listOf(60, 67, 69, 60, 60, 67), notesHeard().take(6))
    }

    @Test
    fun everyVoicedEstimateIsOnANoteOfTheHum() {
        runLiveSink()
        val voiced = estimates.map { it.second }.filter { !it.isNaN() }
        val share = "${voiced.size}/${estimates.size}"
        assertTrue("most of the recording is voiced ($share)", voiced.size > estimates.size * 0.7)
        val off = voiced.filter { v -> NOTES.none { abs(v - it) < ON_NOTE } }
        assertTrue("no jumps or octave errors: ${off.map { "%.1f".format(it) }}", off.size <= voiced.size / 20)
    }

    private companion object {
        const val HEADER = 44
        const val RATE_OFFSET = 24
        const val PCM_SCALE = 32768f
        const val BLOCK = 480
        const val MIN_RUN = 4
        const val NONE = Int.MIN_VALUE
        const val ON_NOTE = 0.8f
        val NOTES = listOf(60, 67, 69)
    }
}
