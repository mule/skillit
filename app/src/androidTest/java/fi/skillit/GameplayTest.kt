package fi.skillit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fi.skillit.music.MusicPack
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.unit.dp
import fi.skillit.music.notationDescription
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GameplayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun freshProgress() {
        runBlocking { ProgressRepository(compose.activity.applicationContext.skillitDataStore).reset(muted = false) }
        compose.activityRule.scenario.recreate()
        await("Aloita seikkailu")
    }
    private fun await(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text), 10_000)
    }
    private fun tap(text: String) { await(text); compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun visible(text: String) { await(text); compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }

    @Test fun childCanStartAHintedQuestionAndReadFeedback() {
        tap("Aloita seikkailu")
        visible("Nuottininja")
        tap("Vihje")
        visible("Musta nuotinpää ja varsi, ei lippua.")
        tap("neljäsosanuotti")
        visible("Hienoa! +10 XP")
        tap("Jatka")
        visible("Kuinka monta iskua nuotti kestää?")
    }

    @Test fun twentyQuestionPlaythroughIncludesWrongRetryRecreationAndSummary() {
        tap("Aloita seikkailu")
        MusicPack.items.forEachIndexed { i, item ->
            val q = item.question
            if (i == 5) {
                visible("Uusi yritys · voit saada 5 XP")
                tap("neljäsosanuotti")
                visible("Hienoa! +5 XP")
                tap("Jatka")
            }
            val label = if (i == 0) q.choices.first { it.id != q.correctId }.label else q.choices.single { it.id == q.correctId }.label
            tap(label)
            if (i == 0) visible("Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.")
            else visible("Hienoa! +10 XP")
            if (i == 1) {
                compose.activityRule.scenario.recreate()
                visible("Hienoa! +10 XP")
                compose.onAllNodesWithText("Hienoa! +10 XP").assertCountEquals(1)
            }
            tap("Jatka")
        }
        visible("Seikkailu valmis!")
        visible("195 XP")
        visible("Oikein ensimmäisellä yrityksellä: 19/20")
        compose.activityRule.scenario.recreate()
        visible("Seikkailu valmis!")
        visible("195 XP")
        tap("Uusi seikkailu")
        compose.onNodeWithText("Peruuta").performClick()
        visible("195 XP")
        tap("Uusi seikkailu")
        compose.onNodeWithText("Nollaa").performClick()
        visible("Aloita seikkailu")
    }

    @Test fun mutePersistsAndCorruptSessionHasAnExplicitRecovery() {
        tap("Asetukset")
        compose.onNodeWithContentDescription("Äänet mykistetty").performClick()
        compose.onNodeWithText("Sulje").performClick()
        compose.activityRule.scenario.recreate()
        tap("Asetukset")
        compose.onNodeWithContentDescription("Äänet mykistetty").assertIsOn()
        compose.onNodeWithText("Sulje").performClick()
        runBlocking {
            compose.activity.applicationContext.skillitDataStore.updateData {
                androidx.datastore.preferences.core.mutablePreferencesOf(ProgressRepository.key to "not-json")
            }
        }
        compose.activityRule.scenario.recreate()
        visible("Tallennusta ei voitu lukea. Voit yrittää uudelleen tai aloittaa alusta.")
        tap("Aloita alusta")
        compose.onNodeWithText("Nollaa").performClick()
        visible("Aloita seikkailu")
    }
    @Test fun notationHasPositionSemanticsAndChoicesHaveLargeTapTargets() {
        tap("Aloita seikkailu")
        val item = MusicPack.items.first()
        compose.onNodeWithContentDescription(notationDescription(item.task)).assertExists()
        item.question.choices.forEach { choice ->
            val node = compose.onNodeWithText(choice.label)
            node.assertHasClickAction()
            node.assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Vihje").assertHeightIsAtLeast(48.dp)
    }
}
