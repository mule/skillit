package fi.skillit.math.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.pack.ContentPack
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackExercise
import fi.skillit.engine.pack.PackUnit
import fi.skillit.engine.pack.SourceRef
import fi.skillit.ink.InputMode
import fi.skillit.math.*
import fi.skillit.mathgen.MathGen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Review of PR #41: table width, resumed answers, corrupt save, parent area lock. */
@RunWith(RobolectricTestRunner::class)
class ReviewFixesTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temporary = TemporaryFolder()

    private val kissa = MathGen.generate(GeneratorRef("letter-code", JsonObject(mapOf("word" to JsonPrimitive(0))), seed = 3)).exercise
    private val strip = MathGen.generate(GeneratorRef("tables", JsonObject(mapOf("table" to JsonPrimitive(10), "count" to JsonPrimitive(10))), seed = 5)).exercise

    /** The pad's cells with the pen's (wider) layout width: the pen view itself needs the device's native ink library. */
    private object PenWidthPad : DigitInput by PadInput {
        override fun width(cell: InputCell): Dp = CellStyle.digit * cell.maxDigits
        @Composable override fun Cell(item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
                                      onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier) =
            PadInput.Cell(item, cell, value, focused, mark, enabled, onFocus, onValue, modifier.width(width(cell)))
    }

    private fun assertTableUsable(exercise: PackExercise, input: DigitInput, contentWidth: Dp) {
        val draft = AnswerDraft(BoardKind.of(exercise))
        compose.setContent { MaterialTheme { Box(Modifier.width(contentWidth)) { Column(Modifier.verticalScroll(rememberScrollState())) {
            MathBoard(exercise, draft, input, null, enabled = true)
        } } } }
        val table = exercise.answer as AnswerSpec.FillTable
        table.blanks.forEachIndexed { i, blank ->
            val node = compose.onAllNodes(hasContentDescription(draft.kind.cells.first { it.id == blank.id }.label, substring = true))[if (exercise == kissa) labelIndex(draft, blank.id) else 0]
            node.performScrollTo()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("cell ${blank.id} is ${bounds.width} wide at $contentWidth", bounds.width >= input.width(draft.kind.cells[i]) - 1.dp && bounds.width >= 48.dp)
            assertTrue(bounds.height >= 48.dp)
            // Its header sits in the same column (same centre).
            val header = (table.rows[0][table.rows[1].indexOfFirst { it == blank }] as fi.skillit.engine.TableEntry.Given).text
            val h = compose.onAllNodesWithText(header)[if (exercise == kissa) labelIndex(draft, blank.id) else 0].getUnclippedBoundsInRoot()
            assertEquals("header over ${blank.id}", ((h.left + h.right) / 2).value, ((bounds.left + bounds.right) / 2).value, 1f)
        }
        val last = draft.kind.cells.last()
        compose.onAllNodes(hasContentDescription(last.label, substring = true)).onLast().performScrollTo().performClick()
        assertEquals(last.id, draft.focused)
    }
    private fun labelIndex(draft: AnswerDraft, id: String): Int {
        val label = draft.kind.cells.first { it.id == id }.label
        return draft.kind.cells.filter { it.label == label }.indexOfFirst { it.id == id }
    }

    @Test fun letterCodeAt360dpPad() = assertTableUsable(kissa, PadInput, 360.dp)
    @Test fun letterCodeAtTabletWidthPad() = assertTableUsable(kissa, PadInput, 600.dp)
    @Test fun letterCodeAtTabletWidthPenWidth() = assertTableUsable(kissa, PenWidthPad, 600.dp)
    @Test fun tenColumnTimesTableStripAt360dp() = assertTableUsable(strip, PenWidthPad, 360.dp)

    // ---- screen-level ----
    private val pack = ContentPack(1, "math-test", "math", 4, 1, "Testi", units = listOf(
        PackUnit("lesson-20", "Allekkain", SourceRef(2, 20), listOf(
            PackExercise("c234", "Laske allekkain 234 · 5.", "Aloita ykkösistä.", "Selitys.", AnswerSpec.ColumnMultiplication.layout(234, 5), "234 * 5"),
            PackExercise("c12", "Laske allekkain 12 · 3.", "Aloita ykkösistä.", "Selitys.", AnswerSpec.ColumnMultiplication.layout(12, 3), "12 * 3"),
        )),
    ))
    private val mode = MutableStateFlow(InputMode.NumberPad)
    private fun env(store: MathStore) = MathEnvironment({ MathCourse(pack) }, store, MemoryAttempts(), mode, { mode.value = it }, { FakePen() }, clock = { 5L })
    private fun cell(label: String) = compose.onNode(hasContentDescription(label, substring = true))
    private fun tap(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun digits(s: String) = s.forEach { compose.onNodeWithContentDescription("Numero $it").performScrollTo().performClick() }

    @Test fun resumingDuringFeedbackAndOnTheRetryShowsTheSubmittedAnswer() {
        val store = MemoryStore(MathProgress(pin = ParentPin.create("2468")))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env(store)) {} } } }
        tap("Aloita: Oppitunti 20")
        compose.waitUntil(5_000) { store.progress.active != null }
        val first = store.progress.active!!.plan.itemIds.first()
        val spec = pack.units[0].exercises.first { it.id == first }.answer as AnswerSpec.ColumnMultiplication
        val results = spec.cells.filter { it.kind == fi.skillit.engine.DigitKind.RESULT }
        // Results only, with the tens digit wrong.
        cell("Tulos, ykköset").performScrollTo().performClick()
        results.forEach { c ->
            cell("Tulos, ${placeName(c.column)}, ").performScrollTo().performClick()
            digits(((if (c.column == 1) c.expected + 1 else c.expected) % 10).toString())
        }
        tap("Tarkista")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Harjoitellaan vielä", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val wrong = (results.first { it.column == 1 }.expected + 1) % 10
        tap("Kartalle"); tap("Jatka: Oppitunti 20")
        cell("Tulos, kymmenet, $wrong").assertExists()
        compose.onNodeWithText("Katso vielä ruutua: Tulos, kymmenet.").assertExists()
        // Through the other item to the retry, again after a trip to the map.
        tap("Jatka")
        val second = pack.units[0].exercises.first { it.id != first }.answer as AnswerSpec.ColumnMultiplication
        second.cells.filter { it.required }.forEach { c ->
            cell("Tulos, ${placeName(c.column)}, ").performScrollTo().performClick(); digits(c.expected.toString())
        }
        tap("Tarkista")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Hienoa! +10 XP").fetchSemanticsNodes().isNotEmpty() }
        tap("Jatka")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Uusi yritys · voit saada 5 XP").fetchSemanticsNodes().isNotEmpty() }
        tap("Kartalle"); tap("Jatka: Oppitunti 20")
        cell("Tulos, kymmenet, $wrong, katso tätä vielä").assertExists()
        cell("Tulos, ykköset, ${results.first().expected}").assertExists()
    }

    private fun dataStore() = PreferenceDataStoreFactory.create { temporary.newFile("math.preferences_pb").also { it.delete() } }

    @Test fun corruptMathSaveOffersAFreshStartThatKeepsTheSalvagedPin() {
        val ds = dataStore()
        val pin = ParentPin.create("2468")
        val damaged = MathProgressCodec.encode(MathProgress(xp = 40, pin = pin)).replace("\"lessons\"", "\"lessonz\"")
        runBlocking { ds.edit { it[MathRepository.key] = damaged } }
        val repo = MathRepository(ds)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env(repo)) {} } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita kertolaskut alusta").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Vanhempien PIN-koodi säilyy.", substring = true).assertExists()
        tap("Aloita kertolaskut alusta")
        compose.onNodeWithText("Aloita alusta").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita: Oppitunti 20").fetchSemanticsNodes().isNotEmpty() }
        val fresh = runBlocking { repo.load() }
        assertEquals(MathProgress(pin = pin), fresh)
    }

    @Test fun unreadableMathSaveRecoversToPinSetup() {
        val ds = dataStore()
        runBlocking { ds.edit { it[MathRepository.key] = "corrupt" } }
        val repo = MathRepository(ds)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env(repo)) {} } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita kertolaskut alusta").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Vanhempi asettaa PIN-koodin uudelleen.", substring = true).assertExists()
        tap("Yritä uudelleen")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita kertolaskut alusta").fetchSemanticsNodes().isNotEmpty() }
        tap("Aloita kertolaskut alusta")
        compose.onNodeWithText("Aloita alusta").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Vanhemmalle: aseta PIN-koodi").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(MathProgress(), runBlocking { repo.load() })
    }

    private fun unlock() {
        compose.onNodeWithText("Vanhemmille").performScrollTo().performTouchInput { longClick() }
        "2468".forEach { compose.onNodeWithContentDescription("PIN $it").performScrollTo().performClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sulje vanhempien alue").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun parentAreaIsNeverRestoredAfterProcessRestore() {
        val store = MemoryStore(MathProgress(pin = ParentPin.create("2468")))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env(store)) {} } } }
        unlock()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita: Oppitunti 20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sulje vanhempien alue").assertDoesNotExist()
    }

    @Test fun parentAreaLocksWhenTheAppGoesToTheBackground() {
        val store = MemoryStore(MathProgress(pin = ParentPin.create("2468")))
        val owner = object : LifecycleOwner { val registry = LifecycleRegistry(this); override val lifecycle: Lifecycle get() = registry }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { MathScreen(env(store)) {} }
        } } }
        unlock()
        compose.runOnUiThread { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.runOnUiThread { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START); owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Aloita: Oppitunti 20").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sulje vanhempien alue").assertDoesNotExist()
    }
}
