package fi.skillit.math.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fi.skillit.ProgressRepository
import fi.skillit.SavedProgress
import fi.skillit.SkillitApp
import fi.skillit.engine.Action
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.adaptive.AttemptEvent
import fi.skillit.engine.adaptive.InputMethod
import fi.skillit.engine.pack.ContentPack
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackUnit
import fi.skillit.engine.pack.SourceRef
import fi.skillit.math.*
import fi.skillit.music.MusicPack
import fi.skillit.music.PcmOutput
import fi.skillit.mathgen.MathGen
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class MemoryStore(var progress: MathProgress = MathProgress()) : MathStore {
    override suspend fun load() = progress
    override suspend fun save(progress: MathProgress) { this.progress = progress }
}
/** Stands in for the S Pen: a tap "writes" [reading]; the board's confirm prompt answers through [confirm]. */
class FakePen(var reading: CellValue = CellValue(null, null, 4 to 9)) : DigitInput {
    override val method = InputMethod.PEN
    val confirmed = mutableListOf<Int>()
    @androidx.compose.runtime.Composable
    override fun Cell(item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
                      onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier) {
        androidx.compose.material3.TextButton(onClick = { onValue(reading) }, modifier = modifier) {
            androidx.compose.material3.Text("Kynäruutu ${cell.label}: ${value.text}")
        }
    }
    override fun confirm(item: String, cell: InputCell, value: CellValue, digit: Int): CellValue {
        confirmed += digit
        return CellValue(digit, .55)
    }
    @androidx.compose.runtime.Composable
    override fun Controls(focused: InputCell?, value: CellValue?, enabled: Boolean, onValue: (CellValue) -> Unit, onNext: () -> Unit) {
        androidx.compose.material3.Text("Kynä käytössä")
    }
}
class MemoryAttempts : AttemptSink {
    val events = mutableListOf<AttemptEvent>()
    override suspend fun read() = events.toList()
    override suspend fun append(event: AttemptEvent) { events += event }
}

