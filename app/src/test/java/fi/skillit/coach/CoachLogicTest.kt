package fi.skillit.coach

import fi.skillit.ink.DigitReading
import kotlinx.coroutines.async
import fi.skillit.ink.DigitRowState
import fi.skillit.ink.DigitScore
import fi.skillit.ink.InkPoint
import fi.skillit.ink.InkStroke
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

    private fun ink(vararg xy: Float) = listOf(InkStroke(xy.toList().chunked(2).mapIndexed { i, (x, y) -> InkPoint(x, y, i.toLong()) }))
    private fun accepted(d: Int, c: Float) = DigitReading.Accepted(d, c, listOf(DigitScore(d, c)))

    @Test fun penRowsReportOnlyNewInkWithOffsetsAndAsks() {
        // Item "q1": a 2-cell row "a" and a 1-cell row "b" (sorted by cell id); item "q2" must be ignored.
        val a = RowInk(true, listOf(3, 6), mapOf(0 to accepted(3, .45f), 1 to DigitReading.Ask(4, 6, listOf(DigitScore(6, .5f), DigitScore(4, .4f)))),
            mapOf(0 to ink(1f, 1f, 5f, 5f), 1 to ink(2f, 2f, 6f, 6f)), 72f, 72f)
        val b = RowInk(true, listOf(7), mapOf(0 to accepted(7, .9f)), mapOf(0 to ink(3f, 3f, 7f, 7f)), 48f, 48f)
        val other = RowInk(true, listOf(1), mapOf(0 to accepted(1, .1f)), mapOf(0 to ink(0f, 0f, 1f, 9f)), 72f, 72f)
        val reported = mutableMapOf<Pair<String, String>, Map<Int, List<InkStroke>>>()
        val rows = mapOf(("q1" to "b") to b, ("q1" to "a") to a, ("q2" to "a") to other)
        val first = writtenSince("q1", rows, reported)
        assertEquals(listOf(0 to 3, 1 to 6, 2 to 7), first.map { it.cell to it.digit })
        assertEquals(listOf(.45f, .5f, .9f), first.map { it.confidence })
        assertEquals(listOf(false, true, false), first.map { it.asked })
        assertEquals(listOf(72f, 72f, 48f), first.map { it.boxWidth })
        assertEquals(9, ClarityCoach().afterAnswer(first)?.let { 9 })
        // Retry: nothing rewritten → nothing to measure, store or prompt again.
        assertTrue(writtenSince("q1", rows, reported).isEmpty())
        // Retry where only the 6 was rewritten.
        val fixed = a.copy(digits = listOf(3, 5), readings = mapOf(0 to accepted(3, .45f), 1 to accepted(5, .8f)), strokes = a.strokes + (1 to ink(9f, 9f, 2f, 2f)))
        val retry = writtenSince("q1", rows + (("q1" to "a") to fixed), reported)
        assertEquals(listOf(1 to 5), retry.map { it.cell to it.digit })
        assertEquals("other item untouched", listOf(1), writtenSince("q2", rows, reported).map { it.digit })
    }

    @Test fun padRowsGiveDigitsWithoutInk() {
        val pad = RowInk(false, listOf(null, 4))
        assertEquals(listOf(WrittenDigit(1, 4, null)), writtenSince("q", mapOf(("q" to "a") to pad), mutableMapOf()))
    }

    @Test fun aSaveStartedBeforeAResetIsDroppedOrWiped() = kotlinx.coroutines.runBlocking {
        val dir = File(tmp.root, "race")
        val legibility = LegibilityStore(File(dir, "legibility.v1.tsv"), { now }, { ZoneId.of("UTC") })
        val inWrite = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val ink = fi.skillit.ink.AttemptInkStore(File(dir, "attempts"), writer = { f, text -> inWrite.countDown(); release.await(); f.writeText(text) })
        val env = CoachEnvironment(legibility, null, io = kotlinx.coroutines.Dispatchers.IO, compute = kotlinx.coroutines.Dispatchers.Unconfined, ink = ink)
        val f = Fixtures["neat-3"]
        val digit = listOf(WrittenDigit(0, 3, .9f, strokes = f.strokes, boxWidth = f.boxWidth, boxHeight = f.boxHeight))
        fun wipe() = HandwritingData.reset { legibility.deleteAll(); ink.deleteAll() }
        fun empty() = legibility.days().isEmpty() && ink.all().isEmpty()

        // 1) The save is mid-write when the parent resets: the reset waits for it, then wipes it.
        val save = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async { env.recordAnswer(digit, "a1", HandwritingData.generation) }
        assertTrue(inWrite.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val reset = Thread { wipe() }.apply { start() }
        Thread.sleep(100)
        assertTrue("reset waits for the save in progress", reset.isAlive)
        release.countDown(); save.await(); reset.join(5_000)
        assertTrue("nothing left after reset: ${legibility.days()} ${ink.all().map { it.key }}", empty())

        // 2) Answer checked (generation taken), reset, then the save runs: dropped.
        val generation = HandwritingData.generation
        wipe()
        env.recordAnswer(digit, "a2", generation)
        assertTrue("stale save dropped", empty())

        // A save after the reset is kept.
        env.recordAnswer(digit, "a3", HandwritingData.generation)
        assertEquals(listOf("a3"), ink.all().map { it.key })
        assertEquals(1, legibility.days().single().count)
    }

    @Test fun answerDigitsWithInkAreMeasured() {
        val f = Fixtures["neat-4"]
        val reports = listOf(WrittenDigit(0, 4, .9f, strokes = f.strokes, boxWidth = f.boxWidth, boxHeight = f.boxHeight), WrittenDigit(1, 2, .9f)).legibility()
        assertEquals(1, reports.size)
        assertEquals(4, reports[0].digit)
        assertEquals(1f, reports[0].scores.getValue(Metric.Confidence), 0f)
    }
}
