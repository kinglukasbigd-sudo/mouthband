package com.mouthband.ui.coach

import com.mouthband.audio.CaptureSink
import com.mouthband.audio.LiveTake
import com.mouthband.audio.LiveWaveformSink
import com.mouthband.audio.LoopClock
import com.mouthband.convert.DrumFeatures
import com.mouthband.convert.LiveOnsetDetector
import com.mouthband.convert.LivePitchSink
import com.mouthband.convert.PitchListener
import com.mouthband.model.DrumClass
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType

/**
 * The live analysis of a running take, all writing into [take] at the loop position where each
 * sound was heard: the waveform for every track, plus drum hits for the beat or the hummed pitch
 * for pitched tracks. Runs on the capture consumer, never the record thread.
 */
fun liveSinks(
    type: TrackType,
    clock: LoopClock,
    take: LiveTake,
    sampleRate: Int,
    classify: (DrumFeatures.Analysis) -> DrumClass,
): List<CaptureSink> {
    val waveform = LiveWaveformSink(take, clock)
    val analysis: CaptureSink? = when (type.kind) {
        TrackKind.DRUMS -> LiveOnsetDetector(sampleRate) { index, features ->
            take.addHit(clock.loopFrameAt(index), classify(features).ordinal)
        }
        TrackKind.PITCHED -> {
            var lastFrame = Long.MIN_VALUE
            LivePitchSink(sampleRate, PitchListener { centre, midi ->
                val frame = clock.loopFrameAt(centre)
                // Fill from the previous estimate so the drawn line has no gaps between hops.
                val from = if (lastFrame == Long.MIN_VALUE) frame else lastFrame + 1
                take.addPitch(from, frame, midi)
                lastFrame = frame
            })
        }
        TrackKind.RAW -> null
    }
    return listOfNotNull(waveform, analysis)
}
