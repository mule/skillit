package fi.skillit.engine

import org.junit.Assert.*
import org.junit.Test

class ExerciseSessionTest {
    private val questions = (1..20).map {
        Question("q$it", (it - 1) / 5, "Question $it", listOf(Choice("yes", "Yes"), Choice("no", "No")), "yes", "Hint", "Explanation")
    }

    @Test fun twentyCorrectAnswersFinishWithTwoHundredXp() {
        val session = ExerciseSession(questions)
        repeat(20) {
            val token = session.current!!.token
            assertTrue(session.answer(token, "yes"))
            assertEquals(10, session.feedback!!.xp)
            assertTrue(session.next(token))
        }
        assertTrue(session.complete)
        assertEquals(200, session.xp)
        assertEquals(20, session.firstTryCorrect)
        assertNull(session.current)
    }
    @Test fun missedQuestionsStayInTheirLevelUntilCorrectAndAlwaysAwardFiveXp() {
        val session = ExerciseSession(questions)
        val first = session.current!!.token
        session.answer(first, "no")
        session.next(first)
        repeat(4) {
            val token = session.current!!.token
            session.answer(token, "yes")
            session.next(token)
        }
        assertEquals("q1:1", session.current!!.token)
        session.answer("q1:1", "yes")
        assertEquals(5, session.feedback!!.xp)
        session.next("q1:1")
        assertEquals("q6:0", session.current!!.token)
        assertEquals(45, session.xp)
        val allMissed = ExerciseSession(questions)
        var attempts = 0
        while (!allMissed.complete && attempts < 50) {
            val token = allMissed.current!!.token
            allMissed.answer(token, "no")
            allMissed.next(token)
            attempts++
        }
        assertFalse(allMissed.complete)
        assertEquals(50, attempts)
        assertEquals(0, allMissed.xp)
        assertEquals(0, allMissed.current!!.question.level)
        while (!allMissed.complete) {
            val attempt = allMissed.current!!
            allMissed.answer(attempt.token, "yes")
            allMissed.next(attempt.token)
        }
        assertEquals(175, allMissed.xp)
        assertEquals(5, allMissed.retryCount)
    }
    @Test fun hintsAndFeedbackReplayWithoutDuplicateAwardsOrStaleTaps() {
        val session = ExerciseSession(questions)
        assertTrue(session.showHint("q1:0"))
        assertFalse(session.showHint("q1:0"))
        assertTrue(session.hintVisible)
        assertFalse(session.answer("q1:0", "unknown"))
        assertFalse(session.answer("stale", "yes"))
        assertTrue(session.answer("q1:0", "yes"))
        assertFalse(session.answer("q1:0", "yes"))
        val resumed = ExerciseSession.restore(questions, session.events)
        assertEquals(10, resumed.xp)
        assertTrue(resumed.hintVisible)
        assertEquals(session.feedback, resumed.feedback)
        assertFalse(resumed.answer("q1:0", "yes"))
        assertTrue(resumed.next("q1:0"))
        assertFalse(resumed.next("q1:0"))
        assertFalse(resumed.hintVisible)
        assertEquals("q2:0", resumed.current!!.token)
    }
    @Test fun retrySuccessAndRestoreAtEveryActionGiveOneHundredXp() {
        var session = ExerciseSession(questions)
        var steps = 0
        while (!session.complete && steps++ < 50) {
            val attempt = session.current!!
            session.showHint(attempt.token)
            session = ExerciseSession.restore(questions, session.events)
            session.answer(attempt.token, if (attempt.retry) "yes" else "no")
            session = ExerciseSession.restore(questions, session.events)
            session.next(attempt.token)
            session = ExerciseSession.restore(questions, session.events)
        }
        assertTrue(session.complete)
        assertEquals(100, session.xp)
        assertEquals(20, session.retryCount)
        assertEquals(0, session.firstTryCorrect)
        assertEquals(20, session.answeredOriginals)
        assertFalse(session.answer("q20:1", "yes"))
        assertFalse(session.next("q20:1"))
        assertFalse(session.showHint("q20:1"))
    }

    @Test fun malformedHistoryAndMalformedQuestionPacksAreRejected() {
        val invalid = listOf(
            listOf(SessionEvent(Action.NEXT, "q1:0")),
            listOf(SessionEvent(Action.ANSWER, "removed:0", "yes")),
            listOf(SessionEvent(Action.ANSWER, "q1:0", "unknown")),
            List(121) { SessionEvent(Action.HINT, "q1:0") },
            listOf(SessionEvent(Action.HINT, "q1:0", "extra")),
        )
        invalid.forEach { history ->
            assertThrows(IllegalArgumentException::class.java) { ExerciseSession.restore(questions, history) }
        }
        assertThrows(IllegalArgumentException::class.java) { ExerciseSession(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { ExerciseSession(questions + questions.first()) }
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSession(listOf(questions.first().copy(correctId = "missing")))
        }
    }
    @Test fun repeatedMissesRemainRetryableAcrossEveryRestoreUntilCorrect() {
        var session = ExerciseSession(questions.take(1))
        repeat(80) { number ->
            val token = "q1:$number"
            assertEquals(token, session.current!!.token)
            assertTrue(session.showHint(token))
            assertTrue(session.answer(token, "no"))
            assertFalse(session.answer(token, "yes"))
            assertEquals(0, session.xp)
            session = ExerciseSession.restore(questions.take(1), session.events)
            assertTrue(session.next(token))
            session = ExerciseSession.restore(questions.take(1), session.events)
            assertFalse(session.complete)
            assertEquals(0, session.resolvedCount)
            assertFalse(session.answer(token, "yes"))
        }
        assertEquals("q1:80", session.current!!.token)
        assertTrue(session.answer("q1:80", "yes"))
        assertEquals(5, session.xp)
        assertEquals(1, session.resolvedCount)
        assertEquals(1, session.retryCount)
        session.next("q1:80")
        val restored = ExerciseSession.restore(questions.take(1), session.events)
        assertTrue(restored.complete)
        assertEquals(5, restored.xp)
        assertFalse(restored.answer("q1:80", "yes"))
    }
    @Test fun retryGuidanceDistinguishesQueuedReviewsFromTheOnlyRemainingQuestion() {
        var session = ExerciseSession(questions)
        repeat(5) { index ->
            val token = session.current!!.token
            session.answer(token, if (index < 2) "no" else "yes")
            session.next(token)
        }
        assertEquals("q1:1", session.current!!.token)
        session.answer("q1:1", "no")
        assertFalse(session.retryIsNext)
        session = ExerciseSession.restore(questions, session.events)
        assertFalse(session.retryIsNext)
        session.next("q1:1")
        assertEquals("q2:1", session.current!!.token)
        session.answer("q2:1", "yes")
        assertFalse(session.retryIsNext)
        session.next("q2:1")
        assertEquals("q1:2", session.current!!.token)
        session.answer("q1:2", "no")
        assertTrue(session.retryIsNext)
        session.next("q1:2")
        assertEquals("q1:3", session.current!!.token)
        assertFalse(session.retryIsNext)
        assertEquals(35, session.xp)
    }
}
