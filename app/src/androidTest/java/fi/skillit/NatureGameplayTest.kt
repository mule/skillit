package fi.skillit

import androidx.compose.ui.test.assertIsDisplayed
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import fi.skillit.nature.NaturePack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NatureGameplayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun enterNature() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()
        tap("Niittyetsivä · luontoretki")
    }

    private fun tap(label: String) {
        compose.waitUntilAtLeastOneExists(hasText(label), 10_000)
        // Scroll only to controls that the user needs to click.
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun assertPictureRoundVisible(index: Int, capture: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$capture.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // No scrolling here: the route/round transition must reveal the content itself.
        compose.onNodeWithText(NaturePack.questions[index].prompt).assertIsDisplayed()
        compose.onNodeWithContentDescription(NaturePack.questions[index].hint!!).assertIsDisplayed()
    }

    @Test fun enteringNatureFromScrolledMapRevealsPromptAndPicture() {
        enterNature()
        assertPictureRoundVisible(0, "nature-entry")
    }

    @Test fun advancingNatureRevealsNextPromptAndPicture() {
        enterNature()
        tap(NaturePack.questions[0].answer)
        tap("Jatka")
        assertPictureRoundVisible(1, "nature-next")
    }

    @Test fun newTripRevealsFirstPromptAndPicture() {
        enterNature()
        NaturePack.questions.forEach { question ->
            tap(question.answer)
            tap("Jatka")
        }
        tap("Uusi retki")
        assertPictureRoundVisible(0, "nature-new-trip")
    }

    @Test fun pictureRoundShowsHintFeedbackAndSourceWithoutChangingMusicSave() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()

        fun tap(label: String) {
            compose.waitUntilAtLeastOneExists(hasText(label), 10_000)
            compose.onNodeWithText(label).performScrollTo().performClick()
        }

        tap("Niittyetsivä · luontoretki")
        compose.onNodeWithContentDescription(NaturePack.species.first().clue).assertExists()
        tap("Vihje")
        compose.onNodeWithText(NaturePack.species.first().clue).assertExists()
        tap("pietaryrtti")
        compose.onNodeWithText("Oikea vastaus: päivänkakkara").assertExists()
        compose.onNodeWithText("Lähde: käyttäjän toimittama ympäristöopin sivu 10.").assertExists()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Oikea vastaus: päivänkakkara").assertExists()
        tap("Jatka")
        tap("pietaryrtti")
        compose.onNodeWithText("Oikein!").assertExists()
        tap("Etusivu")
        compose.onNodeWithText("Aloita seikkailu").assertExists()
        assertTrue(runBlocking { repository.load().events.isEmpty() })
    }
}
