package fi.skillit.math.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.skillit.engine.adaptive.InputMethod

/**
 * One writable answer cell. [maxDigits] is 1 for a column-multiplication digit and more for a whole
 * number (an answer line). [label] is the TalkBack name, e.g. "Tulos, kymmenet".
 */
data class InputCell(val id: String, val label: String, val maxDigits: Int, val small: Boolean = false)

/**
 * What a cell holds. A pen reading may carry recogniser [confidence] (0..1), or be unsure between two
 * digits ([ask]): the board then asks "Tarkoititko 4 vai 9?" and never checks the answer until the
 * child picks one. The pad always gives a sure value with null confidence.
 */
data class CellValue(
    val value: Int? = null, val confidence: Double? = null, val ask: Pair<Int, Int>? = null, val askCell: Int = 0,
    /** Pen ink changed and is not recognised yet; the answer cannot be checked meanwhile. */
    val pending: Boolean = false,
) {
    val text: String get() = value?.toString() ?: ""
    companion object { val Empty = CellValue() }
}

/** How a cell is shown after checking. */
enum class CellMark { NONE, ACTIVE, FIRST_WRONG }

/**
 * The seam between the boards and the input method. Boards lay out cells and call [Cell] for each;
 * [Controls] is drawn once under the board (the pad draws its keys there, the pen draws nothing).
 *
 * [PenInput] (#26's `DigitRowState` ink rows) writes into the cell itself, reports [CellValue.confidence]
 * and, for a close call, [CellValue.ask] ([CellValue.askCell] = which digit of the row). The boards show
 * [ConfirmDigit] and hand the pick back through [confirm]; they do not know which input is active.
 * [item] is the exercise id, so an input can keep per-item state (the pen keeps the child's ink).
 */
interface DigitInput {
    val method: InputMethod

    @Composable
    fun Cell(
        item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
        onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier,
    )

    /** Laid-out width of [cell], so tables can give a column one shared width. */
    fun width(cell: InputCell): Dp = CellStyle.width(cell)

    /** The child answered "Tarkoititko X vai Y?" with [digit]; returns the cell's new value. */
    fun confirm(item: String, cell: InputCell, value: CellValue, digit: Int): CellValue = value.copy(value = digit, ask = null)

    @Composable
    fun Controls(focused: InputCell?, value: CellValue?, enabled: Boolean, onValue: (CellValue) -> Unit, onNext: () -> Unit)

    /**
     * The digits written for [item] since the previous call, with ink and recogniser confidence, for the
     * handwriting coach (#28). Call once per checked answer. Empty for the number pad, so it never offers a
     * "write it more clearly" moment.
     */
    fun written(item: String): List<fi.skillit.coach.WrittenDigit> = emptyList()
}

object CellStyle {
    /** Large enough to write a digit with the pen (the column grid is squared paper of this size). */
    val digit: Dp = 72.dp
    val small: Dp = 48.dp
    fun width(cell: InputCell) = if (cell.maxDigits == 1) (if (cell.small) small else digit) else (40 * cell.maxDigits + 24).dp
    val activeColumn = Color(0xFFFFF1C2)
    val wrong = Color(0xFFB3261E)
}

/** The number pad: tap a cell, then tap digits. Unlocked from the parent area; the pen is the default. */
object PadInput : DigitInput {
    override val method = InputMethod.PAD

    @Composable
    override fun Cell(
        item: String, cell: InputCell, value: CellValue, focused: Boolean, mark: CellMark, enabled: Boolean,
        onFocus: () -> Unit, onValue: (CellValue) -> Unit, modifier: Modifier,
    ) {
        val size = if (cell.small) CellStyle.small else CellStyle.digit
        val border = when {
            mark == CellMark.FIRST_WRONG -> BorderStroke(3.dp, CellStyle.wrong)
            focused -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val description = buildString {
            append(cell.label)
            append(if (value.value == null) ", tyhjä" else ", ${value.value}")
            if (mark == CellMark.FIRST_WRONG) append(", katso tätä vielä")
        }
        Box(
            modifier
                .size(width = CellStyle.width(cell), height = size)
                .background(if (cell.small) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(border, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClickLabel = "Valitse") { onFocus() }
                .clearAndSetSemantics {
                    contentDescription = description
                    if (focused) selected = true
                    onClick("Valitse") { onFocus(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(value.text, fontSize = if (cell.small) 20.sp else 28.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    override fun Controls(focused: InputCell?, value: CellValue?, enabled: Boolean, onValue: (CellValue) -> Unit, onNext: () -> Unit) {
        val active = enabled && focused != null
        fun type(d: Int) {
            val cell = focused ?: return
            val current = value?.text.orEmpty()
            if (cell.maxDigits == 1) { onValue(CellValue(d)); onNext(); return }
            val text = if (current.length >= cell.maxDigits) current else (current + d).trimStart('0').ifEmpty { "0" }
            onValue(CellValue(text.toInt()))
        }
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "Numeronäppäimistö" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 0)).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d ->
                        OutlinedButton(onClick = { type(d) }, enabled = active, modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                            .semantics { contentDescription = "Numero $d" }) { Text("$d", fontSize = 24.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (focused == null) return@OutlinedButton
                    val text = value?.text.orEmpty().dropLast(1)
                    onValue(CellValue(text.toIntOrNull()))
                }, enabled = active, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Pyyhi") }
                OutlinedButton(onClick = onNext, enabled = active, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Seuraava ruutu") }
            }
        }
    }
}

/** Pen unsure between two digits: the child picks. Shared by every input, so pen and pad boards stay identical. */
@Composable
fun ConfirmDigit(cell: InputCell, ask: Pair<Int, Int>, onPick: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tarkoititko ${ask.first} vai ${ask.second}?", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "${cell.label}: tarkoititko ${ask.first} vai ${ask.second}?" })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(ask.first, ask.second).forEach { d ->
                    Button(onClick = { onPick(d) }, modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 72.dp).semantics { contentDescription = "Valitse $d" }) { Text("$d", fontSize = 24.sp) }
                }
            }
        }
    }
}
