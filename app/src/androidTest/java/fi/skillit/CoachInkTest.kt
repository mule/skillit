package fi.skillit

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.coach.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The digit studio on the real S Pen surface (androidx.ink + DigitInkView), with synthetic stylus and finger events. */
class CoachInkTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "coach-ink-test").also { it.deleteRecursively() }
    private val store = LegibilityStore(File(dir, "legibility.v1.tsv"))
    private val env = CoachEnvironment(store, recognizer = null)

    private fun event(down: Long, t: Long, action: Int, x: Float, y: Float, tool: Int): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = .5f; size = .1f }
        return MotionEvent.obtain(down, t, action, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, if (tool == MotionEvent.TOOL_TYPE_STYLUS) 0x4002 else 0x1002, 0)
    }

    private fun write(strokes: List<List<UnitPoint>>, tool: Int = MotionEvent.TOOL_TYPE_STYLUS) {
        val b = rule.onNodeWithTag("coach-pad").fetchSemanticsNode().boundsInWindow
        val root = rule.activity.window.decorView
        strokes.forEach { s ->
            rule.runOnUiThread {
                val down = SystemClock.uptimeMillis()
                s.forEachIndexed { i, p ->
                    val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; s.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                    root.dispatchTouchEvent(event(down, down + 8L * i, action, b.left + p.x * b.width, b.top + p.y * b.height, tool))
                }
            }
            rule.waitForIdle()
        }
    }

    @Test fun penTracesAndWritesInTheStudio() {
        rule.setContent { SkillitTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DigitStudio(env) {} } } }
        rule.onNodeWithContentDescription("Harjoittele numeroa 1").performClick()
        write(DigitGuides.of(1).strokes)
        rule.onNodeWithText("Valmis").assertIsEnabled().performScrollTo().performClick()
        rule.onNodeWithTag("coach-feedback").assertExists()
        rule.onNodeWithText("Kirjoita muistista").performScrollTo().performClick()
        write(DigitGuides.of(1).strokes)
        rule.onNodeWithText("Valmis").performScrollTo().performClick()
        rule.onNodeWithTag("coach-feedback").assertExists()
        assertEquals(1, store.days().single { it.digit == 1 }.count)
        dir.deleteRecursively()
    }
}
