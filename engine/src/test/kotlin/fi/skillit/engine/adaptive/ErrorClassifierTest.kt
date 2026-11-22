package fi.skillit.engine.adaptive

import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import org.junit.Assert.*
import org.junit.Test

class ErrorClassifierTest {
    private fun column(top: Int, bottom: Int, vararg values: Pair<String, Int?>) =
        ErrorClassifier.classify(AnswerSpec.ColumnMultiplication.layout(top, bottom), AnswerInput.Cells(mapOf(*values)))

    @Test fun correctAnswersIgnoreOptionalCarriesAndLowConfidence() {
        assertNull(column(27, 3, "r0" to 1, "r1" to 8))
        assertNull(column(27, 3, "r0" to 1, "r1" to 8, "c0.1" to 0))
        assertNull(ErrorClassifier.classify(AnswerSpec.Numeric(81), AnswerInput.number(81), InputMethod.PEN, 0.1))
    }
    @Test fun missingIncomingCarryIsDiagnosedFromResultDigits() {
        assertEquals(ErrorClass.DROPPED_CARRY, column(27, 3, "r0" to 1, "r1" to 6))
        assertEquals(ErrorClass.WRONG_TABLE_FACT, column(27, 3, "r0" to 2, "r1" to 8))
        assertEquals(ErrorClass.OTHER, column(27, 3, "r0" to 1, "r1" to 7))
    }
    @Test fun omittedOptionalCarryCellsStillAllowDiagnosis() {
        val full = AnswerSpec.ColumnMultiplication.layout(27, 3)
        val requiredOnly = full.copy(cells = full.cells.filter { it.required })
        requiredOnly.validate()
        assertEquals(ErrorClass.DROPPED_CARRY, ErrorClassifier.classify(requiredOnly,
            AnswerInput.Cells(mapOf("r0" to 1, "r1" to 6))))
    }
    @Test fun sumCarryAndPartialProductCarryAreDetected() {
        val spec = AnswerSpec.ColumnMultiplication.layout(27, 13)
        val good = spec.cells.associate { it.id to it.expected }.toMutableMap()
        good["p0.1"] = 6
        assertEquals(ErrorClass.DROPPED_CARRY, ErrorClassifier.classify(spec, AnswerInput.Cells(good)))
        val sum = AnswerSpec.ColumnMultiplication.layout(27, 24)
        val missing = sum.cells.associate { it.id to it.expected }.toMutableMap()
        // 108 + 540: dropping the carry from 8+0 does not apply; explicit wrong carry is evidence.
        val carry = sum.cells.first { it.expected > 0 && it.id.startsWith("c") }
        missing[carry.id] = 0
        missing["r0"] = 9
        assertEquals(ErrorClass.DROPPED_CARRY, ErrorClassifier.classify(sum, AnswerInput.Cells(missing)))
        val addition = AnswerSpec.ColumnMultiplication.layout(99, 99)
        val digits = addition.cells.associate { it.id to it.expected }.toMutableMap()
        val sumCell = addition.cells.first { it.id.startsWith("s") }
        val result = addition.cells.first { it.id == "r${sumCell.column}" }
        digits[result.id] = (result.expected - sumCell.expected + 10) % 10
        assertEquals(ErrorClass.DROPPED_CARRY, ErrorClassifier.classify(addition, AnswerInput.Cells(digits)))
    }
    @Test fun placeValueAndMultifactorFacts() {
        val spec = AnswerSpec.MissingFactor(listOf(7, 9))
        assertEquals(ErrorClass.PLACE_VALUE_SHIFT, ErrorClassifier.classify(spec, AnswerInput.Cells(mapOf("missing" to 630))))
        assertEquals(ErrorClass.WRONG_TABLE_FACT, ErrorClassifier.classify(spec, AnswerInput.Cells(mapOf("missing" to 62))))
        assertEquals(ErrorClass.WRONG_TABLE_FACT, ErrorClassifier.classify(AnswerSpec.MissingFactor(listOf(2, 3, 4)), AnswerInput.Cells(mapOf("missing" to 25))))
        assertEquals(ErrorClass.OTHER, ErrorClassifier.classify(AnswerSpec.Numeric(19), AnswerInput.number(null)))
    }
    @Test fun lowConfidenceIsOnlyAPenHypothesisAndNeverChangesCorrectness() {
        val spec = AnswerSpec.Numeric(19)
        assertEquals(ErrorClass.MISREAD_DIGIT, ErrorClassifier.classify(spec, AnswerInput.number(17), InputMethod.PEN, 0.2))
        assertEquals(ErrorClass.OTHER, ErrorClassifier.classify(spec, AnswerInput.number(17), InputMethod.PEN, 0.9))
        assertEquals(ErrorClass.OTHER, ErrorClassifier.classify(spec, AnswerInput.number(17), InputMethod.PAD, 0.2))
        assertThrows(IllegalArgumentException::class.java) { ErrorClassifier.classify(spec, AnswerInput.Choice("x")) }
    }
    @Test fun unshiftedPartialProductsAreAPlaceValueError() {
        // 23*5 + 23*4 = 207, instead of 23*5 + 23*40 = 1035.
        assertEquals(ErrorClass.PLACE_VALUE_SHIFT, column(23, 45, "r0" to 7, "r1" to 0, "r2" to 2))
        assertEquals(ErrorClass.PLACE_VALUE_SHIFT, column(23, 45, "r0" to 7, "r1" to 0, "r2" to 2, "r3" to 0))
        assertEquals(ErrorClass.OTHER, column(23, 45, "r0" to 8, "r1" to 0, "r2" to 2))
        assertEquals(ErrorClass.OTHER, column(23, 45, "r0" to 7, "r2" to 2))
        val spec = AnswerSpec.ColumnMultiplication.layout(23, 45)
        assertNull(ErrorClassifier.classify(spec, AnswerInput.Cells(spec.cells.filter { it.required }.associate { it.id to it.expected })))
    }

}
