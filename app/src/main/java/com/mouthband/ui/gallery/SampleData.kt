package com.mouthband.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mouthband.R
import com.mouthband.audio.LiveTake
import com.mouthband.model.Arrangement
import com.mouthband.model.CoachStep
import com.mouthband.model.DrumKit
import com.mouthband.model.Instrument
import com.mouthband.model.Mood
import com.mouthband.model.TrackKind
import com.mouthband.model.TrackType
import com.mouthband.ui.arrange.ArrangeUi
import com.mouthband.ui.arrange.TrackMixUi
import com.mouthband.ui.coach.CoachStepUi
import com.mouthband.ui.coach.MicAccess
import com.mouthband.ui.common.coachCopy
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.common.nameRes
import com.mouthband.ui.common.stepSegments
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.DrumGridModel
import com.mouthband.ui.components.LevelStatus
import com.mouthband.ui.components.RollModel
import com.mouthband.ui.components.RollNote
import com.mouthband.ui.components.SectionBlock
import com.mouthband.ui.home.HomeUiState
import com.mouthband.ui.home.SongCardUi
import com.mouthband.ui.review.ReviewPattern
import com.mouthband.ui.review.ReviewUi
import com.mouthband.ui.theme.colors
import kotlin.math.abs
import kotlin.math.sin

/**
 * Deterministic fake data for the debug component gallery and design previews. All text
 * comes from string resources.
 */
object SampleData {
    private const val STEPS = 16
    private const val PEAK_COUNT = 96
    private const val SAMPLE_LOOP_FRAMES = 192_000
    private const val SAMPLE_STEPS = 32
    private const val SAMPLE_NOTE_COLUMNS = 24
    private const val SAMPLE_NOTE_GAP = 4
    private const val SAMPLE_RECORDED = 2f / 3f
    private const val SAMPLE_LATE_DIVISOR = 5
    private val SAMPLE_PITCHES = floatArrayOf(60f, 62f, 64f, 62f, 65f)

    /** Drum classes of boom · tss · pff · tss (kick 0, snare 1, hi-hat 2). */
    private val SAMPLE_PATTERN = intArrayOf(0, 2, 1, 2)
    private const val WAVE_FREQ = 0.37f
    private const val WAVE_MOD = 0.11f
    private const val WAVE_FLOOR = 0.35f
    private const val WAVE_DEPTH = 0.6f
    private val rollPitches = intArrayOf(72, 71, 69, 67, 65, 64, 62, 60)

    fun peaks(count: Int = PEAK_COUNT): FloatArray =
        FloatArray(count) { i ->
            val envelope = WAVE_FLOOR + WAVE_DEPTH * abs(sin(i * WAVE_MOD))
            (abs(sin(i * WAVE_FREQ)) * envelope).coerceIn(0f, 1f)
        }

    @Composable
    fun drumGrid(): DrumGridModel {
        val kick = BooleanArray(STEPS) { it == 0 || it == 6 || it == 10 }
        val snare = BooleanArray(STEPS) { it == 4 || it == 12 }
        val hat = BooleanArray(STEPS) { it % 2 == 0 }
        return DrumGridModel(
            cells = listOf(kick, snare, hat),
            rowLabels = listOf(
                stringResource(R.string.drum_kick),
                stringResource(R.string.drum_snare),
                stringResource(R.string.drum_hihat),
            ),
        )
    }

    fun roll(): RollModel {
        val pitches = rollPitches
        val notes = listOf(
            RollNote(row = 7, startStep = 0, lengthSteps = 4, id = 0),
            RollNote(row = 5, startStep = 4, lengthSteps = 2, id = 1),
            RollNote(row = 3, startStep = 6, lengthSteps = 4, id = 2),
            RollNote(row = 2, startStep = 10, lengthSteps = 2, id = 3),
            RollNote(row = 0, startStep = 12, lengthSteps = 4, id = 4),
        )
        return RollModel(pitches, STEPS, notes, notes.map { "${pitches[it.row]}" })
    }

    @Composable
    fun home(): HomeUiState {
        val meta = stringResource(R.string.bpm_key_format, Mood.POP.bpm, Mood.POP.key.label)
        val songs = listOf(3, 7, 1).mapIndexed { index, step ->
            val current = CoachStep.fromNumber(step)
            val done = CoachStep.entries.filter { it.number < step }.toSet()
            SongCardUi(
                id = index.toLong() + 1,
                title = stringResource(R.string.song_default_title, index + 1),
                meta = meta,
                segments = stepSegments(done, current),
                status = stringResource(R.string.status_next_step, step, stringResource(current.nameRes())),
                progressDescription = stringResource(R.string.step_progress_description, step),
            )
        }
        return HomeUiState(songs = songs, loading = false)
    }

