package fi.skillit

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.engine.ExerciseSession
import fi.skillit.music.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BackNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun tap(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text) and isEnabled(), 10000)
        compose.onNodeWithText(text).performScrollTo().performClick()
    }
    private fun visible(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 10000)
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }
    private fun systemBack(dialogText: String? = null) {
        if (dialogText != null) {
            compose.waitUntilAtLeastOneExists(hasText(dialogText),10000)
            compose.onNodeWithText(dialogText).assertIsDisplayed()
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        // Native system key dispatch waits for window/surface transitions; no route callback shortcut.
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
        compose.waitForIdle()
    }
    @Test fun systemBackReturnsAdventureAndPracticeToMapWithoutChangingTheirHistory() {
        val repo = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        val original = ExerciseSession(MusicPack.questions)
        repeat(5) { val a = original.current!!; original.answer(a.token, a.question.correctId); original.next(a.token) }
        runBlocking { repo.save(SavedProgress(started = true, events = original.events)) }
        compose.activityRule.scenario.recreate()
        visible("Sävelsalapoliisi")
        systemBack()
        visible("Nuottiseikkailu")
        assertFalse(compose.activity.isFinishing)
        tap("Harjoittele saarta 1")
        tap("Vihje"); tap("puolinuotti")
        visible("Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.")
        val before = runBlocking { repo.load() }
        systemBack(); visible("Nuottiseikkailu")
        assertEquals(before, runBlocking { repo.load() })
        compose.activityRule.scenario.recreate()
        visible("Nuottiseikkailu")
        tap("Jatka saaren 1 harjoitusta")
        visible("Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.")
        assertEquals(before, runBlocking { repo.load() })
        systemBack(); visible("Nuottiseikkailu")
        tap("Asetukset"); systemBack("Sulje")
        compose.waitUntilDoesNotExist(hasText("Sulje"),10000)
        visible("Nuottiseikkailu")
        tap("Asetukset"); tap("Nollaa edistyminen"); systemBack("Aloitetaanko alusta?")
        compose.waitUntilDoesNotExist(hasText("Aloitetaanko alusta?"),10000)
        visible("Nuottiseikkailu")
        assertEquals(before, runBlocking { repo.load() })
    }
    @Test fun systemBackCancelsRhythmPlaybackAndTimingAcrossRecreation() {
        class Output : PcmOutput {
            var active = false
            override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) { active = true }
            override fun stop() { active = false }
        }
        val output = Output()
        val repo = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repo.reset(false) }
        compose.runOnUiThread { compose.activity.setContent { SkillitTheme { SkillitApp(repo, output) } } }
        visible("Aloita seikkailu")
        val before = runBlocking { repo.load() }
        tap("Rytmipaja"); tap("Kuuntele esimerkkirytmi")
        compose.runOnIdle { assertTrue(output.active) }
        systemBack(); visible("Nuottiseikkailu")
        compose.runOnIdle { assertFalse(output.active) }
        tap("Rytmipaja"); tap("Kokeile mukana naputtelua")
        visible("Napauta")
        systemBack(); visible("Nuottiseikkailu")
        compose.mainClock.advanceTimeBy(10000)
        compose.activityRule.scenario.recreate()
        visible("Nuottiseikkailu")
        tap("Rytmipaja")
        compose.onNodeWithText("Napauta").assertDoesNotExist()
        compose.onAllNodes(hasText("Kokeilu valmis:", substring = true)).assertCountEquals(0)
        assertEquals(before, runBlocking { repo.load() })
        tap("Asetukset"); systemBack("Sulje")
        compose.waitUntilDoesNotExist(hasText("Sulje"),10000)
        visible("Kokeile mukana naputtelua")
        systemBack(); visible("Nuottiseikkailu")
    }
}
