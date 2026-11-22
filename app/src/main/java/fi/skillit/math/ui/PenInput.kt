package fi.skillit.math.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.skillit.engine.adaptive.InputMethod
import fi.skillit.coach.writtenDigits
import fi.skillit.ink.*
import kotlinx.coroutines.delay

/**
 * The S Pen, built on #26's [DigitRowState]: one row of one-digit ink boxes per answer cell. Each row is
 * read [RECOGNIZE_DELAY_MS] after the pen stops. A close call between two digits is asked through the
 * boards' shared [ConfirmDigit] ("Tarkoititko 4 vai 9?"), and the pick goes back into the row with
 * [DigitRowState.choose]. Rows are kept per item and cell, so a retry still shows the child's own ink.
 */
class PenInput(private val recognizer: DigitRecognizer) : DigitInput {
    override val method = InputMethod.PEN
    private val rows = mutableMapOf<Pair<String, String>, DigitRowState>()
    private var failure by mutableStateOf<String?>(null)

    private fun row(item: String, cell: InputCell) = rows.getOrPut(item to cell.id) {
        DigitRowState(cell.maxDigits, if (cell.small) CellKind.Carry else CellKind.Answer).also { it.mode = InputMode.Pen }
    }

    @Composable
    override fun Cell(
        item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
        onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier,
    ) {
        val state = remember(item, cell) { row(item, cell) }
        var inked by remember(state) { mutableIntStateOf(0) }
        LaunchedEffect(state, inked) {
            if (inked == 0) return@LaunchedEffect
            delay(RECOGNIZE_DELAY_MS)
            failure = recognizeOrMessage(state, recognizer)
            if (enabled) onValue(penValue(state.answer, state.digits, state.readings, state.questions))
        }
        val box = if (cell.small) CellStyle.small else CellStyle.digit
        val border = when (mark) {
            CellMark.FIRST_WRONG -> BorderStroke(3.dp, CellStyle.wrong)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(cell.label)
                append(if (value.value == null) ", tyhjä, kirjoita kynällä" else ", ${value.value}")
                if (mark == CellMark.FIRST_WRONG) append(", katso tätä vielä")
            }
        }) {
            Box(contentAlignment = Alignment.CenterStart) {
                // One native ink view per item and cell: InkBoxes restores `initial` only when its view is created,
                // so without the key the next question would reuse (show and recognise) this question's ink.
                key(item, cell.id) {
                    InkBoxes(state.cells, state.ink, Modifier.border(border, RoundedCornerShape(4.dp)), boxSize = box,
                        initial = state.penStrokes, onInk = {
                            state.inkChanged(it); inked++
                            // The old reading no longer matches the ink: not checkable until this ink is recognised.
                            if (enabled) onValue(CellValue(pending = true))
                        })
                }
                // A restored answer (after resuming) has no ink: show its digits in their boxes until the child writes.
                if (inked == 0 && state.penStrokes.isEmpty() && value.value != null) Row {
                    value.text.padStart(state.cells).forEach { ch ->
                        Box(Modifier.size(box), contentAlignment = Alignment.Center) {
                            Text(ch.toString().trim(), fontSize = (box.value * .45f).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.unreadable.isNotEmpty()) Text("Kirjoita numero uudelleen.", style = MaterialTheme.typography.bodySmall)
        }
    }

    override fun written(item: String): List<fi.skillit.coach.WrittenDigit> {
        var offset = 0
        // One running index over all of the item's cells, so each written digit has its own number.
        return rows.filterKeys { it.first == item }.entries.sortedBy { it.key.second }.flatMap { (_, state) ->
            state.writtenDigits().map { it.copy(cell = offset + it.cell) }.also { offset += state.cells }
        }
    }

    override fun width(cell: InputCell) = (if (cell.small) CellStyle.small else CellStyle.digit) * cell.maxDigits

    override fun confirm(item: String, cell: InputCell, value: CellValue, digit: Int): CellValue {
        val state = rows[item to cell.id] ?: return value.copy(value = digit, ask = null)
        state.choose(value.askCell, digit)
        return penValue(state.answer, state.digits, state.readings, state.questions)
    }

    @Composable
    override fun Controls(focused: InputCell?, value: CellValue?, enabled: Boolean, onValue: (CellValue) -> Unit, onNext: () -> Unit) {
        val model by recognizer.state.collectAsState()
        LaunchedEffect(recognizer) { recognizer.prepare(allowDownload = true) }
        val message = when (val m = model) {
            ModelState.Ready -> failure
            ModelState.Checking -> null
            ModelState.Downloading, ModelState.NotDownloaded -> WIFI_WAIT_MESSAGE
            is ModelState.Failed -> m.message
        }
        message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        Text("Kirjoita jokainen numero omaan ruutuunsa. Kynän sivunappi pyyhkii.", style = MaterialTheme.typography.bodyMedium)
    }

    companion object {
        const val RECOGNIZE_DELAY_MS = 700L

        /**
         * One pen row as a board value. A complete row gives its number and the lowest confidence of its digits
         * (a digit the child confirmed counts as its recogniser share); the first open question becomes [CellValue.ask].
         */
        fun penValue(answer: String?, digits: List<Int?>, readings: Map<Int, DigitReading>, questions: Map<Int, DigitReading.Ask>): CellValue {
            questions.toSortedMap().entries.firstOrNull()?.let { (index, ask) ->
                return CellValue(null, null, ask.first to ask.second, askCell = index)
            }
            val number = answer?.toIntOrNull() ?: return CellValue.Empty
            val confidence = readings.mapNotNull { (i, r) ->
                val digit = digits.getOrNull(i) ?: return@mapNotNull null
                r.ranked.firstOrNull { it.digit == digit }?.confidence?.toDouble() ?: 0.0
            }.minOrNull()?.coerceIn(0.0, 1.0)
            return CellValue(number, confidence)
        }
    }
}
