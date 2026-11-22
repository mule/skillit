package fi.skillit.ink

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

/** Answer cells are full size; carry cells are the small boxes above a column-multiplication row. */
enum class CellKind(val scale: Float) { Answer(1f), Carry(0.6f) }

/**
 * State of one row of one-digit cells, e.g. a product row or a carry row on #27's column board, or a
 * single answer box on the steps board (`cells = 1`).
 *
 * It works the same in both input modes: with the pen each cell is read by the recogniser; with the
 * number pad the child types into the selected cell. Either way [digits] holds what the child means,
 * and [answer] is the row as a number string once every cell is filled.
 */
@Stable
class DigitRowState(val cells: Int, val kind: CellKind = CellKind.Answer) {
    init { require(cells > 0) }
    internal val ink = InkBoxesController()
    /**
     * The pen ink, owned by the row rather than the view, so switching to the number pad and back (which
     * disposes and re-creates the ink view) keeps the child's writing and the readings that belong to it.
     */
    internal var penStrokes: List<InkStroke> = emptyList()
        private set

    /** Pen or number pad. The parent area (#27, behind the PIN) owns this; see [InputModeSettings]. */
    var mode by mutableStateOf(InputMode.Pen)

    /** Latest reading per cell (pen mode). Cleared whenever the ink changes. */
    var readings by mutableStateOf(mapOf<Int, DigitReading>())
        private set
    /** Digits the child picked after a “Tarkoititko 4 vai 9?” question. */
    private var chosen by mutableStateOf(mapOf<Int, Int>())
    /** Digits typed with the number pad. */
    var typed by mutableStateOf(List<Int?>(cells) { null })
        private set
    /** Number-pad cursor. */
    var selected by mutableStateOf(0)

    private var request = 0

    /** What the child means in each cell; null = empty, unreadable or still asking. */
    val digits: List<Int?>
        get() = if (mode == InputMode.NumberPad) typed else List(cells) { i ->
            chosen[i] ?: (readings[i] as? DigitReading.Accepted)?.digit
        }

    /** Cells waiting for the child to answer “Tarkoititko X vai Y?”. */
    val questions: Map<Int, DigitReading.Ask>
        get() = if (mode == InputMode.NumberPad) emptyMap()
            else readings.filterValues { it is DigitReading.Ask }.mapValues { it.value as DigitReading.Ask }.filterKeys { it !in chosen }

    /** Cells with ink that could not be read; the UI asks the child to rewrite them. */
    val unreadable: Set<Int>
        get() = if (mode == InputMode.NumberPad) emptySet() else readings.filterValues { it is DigitReading.Unreadable }.keys

    /**
     * The row as written, left to right, once every cell has a digit. Leading cells without ink are allowed;
     * a cell that is still asking “Tarkoititko…?” or could not be read is not empty, so there is no answer yet.
     */
    val answer: String?
        get() {
            if (questions.isNotEmpty() || unreadable.isNotEmpty()) return null
            val d = digits
            val first = d.indexOfFirst { it != null }
            if (first < 0 || d.drop(first).any { it == null }) return null
            return d.drop(first).joinToString("") { it.toString() }
        }

    /** Strokes per cell, relative to the cell's corner. Pen mode only; this is what #28 stores per attempt. */
    fun strokesByCell(): Map<Int, List<InkStroke>> = ink.strokesByBox()

    fun choose(cell: Int, digit: Int) { chosen = chosen + (cell to digit) }

    fun type(digit: Int) {
        typed = typed.toMutableList().also { it[selected] = digit }
        selected = (selected + 1).coerceAtMost(cells - 1)
    }

    fun eraseTyped() {
        if (typed[selected] == null && selected > 0) selected--
        typed = typed.toMutableList().also { it[selected] = null }
    }

    fun clear() {
        request++
        penStrokes = emptyList()
        ink.clear()
        readings = emptyMap(); chosen = emptyMap()
        typed = List(cells) { null }; selected = 0
    }

