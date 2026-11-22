package fi.skillit.coach

import fi.skillit.ink.InkSampleCodec
import fi.skillit.ink.InkStroke
import org.junit.Assert.*
import org.junit.Test

/** One synthetic “recorded” digit from scripts/make-coach-fixtures.py. Not real handwriting. */
data class Fixture(val name: String, val digit: Int, val boxWidth: Float, val boxHeight: Float, val strokes: List<InkStroke>) {
    fun report(confidence: Float? = null) = Legibility.measure(digit, strokes, boxWidth, boxHeight, confidence)!!
}

object Fixtures {
    val all: Map<String, Fixture> by lazy {
        val text = Fixtures::class.java.getResource("/coach/digit-fixtures.v1.tsv")!!.readText()
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val f = line.split("\t")
            f[0] to Fixture(f[0], f[1].toInt(), f[2].toFloat(), f[3].toFloat(), InkSampleCodec.decodeStrokes(f[4]))
        }
    }
    operator fun get(name: String) = all.getValue(name)
}

class LegibilityTest {
    private fun table() = Fixtures.all.values.joinToString("\n") { f ->
        val r = f.report()
        "%-16s %s overall=%.2f h=%.2f bottom=%.2f closures=%s".format(f.name, r.scores.map { (m, v) -> "${m.name}=%.2f".format(v) }, r.overall, r.height, r.bottom,
            r.closures.map { "g%.2f@(%.2f,%.2f)c%.2f".format(it.gap, it.x, it.y, it.loopCenterY) })
    }

    @Test fun neatDigitsNeedNoTip() {
        (0..9).forEach { d ->
            val r = Fixtures["neat-$d"].report()
            assertTrue("neat-$d should be clear: ${r.scores} \n${table()}", r.overall >= 0.9f)
            assertNull("neat-$d tip: ${r.tip} ${r.scores}", r.tip)
        }
    }

    @Test fun theStudioModelsPassTheirOwnMetrics() {
        DigitGuides.all.forEach { g ->
            val r = Legibility.measureUnit(g.digit, g.strokes)!!
            assertTrue("guide ${g.digit}: ${r.scores} ${r.closures}", r.overall >= 0.95f)
            assertNull("guide ${g.digit}: ${r.tip}", r.tip)
        }
    }

    @Test fun inkOutsideTheBoxIsSeen() {
        val r = Fixtures["spill-3"].report()
        assertTrue(r.scores.getValue(Metric.Inside) < 0.75f)
        assertEquals(Metric.Inside, r.weakest)
        assertTrue(r.tip!!.contains("ruudun sisällä"))
    }

    @Test fun sizeTipsSayWhichWay() {
        val tiny = Fixtures["tiny-2"].report()
        assertEquals(Metric.Size, tiny.weakest)
        assertTrue(tiny.tip!!.contains("isommin"))
        val huge = Fixtures["huge-7"].report()
        assertTrue(huge.scores.getValue(Metric.Size) < 0.75f)
        assertTrue(huge.scores.getValue(Metric.Inside) < 1f)
        assertEquals("Vähän pienemmin, niin numero mahtuu ruutuun.", huge.copy(scores = mapOf(Metric.Size to 0f)).tip)
    }

    @Test fun floatingDigitMissesTheBaseline() {
        val r = Fixtures["float-5"].report()
        assertEquals(Metric.Baseline, r.weakest)
        assertTrue(r.tip!!.contains("alaviivalla"))
        assertEquals(1f, Fixtures["neat-5"].report().scores.getValue(Metric.Baseline), 0.01f)
    }

    @Test fun openLoopsAreFound() {
        listOf("open-0", "open-6", "open-9").forEach { n ->
            val r = Fixtures[n].report()
            assertTrue("$n loops ${r.scores}", r.scores.getValue(Metric.Loops) < 0.75f)
            assertNotNull(r.tip)
        }
        val eight = Fixtures["open-8-top"].report().scores.getValue(Metric.Loops)
        assertTrue("one of two loops open: $eight", eight in 0.4f..0.75f)
        (listOf(0, 6, 8, 9)).forEach { assertEquals("neat-$it", 1f, Fixtures["neat-$it"].report().scores.getValue(Metric.Loops), 0.01f) }
        assertNull("no loop metric for 7", Fixtures["neat-7"].report().scores[Metric.Loops])
    }