    @Composable
    fun coach(track: TrackType = TrackType.BEAT): CoachStepUi {
        val copy = track.coachCopy()
        val step = track.coachStep
        return CoachStepUi(
            songTitle = stringResource(R.string.song_default_title, 1),
            meta = stringResource(R.string.bpm_key_format, Mood.POP.bpm, Mood.POP.key.label),
            segments = stepSegments(CoachStep.entries.filter { it.number < step.number }.toSet(), step),
            progressDescription = stringResource(R.string.step_progress_description, step.number),
            stepNumber = step.number,
            stepName = stringResource(step.nameRes()),
            title = stringResource(copy.title),
            reason = stringResource(copy.reason),
            tips = copy.tips.map { stringResource(it) },
            levelStatus = LevelStatus.GOOD,
            showTeachLink = track == TrackType.BEAT,
            headphones = true,
            examplePlaying = false,
            mic = MicAccess.GRANTED,
        )
    }

    /** A half-recorded take for the live lanes: waveform, a boom-tss-pff-tss pattern and a hum. */
    fun liveTake(): LiveTake {
        val take = LiveTake(SAMPLE_LOOP_FRAMES)
        val recorded = (take.columns * SAMPLE_RECORDED).toInt()
        for (c in 0 until recorded) {
            take.peaks[c] = WAVE_FLOOR + WAVE_DEPTH * abs(sin(c * WAVE_MOD))
            val inNote = c % SAMPLE_NOTE_COLUMNS < SAMPLE_NOTE_COLUMNS - SAMPLE_NOTE_GAP
            take.pitch[c] = if (inNote) SAMPLE_PITCHES[c / SAMPLE_NOTE_COLUMNS % SAMPLE_PITCHES.size] else Float.NaN
        }
        val stepFrames = SAMPLE_LOOP_FRAMES / SAMPLE_STEPS
        val recordedSteps = (SAMPLE_STEPS * SAMPLE_RECORDED).toInt()
        // boom · tss · pff · tss on 8ths, each hit a little late, as a person plays it.
        for (step in 0 until recordedSteps step 2) {
            val cls = SAMPLE_PATTERN[step / 2 % SAMPLE_PATTERN.size]
            take.addHit(step.toLong() * stepFrames + stepFrames / SAMPLE_LATE_DIVISOR, cls)
        }
        return take
    }

    /** Where the sample playhead sits: two thirds into the loop. */
    val livePlayhead: () -> Long = { (SAMPLE_LOOP_FRAMES * SAMPLE_RECORDED).toLong() }

    @Composable
    fun review(track: TrackType = TrackType.BEAT): ReviewUi {
        val colors = track.colors()
        val pattern = when (track.kind) {
            TrackKind.DRUMS -> ReviewPattern.Drums(listOf(drumGrid(), drumGrid()))
            TrackKind.PITCHED -> ReviewPattern.Notes(listOf(roll(), roll()))
            TrackKind.RAW -> ReviewPattern.Vocal(peaks())
        }
        val sounds = if (track == TrackType.BEAT) {
            DrumKit.entries.map { ChipItem(it.name, stringResource(it.labelRes())) }
        } else {
            Instrument.forTrack(track).map { ChipItem(it.name, stringResource(it.labelRes())) }
        }
        return ReviewUi(
            trackLabel = stringResource(track.labelRes()),
            stepNumber = track.coachStep.number,
            trackColor = colors.fill,
            trackOnColor = colors.onFill,
            showAb = !track.isVocals,
            abConverted = true,
            playing = false,
            pattern = pattern,
            bars = 2,
            selectedBar = 0,
            sounds = sounds,
            selectedSound = sounds.firstOrNull()?.id,
            quantize = 0.8f,
            nextLabel = stringResource(R.string.step_name_bass),
            problem = null,
        )
    }

    @Composable
    fun arrange(): ArrangeUi {
        val arrangement = Arrangement.Default
        val blocks = arrangement.sections.map { section ->
            val label = stringResource(section.type.labelRes())
            SectionBlock(
                label = label,
                bars = section.bars,
                trackColors = TrackType.entries.filter { it in section.active }.map { it.colors().fill },
                description = stringResource(
                    R.string.section_description,
                    label,
                    pluralStringResource(R.plurals.section_bars, section.bars, section.bars),
                    pluralStringResource(R.plurals.section_parts_playing, section.active.size, section.active.size),
                ),
            )
        }
        val tracks = TrackType.entries.map { type ->
            TrackMixUi(
                type = type,
                name = stringResource(type.labelRes()),
                sound = stringResource(Mood.POP.instrumentFor(type).labelRes()),
                volume = 0.75f,
                gainText = stringResource(R.string.gain_value, 0f),
                muted = type == TrackType.MELODY,
                hasContent = type != TrackType.VOCALS_CHORUS,
                color = type.colors().fill,
            )
        }
        return ArrangeUi(
            songTitle = stringResource(R.string.song_default_title, 1),
            meta = stringResource(R.string.bpm_key_format, Mood.POP.bpm, Mood.POP.key.label),
            segments = stepSegments(
                CoachStep.entries.filter { it.number < CoachStep.ARRANGE.number }.toSet(),
                CoachStep.ARRANGE,
            ),
            progressDescription = stringResource(R.string.step_progress_description, CoachStep.ARRANGE.number),
            blocks = blocks,
            tracks = tracks,
        )
    }
}
