package com.mouthband.ui

import android.Manifest
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mouthband.MainActivity
import com.mouthband.MouthBandApp
import com.mouthband.R
import com.mouthband.model.Instrument
import com.mouthband.model.TrackType
import com.mouthband.ui.common.labelRes
import com.mouthband.ui.gallery.DemoSongSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The finished-song flows through the real screens: a done song opens in Arrange, every part has a
 * sheet to try sounds and hear the part (also the voice), the editor of a kept take saves back to
 * the coach card when it was opened from there, and leaves without saving when it was opened from
 * Arrange. Runs on an emulator or a device; it only creates and removes its own song.
 */
@RunWith(AndroidJUnit4::class)
class FinishedSongUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<MouthBandApp>()
    private val container get() = app.container
    private var scenario: ActivityScenario<MainActivity>? = null
    private var songId = 0L

    /** Texts come from the app's own string resources, so a copy change cannot break the flow. */
    private fun text(id: Int) = app.getString(id)
    private val arrangeTitle get() = text(R.string.step_name_arrange)
    private val saveChanges get() = text(R.string.review_save_changes)
    private val editKept get() = text(R.string.coach_edit_kept)
    private val undo get() = text(R.string.review_undo)
    private val hearPart get() = text(R.string.arrange_hear_part)
    private val stopHearing get() = text(R.string.arrange_stop_hearing)
    private val recordAgain get() = text(R.string.arrange_record_again)
    private val deleteNote get() = text(R.string.review_delete_note)
    private fun sound(instrument: Instrument) = text(instrument.labelRes())
    private val editBass get() = "${text(R.string.arrange_edit_track)} ${text(TrackType.BASS.labelRes())}"

    @Before
    fun seedASongAndOpenTheApp() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.RECORD_AUDIO)
        // Android 12+ asks "Unblock device microphone?" over the app when the privacy toggle is on (an
        // emulator can be left that way); that system dialog would stop the activity under test. Only
        // an emulator's toggle is touched: a real phone's privacy setting is the owner's to choose.
        if (Build.FINGERPRINT.startsWith("google/sdk_gphone") || Build.HARDWARE in EMULATOR_HARDWARE) {
            ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("cmd sensor_privacy disable 0 microphone"),
            ).use { it.readBytes() }
        }
        runBlocking {
            container.settings.setOnboardingDone()
            container.repository.overviews().first().filter { it.song.title == TITLE }.forEach {
                container.repository.delete(it.song.id)
            }
            songId = DemoSongSeeder(
                container.repository,
                container.trackRepository,
                container.loopRenderer,
                container.engine.sampleRate,
            ).create(TITLE)
        }
        // Screens with per-frame drawing (the coach card's level meter) never go idle; frames are
        // advanced by hand in [waitFor].
        compose.mainClock.autoAdvance = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun removeTheSong() {
        scenario?.close()
        runBlocking { container.repository.delete(songId) }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            compose.mainClock.advanceTimeBy(FRAME_MS)
            Thread.sleep(POLL_MS)
        }
        assertTrue("timed out waiting for $what", condition())
    }

    private fun present(text: String, ignoreCase: Boolean = false) =
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, ignoreCase: Boolean = false) =
        waitFor("\"$text\"") { present(text, ignoreCase) }

    private fun waitForGone(text: String) = waitFor("\"$text\" to disappear") { !present(text) }

    private fun click(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).performClick()
        compose.mainClock.advanceTimeBy(FRAME_MS)
    }

    private fun back() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(FRAME_MS)
    }

    private fun openArrange() {
        click(TITLE)
        waitForText(arrangeTitle)
    }

    /** Taps a part's header in the mixer (the row showing its current sound). */
    private fun openPart(sound: String) {
        waitFor("the \"$sound\" row") {
            compose.onAllNodes(hasClickAction() and hasText(sound, substring = true, ignoreCase = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // A part below the fold is activated through its click action, as an accessibility service
        // would: no gesture or scrolling, so no waiting for animations to settle.
        compose.onAllNodes(hasClickAction() and hasText(sound, substring = true, ignoreCase = true))[0]
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(FRAME_MS)
    }

    private fun bassNotes() = runBlocking {
        container.trackRepository.getAll(songId).first { it.type == TrackType.BASS }.events?.notes?.size ?: 0
    }

    @Test
    fun aFinishedSongOpensInArrangeAndEverySoundOfAPartCanBeChosen() {
        openArrange()
        openPart(sound(Instrument.SYNTH_BASS))
        waitForText(sound(Instrument.DEEP_SUB))
        click(sound(Instrument.DEEP_SUB))
        // The chosen sound plays the part alone: the button turns into Stop.
        waitForText(stopHearing)
        back()
        waitForGone(hearPart)
        waitFor("the row to show the new sound") { present(sound(Instrument.DEEP_SUB), ignoreCase = true) }
        val saved = runBlocking { container.trackRepository.getAll(songId).first { it.type == TrackType.BASS } }
        assertEquals(Instrument.DEEP_SUB, saved.instrument)
    }

    @Test
    fun theVoiceTrackCanBeHeardAndStopped() {
        openArrange()
        openPart(sound(Instrument.VOICE))
        click(hearPart)
        waitForText(stopHearing)
        click(stopHearing)
        waitForText(hearPart)
        back()
        waitForGone(hearPart)
    }

    @Test
    fun editingFromTheCoachCardSavesBackToTheCoachCard() {
        openArrange()
        val before = bassNotes()
        assertTrue("the demo bass has notes", before > 1)
        openPart(sound(Instrument.SYNTH_BASS))
        click(recordAgain)
        waitForText(editKept)
        click(editKept)
        waitForText(saveChanges)
        // Delete the first note through the roll's accessibility action, as TalkBack would.
        waitFor("the piano roll") {
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
                .fetchSemanticsNodes().isNotEmpty()
        }
        val hasActions = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
        val roll = compose.onAllNodes(hasActions)[0].fetchSemanticsNode()
        val delete = roll.config[SemanticsActions.CustomActions].first { it.label.startsWith(deleteNote) }
        compose.runOnUiThread { delete.action() }
        waitForText(undo)
        click(saveChanges)
        // Opened from the coach card: saving returns to the coach card, not to Arrange.
        waitForText(editKept)
        // Arrange stays composed until the slide finishes, then it must be gone: the step did not close.
        waitFor("Arrange to be covered by the coach card") { !present(arrangeTitle) }
        waitFor("the saved take") { bassNotes() == before - 1 }
        back()
        waitForText(arrangeTitle)
    }

    @Test
    fun leavingTheEditorFromArrangeKeepsTheTakeAsItWas() {
        openArrange()
        val before = bassNotes()
        waitFor("the edit button") {
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().isNotEmpty() &&
                runCatching { compose.onNodeWithContentDescription(editBass).assertExists() }.isSuccess
        }
        compose.onNodeWithContentDescription(editBass).performClick()
        compose.mainClock.advanceTimeBy(FRAME_MS)
        waitForText(saveChanges)
        back()
        waitForText(arrangeTitle)
        assertEquals("nothing was saved", before, bassNotes())
    }

    private companion object {
        val EMULATOR_HARDWARE = setOf("ranchu", "goldfish")
        const val TITLE = "UI test song"
        const val TIMEOUT_MS = 30_000L
        const val FRAME_MS = 16L
        const val POLL_MS = 40L
    }
}
