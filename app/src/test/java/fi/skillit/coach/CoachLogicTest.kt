package fi.skillit.coach

import fi.skillit.ink.DigitRowState
import fi.skillit.ink.InputMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CoachLogicTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day0 = LocalDate.of(2026, 11, 22)
    private var now = day0.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun store(file: File = File(tmp.root, "h/legibility.v1.tsv")) = LegibilityStore(file, { now }, { ZoneId.of("UTC") })
    private fun report(digit: Int, overall: Float, height: Float = .65f) =
        LegibilityReport(digit, mapOf(Metric.Inside to overall, Metric.Size to overall), height, .85f, emptyList())
    private fun day(d: Int, digit: Int, score: Float, n: Int = 3, h: Float = .65f) =
        DigitDay(day0.toEpochDay() + d, digit, n, score * n, h * n, h * h * n, mapOf(Metric.Inside to (n to score * n)))

    @Test fun storeKeepsDailyAggregatesOnly() {
        val s = store()
        s.record(listOf(report(4, .5f), report(4, .7f), report(9, 1f)))
        now += 24 * 3600 * 1000L
        s.record(report(4, .9f))
        val days = s.days()
        assertEquals(3, days.size)
        val first = days.first { it.digit == 4 && it.day == day0.toEpochDay() }
        assertEquals(2, first.count); assertEquals(.6f, first.score, 1e-4f)
        assertEquals(2, first.metrics[Metric.Inside]!!.first); assertEquals(1.2f, first.metrics[Metric.Inside]!!.second, 1e-4f)
        // Only numbers: no strokes, no point lists, no answers.
        val text = File(tmp.root, "h/legibility.v1.tsv").readText()
        assertTrue(text.startsWith("v1\n"))
        text.lines().drop(1).filter { it.isNotEmpty() }.forEach { assertEquals(7, it.split("\t").size) }
        assertFalse(File(tmp.root, "h/legibility.v1.tsv.tmp").exists())
        s.deleteAll(); assertTrue(s.days().isEmpty())
    }

    @Test fun oldDaysAreDroppedAndBadLinesSkipped() {
        val file = File(tmp.root, "x.tsv")
        file.writeText(LegibilityStore.encode(listOf(day(-LegibilityStore.KEEP_DAYS - 1, 3, .5f), day(-2, 3, .5f))) + "garbage\tline\n")
        assertEquals("bad line skipped", 2, LegibilityStore.decode(file.readText()).size)
        assertTrue(LegibilityStore.decode("v9\n1\t2").isEmpty())
        val s = store(file)
        s.record(report(3, 1f))
        assertEquals(listOf(day0.toEpochDay() - 2, day0.toEpochDay()), s.days().map { it.day })
    }

    @Test fun trendComparesThisWeekWithTheWeeksBefore() {
        val today = day0.toEpochDay()
        val days = listOf(day(-20, 4, .4f), day(-10, 4, .5f), day(-3, 4, .8f), day(0, 4, .9f), day(-1, 7, .9f, n = 1))
        val t = LegibilityStats.trend(days, today)
        assertEquals(10, t.size)
        val four = t[4]
        listOf(.4f, .5f, .8f, .9f).zip(four.days.map { it.score }).forEach { (want, got) -> assertEquals(want, got, 1e-4f) }
        assertEquals(4, four.days.size)
        assertEquals(.85f, four.recent!!, 1e-4f); assertEquals(.45f, four.earlier!!, 1e-4f); assertEquals(.4f, four.change!!, 1e-4f)
        assertEquals(12, four.samples)
        assertEquals(1f, four.consistency!!, 1e-3f)
        assertEquals(Metric.Inside, four.weakestMetric)
        assertNull(t[7].change)
        assertEquals(0, t[2].samples)
        assertTrue("outside the window", LegibilityStats.trend(listOf(day(-40, 1, .2f)), today)[1].days.isEmpty())
    }

    @Test fun warmUpStartsWithTheWeakestAndItsLookAlike() {
        val today = day0.toEpochDay()
        val days = (0..9).map { day(-1, it, .95f) } + day(-2, 4, .3f) + day(-1, 2, .5f) + day(-1, 8, .6f)
        val pick = WarmUp.pick(days, today)
        assertEquals(5, pick.size); assertEquals(5, pick.toSet().size)
        assertEquals("weakest first", 4, pick[0])
        assertEquals("4's look-alike next", 9, pick[1])
        assertEquals(listOf(2, 8), pick.subList(2, 4))
        assertEquals("deterministic", pick, WarmUp.pick(days, today))
    }

    @Test fun warmUpWithoutDataVisitsDifferentDigitsEachDay() {
        val a = WarmUp.pick(emptyList(), day0.toEpochDay())
        val b = WarmUp.pick(emptyList(), day0.toEpochDay() + 1)
        assertEquals(5, a.toSet().size)
        assertEquals(Math.floorMod(day0.toEpochDay(), 10L).toInt(), a[0])
        assertNotEquals(a, b)
        assertTrue("unknown digits come before clearly fine ones",
            WarmUp.pick((0..8).map { day(0, it, 1f) }, day0.toEpochDay()).first() == 9)
    }

    @Test fun clarityPromptOnlyForUnsurePenDigits() {
        val c = ClarityCoach()
        assertNull("sure digits", c.afterAnswer(listOf(WrittenDigit(0, 4, .95f), WrittenDigit(1, 2, .9f))))
        val p = c.afterAnswer(listOf(WrittenDigit(0, 3, .9f), WrittenDigit(1, 9, .45f), WrittenDigit(2, 7, .55f)))
        assertEquals(ClarityPrompt(9, 1, .45f), p)
        assertNull("number pad never prompts", ClarityCoach().afterAnswer(listOf(WrittenDigit(0, 4, null))))
        assertEquals("asked digits count as least sure", 1, ClarityCoach().afterAnswer(listOf(WrittenDigit(0, 1, .2f), WrittenDigit(1, 7, .7f, asked = true)))!!.cell)
    }

    @Test fun clarityPromptDoesNotNag() {
        val c = ClarityCoach(everyAnswers = 3, maxPerSession = 2)
        val unsure = listOf(WrittenDigit(0, 4, .3f))
        val shown = (1..12).map { c.afterAnswer(unsure) != null }
        assertEquals(listOf(true, false, false, true) + List(8) { false }, shown)
        assertEquals(2, c.shown)
    }

    @Test fun numberPadRowGivesDigitsWithoutConfidence() {
        val row = DigitRowState(3).apply { mode = InputMode.NumberPad; type(4); type(2) }
        val digits = row.writtenDigits()
        assertEquals(listOf(4, 2), digits.map { it.digit })
        assertTrue(digits.all { it.confidence == null && it.strokes.isEmpty() })
        assertNull(ClarityCoach().afterAnswer(digits))
        assertTrue("nothing to measure without ink", digits.legibility().isEmpty())
    }

    @Test fun answerInkIsKeptPerAttemptAndPruned() = kotlinx.coroutines.runBlocking {
        val ink = fi.skillit.ink.AttemptInkStore(File(tmp.root, "attempts"), clock = { now++ })
        val env = CoachEnvironment(store(), null, io = kotlinx.coroutines.Dispatchers.Unconfined, compute = kotlinx.coroutines.Dispatchers.Unconfined, ink = ink)
        val f = Fixtures["neat-3"]
        val digit = listOf(WrittenDigit(0, 3, .9f, strokes = f.strokes, boxWidth = f.boxWidth, boxHeight = f.boxHeight))
        repeat(fi.skillit.ink.AttemptInkStore.KEEP_ATTEMPTS + 5) { assertTrue(env.recordAnswer(digit, attemptKey = "attempt-$it")) }
        val kept = ink.all()
        assertEquals(fi.skillit.ink.AttemptInkStore.KEEP_ATTEMPTS, kept.size)
        assertEquals("newest kept", "attempt-${fi.skillit.ink.AttemptInkStore.KEEP_ATTEMPTS + 4}", kept.last().key)
        assertNull("oldest pruned", ink.load("attempt-0"))
        assertTrue("pad answers store no ink", env.recordAnswer(listOf(WrittenDigit(0, 3, null)), attemptKey = "pad"))
        assertNull(ink.load("pad"))
    }

    @Test fun metricWorkRunsOnTheComputeDispatcher() = kotlinx.coroutines.runBlocking {
        var dispatched = 0
        val counting = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun isDispatchNeeded(context: kotlin.coroutines.CoroutineContext) = true
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { dispatched++; block.run() }
        }
        val env = CoachEnvironment(store(), null, io = kotlinx.coroutines.Dispatchers.Unconfined, compute = counting)
        val f = Fixtures["neat-4"]
        val pad = CoachPad().apply { strokes = f.strokes; boxWidth = f.boxWidth; boxHeight = f.boxHeight }
        assertNotNull(env.measure(4, pad)); val afterMeasure = dispatched
        assertTrue(afterMeasure > 0)
        env.trace(DigitGuides.of(4), pad.unitStrokes); assertTrue(dispatched > afterMeasure)
        val beforeAnswer = dispatched
        env.recordAnswer(listOf(WrittenDigit(0, 4, .9f, strokes = f.strokes, boxWidth = f.boxWidth, boxHeight = f.boxHeight)))
        assertTrue(dispatched > beforeAnswer)
    }

    @Test fun answerDigitsWithInkAreMeasured() {
        val f = Fixtures["neat-4"]
        val reports = listOf(WrittenDigit(0, 4, .9f, strokes = f.strokes, boxWidth = f.boxWidth, boxHeight = f.boxHeight), WrittenDigit(1, 2, .9f)).legibility()
        assertEquals(1, reports.size)
        assertEquals(4, reports[0].digit)
        assertEquals(1f, reports[0].scores.getValue(Metric.Confidence), 0f)
    }
}