    /** From the ink view. A re-created view reporting the same strokes (after a mode switch) keeps the readings. */
    internal fun inkChanged(strokes: List<InkStroke>) {
        if (strokes == penStrokes) return
        penStrokes = strokes
        request++; readings = emptyMap(); chosen = emptyMap()
    }

    /**
     * Reads every inked cell. Results for ink that changed meanwhile, or from a superseded request, are
     * dropped (returns false). Recogniser failures propagate; callers show a retry.
     */
    suspend fun recognize(recognizer: DigitRecognizer): Boolean {
        val row = ink.row ?: return false
        val id = ++request
        val revision = ink.revision
        val boxes = ink.strokesByBox()
        val result = boxes.mapValues { (_, s) -> DigitRules.read(recognizer.recognize(s, row.boxWidth, row.boxHeight)) }
        if (id != request || revision != ink.revision) return false
        readings = result; chosen = emptyMap()
        return true
    }
}

@Composable
fun rememberDigitRowState(cells: Int, kind: CellKind = CellKind.Answer, mode: InputMode = InputMode.Pen): DigitRowState =
    remember(cells, kind) { DigitRowState(cells, kind) }.also { it.mode = mode }

/**
 * One row of digit cells. Pen mode: an ink surface split into cells, with “Tarkoititko…?” buttons under
 * any cell that needs them. Number-pad mode: tappable cells plus [NumberPad]. Recognition is started by
 * the caller (e.g. on “Tarkista”) through [DigitRowState.recognize], so the game decides when to read.
 */
@Composable
fun DigitInkRow(state: DigitRowState, modifier: Modifier = Modifier, cellSize: Dp = 96.dp, showPad: Boolean = true, onInk: () -> Unit = {}) {
    val size = cellSize * state.kind.scale
    Column(modifier.testTag("digit-row"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.mode == InputMode.Pen) {
            InkBoxes(state.cells, state.ink, boxSize = size, initial = state.penStrokes, onInk = { state.inkChanged(it); onInk() })
            val questions = state.questions
            if (questions.isNotEmpty() || state.unreadable.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                questions.toSortedMap().forEach { (cell, ask) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (state.cells > 1) "Ruutu ${cell + 1}: ${ask.question}" else ask.question)
                        listOf(ask.first, ask.second).forEach { d ->
                            OutlinedButton(onClick = { state.choose(cell, d) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("$d") }
                        }
                    }
                }
                state.unreadable.sorted().forEach { cell ->
                    Text(if (state.cells > 1) "Ruutu ${cell + 1}: kirjoita numero uudelleen." else "Kirjoita numero uudelleen.")
                }
            }
        } else {
            Row {
                repeat(state.cells) { i ->
                    val selected = i == state.selected
                    Box(
                        Modifier.size(size).border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0xFF9CB8B0)), RoundedCornerShape(8.dp))
                            .clickable { state.selected = i }.semantics { contentDescription = "Ruutu ${i + 1}: ${state.typed[i] ?: "tyhjä"}" },
                        contentAlignment = Alignment.Center,
                    ) { Text(state.typed[i]?.toString() ?: "", fontSize = (size.value * 0.5f).sp) }
                }
            }
            if (showPad) NumberPad(onDigit = state::type, onErase = state::eraseTyped)
        }
    }
}

/** A single one-digit cell (steps board). */
@Composable
fun DigitInkCell(state: DigitRowState, modifier: Modifier = Modifier, cellSize: Dp = 96.dp) {
    require(state.cells == 1) { "DigitInkCell needs a one-cell DigitRowState" }
    DigitInkRow(state, modifier, cellSize)
}

/** Recognises [state], mapping failures to a Finnish retry message instead of crashing. Null on success. */
suspend fun recognizeOrMessage(state: DigitRowState, recognizer: DigitRecognizer): String? = try {
    state.recognize(recognizer); null
} catch (e: CancellationException) { throw e }
catch (_: Exception) { "Käsialan tunnistus ei onnistunut. Yritä uudelleen." }
