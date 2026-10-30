package fi.skillit

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.engine.ExerciseSession
import fi.skillit.music.MusicPack
import fi.skillit.music.notationDescription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class RouteViewportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun seed(saved: SavedProgress) {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.save(saved) }
        compose.runOnUiThread { compose.activity.setContent {
            // Own the regression viewport and text scale instead of inheriting the CI device's.
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.5f)) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.requiredSize(360.dp, 640.dp).testTag("route-viewport")) {
                        SkillitTheme { SkillitApp(repository) }
                    }
                }
            }
        } }
        compose.onNodeWithTag("route-viewport").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(640.dp)
    }

    private fun tap(label: String) {
        compose.waitUntilAtLeastOneExists(hasText(label) and isEnabled(), 10_000)
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun enterFromScrolledMap(label: String) {
        compose.waitUntilAtLeastOneExists(hasText(label) and isEnabled(), 10_000)
        compose.onNodeWithText(label).performScrollTo()
        compose.onNodeWithText("Nuottiseikkailu").assertIsNotDisplayed()
        compose.onNodeWithText(label).performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(compose.activity.getExternalFilesDir(null), "route-$name.png")
        file.outputStream().use {
            checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertAdventureVisible(questionIndex: Int, captureName: String) {
        compose.waitUntilAtLeastOneExists(hasText(MusicPack.questions[questionIndex].prompt), 10_000)
        capture(captureName)
        // Visibility checks must never scroll: navigation itself must reveal this content.
        compose.onNodeWithText(MusicPack.levels[MusicPack.questions[questionIndex].level]).assertIsDisplayed()
        compose.onNodeWithText(MusicPack.questions[questionIndex].prompt).assertIsDisplayed()
        compose.onNodeWithContentDescription(notationDescription(MusicPack.item(MusicPack.questions[questionIndex].id).task))
            .assertIsDisplayed()
    }

    @Test fun rhythmEntryAndReturnRevealHeadingsWithoutScrolling() {
        seed(SavedProgress())
        enterFromScrolledMap("Rytmipaja")
        capture("rhythm-entry")
        compose.onNodeWithText("Rytmipaja").assertIsDisplayed()
        compose.onNodeWithText("Täydennä tahti nuoteilla tai tauoilla. Napauta merkkiä tai pidä sitä painettuna ja vedä tahtiin. Ei aikarajaa eikä XP-vaatimusta.")
            .assertIsDisplayed()
        tap("Takaisin kartalle")
        capture("rhythm-return")
        compose.onNodeWithText("Nuottiseikkailu").assertIsDisplayed()
    }

    @Test fun resumeFromScrolledMapRevealsSameAdventurePromptAndKeepsFeedback() {
        val session = ExerciseSession(MusicPack.questions)
        session.showHint("m1:0")
        session.answer("m1:0", "o0")
        seed(SavedProgress(started = true, events = session.events))
        tap("Etusivu")
        enterFromScrolledMap("Jatka seikkailua")
        assertAdventureVisible(0, "adventure-resume")
        compose.onNodeWithText("Valitsit tämän", substring = true).assertExists()
        tap("Etusivu")
        capture("adventure-home")
        compose.onNodeWithText("Nuottiseikkailu").assertIsDisplayed()
    }

    @Test fun practiceEntryAndSystemBackRevealHeadingsWithoutScrolling() {
        val session = ExerciseSession(MusicPack.questions)
        repeat(5) {
            val attempt = session.current!!
            session.answer(attempt.token, attempt.question.correctId)
            session.next(attempt.token)
        }
        seed(SavedProgress(started = true, events = session.events))
        tap("Etusivu")
        enterFromScrolledMap("Harjoittele saarta 1")
        assertAdventureVisible(0, "practice-entry")
        // Scroll to a lower control before Back, so returning to the map must reset too.
        compose.onNodeWithText("Etusivu").performScrollTo()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
        compose.waitUntilAtLeastOneExists(hasText("Nuottiseikkailu"), 10_000)
        capture("practice-back")
        compose.onNodeWithText("Nuottiseikkailu").assertIsDisplayed()
    }
}
