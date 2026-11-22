package fi.skillit.coach

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fi.skillit.SkillitTheme
import fi.skillit.ink.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A plain Compose writing surface for JVM tests: androidx.ink's native code doesn't load under Robolectric.
 * Like the real surface it writes with the stylus only once a stylus is used; here, stylus only.
 */
object TestSurface : CoachSurface {
    @Composable override fun Pad(pad: CoachPad, size: Dp, modifier: Modifier) {
        Box(modifier.size(size)
            .onSizeChanged { pad.boxWidth = it.width.toFloat(); pad.boxHeight = it.height.toFloat() }
            .pointerInput(pad) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.type != PointerType.Stylus) return@awaitEachGesture
                    val points = mutableListOf(InkPoint(down.position.x, down.position.y, down.uptimeMillis))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        points += InkPoint(change.position.x, change.position.y, change.uptimeMillis)
                        change.consume()
                        if (!change.pressed) break
                    }
                    pad.strokes = pad.strokes + InkStroke(points)
                }
            })
    }
}

class FakeRecognizer(private val read: (List<InkStroke>) -> List<RawCandidate>) : DigitRecognizer {
    override val modelTag = "fake"
    override val state = MutableStateFlow<ModelState>(ModelState.Ready)
    var calls = 0
    override suspend fun prepare(allowDownload: Boolean) {}
    override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float): List<RawCandidate> { calls++; return read(strokes) }
}

