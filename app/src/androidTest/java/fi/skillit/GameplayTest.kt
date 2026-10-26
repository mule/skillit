package fi.skillit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fi.skillit.music.MusicPack
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
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

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "review-fixes").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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
                tap("puolinuotti")
                visible("Harjoitellaan vielä. Voit yrittää uudelleen.")
                visible("Tämä on neljäsosanuotti. Sen kesto on 1 isku.")
                compose.onNodeWithText("Seikkailu valmis!").assertDoesNotExist()
                compose.activityRule.scenario.recreate()
                visible("Harjoitellaan vielä. Voit yrittää uudelleen.")
                tap("Yritä uudelleen")
                visible("Uusi yritys · voit saada 5 XP")
                tap("Vihje")
                visible("Musta nuotinpää ja varsi, ei lippua.")
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
    @Test fun multipleMissesDescribeQueuedAndImmediateRetriesAccurately() {
        tap("Aloita seikkailu")
        tap("puolinuotti")
        tap("Jatka")
        tap("4")
        tap("Jatka")
        listOf("4", "½", "2").forEach { answer -> tap(answer); tap("Jatka") }
        tap("puolinuotti")
        visible("Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.")
        compose.onNodeWithText("Yritä uudelleen").assertDoesNotExist()
        capture("01-queued-review-feedback")
        compose.activityRule.scenario.recreate()
        visible("Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.")
        tap("Jatka")
        compose.onNodeWithContentDescription(notationDescription(MusicPack.items[1].task)).assertExists()
        tap("2")
        visible("Hienoa! +5 XP")
        tap("Jatka")
        tap("puolinuotti")
        visible("Harjoitellaan vielä. Voit yrittää uudelleen.")
        tap("Yritä uudelleen")
        compose.onNodeWithContentDescription(notationDescription(MusicPack.items[0].task)).assertExists()
        tap("neljäsosanuotti")
        visible("Hienoa! +5 XP")
        visible("Taso 1/4 · 5/20 ratkaistu · 40 XP")
        tap("Jatka")
        visible("Sävelsalapoliisi")
    }
    @Test fun chosenAndCorrectChoicesAreMarkedOnlyAfterAnswerAndSurviveRecreation() {
        val chosen = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Valitsit tämän")
        val correct = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Oikea vastaus")
        tap("Aloita seikkailu")
        compose.onAllNodes(chosen).assertCountEquals(0)
        compose.onAllNodes(correct).assertCountEquals(0)
        tap("puolinuotti")
        compose.onNode(hasText("puolinuotti") and chosen).assertExists()
        compose.onNode(hasText("neljäsosanuotti") and correct).performScrollTo().assertIsDisplayed()
        capture("02-answer-markings")
        compose.activityRule.scenario.recreate()
        compose.waitUntilExactlyOneExists(hasText("puolinuotti") and chosen, 10_000)
        compose.onNode(hasText("neljäsosanuotti") and correct).assertExists()
        tap("Jatka")
        compose.onAllNodes(chosen).assertCountEquals(0)
        compose.onAllNodes(correct).assertCountEquals(0)
        tap("2")
        compose.onNode(hasText("2") and SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Valitsit tämän · Oikea vastaus",
        )).assertExists()
    }
    @Test fun homeSettingsAndResetConfirmationSurviveActivityRecreation() {
        tap("Aloita seikkailu")
        tap("Etusivu")
        compose.activityRule.scenario.recreate()
        visible("Jatka seikkailua")
        tap("Asetukset")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Sulje").assertIsDisplayed().performClick()
        tap("Asetukset")
        compose.onNodeWithText("Nollaa edistyminen").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Aloitetaanko alusta?").assertIsDisplayed()
        compose.onNodeWithText("Peruuta").performClick()
        visible("Jatka seikkailua")
        tap("Jatka seikkailua")
        visible("Nuottininja")
    }
}
