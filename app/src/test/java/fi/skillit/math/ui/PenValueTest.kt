package fi.skillit.math.ui

import fi.skillit.ink.DigitReading
import fi.skillit.ink.DigitScore
import org.junit.Assert.assertEquals
import org.junit.Test

/** How a #26 ink row becomes a board value. */
class PenValueTest {
    private fun accepted(d: Int, c: Float) = DigitReading.Accepted(d, c, listOf(DigitScore(d, c)))

    @Test fun completeRowGivesTheNumberAndItsLowestDigitConfidence() {
        val v = PenInput.penValue("27", listOf(2, 7), mapOf(0 to accepted(2, .9f), 1 to accepted(7, .7f)), emptyMap())
        assertEquals(27, v.value)
        assertEquals(.7, v.confidence!!, 1e-6)
        assertEquals(null, v.ask)
    }

    @Test fun anOpenQuestionBecomesTheBoardsConfirmPromptForThatDigit() {
        val ask = DigitReading.Ask(4, 9, listOf(DigitScore(9, .5f), DigitScore(4, .4f)))
        val v = PenInput.penValue(null, listOf(1, null), mapOf(0 to accepted(1, .9f), 1 to ask), mapOf(1 to ask))
        assertEquals(CellValue(null, null, 4 to 9, askCell = 1), v)
    }

    @Test fun aConfirmedDigitCountsWithItsRecogniserShare() {
        val ask = DigitReading.Ask(4, 9, listOf(DigitScore(9, .5f), DigitScore(4, .4f)))
        val v = PenInput.penValue("4", listOf(4), mapOf(0 to ask), emptyMap())
        assertEquals(4, v.value)
        assertEquals(.4, v.confidence!!, 1e-6)
    }

    @Test fun emptyOrUnreadableRowIsEmpty() {
        assertEquals(CellValue.Empty, PenInput.penValue(null, listOf(null), emptyMap(), emptyMap()))
        assertEquals(CellValue.Empty, PenInput.penValue(null, listOf(null), mapOf(0 to DigitReading.Unreadable(emptyList())), emptyMap()))
    }

    @Test fun inkStillBeingReadKeepsAFilledBoardUncheckable() {
        val draft = AnswerDraft(BoardKind.Number(word = false))
        draft.set("answer", CellValue(27, .9))
        assertEquals(true, draft.complete)
        draft.set("answer", CellValue(pending = true))
        assertEquals(false, draft.complete)
        draft.set("answer", CellValue(28, .8))
        assertEquals(true, draft.complete)
    }
}