/** Studio, warm-up and the in-game moment, driven with synthetic S Pen MotionEvents. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w800dp-h1280dp-xhdpi")
class CoachUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val tmp = TemporaryFolder()
    private val store by lazy { LegibilityStore(File(tmp.root, "legibility.v1.tsv")) }
    private var recognized = 0
    private val recognizer by lazy { FakeRecognizer { recognized++; listOf(RawCandidate("$expected", 0f), RawCandidate("x", 3f)) } }
    private var expected = 0
    private val env by lazy { CoachEnvironment(store, recognizer, TestSurface, Dispatchers.Unconfined) }

    private fun event(down: Long, t: Long, action: Int, x: Float, y: Float, tool: Int): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f; size = .1f }
        val source = if (tool == MotionEvent.TOOL_TYPE_STYLUS) 0x4002 else 0x1002
        return MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0)
    }

    /** Writes [strokes] (unit box coordinates) into the coach pad with the pen, or a finger. */
    private fun write(strokes: List<List<UnitPoint>>, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val b = rule.onNodeWithTag("coach-pad").fetchSemanticsNode().boundsInWindow
        val root = rule.activity.window.decorView
        strokes.forEach { s ->
            rule.runOnUiThread {
                val down = SystemClock.uptimeMillis()
                fun at(p: UnitPoint) = (b.left + p.x * b.width) to (b.top + p.y * b.height)
                val (x0, y0) = at(s.first())
                root.dispatchTouchEvent(event(down, down, MotionEvent.ACTION_DOWN, x0, y0, tool))
                s.drop(1).forEachIndexed { i, p -> val (x, y) = at(p); root.dispatchTouchEvent(event(down, down + 8L * (i + 1), MotionEvent.ACTION_MOVE, x, y, tool)) }
                val (x1, y1) = at(s.last())
                root.dispatchTouchEvent(event(down, down + 8L * s.size, MotionEvent.ACTION_UP, x1, y1, tool))
            }
            rule.waitForIdle()
        }
    }

    private fun unit(f: Fixture) = Legibility.toUnit(f.strokes, f.boxWidth, f.boxHeight)
    private fun studio(onBack: () -> Unit = {}) = rule.setContent {
        SkillitTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DigitStudio(env, onBack) } }
    }
    private fun click(text: String) = rule.onNodeWithText(text).performScrollTo().performClick()

    @Test fun traceThenWriteFromMemory() {
        expected = 0
        studio()
        rule.onNodeWithContentDescription("Harjoittele numeroa 0").performClick()
        rule.onNodeWithText("Numero 0: jäljennä mallia").assertExists()
        rule.onNodeWithText("Valmis").assertIsNotEnabled()
        write(DigitGuides.of(0).strokes, MotionEvent.TOOL_TYPE_FINGER)
        rule.onNodeWithText("Valmis").assertIsNotEnabled()
        write(DigitGuides.of(0).strokes)
        rule.onNodeWithText("Valmis").assertIsEnabled()
        click("Valmis")
        rule.onNodeWithContentDescription("3 tähteä kolmesta").assertExists()
        assertTrue("tracing over the model is not recorded", store.days().isEmpty())

        click("Kirjoita muistista")
        rule.onNodeWithText("Numero 0: kirjoita muistista").assertExists()
        rule.onNodeWithText("Valmis").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Kirjoitusruutu numerolle 0").assertExists()
        click("Näytä malli")
        rule.onNodeWithContentDescription("Kirjoitusruutu, mallina numero 0. Aloita vihreästä pisteestä.").assertExists()
        write(unit(Fixtures["neat-0"]))
        click("Valmis")
        rule.onNodeWithTag("coach-feedback").assertExists()
        val days = store.days()
        assertEquals(1, days.single { it.digit == 0 }.count)
        assertTrue("recogniser confidence was used", days.single().metrics.containsKey(Metric.Confidence))
        click("Uudestaan")
        rule.onNodeWithText("Valmis").assertIsNotEnabled()
    }

    @Test fun gentleTipForAnOpenLoopAndNoWrongMark() {
        expected = 9
        studio()
        rule.onNodeWithContentDescription("Harjoittele numeroa 9").performClick()
        write(DigitGuides.of(9).strokes)
        click("Valmis")
        click("Kirjoita muistista")
        write(unit(Fixtures["open-9"]))
        click("Valmis")
        rule.onNode(hasText("Vinkki:", substring = true) and hasText("ysin", substring = true)).assertExists()
        rule.onAllNodes(hasText("väärin", substring = true, ignoreCase = true)).assertCountEquals(0)
        rule.onNode(hasContentDescription("tähteä kolmesta", substring = true)).assertExists()
    }

    @Test fun warmUpPracticesTheWeakestDigitsFirst() {
        val today = store.today()
        store.record((0..9).map { d -> LegibilityReport(d, mapOf(Metric.Inside to if (d == 4) .2f else 1f), .65f, .85f, emptyList()) }
            .let { it + it + it })
        val plan = WarmUp.pick(store.days(), today)
        assertEquals(listOf(4, 9), plan.take(2))
        studio()
        click("Päivän lämmittely · 5 numeroa")
        plan.forEachIndexed { i, d ->
            expected = d
            rule.onNodeWithText("Lämmittely ${i + 1}/5: kirjoita $d").assertExists()
            write(DigitGuides.of(d).strokes)
            click("Valmis")
            click(if (i < 4) "Seuraava numero" else "Valmis!")
        }
        rule.onNodeWithText("Lämmittely tehty!").assertExists()
        plan.forEach { d -> assertEquals("digit $d", 4, store.days().filter { it.digit == d }.sumOf { it.count }) }
    }

    @Test fun clarityMomentCanBeSkipped() {
        var closed = 0
        rule.setContent { SkillitTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ClarityMoment(ClarityPrompt(4, 0, .4f), env, onClose = { closed++ }) } } }
        rule.onNodeWithText("Kirjoitetaanko numero 4 vielä kerran selvemmin?").assertExists()
        rule.onNodeWithText("Se vie 10 sekuntia. Vastauksesi ja pisteesi pysyvät ennallaan.").assertExists()
        click("Ei nyt")
        assertEquals(1, closed)
        assertTrue(store.days().isEmpty())
    }

    @Test fun clarityMomentMeasuresTheRewrite() {
        expected = 4
        var closed = 0
        rule.setContent { SkillitTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ClarityMoment(ClarityPrompt(4, 0, .4f), env, onClose = { closed++ }) } } }
        click("Kokeillaan!")
        rule.onNode(hasContentDescription("Aikaa jäljellä", substring = true)).assertExists()
        write(unit(Fixtures["neat-4"]))
        click("Valmis")
        rule.onNodeWithTag("coach-feedback").assertExists()
        click("Jatka")
        assertEquals(1, closed)
        assertEquals(1, store.days().single { it.digit == 4 }.count)
    }

    @Test fun clarityMomentEndsByItselfAfterTenSeconds() {
        var closed = 0
        rule.setContent { SkillitTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ClarityMoment(ClarityPrompt(7, 0, .4f), env, onClose = { closed++ }) } } }
        click("Kokeillaan!")
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals(0, closed)
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitForIdle()
        assertEquals("an empty box just closes", 1, closed)
        assertTrue(store.days().isEmpty())
    }

    @Test fun parentTrendShowsAggregatesWithTalkBackLabels() {
        val today = store.today()
        val days = listOf(DigitDay(today - 10, 4, 2, 1f, 1.3f, .845f), DigitDay(today, 4, 2, 1.6f, 1.3f, .845f, mapOf(Metric.Distinct to (2 to 1f))))
        rule.setContent { SkillitTheme { LegibilityTrendSection(LegibilityStats.trend(days, today)) } }
        rule.onNodeWithContentDescription("Numero 4: selkeys 80 %, nousua 30 prosenttiyksikköä, 4 kirjoitusta, harjoiteltavaa: erottuu parista").assertExists()
        rule.onNodeWithText("Tässä näkyvät vain yhteenvedot (0–100 %). Kynän jäljet pysyvät tabletilla. Käsiala ei vaikuta XP:hen.").assertExists()
    }

    @Test fun parentTrendWithoutData() {
        rule.setContent { SkillitTheme { LegibilityTrendSection(LegibilityStats.trend(emptyList(), 0)) } }
        rule.onNodeWithText("Ei vielä kirjoitettuja numeroita.").assertExists()
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        studio()
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().indices.forEach { i ->
            rule.onAllNodes(hasClickAction())[i].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        rule.onNodeWithContentDescription("Harjoittele numeroa 7").performClick()
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().indices.forEach { i ->
            rule.onAllNodes(hasClickAction())[i].assertHeightIsAtLeast(48.dp)
        }
    }
}
