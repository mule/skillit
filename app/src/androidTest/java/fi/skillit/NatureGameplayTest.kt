package fi.skillit

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
        tap("Ympäristöoppi · luontoretket")
        tap("Niittyetsivä")
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

        tap("Ympäristöoppi · luontoretket")
        tap("Niittyetsivä")
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

    private fun enterEnvironmentInOwnedViewport() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.save(SavedProgress()) }
        compose.runOnUiThread { compose.activity.setContent { OwnedRouteViewport(repository) } }
        compose.onNodeWithTag("route-viewport").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo(640.dp)
        tap("Ympäristöoppi · luontoretket")
    }

    private fun systemBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
        compose.waitForIdle()
    }

    private fun waitForVisible(label: String) {
        compose.waitUntilAtLeastOneExists(hasText(label), 10_000)
        // No scrolling here: the route transition must reveal the content itself.
        compose.onNodeWithText(label).assertIsDisplayed()
    }

    @Test fun chapterShowsReadingBeforePractice() {
        enterEnvironmentInOwnedViewport()
        tap("Eliökunnan luokittelu")
        waitForVisible("Kirjan sivut 6–7")
        compose.onNodeWithText("Eliöitä ovat eläimet, kasvit, sienet, alkueliöt ja bakteerit.").assertExists()
        compose.onNodeWithText("Aloita harjoitus (2 tehtävää)").assertExists()
    }

    @Test fun speciesChapterReadingShowsItsCards() {
        enterEnvironmentInOwnedViewport()
        tap("Niityn ja pientareen eläimiä")
        waitForVisible("Kirjan sivut 12–13")
        compose.onNodeWithText("kontiainen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kontiainen syö kastematoja ja toukkia.").assertExists()
    }

    @Test fun contentAwaitingValidationCannotBeOpened() {
        enterEnvironmentInOwnedViewport()
        compose.waitUntilAtLeastOneExists(hasText("Eliökunnan luokittelu"), 10_000)
        compose.onNodeWithText("Puita").assertDoesNotExist()
        compose.onNodeWithText("Ravintoketju alkaa kasveista").assertDoesNotExist()
    }

    @Test fun chapterRoundGivesFeedbackAndLeadsToNextChapter() {
        enterEnvironmentInOwnedViewport()
        tap("Eliökunnan luokittelu")
        tap("Aloita harjoitus (2 tehtävää)")
        waitForVisible("Mihin eliökuntaan sieni kuuluu?")
        tap("kasveihin")
        compose.onNodeWithText("Oikea vastaus: sieniin").assertExists()
        compose.onNodeWithText("Lähde: käyttäjän toimittama ympäristöopin sivu 6.").assertExists()
        tap("Jatka")
        waitForVisible("Mihin selkärankaisten ryhmään sammakko kuuluu?")
        tap("sammakkoeläimiin")
        compose.onNodeWithText("Oikein!").assertExists()
        tap("Jatka")
        waitForVisible("Tunnistit oikein 1/2 tehtävää. Voit tutkia lajeja uudelleen omaan tahtiisi.")
        tap("Seuraava luku: Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja")
        waitForVisible("Kirjan sivut 8–9")
    }

    @Test fun backStepsFromPracticeToReadingToListToHome() {
        enterEnvironmentInOwnedViewport()
        tap("Eliökunnan luokittelu")
        tap("Aloita harjoitus (2 tehtävää)")
        waitForVisible("Mihin eliökuntaan sieni kuuluu?")
        systemBack()
        waitForVisible("Kirjan sivut 6–7")
        systemBack()
        waitForVisible("Valitse luku. Voit lukea ja harjoitella omaan tahtiisi.")
        systemBack()
        compose.waitUntilAtLeastOneExists(hasText("Ympäristöoppi · luontoretket"), 10_000)
    }

    // Production content on purpose: recreation would drop the owned wrapper, and with it the
    // position-based saveable keys. This test makes no visibility claims, so it needs no viewport.
    @Test fun chapterPracticeSurvivesRecreationAndLeavesMusicSaveUntouched() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()
        tap("Ympäristöoppi · luontoretket")
        tap("Eliökunnan luokittelu")
        tap("Aloita harjoitus (2 tehtävää)")
        tap("kasveihin")
        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Oikea vastaus: sieniin"), 10_000)
        val saved = runBlocking { repository.load() }
        assertTrue(saved.events.isEmpty() && saved.practice.isEmpty())
    }
}
