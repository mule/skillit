package fi.skillit.engine

import org.junit.Assert.*
import org.junit.Test

/** ExerciseSession with non-choice answers keeps the M1 rules: 10/5/0 XP, retries, stale tokens, replay. */
class AnswerSessionTest {
    private val questions = listOf(
        Question.of("n1", 0, "7 · 8", AnswerSpec.Numeric(56), "Vihje", "56"),
        Question.of("c1", 0, "23 · 4", AnswerSpec.ColumnMultiplication.layout(23, 4), "Vihje", "92"),
        Question.of("s1", 1, "3 + 4 · 5", AnswerSpec.Steps(listOf(20, 23)), "Vihje", "23"),
        Question.of("m1", 1, "Valitse", AnswerSpec.Choice(listOf(Choice("a", "A"), Choice("b", "B")), "a"), "Vihje", "A"),
    )

    @Test fun cellAnswersAwardXpAndReportTheFirstWrongCell() {
        val session = ExerciseSession(questions)
        assertFalse(session.answer("n1:0", "56")) // choice API does not apply to a numeric question
        assertFalse(session.submit("n1:0", AnswerInput.Choice("56")))
        assertFalse(session.submit("n1:0", AnswerInput.Cells(mapOf("bogus" to 1))))
        assertFalse(session.submit("stale", AnswerInput.number(56)))
        assertTrue(session.submit("n1:0", AnswerInput.number(56)))
        assertFalse(session.submit("n1:0", AnswerInput.number(56)))
        assertEquals(10, session.feedback!!.xp)
        assertEquals("", session.feedback!!.choiceId)
        session.next("n1:0")
        assertTrue(session.submit("c1:0", AnswerInput.Cells(mapOf("r0" to 2, "c0.1" to 1, "r1" to 8))))
        assertFalse(session.feedback!!.correct)
        assertEquals("r1", session.feedback!!.check!!.firstWrong!!.id)
        assertEquals(8, session.outcomes.last().check!!.cells.single { it.id == "r1" }.given)
        session.next("c1:0")
        assertEquals("c1:1", session.current!!.token) // retried at the end of its level
        assertTrue(session.submit("c1:1", AnswerInput.Cells(mapOf("r0" to 2, "r1" to 9))))
        assertEquals(5, session.feedback!!.xp)
        session.next("c1:1")
        assertTrue(session.submit("s1:0", AnswerInput.steps(20, 23)))
        session.next("s1:0")
        assertTrue(session.answer("m1:0", "a"))
        session.next("m1:0")
        assertTrue(session.complete)
        assertEquals(35, session.xp)
    }

    @Test fun mixedHistoryReplaysAtEveryStepAndRejectsTamperedInput() {
        var session = ExerciseSession(questions)
        val script = listOf<(ExerciseSession) -> Boolean>(
            { it.showHint("n1:0") }, { it.submit("n1:0", AnswerInput.number(null)) }, { it.next("n1:0") },
            { it.submit("c1:0", AnswerInput.Cells(mapOf("r0" to 2, "r1" to 9))) }, { it.next("c1:0") },
            { it.submit("n1:1", AnswerInput.number(56)) }, { it.next("n1:1") },
            { it.submit("s1:0", AnswerInput.steps(35, 23)) },
        )
        script.forEach { step ->
            assertTrue(step(session))
            val restored = ExerciseSession.restore(questions, session.events)
            assertEquals(session.events, restored.events)
            assertEquals(session.feedback, restored.feedback)
            assertEquals(session.outcomes, restored.outcomes)
            session = restored
        }
        assertEquals(15, session.xp) // miss, 10, retry 5, miss
        assertEquals("s0", session.feedback!!.check!!.firstWrong!!.id)
        val answer = session.events.last()
        listOf(
            answer.copy(choiceId = """{"type":"cells","values":{"s0":20,"s1":23}} """), // not canonical
            answer.copy(choiceId = "{"),
            answer.copy(choiceId = """{"type":"choice","id":"a"}"""),
            answer.copy(choiceId = """{"type":"cells","values":{"x":1}}"""),
        ).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                ExerciseSession.restore(questions, session.events.dropLast(1) + bad)
            }
        }
    }

    @Test fun invalidSpecsAndMixedQuestionShapesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSession(listOf(Question.of("x", 0, "?", AnswerSpec.Steps(emptyList()), "h", "e")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSession(listOf(Question("x", 0, "?", listOf(Choice("a", "A"), Choice("b", "B")), "a", "h", "e", AnswerSpec.Numeric(1))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSession(listOf(Question("x", 0, "?", emptyList(), "", "h", "e", AnswerSpec.Choice(listOf(Choice("a", "A"), Choice("b", "B")), "a"))))
        }
        assertEquals(AnswerSpec.Choice(listOf(Choice("a", "A"), Choice("b", "B")), "a"), questions[3].spec)
        assertNull(questions[3].answer)
    }
}
