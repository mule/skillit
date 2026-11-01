package fi.skillit

import android.os.SystemClock
import android.view.KeyEvent
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

    private fun enterClassificationChapter() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()
        tap("Ympäristöoppi · luontoretket")
        tap("Eliökunnan luokittelu")
    }

    private fun systemBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
        compose.waitForIdle()
    }

    @Test fun chapterShowsReadingBeforePractice() {
        enterClassificationChapter()
        compose.onNodeWithText("Kirjan sivut 6–7").assertExists()
        compose.onNodeWithText("Eliöitä ovat eläimet, kasvit, sienet, alkueliöt ja bakteerit.").assertExists()
        compose.onNodeWithText("Aloita harjoitus (2 tehtävää)").assertExists()
    }

    @Test fun speciesChapterReadingShowsItsCards() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()
        tap("Ympäristöoppi · luontoretket")
        tap("Niityn ja pientareen eläimiä")
        compose.onNodeWithText("kontiainen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kontiainen syö kastematoja ja toukkia.").assertExists()
    }

    @Test fun contentAwaitingValidationCannotBeOpened() {
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repository.reset(muted = false) }
        compose.activityRule.scenario.recreate()
        tap("Ympäristöoppi · luontoretket")
        compose.onNodeWithText("Eliökunnan luokittelu").assertExists()
        compose.onNodeWithText("Puita").assertDoesNotExist()
        compose.onNodeWithText("Ravintoketju alkaa kasveista").assertDoesNotExist()
    }

    @Test fun chapterRoundGivesFeedbackAndLeadsToNextChapter() {
        enterClassificationChapter()
        tap("Aloita harjoitus (2 tehtävää)")
        compose.onNodeWithText("Mihin eliökuntaan sieni kuuluu?").assertExists()
        tap("kasveihin")
        compose.onNodeWithText("Oikea vastaus: sieniin").assertExists()
        compose.onNodeWithText("Lähde: käyttäjän toimittama ympäristöopin sivu 6.").assertExists()
        tap("Jatka")
        tap("sammakkoeläimiin")
        compose.onNodeWithText("Oikein!").assertExists()
        tap("Jatka")
        compose.onNodeWithText("Tunnistit oikein 1/2 tehtävää. Voit tutkia lajeja uudelleen omaan tahtiisi.").assertExists()
        tap("Seuraava luku: Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja")
        compose.onNodeWithText("Kirjan sivut 8–9").assertExists()
    }

    @Test fun backStepsFromPracticeToReadingToListToHome() {
        enterClassificationChapter()
        tap("Aloita harjoitus (2 tehtävää)")
        compose.waitUntilAtLeastOneExists(hasText("Mihin eliökuntaan sieni kuuluu?"), 10_000)
        systemBack()
        compose.waitUntilAtLeastOneExists(hasText("Kirjan sivut 6–7"), 10_000)
        systemBack()
        compose.waitUntilAtLeastOneExists(hasText("Valitse luku. Voit lukea ja harjoitella omaan tahtiisi."), 10_000)
        systemBack()
        compose.waitUntilAtLeastOneExists(hasText("Ympäristöoppi · luontoretket"), 10_000)
    }

    @Test fun chapterPracticeSurvivesRecreationAndLeavesMusicSaveUntouched() {
        enterClassificationChapter()
        tap("Aloita harjoitus (2 tehtävää)")
        tap("kasveihin")
        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Oikea vastaus: sieniin"), 10_000)
        val repository = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        val saved = runBlocking { repository.load() }
        assertTrue(saved.events.isEmpty() && saved.practice.isEmpty())
    }
}
