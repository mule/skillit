package fi.skillit

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
