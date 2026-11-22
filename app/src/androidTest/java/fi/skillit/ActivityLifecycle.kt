package fi.skillit

import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/*
 * Real backgrounding for instrumented tests. Not `ActivityScenario.moveToState`: that opens a second activity
 * on top, and in One UI's desktop (freeform) windowing it opens beside ours, both stay resumed and the
 * activity is never stopped (seen on the Tab S9). These helpers let the system itself pause and stop the
 * activity, as when the child presses Home, so they work in fullscreen and freeform alike.
 */

/** Polls the activity's real lifecycle (set by the system, not faked) until it is [want]. */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.awaitLifecycle(want: Lifecycle.State, timeoutMs: Long = 10_000) {
    val until = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        var state = Lifecycle.State.INITIALIZED
        runOnUiThread { state = activity.lifecycle.currentState }
        if (state == want) return
        if (SystemClock.uptimeMillis() > until) fail("activity never became $want (state $state)")
        Thread.sleep(50)
    }
}

/** Sends the task to the back and waits for the system's onStop (state CREATED; DESTROYED fails). */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.background() {
    runOnUiThread { assertTrue("task moved back", activity.moveTaskToBack(true)) }
    awaitLifecycle(Lifecycle.State.CREATED)
}

/** Brings the same activity instance back to the front, as tapping its window or recents entry would. */
fun <A : ComponentActivity> AndroidComposeTestRule<*, A>.foreground() {
    val a = activity
    a.startActivity(Intent(a, a.javaClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    awaitLifecycle(Lifecycle.State.RESUMED)
    waitForIdle()
}
