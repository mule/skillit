package fi.skillit.math

import fi.skillit.engine.Action
import fi.skillit.engine.SessionEvent
import fi.skillit.engine.pack.ContentPack
import org.junit.Assert.*
import org.junit.Test

class MathCourseTest {
    private val course = MathCourse(MathFixture.build())

    @Test fun eachLessonPlansTwelveItemsEndingWithTheRewardLetterCode() {
        course.stops.forEach { stop ->
            val plan = course.plan(stop.id, emptyList(), now = 1_000_000, seed = 7)
            assertEquals(stop.id, MathCourse.SESSION_SIZE, plan.itemIds.size)
            assertEquals(plan.itemIds.size, plan.itemIds.toSet().size)
            val exercises = course.exercises(plan)!!
            assertEquals(MathCourse.LETTER_CODE, exercises.last().generator?.family)
            assertTrue(exercises.dropLast(1).none { it.generator?.family == MathCourse.LETTER_CODE })
            assertNotNull(MathCourse.rewardWord(exercises.last()))
            assertTrue(plan.explanation.isNotBlank())
        }
    }

    @Test fun planIsDeterministicForTheSameSeedAndHistory() {
        val a = course.plan("lesson-20", emptyList(), 5, seed = 42)
        assertEquals(a, course.plan("lesson-20", emptyList(), 5, seed = 42))
    }

    @Test fun aPlanFromAnOlderPackIsDroppedInsteadOfCrashing() {
        val plan = MathCourse.Plan("lesson-20", listOf("not-in-pack"), "x")
        assertNull(course.exercises(plan))
        assertNull(course.exercises(plan.copy(stopId = "gone")))
    }

    @Test fun sessionReplaysAndAllItemsShareOneLevelSoMissesComeBackAtTheEnd() {
        val plan = course.plan("lesson-18", emptyList(), 1, seed = 1)
        val session = course.session(course.exercises(plan)!!, emptyList())
        assertTrue(session.questions.all { it.level == 0 })
        assertEquals(plan.itemIds.first(), session.current!!.question.id)
    }

    @Test fun progressRoundTripsAndKeepsMathXpOnItsOwn() {
        val p = MathProgress("math-ch2-fixture", 1, 135, mapOf("lesson-14" to LessonRecord(2, 120, "KISSA")),
            ActiveLesson(MathCourse.Plan("lesson-18", listOf("a", "b"), "Monipuolista harjoittelua."),
                listOf(SessionEvent(Action.HINT, "a:0"), SessionEvent(Action.ANSWER, "a:0", "{\"type\":\"cells\",\"values\":{\"answer\":5}}"))),
            ParentPin.create("1234"), "Harjoitellaan lisää: muistinumeroita.")
        assertEquals(p, MathProgressCodec.decode(MathProgressCodec.encode(p)))
        assertEquals(MathProgress(), MathProgressCodec.decode(MathProgressCodec.encode(MathProgress())))
    }

    @Test fun corruptOrUnknownSavesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MathProgressCodec.decode("corrupt") }
        assertThrows(IllegalArgumentException::class.java) { MathProgressCodec.decode("{\"schema\":9}") }
    }

    @Test fun theSampleMathgenPackAlsoLoadsAsACourse() {
        val text = java.io.File("../content-schema/examples/mathgen-sample.v1.json").readText()
        val sample = MathCourse.parse(text)
        assertEquals(1, sample.stops.size)
        assertEquals(sample.pack, ContentPack.parse(text, fi.skillit.mathgen.MathGen.recomputers))
    }
}

class ParentPinTest {
    @Test fun rightPinOpensWrongPinBacksOffAndNothingPlainIsStored() {
        val pin = ParentPin.create("2580")
        assertFalse(pin.hash.contains("2580") || pin.salt.contains("2580") && pin.hash == pin.salt)
        assertEquals(ParentPin.Check.Ok, pin.check("2580", 0))
        var state = pin
        repeat(2) { state = (state.check("0000", 0) as ParentPin.Check.Wrong).updated; assertEquals(0L, state.lockedUntil) }
        state = (state.check("0000", 0) as ParentPin.Check.Wrong).updated
        assertEquals(30_000L, state.lockedUntil)
        // Even the right PIN waits out the lock, then works.
        assertEquals(ParentPin.Check.Locked(30), state.check("2580", 0))
        assertEquals(ParentPin.Check.Ok, state.check("2580", 30_000))
        state = (state.check("1111", 30_000) as ParentPin.Check.Wrong).updated
        assertEquals(30_000L + 60_000L, state.lockedUntil)
        assertEquals(0, state.cleared().failures)
    }

    @Test fun onlyFourDigitPinsAreAccepted() {
        listOf("123", "12345", "12a4", "").forEach { assertThrows(IllegalArgumentException::class.java) { ParentPin.create(it) } }
        assertNotEquals(ParentPin.create("1234").salt, ParentPin.create("1234").salt)
    }
}