@RunWith(RobolectricTestRunner::class)
class MathScreenTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    /** Lesson 18 with two items, plus a letter code: three items per session. */
    private val pack = ContentPack(1, "math-test", "math", 4, 1, "Testi", units = listOf(
        PackUnit("lesson-18", "Kymmenillä", SourceRef(2, 18), listOf(
            MathGen.generate(GeneratorRef("tens-hundreds", seed = 1)).exercise,
            MathGen.generate(GeneratorRef("tens-hundreds", seed = 2)).exercise,
            MathGen.generate(GeneratorRef("letter-code", JsonObject(mapOf("word" to JsonPrimitive(0))), seed = 3)).exercise,
        )),
        PackUnit("lesson-27-test", "Koe", SourceRef(2, 27), listOf(MathGen.generate(GeneratorRef("column", seed = 4)).exercise)),
    ))
    private val course = MathCourse(pack)
    private val store = MemoryStore()
    private val attempts = MemoryAttempts()
    private var now = 1_000_000L
    private val mode = kotlinx.coroutines.flow.MutableStateFlow(fi.skillit.ink.InputMode.Pen)
    private val pen = FakePen()
    private val env = MathEnvironment({ course }, store, attempts, mode, { mode.value = it }, { pen }, clock = { now })
    private fun padUnlocked() { mode.value = fi.skillit.ink.InputMode.NumberPad }

    private fun show() = compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env) {} } } }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun pin(digits: String) = digits.forEach { compose.onNodeWithContentDescription("PIN $it").performScrollTo().performClick() }
    private fun openParentArea() {
        compose.onNodeWithText("Vanhemmille").performScrollTo().performTouchInput { longClick() }
        compose.waitForIdle()
    }

    @Test fun parentSetsPinOnFirstEntryAndAMismatchStartsOver() {
        show()
        compose.onNodeWithText("Vanhemmalle: aseta PIN-koodi").assertExists()
        pin("1234"); pin("4321")
        compose.onNodeWithText("Koodit eivät täsmää. Aloita alusta.").assertExists()
        pin("1234"); pin("1234")
        compose.waitUntil(5_000) { store.progress.pin != null }
        compose.onNodeWithText("Kertolaskuseikkailu").assertExists()
        assertEquals(ParentPin.Check.Ok, store.progress.pin!!.check("1234", 0))
        listOf("Oppitunti 18: Kymmenillä", "Kappaleen koe: Koe").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun longPressAndPinUnlockTheNumberPadAndATapDoesNot() {
        store.progress = MathProgress(pin = ParentPin.create("2468"))
        show()
        tap("Vanhemmille")
        compose.onNodeWithText("Vanhempi: pidä painiketta pohjassa.").assertExists()
        compose.onNodeWithText("Vanhempien alue").assertDoesNotExist()
        openParentArea()
        pin("1111")
        compose.waitUntil(5_000) { store.progress.pin!!.failures == 1 }
        compose.onNodeWithText("Väärä koodi.").assertExists()
        pin("2468")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Numeronäppäimet").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, store.progress.pin!!.failures)
        assertEquals(fi.skillit.ink.InputMode.Pen, mode.value)
        compose.onNodeWithText("Numeronäppäimet").performClick()
        compose.waitUntil(5_000) { mode.value == fi.skillit.ink.InputMode.NumberPad }
        tap("Sulje vanhempien alue")
        compose.onNodeWithText("Kertolaskuseikkailu").assertExists()
    }

    @Test fun penIsTheDefaultAndAnUnsureReadingIsConfirmedBeforeChecking() {
        store.progress = MathProgress(pin = ParentPin.create("2468"))
        show()
        tap("Aloita: Oppitunti 18")
        compose.waitUntil(5_000) { store.progress.active != null }
        compose.onNodeWithText("Kynä käytössä").assertExists()
        compose.onAllNodesWithContentDescription("Numero 1").assertCountEquals(0)
        compose.onNodeWithText("Tarkista").assertIsNotEnabled()
        compose.onNodeWithText("Kynäruutu Tulos", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Tarkoititko 4 vai 9?").assertExists()
        compose.onNodeWithText("Tarkista").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Valitse 9").performScrollTo().performClick()
        assertEquals(listOf(9), pen.confirmed)
        tap("Tarkista")
        compose.waitUntil(5_000) { attempts.events.size == 1 }
        val event = attempts.events.single()
        assertEquals(InputMethod.PEN, event.inputMethod)
        assertEquals(.55, event.recognizerConfidence!!, 1e-9)
        assertEquals(mapOf("answer" to 9), (event.answer as fi.skillit.engine.AnswerInput.Cells).values)
    }

    @Test fun aPadLessonLogsAttemptsAwardsMathXpAndRevealsTheRewardWord() {
        store.progress = MathProgress(pin = ParentPin.create("2468"))
        padUnlocked()
        show()
        tap("Aloita: Oppitunti 18")
        compose.waitUntil(5_000) { store.progress.active != null }
        val plan = store.progress.active!!.plan
        assertEquals(3, plan.itemIds.size)
        assertEquals("lesson-18", plan.stopId)
        val exercises = course.exercises(plan)!!
        exercises.forEachIndexed { index, exercise ->
            compose.waitUntil(5_000) { compose.onAllNodesWithText(exercise.prompt).fetchSemanticsNodes().isNotEmpty() }
            val kind = BoardKind.of(exercise)
            val expected = when (val a = exercise.answer) {
                is AnswerSpec.Numeric -> mapOf(kind.cells.single().id to a.expected)
                is AnswerSpec.FillTable -> a.blanks.associate { it.id to it.expected }
                else -> error("unexpected")
            }
            // The first item is answered wrong once to see the retry path, then right.
            if (index == 0) {
                fill(kind, expected.mapValues { it.value + 1 })
                tap("Tarkista")
                compose.waitUntil(5_000) { attempts.events.size == 1 }
                compose.onNodeWithText("Harjoitellaan vielä", substring = true).assertExists()
                tap("Jatka")
            } else {
                fill(kind, expected)
                tap("Tarkista")
                compose.waitUntil(5_000) { compose.onAllNodesWithText("Hienoa! +10 XP").fetchSemanticsNodes().isNotEmpty() }
                tap("Jatka")
            }
        }
        // Retry of the first item comes at the end, prefilled with the previous answer and its first wrong cell marked.
        val first = exercises.first()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Uusi yritys · voit saada 5 XP").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Katso vielä ruutua: Tulos.").assertExists()
        compose.onNodeWithContentDescription("Tulos, ", substring = true).performScrollTo().performClick()
        repeat(6) { compose.onNodeWithText("Pyyhi").performScrollTo().performClick() }
        enter("Tulos", (first.answer as AnswerSpec.Numeric).expected)
        tap("Tarkista")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Hienoa! +5 XP").fetchSemanticsNodes().isNotEmpty() }
        tap("Jatka")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Palkintosana").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("KISSA").assertExists()
        compose.onNodeWithText("25 XP").assertExists()
        tap("Takaisin kartalle")
        compose.waitUntil(5_000) { store.progress.active == null }
        assertEquals(25, store.progress.xp)
        assertEquals(LessonRecord(1, 25, "KISSA"), store.progress.lessons["lesson-18"])
        compose.onNodeWithText("Valmis 1× · paras 25 XP · palkintosana KISSA").assertExists()
        // One attempt per accepted answer, all from the pad, with the retry numbered and the right family.
        assertEquals(4, attempts.events.size)
        assertTrue(attempts.events.all { it.inputMethod == InputMethod.PAD && it.recognizerConfidence == null && it.packId == "math-test" })
        assertEquals(listOf(false, true, true, true), attempts.events.map { it.correct })
        assertEquals(listOf(0, 0, 0, 1), attempts.events.map { it.retryNumber })
        assertEquals("tens-hundreds", attempts.events.first().family)
        assertEquals(now, attempts.events.first().timestamp)
    }

    private fun cell(label: String) = SemanticsMatcher("cell $label") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.any { it.startsWith("$label, ") } == true
    }
    private fun digits(number: Int) = number.toString().forEach { compose.onNodeWithContentDescription("Numero $it").performScrollTo().performClick() }
    private fun enter(label: String, number: Int) {
        compose.onNode(cell(label)).performScrollTo().performClick()
        digits(number)
    }
    /** Like a child: tap the first cell, then write each cell and move on (labels may repeat in a letter code). */
    private fun fill(kind: BoardKind, values: Map<String, Int>) {
        compose.onAllNodes(cell(kind.cells.first().label))[0].performScrollTo().performClick()
        kind.cells.forEach { c ->
            digits(values.getValue(c.id))
            if (c.maxDigits > 1) compose.onNodeWithText("Seuraava ruutu").performScrollTo().performClick()
        }
    }

    @Test fun resumingKeepsTheSessionAndSwitchingLessonsAsksFirst() {
        store.progress = MathProgress(pin = ParentPin.create("2468"))
        padUnlocked()
        show()
        tap("Aloita: Oppitunti 18")
        compose.waitUntil(5_000) { store.progress.active != null }
        tap("Kartalle")
        compose.onNodeWithText("Kesken · jatka siitä, mihin jäit.").assertExists()
        tap("Aloita: Kappaleen koe")
        compose.onNodeWithText("Vaihdetaanko oppituntia?").assertExists()
        compose.onNodeWithText("Peruuta").performClick()
        assertEquals("lesson-18", store.progress.active!!.plan.stopId)
        tap("Jatka: Oppitunti 18")
        compose.onNodeWithText("0/3 ratkaistu · 0 XP").assertExists()
    }
}

