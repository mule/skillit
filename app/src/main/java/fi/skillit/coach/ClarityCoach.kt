package fi.skillit.coach

import fi.skillit.ink.DigitReading
import fi.skillit.ink.DigitRowState
import fi.skillit.ink.InkStroke
import fi.skillit.ink.InputMode

/**
 * One digit the child wrote in an answer, as the game saw it. [confidence] is the recogniser's share for
 * [digit] (null for the number pad); [asked] means the game had to ask “Tarkoititko 4 vai 9?”.
 * [strokes] are relative to the cell's corner; [boxWidth]×[boxHeight] is the cell in the same units.
 */
data class WrittenDigit(
    val cell: Int,
    val digit: Int,
    val confidence: Float?,
    val asked: Boolean = false,
    val strokes: List<InkStroke> = emptyList(),
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
)

/** “Write this digit a bit more clearly?” for [digit], from answer cell [cell]. */
data class ClarityPrompt(val digit: Int, val cell: Int, val confidence: Float?)

/**
 * The in-game micro-feedback hook (#28) for the math boards (#27).
 *
 * After an answer has been checked and its XP given, the board calls [afterAnswer] with the digits the child
 * wrote. When the recogniser was unsure about one of them, it returns a [ClarityPrompt]; the board then shows
 * [ClarityMoment] for it, an optional 10-second “kirjoita se vielä selvemmin” moment. The prompt never
 * changes the answer, its correctness or the XP: the coach has no access to the session at all.
 *
 * It doesn't nag: at most one prompt per [everyAnswers] answers and [maxPerSession] per coach instance (make
 * one per lesson). Digits from the number pad never trigger it.
 */
class ClarityCoach(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val everyAnswers: Int = 3,
    private val maxPerSession: Int = 3,
) {
    private var sincePrompt = everyAnswers
    var shown = 0
        private set

    fun afterAnswer(digits: List<WrittenDigit>): ClarityPrompt? {
        sincePrompt++
        if (shown >= maxPerSession || sincePrompt < everyAnswers) return null
        val unsure = digits.filter { it.asked || (it.confidence != null && it.confidence < threshold) }
        // The least sure digit; a digit the game had to ask about counts as least sure.
        val pick = unsure.minByOrNull { if (it.asked) -1f else it.confidence!! } ?: return null
        sincePrompt = 0
        shown++
        return ClarityPrompt(pick.digit, pick.cell, pick.confidence)
    }

    companion object {
        /** Below this share the recogniser was unsure. Same as [fi.skillit.ink.DigitRules.ACCEPT]. */
        const val DEFAULT_THRESHOLD = 0.6f
    }
}

/**
 * What the child wrote in [state] (pen mode), cell by cell, for [ClarityCoach.afterAnswer] and
 * [CoachEnvironment.recordAnswer]. Number-pad rows give digits with no confidence, so they never prompt.
 */
fun DigitRowState.writtenDigits(): List<WrittenDigit> = rowInk().writtenDigits()

/** A plain snapshot of one [DigitRowState], so the mapping to [WrittenDigit] is testable without an ink view. */
data class RowInk(
    val pen: Boolean,
    val digits: List<Int?>,
    val readings: Map<Int, DigitReading> = emptyMap(),
    /** Strokes per cell, relative to the cell's corner. */
    val strokes: Map<Int, List<InkStroke>> = emptyMap(),
    val boxWidth: Float = 0f,
    val boxHeight: Float = 0f,
) {
    val cells: Int get() = digits.size

    fun writtenDigits(): List<WrittenDigit> = digits.mapIndexedNotNull { cell, digit ->
        digit ?: return@mapIndexedNotNull null
        if (!pen) return@mapIndexedNotNull WrittenDigit(cell, digit, null)
        val reading = readings[cell]
        WrittenDigit(
            cell = cell, digit = digit,
            confidence = reading?.ranked?.firstOrNull { it.digit == digit }?.confidence ?: 0f,
            asked = reading is DigitReading.Ask,
            strokes = strokes[cell].orEmpty(),
            boxWidth = boxWidth, boxHeight = boxHeight,
        )
    }
}

fun DigitRowState.rowInk(): RowInk {
    val pen = mode == InputMode.Pen
    // `ink` is module-internal: the coach lives in the same module as the pen cells.
    val row = ink.row
    return RowInk(pen, digits, if (pen) readings else emptyMap(), if (pen) strokesByCell() else emptyMap(), row?.boxWidth ?: 0f, row?.boxHeight ?: 0f)
}

/**
 * Collects what was written for one board item across its rows, for the coach. Cells get one running index in
 * row order. Only digits whose ink changed since the last call for the item are returned ([reported] remembers
 * that), so a retry that rewrites one cell is not measured, stored or prompted again for the cells the child
 * left alone (review S1).
 */
fun writtenSince(item: String, rows: Map<Pair<String, String>, RowInk>, reported: MutableMap<Pair<String, String>, Map<Int, List<InkStroke>>>): List<WrittenDigit> {
    var offset = 0
    return rows.filterKeys { it.first == item }.entries.sortedBy { it.key.second }.flatMap { (key, row) ->
        val before = reported[key].orEmpty()
        reported[key] = row.strokes
        row.writtenDigits().filter { !row.pen || it.strokes.isNotEmpty() && it.strokes != before[it.cell] }
            .map { it.copy(cell = offset + it.cell) }.also { offset += row.cells }
    }
}

/** Measures the inked digits of an answer, for [LegibilityStore.record]. Cells without ink or size are skipped. */
fun List<WrittenDigit>.legibility(): List<LegibilityReport> = mapNotNull {
    if (it.strokes.isEmpty() || it.boxWidth <= 0f || it.boxHeight <= 0f) null
    else Legibility.measure(it.digit, it.strokes, it.boxWidth, it.boxHeight, it.confidence)
}
