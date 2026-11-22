package fi.skillit

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import fi.skillit.ink.*
import fi.skillit.inklab.InkLab
import fi.skillit.inklab.InkSampleStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Drives DigitInkView with synthetic stylus, finger and palm MotionEvents. */
class InkInputTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val controller = InkBoxesController()

    private fun showBoxes(count: Int = 3) {
        rule.setContent { SkillitTheme { InkBoxes(count, controller, boxSize = 150.dp) } }
        rule.waitForIdle()
    }

    private val view get() = controller.view!!

    private fun event(down: Long, action: Int, x: Float, y: Float, tool: Int, buttons: Int = 0, pressure: Float = .6f, flags: Int = 0): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; this.pressure = pressure; size = .1f }
        return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, arrayOf(props), arrayOf(coords), 0, buttons, 1f, 1f, 0, 0, 0x1002, flags)
    }

    /** One stroke from (x0,y0) to (x1,y1) in 8 steps. Returns nothing; check the view's strokes afterwards. */
    private fun draw(x0: Float, y0: Float, x1: Float, y1: Float, tool: Int = MotionEvent.TOOL_TYPE_STYLUS, buttons: Int = 0, cancel: Boolean = false) {
        rule.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            view.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, x0, y0, tool, buttons, .2f))
            for (i in 1..8) view.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / 8, y0 + (y1 - y0) * i / 8, tool, buttons, .2f + i * .08f))
            view.dispatchTouchEvent(event(down, if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, x1, y1, tool, buttons))
        }
        rule.waitForIdle()
    }

    private fun box(i: Int) = view.row.let { it.boxLeft(i) + it.boxWidth / 2 }

    @Test fun stylusStrokeLandsInItsBoxWithPressure() {
        showBoxes()
        draw(box(1), 20f, box(1), view.height - 20f)
        assertEquals(1, view.strokes.size)
        assertEquals(listOf(1), controller.strokesByBox().keys.toList())
        val pressures = view.strokes.single().points.map { it.pressure }
        assertTrue("pressure varies along the stroke: $pressures", pressures.max() - pressures.min() > .3f)
        assertTrue("box-relative x", controller.strokesByBox()[1]!!.single().points.all { it.x < view.row.boxWidth })
    }

    @Test fun fingerWritesUntilAPenIsSeenThenIsIgnored() {
        showBoxes()
        draw(box(0), 20f, box(0), 100f, tool = MotionEvent.TOOL_TYPE_FINGER)
        assertEquals("finger fallback with no pen", 1, view.strokes.size)
        draw(box(1), 20f, box(1), 100f)
        draw(box(2), 20f, box(2), 100f, tool = MotionEvent.TOOL_TYPE_FINGER)
        assertEquals("finger ignored once the pen is used", 2, view.strokes.size)
    }

    @Test fun hoveringPenTurnsOffFingerInput() {
        showBoxes()
        rule.runOnUiThread {
            val t = SystemClock.uptimeMillis()
            view.dispatchGenericMotionEvent(event(t, MotionEvent.ACTION_HOVER_ENTER, box(0), 50f, MotionEvent.TOOL_TYPE_STYLUS).apply { source = 0x1002 or 0x4000 })
        }
        // Not every system routes synthetic hover events to onHoverEvent; feed the policy directly as a fallback.
        rule.runOnUiThread { view.policy.onHover(MotionEvent.TOOL_TYPE_STYLUS) }
        draw(box(0), 20f, box(0), 100f, tool = MotionEvent.TOOL_TYPE_FINGER)
        assertTrue(view.strokes.isEmpty())
    }

    @Test fun penHoverCancelsAFingerStrokeInProgress() {
        showBoxes()
        rule.runOnUiThread {
            val t = SystemClock.uptimeMillis()
            view.dispatchTouchEvent(event(t, MotionEvent.ACTION_DOWN, box(0), 20f, MotionEvent.TOOL_TYPE_FINGER))
            view.dispatchTouchEvent(event(t, MotionEvent.ACTION_MOVE, box(0), 60f, MotionEvent.TOOL_TYPE_FINGER))
            // Called directly: synthetic hover events are not always routed to onHoverEvent.
            view.onHoverEvent(event(t, MotionEvent.ACTION_HOVER_ENTER, box(1), 50f, MotionEvent.TOOL_TYPE_STYLUS))
            view.dispatchTouchEvent(event(t, MotionEvent.ACTION_MOVE, box(0), 100f, MotionEvent.TOOL_TYPE_FINGER))
            view.dispatchTouchEvent(event(t, MotionEvent.ACTION_UP, box(0), 120f, MotionEvent.TOOL_TYPE_FINGER))
        }
        rule.waitForIdle()
        assertTrue("palm stroke dropped when the pen came near", view.strokes.isEmpty())
        draw(box(1), 20f, box(1), 100f)
        assertEquals("the pen still writes", 1, view.strokes.size)
    }

    @Test fun palmCancelledBySystemLeavesNoInk() {
        showBoxes()
        draw(box(0), 20f, box(0), 100f, cancel = true)
        assertTrue(view.strokes.isEmpty())
    }

    @Test fun sideButtonErasesAStroke() {
        showBoxes()
        draw(box(0), 20f, box(0), 120f)
        draw(box(2), 20f, box(2), 120f)
        draw(box(0) - 30f, 70f, box(0) + 30f, 70f, buttons = MotionEvent.BUTTON_STYLUS_PRIMARY)
        assertEquals("eraser path adds no ink and removes the crossed stroke", 1, view.strokes.size)
        assertEquals(listOf(2), controller.strokesByBox().keys.toList())
    }

    @Test fun labAsksFourOrNineAndRecordsSamplesLocally() {
        val dir = File(rule.activity.cacheDir, "ink-lab-test").apply { deleteRecursively() }
        val fake = object : DigitRecognizer {
            override val modelTag = "fake"
            override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
            override suspend fun prepare(allowDownload: Boolean) {}
            override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float) =
                listOf(RawCandidate("4", 1f), RawCandidate("9", 1.2f))
        }
        val tryController = InkBoxesController()
        rule.setContent { SkillitTheme { InkLab(InkSampleStore(dir), recognizerFactory = { fake }) } }
        rule.onNodeWithText("Malli valmis. Tunnistus toimii ilman verkkoa.").assertExists()
        // Draw in the first ink surface on screen (the 4-box try row).
        rule.runOnUiThread {
            val v = findInkViews(rule.activity.window.decorView).first()
            tryController.view = v
        }
        val v = tryController.view!!
        rule.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, 40f, 20f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, 40f, 90f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, 40f, 120f, MotionEvent.TOOL_TYPE_STYLUS))
        }
        rule.onNodeWithText("Tunnista").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTextExists("Ruutu 1: Tarkoititko 4 vai 9?", substring = true) }

        val recorder = findInkViews(rule.activity.window.decorView).last()
        rule.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            recorder.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, 100f, 40f, MotionEvent.TOOL_TYPE_STYLUS))
            recorder.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, 100f, 200f, MotionEvent.TOOL_TYPE_STYLUS))
            recorder.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, 100f, 300f, MotionEvent.TOOL_TYPE_STYLUS))
        }
        rule.onNodeWithText("Tallenna").performScrollTo().performClick()
        rule.waitUntil(5_000) { File(dir, "samples-v1.txt").let { it.exists() && it.readLines().size == 1 } }
        val saved = InkSampleCodec.decode(File(dir, "samples-v1.txt").readLines().single())!!
        assertEquals(0, saved.label)
        assertEquals("fake", saved.model)
        assertTrue(saved.strokes.single().points.size >= 3)
        rule.onNodeWithTag("report").assertExists()
    }

    /** A recogniser that waits for [gate] and can fail, to test the lab's async edges. */
    private class GatedRecognizer(var fail: Boolean = false) : DigitRecognizer {
        override val modelTag = "gated"
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
        @Volatile var gate = CompletableDeferred<Unit>()
        @Volatile var calls = 0
        override suspend fun prepare(allowDownload: Boolean) {}
        override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float): List<RawCandidate> {
            calls++
            gate.await()
            if (fail) throw IllegalStateException("fake inference failure")
            return listOf(RawCandidate("4", 0f))
        }
    }

    private fun openLab(store: InkSampleStore, fake: DigitRecognizer) {
        rule.setContent { SkillitTheme { InkLab(store, recognizerFactory = { fake }) } }
        rule.waitForIdle()
    }

    private fun writeIn(v: DigitInkView, x: Float = 100f) {
        rule.runOnUiThread {
            val down = SystemClock.uptimeMillis()
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, x, 40f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, x, 150f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, x, 250f, MotionEvent.TOOL_TYPE_STYLUS))
        }
        rule.waitForIdle()
    }

    private fun recorderView() = findInkViews(rule.activity.window.decorView).last()
    private fun tryView() = findInkViews(rule.activity.window.decorView).first()
    private fun labDir(name: String) = File(rule.activity.cacheDir, name).apply { deleteRecursively() }
    private fun lines(dir: File) = File(dir, "samples-v1.txt").let { if (it.exists()) it.readLines() else emptyList() }

    @Test fun slowSaveKeepsItsLabelAndCannotBeDoubled() {
        val dir = labDir("ink-lab-slow")
        val fake = GatedRecognizer()
        openLab(InkSampleStore(dir), fake)
        writeIn(recorderView())
        rule.onNodeWithTag("save").performScrollTo().performClick()
        rule.waitForIdle()
        // While recognition is pending: switching the digit and saving again must do nothing.
        rule.onNodeWithTag("digit-1").performScrollTo().assertIsNotEnabled().performClick()
        rule.onNodeWithTag("save").assertIsNotEnabled().performClick()
        rule.waitForIdle()
        // Stroke B starts during the wait, and the save completes between B's DOWN and UP: B must survive.
        val v = recorderView()
        val bDown = SystemClock.uptimeMillis()
        rule.runOnUiThread {
            v.dispatchTouchEvent(event(bDown, MotionEvent.ACTION_DOWN, 200f, 40f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(bDown, MotionEvent.ACTION_MOVE, 200f, 120f, MotionEvent.TOOL_TYPE_STYLUS))
        }
        fake.gate.complete(Unit)
        rule.waitUntil(5_000) { lines(dir).isNotEmpty() }
        rule.waitForIdle()
        assertTrue("sample A removed while B is still being written", v.strokes.isEmpty())
        rule.runOnUiThread {
            v.dispatchTouchEvent(event(bDown, MotionEvent.ACTION_MOVE, 200f, 200f, MotionEvent.TOOL_TYPE_STYLUS))
            v.dispatchTouchEvent(event(bDown, MotionEvent.ACTION_UP, 200f, 250f, MotionEvent.TOOL_TYPE_STYLUS))
        }
        rule.waitForIdle()
        assertEquals("B finished whole", 4, v.strokes.single().points.size)
        assertEquals(1, fake.calls)
        assertEquals(1, lines(dir).size)
        assertEquals(0, InkSampleCodec.decode(lines(dir).single())!!.label)
        assertEquals("saved stroke removed, newer ink kept", listOf(200f), recorderView().strokes.map { it.points.first().x })
        rule.onNodeWithTag("digit-0").assertTextContains("0 · 1/10")
    }

    @Test fun staleTryResultIsDroppedAfterClear() {
        val fake = GatedRecognizer()
        openLab(InkSampleStore(labDir("ink-lab-stale")), fake)
        writeIn(tryView(), x = 60f)
        rule.onNodeWithText("Tunnista").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tyhjennä").performClick()
        fake.gate.complete(Unit)
        rule.waitForIdle()
        Thread.sleep(300)
        rule.waitForIdle()
        assertFalse(rule.onAllNodesWithTextExists("Ruutu 1:", substring = true))
    }

    @Test fun failedInferenceShowsRetryAndSavesNothing() {
        val dir = labDir("ink-lab-fail")
        val fake = GatedRecognizer(fail = true).apply { gate.complete(Unit) }
        openLab(InkSampleStore(dir), fake)
        writeIn(recorderView())
        rule.onNodeWithTag("save").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("lab-error")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("lab-error").assertTextContains("Tallennus epäonnistui", substring = true)
        assertTrue(lines(dir).isEmpty())
        assertEquals("ink kept for retry", 1, recorderView().strokes.size)
        fake.fail = false
        rule.onNodeWithTag("save").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(5_000) { lines(dir).size == 1 }
    }

    @Test fun failedStoreWriteShowsRetryAndCountsNothing() {
        // A regular file where the sample directory should be: every write fails like a full disk.
        val blocked = labDir("ink-lab-blocked").apply { writeText("not a directory") }
        openLab(InkSampleStore(blocked), GatedRecognizer().apply { gate.complete(Unit) })
        writeIn(recorderView())
        rule.onNodeWithTag("save").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("lab-error")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("digit-0").assertTextContains("0 · 0/10")
        rule.onNodeWithTag("save").assertIsEnabled()
        assertEquals(1, recorderView().strokes.size)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextExists(text: String, substring: Boolean) =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun findInkViews(v: android.view.View): List<DigitInkView> = when (v) {
        is DigitInkView -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { findInkViews(v.getChildAt(it)) }
        else -> emptyList()
    }
}