/** Main menu → Kertolaskuseikkailu, and the music save is left exactly as it was. */
@RunWith(RobolectricTestRunner::class)
class MathNavigationTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    private object Silent : PcmOutput {
        override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) = onFinished()
        override fun stop() = Unit
    }

    @Test fun mainMenuOpensMathAndBackReturnsWithMusicProgressIntact() {
        val music = ProgressRepository(PreferenceDataStoreFactory.create { temporary.newFile("music.preferences_pb").also { it.delete() } })
        val session = ExerciseSession(MusicPack.questions)
        session.answer(session.current!!.token, session.current!!.question.correctId)
        val before = SavedProgress(started = true, events = session.events, volume = .4f)
        runBlocking { music.save(before) }
        val store = MemoryStore()
        val env = MathEnvironment({ MathCourse(fi.skillit.math.MathFixture.build()) }, store, MemoryAttempts(),
            kotlinx.coroutines.flow.flowOf(fi.skillit.ink.InputMode.Pen), {}, { FakePen() })
        compose.setContent { MaterialTheme { SkillitApp(music, Silent, env) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Etusivu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Etusivu").performScrollTo().performClick()
        compose.onNodeWithText("Kertolaskuseikkailu").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Vanhemmalle: aseta PIN-koodi").fetchSemanticsNodes().isNotEmpty() }
        "13571357".forEach { compose.onNodeWithContentDescription("PIN $it").performScrollTo().performClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita: Oppitunti 14").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Matematiikan pisteet: 0 XP", substring = true).assertExists()
        // Opening a lesson low on the map starts the lesson at the top: no performScrollTo on the check.
        compose.onNodeWithText("Aloita: Kappaleen koe").performScrollTo().performClick()
        compose.waitUntil(5_000) { store.progress.active != null }
        compose.onNodeWithText("Kappaleen koe", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Kartalle").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Kertolaskuseikkailu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kertolaskuseikkailu").assertIsDisplayed()
        compose.onNodeWithText("Etusivu").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Nuottiseikkailu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Seikkailun pisteet: 10 XP", substring = true).assertExists()
        assertEquals(before, runBlocking { music.load() })
        assertNotNull(store.progress.pin)
        assertEquals(Action.ANSWER, before.events.single().action)
    }
}