    @Test fun confusedPairsAreTold() {
        mapOf("closed-4" to "nelosen yläpää auki", "roof-1" to "Ykkönen on kapea", "short-7" to "pitkä katto",
            "closed-3" to "kolmosen vasen puoli auki", "closed-5" to "avoin vatsa").forEach { (n, tip) ->
            val r = Fixtures[n].report()
            assertTrue("$n distinct ${r.scores} ${r.closures}", r.scores.getValue(Metric.Distinct) < 0.6f)
            assertEquals(n, Metric.Distinct, r.weakest)
            assertTrue("$n: ${r.tip}", r.tip!!.contains(tip))
        }
        listOf(1, 3, 4, 5, 6, 7, 9).forEach { assertTrue("neat-$it", Fixtures["neat-$it"].report().scores.getValue(Metric.Distinct) >= 0.9f) }
        assertEquals(setOf(4 to 9, 1 to 7, 3 to 8, 5 to 6), Legibility.confusedPairs.toSet())
        assertEquals(9, Legibility.partnerOf(4)); assertEquals(1, Legibility.partnerOf(7)); assertNull(Legibility.partnerOf(2))
    }

    @Test fun aCorrectionWrittenOverAnOldDigitIsSeen() {
        val messy = Fixtures["over-written-8"].report()
        assertTrue(messy.overall < Fixtures["neat-8"].report().overall)
        assertEquals(Metric.Single, messy.weakest)
        assertTrue(messy.tip!!.contains("pyyhi vanha numero"))
        (0..9).forEach { assertEquals("neat-$it", 1f, Fixtures["neat-$it"].report().scores.getValue(Metric.Single), 0.01f) }
    }

    @Test fun recogniserConfidenceIsOneMetricAmongOthers() {
        val f = Fixtures["neat-4"]
        assertNull(f.report().scores[Metric.Confidence])
        val unsure = f.report(confidence = 0.3f)
        assertTrue(unsure.scores.getValue(Metric.Confidence) < 0.3f)
        assertEquals(Metric.Confidence, unsure.weakest)
        assertEquals(1f, f.report(confidence = 0.95f).scores.getValue(Metric.Confidence), 0f)
    }

    @Test fun sizeConsistencyAcrossWritings() {
        assertNull(Legibility.sizeConsistency(listOf(.6f)))
        assertEquals(1f, Legibility.sizeConsistency(listOf(.6f, .6f, .6f))!!, 1e-4f)
        assertTrue(Legibility.sizeConsistency(listOf(.3f, .8f, .5f, .9f))!! < 0.3f)
        val neat = (0..9).map { Fixtures["neat-$it"].report().height }
        assertTrue("neat set is even: $neat", Legibility.sizeConsistency(neat)!! > 0.8f)
    }

    @Test fun emptyBoxHasNoReport() {
        assertNull(Legibility.measure(3, emptyList(), 100f, 100f))
    }

    @Test fun tracingFollowsTheModel() {
        val g = DigitGuides.of(2)
        val exact = Trace.score(g, g.strokes)!!
        assertTrue(exact.score > 0.95f); assertTrue(exact.startedAtDot && exact.rightDirection); assertNull(exact.tip)
        val reversed = Trace.score(g, g.strokes.map { it.reversed() })!!
        assertFalse(reversed.rightDirection)
        assertTrue(reversed.tip!!.contains("vihreästä pisteestä"))
        val off = Trace.score(g, g.strokes.map { s -> s.map { UnitPoint(it.x + .2f, it.y) } })!!
        assertTrue(off.score < 0.6f)
        val half = Trace.score(g, listOf(g.strokes[0].take(g.strokes[0].size / 3)))!!
        assertTrue(half.tip!!.contains("loppuun"))
        assertNull(Trace.score(g, emptyList()))
    }

    @Test fun everyDigitHasAGuideStartingAtTheTop() {
        assertEquals((0..9).toList(), DigitGuides.all.map { it.digit })
        DigitGuides.all.forEach { g -> assertTrue("${g.digit} starts high: ${g.start}", g.start.y < 0.5f) }
        assertEquals("4 and 5 are two strokes", listOf(2, 2), listOf(4, 5).map { DigitGuides.of(it).strokes.size })
    }
}
