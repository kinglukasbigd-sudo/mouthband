package com.mouthband.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.convert.DrumClassifier
import com.mouthband.dsp.Rms
import com.mouthband.model.DrumClass
import com.mouthband.model.TrackType
import com.mouthband.ui.coach.liveSinks
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * Everything of the recording path except the microphone hardware, on a device: a producer thread
 * plays the role of the record thread and writes hummed audio into the real [Recorder] ring buffer
 * in real time (10 ms blocks); the real [CaptureConsumer] drains it on its own coroutine into the live
 * sinks and a take collector, and publishes the level the coach card's meter shows.
 */
@RunWith(AndroidJUnit4::class)
class CapturePipelineInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val rate = 48_000
    private val block = rate / 100

    private fun midiToHz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Four seconds: C4, E4, G4 hummed with harmonics and vibrato, pauses between, scaled to [peakDb]. */
    private fun hum(peakDb: Float): FloatArray {
        val samples = FloatArray(4 * rate)
        listOf(Triple(60, 0.2, 1.0), Triple(64, 1.3, 2.1), Triple(67, 2.4, 3.2)).forEach { (midi, start, end) ->
            var phase = 0.0
            val from = (start * rate).toInt()
            val n = ((end - start) * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                phase += 2 * PI * midiToHz(midi) * (1 + 0.004 * sin(2 * PI * 5.5 * t)) / rate
                val env = minOf(1.0, i / (0.015 * rate), (n - i) / (0.015 * rate))
                samples[from + i] += (env * (1..8).sumOf { sin(it * phase) / it }).toFloat()
            }
        }
        val gain = Rms.fromDb(peakDb) / Rms.peak(samples)
        val random = Random(4)
        val floor = Rms.fromDb(peakDb - 45f)
        return FloatArray(samples.size) { (samples[it] * gain + random.nextGaussian() * floor).toFloat() }
    }

    private class Run(val collected: FloatArray, val take: LiveTake, val consumer: CaptureConsumer)

    /** Feeds [audio] through ring → consumer → sinks at real-time pace; returns what the consumer saw. */
    private fun performance(audio: FloatArray, type: TrackType = TrackType.MELODY): Run {
        val recorder = Recorder(context, rate)
        val consumer = CaptureConsumer(recorder)
        val take = LiveTake(audio.size)
        val collected = FloatArray(audio.size)
        var seen = 0
        val collector = CaptureSink { samples, count ->
            System.arraycopy(samples, 0, collected, seen, minOf(count, collected.size - seen))
            seen += count
        }
        val sinks = liveSinks(type, LoopClock { it }, take, rate) { DrumClassifier.byRules(it) } + collector
        runBlocking {
            val job: Job = CoroutineScope(Dispatchers.Default).launch { consumer.drain(sinks) }
            val producer = Thread {
                var at = 0
                while (at < audio.size) {
                    val n = minOf(block, audio.size - at)
                    recorder.ring.write(audio, at, n)
                    at += n
                    Thread.sleep(PACE_MS)
                }
            }
            producer.start()
            producer.join()
            val deadline = System.currentTimeMillis() + DRAIN_MS
            while (seen < audio.size && System.currentTimeMillis() < deadline) delay(20)
            job.cancelAndJoin()
        }
        assertEquals("every sample made it through", audio.size, seen)
        return Run(collected, take, consumer)
    }

    @Test
    fun everySampleArrivesInOrderAndTheHummedLineIsDrawnOnItsNotes() {
        val audio = hum(-20f)
        val run = performance(audio)
        assertTrue("samples are delivered unchanged and in order", run.collected.contentEquals(audio))
        for ((midi, start, end) in listOf(Triple(60, 0.2, 1.0), Triple(64, 1.3, 2.1), Triple(67, 2.4, 3.2))) {
            val from = run.take.column(((start + 0.2) * rate).toLong())
            val to = run.take.column(((end - 0.1) * rate).toLong())
            val voiced = (from..to).map { run.take.pitch[it] }.filter { !it.isNaN() }
            assertTrue("MIDI $midi drawn (${voiced.size}/${to - from + 1})", voiced.size > (to - from + 1) * 0.8)
            voiced.forEach { assertEquals(midi.toFloat(), it, 0.7f) }
        }
        assertTrue("the waveform has peaks", run.take.peaks.any { it > 0.1f })
    }

    @Test
    fun theMeterVerdictFollowsHowLoudThePerformanceIs() {
        // The verdict is read while the sound is still held, so check it right after each performance.
        assertEquals(MicVerdict.GOOD, performance(hum(-20f)).consumer.level.value.verdict)
        assertEquals(MicVerdict.TOO_QUIET, performance(hum(-55f)).consumer.level.value.verdict)
        // 6 dB over full scale: the clipped shout a phone microphone cannot represent any louder.
        assertEquals(MicVerdict.TOO_LOUD, performance(hum(6f)).consumer.level.value.verdict)
    }

    @Test
    fun aFastBurstOfSamplesLongerThanTheRingIsNeverReorderedOrDuplicated() {
        // The ring holds a limited number of samples: a producer that outruns the consumer drops the
        // newest samples (it may never block); what is delivered must still be an exact prefix.
        val recorder = Recorder(context, rate)
        val consumer = CaptureConsumer(recorder)
        val audio = FloatArray(recorder.ring.capacity * 2) { it.toFloat() / (recorder.ring.capacity * 2) }
        val written = recorder.ring.write(audio, 0, audio.size)
        assertTrue("an overfull ring accepts only what fits", written in 1..recorder.ring.capacity)
        val got = ArrayList<Float>()
        consumer.drainOnce(listOf(CaptureSink { samples, count -> for (i in 0 until count) got.add(samples[i]) }))
        assertEquals(written, got.size)
        assertTrue("the delivered samples are the first ones, in order", got.indices.all { got[it] == audio[it] })
    }

    private companion object {
        const val PACE_MS = 10L
        const val DRAIN_MS = 3_000L
    }
}
