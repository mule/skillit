package fi.skillit.engine

import fi.skillit.engine.AnswerSpec.ColumnMultiplication
import org.junit.Assert.*
import org.junit.Test

class AnswerSpecTest {
    private fun cells(vararg pairs: Pair<String, Int?>) = AnswerInput.Cells(mapOf(*pairs))

    @Test fun choiceKeepsExistingBehaviour() {
        val spec = AnswerSpec.Choice(listOf(Choice("a", "A"), Choice("b", "B")), "b")
        spec.validate()
        assertTrue(spec.check(AnswerInput.Choice("b"))!!.correct)
        assertFalse(spec.check(AnswerInput.Choice("a"))!!.correct)
        assertNull(spec.check(AnswerInput.Choice("x")))
        assertNull(spec.check(AnswerInput.number(1)))
        assertThrows(IllegalArgumentException::class.java) { spec.copy(correctId = "x").validate() }
    }

    @Test fun numericChecksOneCellAndRejectsForeignInput() {
        val spec = AnswerSpec.Numeric(56)
        assertTrue(spec.check(AnswerInput.number(56))!!.correct)
        val wrong = spec.check(AnswerInput.number(54))!!
        assertFalse(wrong.correct)
        assertEquals(CellResult("answer", 56, 54), wrong.firstWrong)
        assertFalse(spec.check(AnswerInput.number(null))!!.correct)
        assertNull(spec.check(cells("other" to 56)))
        assertNull(spec.check(AnswerInput.Choice("56")))
    }

    @Test fun oneDigitMultiplierLayoutHasResultDigitsAndOptionalCarries() {
        val spec = ColumnMultiplication.layout(23, 4)
        assertEquals(listOf("r0", "c0.1", "r1"), spec.cells.map { it.id })
        assertEquals(listOf(2, 1, 9), spec.cells.map { it.expected })
        assertEquals(listOf(true, false, true), spec.cells.map { it.required })
        spec.validate()
        assertTrue(spec.check(cells("r0" to 2, "r1" to 9))!!.correct)
        // A wrong carry is shown but does not cost the answer.
        val wrongCarry = spec.check(cells("r0" to 2, "c0.1" to 2, "r1" to 9))!!
        assertTrue(wrongCarry.correct)
        assertEquals("c0.1", wrongCarry.firstWrong!!.id)
        // The first wrong cell in solving order is the ones digit, not the tens digit.
        val wrong = spec.check(cells("r0" to 3, "r1" to 8))!!
        assertFalse(wrong.correct)
        assertEquals("r0", wrong.firstWrong!!.id)
        assertNull(spec.check(cells("r9" to 1)))
    }

    @Test fun twoDigitMultiplierLayoutHasPartialRowsSumCarriesAndTheProduct() {
        val spec = ColumnMultiplication.layout(76, 58) // 608 + 3800 = 4408
        spec.validate()
        val byId = spec.cells.associateBy { it.id }
        val digits = { ids: String -> ids.split(" ").map { byId.getValue(it).expected } }
        assertEquals(listOf(8, 0, 6), digits("p0.0 p0.1 p0.2"))
        assertEquals(4, byId.getValue("c0.1").expected) // 8 · 6 = 48
        assertEquals(listOf(0, 8, 3), digits("p1.1 p1.2 p1.3"))
        assertEquals(3, byId.getValue("c1.2").expected) // 5 · 6 = 30
        assertEquals(listOf(8, 0, 4, 4), digits("r0 r1 r2 r3"))
        assertEquals(1, byId.getValue("s3").expected) // 6 + 8 = 14
        assertEquals(listOf("p0.0", "c0.1", "p0.1", "p0.2", "p1.1", "c1.2", "p1.2", "p1.3", "r0", "r1", "r2", "s3", "r3"), spec.cells.map { it.id })
        val answer = spec.cells.filter { it.required }.associate { it.id to it.expected }
        assertTrue(spec.check(AnswerInput.Cells(answer))!!.correct)
        val slip = spec.check(AnswerInput.Cells(answer + ("p1.2" to 5)))!!
        assertFalse(slip.correct)
        assertEquals("p1.2", slip.firstWrong!!.id)
    }

    @Test fun columnMultiplicationRejectsTamperedDigitsAndMissingResultCells() {
        val spec = ColumnMultiplication.layout(23, 4)
        val tampered = spec.copy(cells = spec.cells.map { if (it.id == "r1") it.copy(expected = 8) else it })
        assertThrows(IllegalArgumentException::class.java) { tampered.validate() }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(cells = spec.cells.filter { it.id != "r1" }).validate() }
        assertThrows(IllegalArgumentException::class.java) { spec.copy(factors = listOf(23, 5)).validate() }
        // Making a carry required is allowed; inventing a cell is not.
        spec.copy(cells = spec.cells.map { it.copy(required = true) }).validate()
        assertThrows(IllegalArgumentException::class.java) {
            spec.copy(cells = spec.cells + DigitCell("c0.2", DigitKind.CARRY, 0, 2, 1)).validate()
        }
    }

    @Test fun stepsReportTheFirstWrongIntermediateValue() {
        val spec = AnswerSpec.Steps(listOf(20, 23))
        assertTrue(spec.check(AnswerInput.steps(20, 23))!!.correct)
        val wrong = spec.check(AnswerInput.steps(35, 23))!!
        assertFalse(wrong.correct)
        assertEquals("s0", wrong.firstWrong!!.id)
        assertEquals("s1", spec.check(AnswerInput.steps(20, null))!!.firstWrong!!.id)
        assertThrows(IllegalArgumentException::class.java) { AnswerSpec.Steps(emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { AnswerSpec.Steps(listOf(1, 2), listOf("a")).validate() }
    }

    @Test fun fillTableChecksBlanksInRowMajorOrder() {
        val spec = AnswerSpec.FillTable(listOf(
            listOf(TableEntry.Given("·"), TableEntry.Given("4"), TableEntry.Given("6")),
            listOf(TableEntry.Given("3"), TableEntry.Blank("a", 12), TableEntry.Blank("b", 18)),
        ))
        spec.validate()
        assertTrue(spec.check(cells("a" to 12, "b" to 18))!!.correct)
        assertEquals("b", spec.check(cells("a" to 12, "b" to 16))!!.firstWrong!!.id)
        assertEquals("a", spec.check(cells("a" to 10, "b" to 16))!!.firstWrong!!.id)
        assertThrows(IllegalArgumentException::class.java) {
            AnswerSpec.FillTable(listOf(listOf(TableEntry.Given("1")), listOf(TableEntry.Blank("a", 1), TableEntry.Given("2")))).validate()
        }
        assertThrows(IllegalArgumentException::class.java) { AnswerSpec.FillTable(listOf(listOf(TableEntry.Given("1")))).validate() }
    }

    @Test fun missingFactorComputesTheUnknown() {
        val factor = AnswerSpec.MissingFactor(listOf(6, null), 42)
        factor.validate()
        assertEquals(7, factor.expected)
        assertTrue(factor.check(cells("missing" to 7))!!.correct)
        assertFalse(factor.check(cells("missing" to 6))!!.correct)
        val product = AnswerSpec.MissingFactor(listOf(2, 3, 4))
        product.validate()
        assertEquals(24, product.expected)
        listOf(
            AnswerSpec.MissingFactor(listOf(6, null), 40),
            AnswerSpec.MissingFactor(listOf(6, null, null), 42),
            AnswerSpec.MissingFactor(listOf(6, 7), 42),
            AnswerSpec.MissingFactor(listOf(0, null), 0),
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it.validate() } }
    }
}
