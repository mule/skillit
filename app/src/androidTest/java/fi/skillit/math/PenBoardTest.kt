package fi.skillit.math

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fi.skillit.SkillitTheme
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.pack.PackExercise
import fi.skillit.ink.*
import fi.skillit.math.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * The real ink view on the math boards (Canvas path on the emulator; the native ink library is missing
 * under Robolectric). Two same-shaped questions must never share ink, and editing ink must make the
 * answer uncheckable until it is read again.
 */
class PenBoardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Reads every box as [digit]. */
    private class Fixed(var digit: String) : DigitRecognizer {
        override val modelTag = "test"
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready)
        override suspend fun prepare(allowDownload: Boolean) = Unit
        override suspend fun recognize(strokes: List<InkStroke>, width: Float, height: Float) =
            if (strokes.isEmpty()) emptyList() else listOf(RawCandidate(digit, 0f))
    }

    private fun exercise(id: String, answer: Int) = PackExercise(id, "Laske.", "Vihje.", "Selitys.", AnswerSpec.Numeric(answer), "$answer * 1")

    private fun inkViews(v: View = rule.activity.window.decorView): List<DigitInkView> = when (v) {
        is DigitInkView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { inkViews(v.getChildAt(it)) }
        else -> emptyList()
    }

    private fun event(down: Long, action: Int, x: Float, y: Float): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f; size = .1f }
        return MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, 0x1002, 0)
    }

    /** A vertical stroke in the last box (ones place), dispatched on the UI thread; returns right after pen-up. */
    private fun write(v: DigitInkView, after: () -> Unit = {}) = rule.runOnUiThread {
        val x = v.width - v.width / v.boxCount / 2f
        val t = SystemClock.uptimeMillis()
        v.dispatchTouchEvent(event(t, MotionEvent.ACTION_DOWN, x, v.height * .2f))
        v.dispatchTouchEvent(event(t, MotionEvent.ACTION_MOVE, x, v.height * .5f))
        v.dispatchTouchEvent(event(t, MotionEvent.ACTION_UP, x, v.height * .8f))
        after()
    }

    private val a = exercise("a", 7)
    private val b = exercise("b", 3)
    private val drafts = mapOf("a" to AnswerDraft(BoardKind.of(a)), "b" to AnswerDraft(BoardKind.of(b)))
    private var current by mutableStateOf(a)
    private val recognizer = Fixed("7")
    private val pen = PenInput(recognizer)

    private fun show() {
        rule.setContent { SkillitTheme { Column {
            val ex = current
            MathBoard(ex, drafts.getValue(ex.id), pen, null, enabled = true)
        } } }
        rule.waitForIdle()
    }
    private fun waitForValue(id: String, value: Int) =
        rule.waitUntil(5_000) { drafts.getValue(id).values["answer"]?.value == value }

    @Test fun eachQuestionHasItsOwnInkAndARetryGetsItsOwnInkBack() {
        show()
        write(inkViews().single())
        waitForValue("a", 7)
        assertEquals(1, inkViews().single().strokes.size)

        rule.runOnUiThread { current = b }
        rule.waitForIdle()
        val next = inkViews().single()
        assertTrue("the next question starts with clean paper", next.strokes.isEmpty())
        recognizer.digit = "3"
        write(next)
        waitForValue("b", 3)
        assertEquals("question a keeps its own answer", 7, drafts.getValue("a").values["answer"]?.value)
        assertEquals(1, next.strokes.size)

        rule.runOnUiThread { current = a }  // the retry of a
        rule.waitForIdle()
        assertEquals("a's own stroke is back, not b's", 1, inkViews().single().strokes.size)
        assertNotSame(next, inkViews().single())
        assertEquals(7, drafts.getValue("a").values["answer"]?.value)
    }

    @Test fun editingRecognisedInkMakesTheAnswerUncheckableUntilItIsReadAgain() {
        show()
        write(inkViews().single())
        waitForValue("a", 7)
        val draft = drafts.getValue("a")
        assertTrue(draft.complete)
        recognizer.digit = "1"
        var completeRightAfterPenUp = true
        var valueRightAfterPenUp: Int? = -1
        write(inkViews().single()) { completeRightAfterPenUp = draft.complete; valueRightAfterPenUp = draft.values["answer"]?.value }
        assertFalse("not checkable while the new ink is read", completeRightAfterPenUp)
        assertNull(valueRightAfterPenUp)
        // Two strokes in one box now read as "1": the new reading replaces the old 7.
        waitForValue("a", 1)
        assertTrue(draft.complete)
        assertEquals(AnswerSpec.Numeric(7).check(draft.input())!!.correct, false)
    }
}
