package fi.skillit

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import fi.skillit.music.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PlaybackUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun composeControlsSendPitchPcmAndCancelOnSettingsMuteNavigationAndBackground() {
        class Output : PcmOutput {
            var samples = shortArrayOf()
            var active = false
            var volume = 0f
            var finished: () -> Unit = {}
            override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) {
                this.samples = samples; this.volume = volume; active = true; finished = onFinished
                assertEquals(24000, sampleRate)
            }
            override fun stop() { active = false }
        }
        val output = Output()
        val repo = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repo.reset(false) }
        compose.runOnUiThread { compose.activity.setContent { SkillitTheme { SkillitApp(repo, output) } } }
        fun tap(text: String) {
            compose.waitUntilAtLeastOneExists(hasText(text) and isEnabled(),10000)
            if (text == "Sulje") compose.onNodeWithText(text).performClick()
            else compose.onNodeWithText(text).performScrollTo().performClick()
        }
        fun hear() {
            compose.onNodeWithContentDescription("Kuuntele A4").performScrollTo().performClick()
            compose.waitForIdle()
        }
        compose.waitUntilAtLeastOneExists(hasText("Aloita seikkailu"),10000)
        hear()
        compose.runOnIdle {
            assertTrue(output.active)
            assertArrayEquals(MusicAudio.render(listOf(Note(Pitch.A4, Duration.QUARTER))), output.samples)
            assertEquals(.6f, output.volume)
        }
        compose.runOnIdle { output.active = false; output.finished() }
        compose.waitUntilAtLeastOneExists(hasText("Kuuntelu valmis."),10000)
        hear()
        compose.runOnIdle { assertTrue(output.active) }
        tap("Asetukset")
        compose.runOnIdle { assertFalse(output.active) }
        compose.onNodeWithContentDescription("Äänenvoimakkuus").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.3f) }
        compose.waitUntil(10000) { runBlocking { kotlin.math.abs(repo.load().volume - .3f) < .001f } }
        tap("Sulje"); hear()
        compose.runOnIdle { assertEquals(.3f, output.volume, .001f) }
        tap("Asetukset")
        compose.onNodeWithContentDescription("Äänet mykistetty").performClick()
        tap("Sulje"); hear()
        compose.runOnIdle { assertFalse(output.active) }
        tap("Asetukset"); compose.onNodeWithContentDescription("Äänet mykistetty").performClick(); tap("Sulje")
        hear()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(output.active)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        hear(); tap("Rytmipaja")
        compose.runOnIdle { assertFalse(output.active) }
        tap("Takaisin kartalle"); hear(); tap("Aloita seikkailu")
        compose.runOnIdle { assertFalse(output.active) }
    }
}
