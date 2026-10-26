package fi.skillit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fi.skillit.engine.ExerciseSession
import fi.skillit.music.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class M2GameplayTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun fresh() {
        runBlocking { ProgressRepository(compose.activity.applicationContext.skillitDataStore).reset(false) }
        compose.activityRule.scenario.recreate()
        await("Aloita seikkailu")
    }
    private fun await(text: String) { compose.waitUntilAtLeastOneExists(hasText(text), 10000) }
    private fun tap(text: String) { await(text); compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun visible(text: String) { await(text); compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
    private fun capture(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "m2").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun islandReplayResumesAndLeavesOriginalAdventureUntouched() {
        val session = ExerciseSession(MusicPack.questions)
        repeat(5) { val a = session.current!!; session.answer(a.token, a.question.correctId); session.next(a.token) }
        val repo = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        runBlocking { repo.save(SavedProgress(started = true, events = session.events)) }
        compose.activityRule.scenario.recreate()
        visible("Sävelsalapoliisi")
        tap("Etusivu")
        visible("Valmis · saaren sävelmerkki ansaittu!")
        capture("01-earned-island")
        tap("Harjoittele saarta 1")
        visible("Harjoituskierros · erilliset harjoitus-XP")
        tap("puolinuotti"); tap("Jatka")
        compose.activityRule.scenario.recreate()
        visible("Kuinka monta iskua nuotti kestää?")
        tap("Etusivu"); tap("Jatka saaren 1 harjoitusta")
        val replay = ExerciseSession.restore(Adventure.questions(0), runBlocking { repo.load() }.practice.getValue(0))
        while (!replay.complete) {
            val a = replay.current!!
            tap(a.question.choices.single { it.id == a.question.correctId }.label)
            replay.answer(a.token, a.question.correctId)
            tap("Jatka"); replay.next(a.token)
        }
        visible("Harjoituskierros valmis!")
        visible("45 harjoitus-XP")
        capture("02-practice-reward")
        assertEquals(session.events, runBlocking { repo.load() }.events)
        tap("Etusivu"); tap("Jatka seikkailua")
        visible("Sävelsalapoliisi")
    }
    @Test fun rhythmButtonsHandleUnderfillOverflowCorrectAndRecreation() {
        tap("Rytmipaja")
        visible("Rytmipaja")
        tap("Tarkista tahti")
        visible("Tahtiin mahtuu vielä. Lisää nuotti tai tauko.")
        val challenge = RhythmChallenge.generate(Meter.TWO_FOUR, 0)
        challenge.solution.forEach { symbol ->
            tap("Lisää ${if (symbol is Rest) symbol.duration.restFi else symbol.duration.noteFi} · ${beatsFi(symbol.duration.eighths)} iskua")
        }
        compose.activityRule.scenario.recreate()
        tap("Tarkista tahti")
        visible("Hienoa! Tahti on täynnä. Kuuntele oma rytmisi.")
        capture("03-rhythm-correct")
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        visible("Hienoa! Tahti on täynnä. Kuuntele oma rytmisi.")
        capture("04-rhythm-landscape")
        compose.runOnUiThread { compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        visible("Hienoa! Tahti on täynnä. Kuuntele oma rytmisi.")
        tap("Lisää puolinuotti · 2 iskua"); tap("Tarkista tahti")
        visible("Tahti on liian pitkä. Poista merkki ja kokeile uudelleen.")
        tap("Poista viimeinen"); tap("Tarkista tahti")
        visible("Hienoa! Tahti on täynnä. Kuuntele oma rytmisi.")
        tap("Kokeile mukana naputtelua")
        visible("Napauta")
        compose.onNodeWithText("Napauta").performClick()
        tap("Pysäytä harjoitus")
        visible("Pysäytetty. Voit aloittaa uudelleen.")
        tap("4/4"); tap("Uusi rytmi")
        tap("Takaisin kartalle"); visible("Aloita seikkailu")
    }
    @Test fun finalFeedbackCanContinueDirectlyToTheNewlyUnlockedIsland() {
        tap("Aloita seikkailu")
        MusicPack.questions.take(5).forEachIndexed { index, q ->
            tap(q.choices.single { it.id == q.correctId }.label)
            if (index < 4) tap("Jatka")
        }
        tap("Etusivu"); tap("Jatka saarelle 2")
        visible("Sävelsalapoliisi")
    }
    @Test fun childCanDragTheSameSymbolThatButtonsAdd() {
        tap("Rytmipaja")
        val source = compose.onNodeWithText("Lisää puolinuotti · 2 iskua")
        source.performScrollTo()
        val target = compose.onNodeWithContentDescription("Täydennettävä tahti")
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        // Drop into the visible lower edge when large text puts the card's centre above the viewport.
        val destination = androidx.compose.ui.geometry.Offset(targetBounds.center.x, targetBounds.bottom - 20)
        val origin = source.fetchSemanticsNode().boundsInRoot.topLeft
        source.performTouchInput {
            down(center); advanceEventTime(700); moveTo(destination - origin, 500); up()
        }
        val beatsAfterOneDrop = beatsFi(RhythmChallenge.generate(Meter.TWO_FOUR, 0).prefix.sumOf { it.duration.eighths } + 4)
        visible("2/4 · $beatsAfterOneDrop/2 iskua")
        tap("Tarkista tahti")
        val expected = RhythmChallenge.generate(Meter.TWO_FOUR, 0).evaluate(listOf(Note(Pitch.C4,Duration.HALF)))
        visible(if (expected == FillResult.CORRECT) "Hienoa! Tahti on täynnä. Kuuntele oma rytmisi." else "Tahti on liian pitkä. Poista merkki ja kokeile uudelleen.")
    }
}
