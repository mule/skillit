package fi.skillit.ink

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Handle to the ink surface: clear it, read strokes, see the box geometry. Spike API; #27 builds DigitInkRow on it. */
@Stable
class InkBoxesController {
    internal var view: DigitInkView? = null
    val strokes: List<InkStroke> get() = view?.strokes.orEmpty()
    val row: BoxRow? get() = view?.row
    /** Ink revision; compare before and after an async step to drop stale results. */
    val revision: Int get() = view?.revision ?: -1
    fun clear() { view?.clear() }
    /** Removes exactly [done] (e.g. strokes already saved) and keeps anything written since, even a stroke in progress. */
    fun remove(done: List<InkStroke>) { view?.removeFinished(done) }
    /** Box index → that box's strokes, moved so the box's top-left corner is the origin. */
    fun strokesByBox(): Map<Int, List<InkStroke>> {
        val r = row ?: return emptyMap()
        return BoxSegmentation.segment(strokes, r).mapValues { (i, s) -> s.relativeTo(r.boxLeft(i), r.top) }
    }
}

@Composable
fun InkBoxes(boxCount: Int, controller: InkBoxesController, modifier: Modifier = Modifier, boxSize: Dp = 120.dp, onInk: (List<InkStroke>) -> Unit = {}) {
    AndroidView(
        factory = { DigitInkView(it, boxCount).also { v -> controller.view = v } },
        update = { it.boxCount = boxCount; it.onInkChanged = onInk; controller.view = it },
        onRelease = { if (controller.view === it) controller.view = null },
        modifier = modifier.testTag("ink-boxes").width(boxSize * boxCount).height(boxSize),
    )
}

/** The fallback: a 0–9 pad. Parent-PIN unlocking lives in #27. */
@Composable
fun NumberPad(onDigit: (Int) -> Unit, onErase: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { d -> Button(onClick = { onDigit(d) }, Modifier.size(64.dp)) { Text("$d") } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.size(64.dp))
            Button(onClick = { onDigit(0) }, Modifier.size(64.dp)) { Text("0") }
            OutlinedButton(onClick = onErase, Modifier.size(64.dp)) { Text("⌫") }
        }
    }
}
