package com.mouthband.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mouthband.R
import com.mouthband.model.CoachStep
import com.mouthband.model.DrumKit
import com.mouthband.model.TrackType
import com.mouthband.ui.common.ScreenScaffold
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.common.stepSegments
import com.mouthband.ui.components.AdBannerSlot
import com.mouthband.ui.components.BeatCounter
import com.mouthband.ui.components.ChipItem
import com.mouthband.ui.components.ChipRow
import com.mouthband.ui.components.DrumGrid
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.FramedCard
import com.mouthband.ui.components.GridCellLabels
import com.mouthband.ui.components.HighlighterTag
import com.mouthband.ui.components.IndigoPillButton
import com.mouthband.ui.components.LevelMeter
import com.mouthband.ui.components.LiveDrumLane
import com.mouthband.ui.components.LivePitchLane
import com.mouthband.ui.components.LiveWaveform
import com.mouthband.ui.components.MbSlider
import com.mouthband.ui.components.MiniPianoRoll
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.MonoPill
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.ProgressRing
import com.mouthband.ui.components.RecordButton
import com.mouthband.ui.components.SecondaryPillButton
import com.mouthband.ui.components.SectionHeading
import com.mouthband.ui.components.SectionStrip
import com.mouthband.ui.components.SegmentedToggle
import com.mouthband.ui.components.StatusChip
import com.mouthband.ui.components.StepProgressStrip
import com.mouthband.ui.components.TextLinkButton
import com.mouthband.ui.components.TopBar
import com.mouthband.ui.components.TrackRow
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.colors

/** Debug-only screen listing every component from spec §3 with sample data. */
@Composable
fun ComponentGallery(onBack: () -> Unit, onDemoSong: () -> Unit, modifier: Modifier = Modifier) {
    var recording by remember { mutableStateOf(false) }
    var toggle by remember { mutableIntStateOf(0) }
    var kit by remember { mutableStateOf(DrumKit.BOOM_BAP.name) }
    var slider by remember { mutableFloatStateOf(0.8f) }
    var muted by remember { mutableStateOf(false) }
    val grid = SampleData.drumGrid()
    val labels = GridCellLabels(
        stringResource(R.string.grid_step),
        stringResource(R.string.grid_on),
        stringResource(R.string.grid_off),
    )
    ScreenScaffold(modifier = modifier) {
        TopBar(
            backLabel = stringResource(R.string.action_back),
            onBack = onBack,
            title = stringResource(R.string.gallery_title),
        )
        SectionHeading(stringResource(R.string.gallery_title), trailing = null)
        SecondaryPillButton(stringResource(R.string.gallery_demo_song), onDemoSong, Modifier.fillMaxWidth())
        PrimaryPillButton(stringResource(R.string.home_new_song), {}, Modifier.fillMaxWidth(), showArrow = true)
        SecondaryPillButton(stringResource(R.string.arrange_auto_mix), {}, Modifier.fillMaxWidth(), icon = MbIcons.Wave)
        IndigoPillButton(stringResource(R.string.action_done), {}, Modifier.fillMaxWidth())
        TextLinkButton(stringResource(R.string.coach_teach_link), {})
        FramedCard(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MbDimens.GapSmall),
            ) {
                HighlighterTag(stringResource(R.string.step_tag, 2))
                MonoLabel(stringResource(R.string.step_name_beat), color = MbColors.Ink, bold = true)
                MonoPill(stringResource(R.string.bpm_key_format, 110, "C MAJ"))
            }
        }
        StepProgressStrip(
            stepSegments(setOf(CoachStep.VIBE, CoachStep.BEAT), CoachStep.BASS),
            stringResource(R.string.step_progress_description, 3),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RecordButton(
                recording = recording,
                contentDescription = stringResource(R.string.coach_record),
                onClick = { recording = !recording },
            )
        }
        LevelMeter({ 0.62f }, stringResource(R.string.level_meter_description, stringResource(R.string.level_good)))
        Column(
            Modifier.fillMaxWidth().background(MbColors.Indigo).padding(MbDimens.GapLarge),
            verticalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            BeatCounter(currentBeat = 1, description = stringResource(R.string.rec_beat_description, 2))
            val take = remember { SampleData.liveTake() }
            LiveWaveform(take, SampleData.livePlayhead, stringResource(R.string.rec_waveform))
            LiveDrumLane(
                take,
                grid.rowLabels,
                loopBars = 2,
                playheadFrame = SampleData.livePlayhead,
                description = stringResource(R.string.rec_live_hits),
            )
            LivePitchLane(take, 2, SampleData.livePlayhead, stringResource(R.string.rec_live_notes))
            DrumGrid(grid, labels, onIndigo = true, playhead = { 0.3f })
            LevelMeter({ 0.4f }, stringResource(R.string.coach_mic), onIndigo = true)
        }
        DrumGrid(grid, labels, onToggle = { _, _ -> })
        MiniPianoRoll(
            model = SampleData.roll(),
            color = TrackType.MELODY.colors().fill,
            description = pluralStringResource(R.plurals.roll_description, 5, 5),
            deleteActionLabel = stringResource(R.string.roll_delete_note),
        )
        SegmentedToggle(
            listOf(stringResource(R.string.review_ab_voice), stringResource(R.string.review_ab_converted)),
            toggle,
            { toggle = it },
        )
        ChipRow(DrumKit.entries.map { ChipItem(it.name, stringResource(it.labelRes())) }, kit, { kit = it })
        MbSlider(slider, { slider = it }, stringResource(R.string.review_timing), "${(slider * 100).toInt()}")
        TrackRow(
            color = TrackType.BASS.colors().fill,
            name = stringResource(R.string.track_bass),
            soundName = stringResource(R.string.inst_synth_bass),
            volume = slider,
            volumeText = stringResource(R.string.gain_value, -2f),
            muted = muted,
            muteLabel = stringResource(R.string.mute),
            unmuteLabel = stringResource(R.string.unmute),
            volumeLabel = stringResource(R.string.volume),
            onVolumeChange = { slider = it },
            onMuteToggle = { muted = !muted },
        )
        SectionStrip(SampleData.arrange().blocks, selectedIndex = 2)
        StatusChip(stringResource(R.string.coach_headphones), MbIcons.Headphones)
        ProgressRing(0.42f, label = "42%")
        EmptyState(
            stringResource(R.string.review_no_hits_title),
            stringResource(R.string.review_no_hits_body),
            actionLabel = stringResource(R.string.action_retry),
            onAction = {},
        )
        AdBannerSlot { Text(stringResource(R.string.app_name)) }
    }
}
