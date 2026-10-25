package com.mouthband.ui

import com.mouthband.model.CoachStep
import com.mouthband.model.CoachTarget
import com.mouthband.model.TrackType
import com.mouthband.ui.home.resumeTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeResumeTest {
    @Test
    fun aSongResumesAtItsCoachStep() {
        assertEquals(CoachTarget.Vibe, resumeTarget(CoachStep.VIBE))
        assertEquals(CoachTarget.Record(TrackType.BEAT), resumeTarget(CoachStep.BEAT))
        assertEquals(CoachTarget.Record(TrackType.BASS), resumeTarget(CoachStep.BASS))
        assertEquals(CoachTarget.Record(TrackType.CHORDS), resumeTarget(CoachStep.CHORDS))
        assertEquals(CoachTarget.Record(TrackType.MELODY), resumeTarget(CoachStep.MELODY))
        assertEquals(CoachTarget.Record(TrackType.VOCALS_VERSE), resumeTarget(CoachStep.VOCALS))
        assertEquals(CoachTarget.Arrange, resumeTarget(CoachStep.ARRANGE))
    }

    @Test
    fun aFinishedSongOpensInArrangeWhereItCanBeEditedAndMixed() {
        assertEquals(CoachTarget.Arrange, resumeTarget(CoachStep.EXPORT))
    }

    @Test
    fun everyStepHasATarget() {
        CoachStep.entries.forEach { resumeTarget(it) }
    }
}
