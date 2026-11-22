package fi.skillit.math.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import fi.skillit.coach.*
import fi.skillit.engine.adaptive.InputMethod
import fi.skillit.engine.pack.ContentPack
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackUnit
import fi.skillit.engine.pack.SourceRef
import fi.skillit.ink.InputMode
import fi.skillit.math.*
import fi.skillit.mathgen.MathGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** A pen whose tap "writes" an unsure 9, and which reports that digit's ink to the coach. */
class UnsurePen(private val digits: List<WrittenDigit>) : DigitInput {
    override val method = InputMethod.PEN
    @androidx.compose.runtime.Composable
    override fun Cell(item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
                      onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier) {
        androidx.compose.material3.TextButton(onClick = { onValue(CellValue(9, .4)) }, modifier = modifier) {
            androidx.compose.material3.Text("Kynäruutu ${cell.label}: ${value.text}")
        }
    }
    @androidx.compose.runtime.Composable
    override fun Controls(focused: InputCell?, value: CellValue?, enabled: Boolean, onValue: (CellValue) -> Unit, onNext: () -> Unit) {}
    override fun written(item: String) = digits
}

/** #28 wired into #27: Numeropaja from the map, the clarity moment after an answer, the parent trend. */
@RunWith(RobolectricTestRunner::class)
class CoachInLessonTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()

    private val pack = ContentPack(1, "math-test", "math", 4, 1, "Testi", units = listOf(
        PackUnit("lesson-18", "Kymmenillä", SourceRef(2, 18), listOf(
            MathGen.generate(GeneratorRef("tens-hundreds", seed = 1)).exercise,
            MathGen.generate(GeneratorRef("tens-hundreds", seed = 2)).exercise,
        )),
    ))
    private val course = MathCourse(pack)
    private val store = MemoryStore(MathProgress(pin = ParentPin.create("2468")))
    private val attempts = MemoryAttempts()
    private val mode = MutableStateFlow(InputMode.Pen)
    private val legibility by lazy { LegibilityStore(File(tmp.root, "legibility.v1.tsv")) }
    private val ink by lazy { fi.skillit.ink.AttemptInkStore(File(tmp.root, "attempts")) }
    private val coach by lazy { CoachEnvironment(legibility, null, TestSurface, Dispatchers.Unconfined, Dispatchers.Unconfined, ink) }
    private val nine = Fixtures["neat-9"]
    private val pen = UnsurePen(listOf(WrittenDigit(0, 9, .4f, strokes = nine.strokes, boxWidth = nine.boxWidth, boxHeight = nine.boxHeight)))
    private fun env(withCoach: Boolean = true) = MathEnvironment({ course }, store, attempts, mode, { mode.value = it }, { pen }, clock = { 5L },
        coach = { if (withCoach) coach else null })

    private fun show(withCoach: Boolean = true) {
        val e = env(withCoach)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(e) {} } } }
    }
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()

    private fun answerFirstItem() {
        tap("Aloita: Oppitunti 18")
        compose.waitUntil(5_000) { store.progress.active != null }
        compose.onAllNodesWithText("Kynäruutu", substring = true).fetchSemanticsNodes().indices.forEach {
            compose.onAllNodesWithText("Kynäruutu", substring = true)[it].performScrollTo().performClick()
        }
        tap("Tarkista")
        compose.waitUntil(5_000) { attempts.events.size == 1 }
    }

    @Test fun anUnsureDigitGetsAnOptionalMomentThatNeverChangesXp() {
        show()
        answerFirstItem()
        compose.onNodeWithText("Kirjoitetaanko numero 9 vielä kerran selvemmin?").assertExists()
        val eventsBefore = store.progress.active!!.events
        val feedback = compose.onAllNodes(hasText("XP", substring = true) or hasText("Harjoitellaan", substring = true)).fetchSemanticsNodes().size
        tap("Ei nyt")
        compose.onNodeWithText("Kirjoitetaanko numero 9 vielä kerran selvemmin?").assertDoesNotExist()
        assertEquals("the moment adds no session events", eventsBefore, store.progress.active!!.events)
        assertEquals(feedback, compose.onAllNodes(hasText("XP", substring = true) or hasText("Harjoitellaan", substring = true)).fetchSemanticsNodes().size)
        assertEquals(1, attempts.events.size)
        assertEquals("the answer's ink was measured into the aggregates", 1, legibility.days().single { it.digit == 9 }.count)
        // N1: the attempt's strokes are kept under its event id (and pruned on every save).
        val saved = ink.load(attempts.events.single().eventId)!!
        assertEquals(9, saved.cells.getValue(0).digit)
        assertEquals(nine.strokes, saved.cells.getValue(0).strokes)
    }

    @Test fun theNumberPadNeverPrompts() {
        mode.value = InputMode.NumberPad
        show()
        tap("Aloita: Oppitunti 18")
        compose.waitUntil(5_000) { store.progress.active != null }
        compose.onAllNodesWithText("Kirjoitetaanko", substring = true).assertCountEquals(0)
        assertTrue(PadInput.written("any").isEmpty())
    }

    @Test fun numeropajaOpensFromTheMathMap() {
        show()
        tap("Numeropaja · harjoittele numeroiden kirjoittamista")
        compose.onNodeWithText("Numeropaja").assertExists()
        tap("Takaisin")
        compose.onNodeWithText("Kertolaskuseikkailu").assertExists()
    }

    @Test fun withoutTheCoachTheMapHasNoStudio() {
        show(withCoach = false)
        compose.onNodeWithText("Kertolaskuseikkailu").assertExists()
        compose.onAllNodesWithText("Numeropaja", substring = true).assertCountEquals(0)
    }

    @Test fun parentAreaShowsTheLegibilityTrend() {
        legibility.record(Fixtures["neat-4"].report())
        show()
        compose.onNodeWithText("Vanhemmille").performScrollTo().performTouchInput { longClick() }
        compose.waitForIdle()
        "2468".forEach { compose.onNodeWithContentDescription("PIN $it").performScrollTo().performClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Numeroiden selkeys").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Numero 4: selkeys", substring = true)).assertExists()
    }
}
