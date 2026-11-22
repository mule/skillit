package fi.skillit

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import fi.skillit.ink.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class InkCellsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun event(down: Long, action: Int, x: Float, y: Float, tool: Int = MotionEvent.TOOL_TYPE_STYLUS): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f; size = .1f }
        return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, 0x1002, 0)
    }

    private fun inkViews(v: View = rule.activity.window.decorView): List<DigitInkView> = when (v) {
        is DigitInkView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { inkViews(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun stroke(v: DigitInkView, x: Float) {
        rule.runOnUiThread {
            val t = SystemClock.uptimeMillis()
            v.dispatchTouchEvent(event(t, MotionEvent.ACTION_DOWN, x, 20f))
            v.dispatchTouchEvent(event(t, MotionEvent.ACTION_MOVE, x, 80f))
            v.dispatchTouchEvent(event(t, MotionEvent.ACTION_UP, x, 140f))
        }
        rule.waitForIdle()
    }

    /**
     * Background the activity mid-stroke and come back. The stroke in progress is dropped and writing works
     * again. On a real tablet the front-buffered layer is also released while hidden and re-created on resume.
     * Emulators never create that layer: on the API 35 CI emulator graphics-core's front buffer crashes hwui's
     * RenderThread as soon as it is created, even with the window visible, so the layer checks run on devices only.
     */
    @Test fun backgroundingMidStrokeReleasesAndRestoresTheLiveLayer() {
        val controller = InkBoxesController()
        rule.setContent { SkillitTheme { InkBoxes(2, controller, boxSize = 150.dp) } }
        rule.waitForIdle()
        val v = inkViews().single()
        assertEquals(v.lowLatency, v.hasLiveLayer)
        stroke(v, 60f)
        val down = SystemClock.uptimeMillis()
        rule.runOnUiThread {
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, 220f, 20f))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, 220f, 70f))
        }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.runOnUiThread { assertFalse("front buffer released while hidden", v.hasLiveLayer) }  // always true when off
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.runOnUiThread {
            assertEquals("front buffer back on resume (devices)", v.lowLatency, v.hasLiveLayer)
            // The rest of the interrupted stroke must not commit anything.
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, 220f, 120f))
            v.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, 220f, 140f))
        }
        rule.waitForIdle()
        assertEquals("finished ink kept, interrupted stroke dropped", 1, v.strokes.size)
        stroke(v, 230f)
        assertEquals(2, v.strokes.size)
        // A few more pause/resume cycles with ink on screen: no crash.
        repeat(3) {
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        }
        rule.waitForIdle()
        assertEquals(2, v.strokes.size)
    }

    private class FakeRecognizer(val answers: Map<Float, List<RawCandidate>>) : DigitRecognizer {
        override val modelTag = "fake"
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
        override suspend fun prepare(allowDownload: Boolean) {}
        /** Answers by the cell-relative x of the first point, so each cell can get its own reading. */
        override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float) =
            answers.entries.minBy { kotlin.math.abs(it.key - strokes.first().points.first().x) }.value
    }

    @Test fun penRowAsksFourOrNineThenGivesTheAnswerAndStoresTheAttempt() {
        val state = DigitRowState(2)
        rule.setContent { SkillitTheme { Column { DigitInkRow(state, cellSize = 150.dp) } } }
        rule.waitForIdle()
        val v = inkViews().single()
        val cell = v.row.boxWidth
        stroke(v, 30f)               // cell 0, x 30 → "4 vai 9"
        stroke(v, cell + 90f)        // cell 1, x 90 → clear 2
        val fake = FakeRecognizer(mapOf(30f to listOf(RawCandidate("4", 1f), RawCandidate("9", 1.2f)), 90f to listOf(RawCandidate("2", 0f))))
        runBlocking { assertTrue(state.recognize(fake)) }
        rule.waitForIdle()
        assertNull("still asking", state.answer)
        rule.onNodeWithText("Ruutu 1: Tarkoititko 4 vai 9?").assertExists()
        rule.onNodeWithText("4").performClick()
        rule.waitForIdle()
        assertEquals("42", state.answer)

        val dir = File(rule.activity.filesDir, "attempt-ink-test").apply { deleteRecursively() }
        val saved = AttemptInkStore(dir).save("test/q1/1", state)!!
        assertEquals(setOf(0, 1), saved.cells.keys)
        assertEquals(4, AttemptInkStore(dir).load("test/q1/1")!!.cells[0]!!.digit)
        assertTrue(saved.cells.values.all { c -> c.strokes.all { s -> s.points.all { it.x <= cell } } })
        dir.deleteRecursively()
    }

    @Test fun staleRecognitionIsDroppedWhenInkChanges() {
        val state = DigitRowState(1)
        rule.setContent { SkillitTheme { DigitInkCell(state, cellSize = 150.dp) } }
        rule.waitForIdle()
        val v = inkViews().single()
        stroke(v, 50f)
        val slow = object : DigitRecognizer {
            override val modelTag = "slow"
            override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
            override suspend fun prepare(allowDownload: Boolean) {}
            override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float): List<RawCandidate> {
                rule.runOnUiThread { state.clear() }   // the child clears the cell while recognition runs
                return listOf(RawCandidate("7", 0f))
            }
        }
        runBlocking { assertFalse(state.recognize(slow)) }
        assertNull(state.answer)
        assertTrue(state.readings.isEmpty())
    }

    @Test fun numberPadModeTypesWithoutInk() {
        val state = DigitRowState(2).apply { mode = InputMode.NumberPad }
        rule.setContent { SkillitTheme { DigitInkRow(state) } }
        rule.onNodeWithText("3").performClick()
        rule.onNodeWithText("5").performClick()
        rule.waitForIdle()
        assertEquals("35", state.answer)
        assertTrue(inkViews().isEmpty())
        assertNull(AttemptInkStore(File(rule.activity.cacheDir, "x")).save("k", state))
    }
}
