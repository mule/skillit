package fi.skillit.engine.adaptive

import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.DigitKind

/** Diagnostic hypotheses, never an XP rule. Unknown/ambiguous mistakes fall back to OTHER. */
object ErrorClassifier {
    const val LOW_CONFIDENCE = 0.65

    fun classify(
        spec: AnswerSpec, answer: AnswerInput,
        inputMethod: InputMethod = InputMethod.PAD, recognizerConfidence: Double? = null,
    ): ErrorClass? {
        require(recognizerConfidence == null || recognizerConfidence.isFinite() && recognizerConfidence in 0.0..1.0)
        val check = requireNotNull(spec.check(answer)) { "Answer does not belong to this item" }
        if (check.correct) return null
        if (inputMethod == InputMethod.PEN && recognizerConfidence != null && recognizerConfidence < LOW_CONFIDENCE)
            return ErrorClass.MISREAD_DIGIT
        val values = (answer as? AnswerInput.Cells)?.values ?: return ErrorClass.OTHER
        if (spec is AnswerSpec.ColumnMultiplication) {
            val layout = AnswerSpec.ColumnMultiplication.layout(spec.factors[0], spec.factors[1]).cells
            val result = spec.cells.filter { it.kind == DigitKind.RESULT }
            val given = if (result.all { values[it.id] in 0..9 })
                result.sumOf { values.getValue(it.id)!!.toLong() * pow10(it.column) } else null
            if (given != null && shifted(spec.factors[0].toLong() * spec.factors[1], given))
                return ErrorClass.PLACE_VALUE_SHIFT
            // Identify the actual missing incoming carry at a wrong digit, not just an empty scratch cell.
            val carryDropped = spec.cells.any { cell ->
                if (values[cell.id] == null || values[cell.id] == cell.expected) false
                else when (cell.kind) {
                    DigitKind.CARRY, DigitKind.SUM_CARRY -> values[cell.id] == 0 && cell.expected > 0
                    DigitKind.PARTIAL, DigitKind.RESULT -> {
                        val single = spec.factors[1] < 10
                        if (cell.kind == DigitKind.RESULT && !single) {
                            val partials = layout.filter { it.kind == DigitKind.PARTIAL && it.column == cell.column }
                            val incoming = layout.find { it.kind == DigitKind.SUM_CARRY && it.column == cell.column }?.expected ?: 0
                            incoming > 0 && values[cell.id] == partials.sumOf { it.expected } % 10
                        } else {
                            val i = cell.column - cell.row
                            val incoming = layout.find { it.kind == DigitKind.CARRY && it.row == cell.row && it.column == cell.column }?.expected ?: 0
                            val topDigit = ((spec.factors[0] / pow10(i)) % 10).toInt()
                            val bottomDigit = ((spec.factors[1] / pow10(cell.row)) % 10).toInt()
                            incoming > 0 && values[cell.id] == topDigit * bottomDigit % 10
                        }
                    }
                }
            }
            if (carryDropped) return ErrorClass.DROPPED_CARRY
            // A wrong units multiplication digit has no incoming carry to confuse the diagnosis.
            if (spec.cells.any { (it.kind == DigitKind.PARTIAL || it.kind == DigitKind.RESULT && spec.factors[1] < 10) &&
                    it.column == it.row && values[it.id] != null && values[it.id] != it.expected })
                return ErrorClass.WRONG_TABLE_FACT
        } else {
            if (check.cells.any { it.given != null && shifted(it.expected.toLong(), it.given.toLong()) })
                return ErrorClass.PLACE_VALUE_SHIFT
            if (spec is AnswerSpec.MissingFactor && spec.factors.filterNotNull().all { it in 1..10 } &&
                (spec.product == null || spec.expected in 1..10) && values.values.all { it != null })
                return ErrorClass.WRONG_TABLE_FACT
        }
        return ErrorClass.OTHER
    }

    private fun shifted(expected: Long, given: Long): Boolean = expected != 0L && given != 0L &&
        (1..5).any { expected * pow10(it) == given || given * pow10(it) == expected }
    private fun pow10(n: Int): Long = (1..n).fold(1L) { a, _ -> a * 10 }
}
