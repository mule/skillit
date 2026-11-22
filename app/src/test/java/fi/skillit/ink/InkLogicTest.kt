package fi.skillit.ink

import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test

class InkLogicTest {
    private fun stroke(vararg xy: Pair<Float, Float>) = InkStroke(xy.mapIndexed { i, (x, y) -> InkPoint(x, y, i * 8L, .5f) })
    private val row = BoxRow(count = 3, boxWidth = 100f, boxHeight = 100f)

    @Test fun strokeGoesToBoxHoldingMostOfIt() {
        // A "4" crossbar that starts in box 0 and pokes 10 px into box 1 stays in box 0.
        assertEquals(0, BoxSegmentation.boxOf(stroke(20f to 60f, 50f to 60f, 80f to 60f, 95f to 60f, 110f to 60f), row))
        assertEquals(2, BoxSegmentation.boxOf(stroke(250f to 10f, 250f to 90f), row))
    }

    @Test fun pointsOutsideTheRowClampToTheEdgeBoxes() {
        assertEquals(0, row.nearestBox(-40f))
        assertEquals(2, row.nearestBox(900f))
    }

    @Test fun gapsSplitToTheNearerBox() {
        val gapped = BoxRow(2, 100f, 100f, gap = 20f)
        assertEquals(0, gapped.nearestBox(105f))
        assertEquals(1, gapped.nearestBox(115f))
        assertEquals(120f, gapped.boxLeft(1))
    }

    @Test fun segmentationGroupsStrokesPerBoxInWritingOrder() {
        val a = stroke(10f to 10f, 10f to 90f)
        val b = stroke(150f to 10f, 150f to 90f)
        val c = stroke(30f to 50f, 70f to 50f)
        val boxes = BoxSegmentation.segment(listOf(a, b, c, InkStroke(emptyList())), row)
        assertEquals(listOf(0, 1), boxes.keys.toList())
        assertEquals(listOf(a, c), boxes[0])
    }

    @Test fun onlyDigitsSurviveFiltering() {
        assertEquals(7 to false, DigitRules.digitOf("7"))
        assertEquals(7 to false, DigitRules.digitOf(" 7 "))
        assertEquals(1 to true, DigitRules.digitOf("l"))
        assertEquals(9 to true, DigitRules.digitOf("g"))
        assertNull(DigitRules.digitOf("47"))
        assertNull(DigitRules.digitOf("x"))
        assertNull(DigitRules.digitOf(""))
        val ranked = DigitRules.rank(listOf(RawCandidate("x"), RawCandidate("4"), RawCandidate("ab"), RawCandidate("9")))
        assertEquals(listOf(4, 9), ranked.map { it.digit })
    }

    @Test fun clearDigitIsAccepted() {
        val r = DigitRules.read(listOf(RawCandidate("4", 0.1f), RawCandidate("9", 3f), RawCandidate("A", 4f)))
        assertTrue(r is DigitReading.Accepted)
        assertEquals(4, (r as DigitReading.Accepted).digit)
        assertTrue(r.confidence > 0.9f)
    }

    @Test fun closeFourAndNineAsksTheChild() {
        val r = DigitRules.read(listOf(RawCandidate("4", 1.0f), RawCandidate("9", 1.3f)))
        assertTrue(r is DigitReading.Ask)
        assertEquals("Tarkoititko 4 vai 9?", (r as DigitReading.Ask).question)
    }

    @Test fun confusablePairsNeedAWiderMargin() {
        // Margin ≈ 0.34: enough for 3 vs 8, not for 1 vs 7.
        val gap = 0.7f
        assertTrue(DigitRules.read(listOf(RawCandidate("3", 0f), RawCandidate("8", gap))) is DigitReading.Accepted)
        val r = DigitRules.read(listOf(RawCandidate("7", 0f), RawCandidate("1", gap)))
        assertEquals("Tarkoititko 1 vai 7?", (r as DigitReading.Ask).question)
    }

    @Test fun lettersOnlyMeansWriteAgain() {
        assertTrue(DigitRules.read(listOf(RawCandidate("x"), RawCandidate("y"))) is DigitReading.Unreadable)
        assertTrue(DigitRules.read(emptyList()) is DigitReading.Unreadable)
    }

    @Test fun unscoredCandidatesUseRank() {
        val r = DigitRules.read(listOf(RawCandidate("5"), RawCandidate("x"), RawCandidate("y")))
        assertEquals(5, (r as DigitReading.Accepted).digit)
    }

    @Test fun penPolicyFallsBackToFingerUntilAPenAppears() {
        val p = PenPolicy()
        assertTrue(p.accepts(MotionEvent.TOOL_TYPE_FINGER))
        p.onHover(MotionEvent.TOOL_TYPE_STYLUS)
        assertFalse("palm/finger ignored once the pen is near", p.accepts(MotionEvent.TOOL_TYPE_FINGER))
        assertTrue(p.accepts(MotionEvent.TOOL_TYPE_STYLUS))
        p.forgetPen()
        assertTrue(p.accepts(MotionEvent.TOOL_TYPE_FINGER))
        assertFalse(PenPolicy().accepts(MotionEvent.TOOL_TYPE_MOUSE))
    }

    @Test fun sideButtonAndEraserEndErase() {
        val p = PenPolicy()
        assertTrue(p.isEraser(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertTrue(p.isEraser(MotionEvent.TOOL_TYPE_ERASER, 0))
        assertFalse(p.isEraser(MotionEvent.TOOL_TYPE_STYLUS, 0))
        assertFalse(p.isEraser(MotionEvent.TOOL_TYPE_FINGER, MotionEvent.BUTTON_STYLUS_PRIMARY))
    }

    @Test fun systemPalmCancelDiscardsTheStroke() {
        val p = PenPolicy()
        assertTrue(p.isCanceled(MotionEvent.ACTION_CANCEL, 0))
        assertTrue(p.isCanceled(MotionEvent.ACTION_POINTER_UP, PenPolicy.FLAG_CANCELED))
        assertFalse(p.isCanceled(MotionEvent.ACTION_UP, 0))
    }

    @Test fun eraserHitsSegmentsNotJustPoints() {
        val s = stroke(0f to 0f, 100f to 0f)
        assertTrue(StrokeEraser.hits(s, 50f, 5f, 6f))
        assertFalse(StrokeEraser.hits(s, 50f, 20f, 6f))
        assertTrue(StrokeEraser.hits(stroke(10f to 10f), 12f, 12f, 3f))
    }

    @Test fun samplesRoundTripThroughTheLineFormat() {
        val s = InkSample(4, listOf(stroke(1.5f to 2f, 3f to 4.25f), stroke(9f to 9f)), listOf(RawCandidate("4", 0.5f), RawCandidate("a:b,", null)), "en-US")
        assertEquals(s, InkSampleCodec.decode(InkSampleCodec.encode(s)))
        assertNull(InkSampleCodec.decode("garbage"))
        assertNull(InkSampleCodec.decode("v1\t12\tx\t\t"))
    }

    @Test fun reportCountsAccuracyAndTheFourNineMixUp() {
        fun sample(label: Int, vararg c: String) = InkSample(label, emptyList(), c.map { RawCandidate(it) }, "en-US")
        val report = AccuracyReport.of(listOf(
            sample(4, "4"), sample(4, "9"), sample(4, "4", "9"), sample(9, "9"), sample(1, "x"),
        ))
        val four = report.perDigit[4]
        assertEquals(3, four.samples)
        assertEquals(1, four.correct)
        assertEquals(1, four.wrongAccepted)
        assertEquals(1, four.asked)
        assertEquals(1, four.askedIncludingLabel)
        assertEquals(1, report.confused(4, 9))
        assertEquals(1, report.perDigit[1].unreadable)
        assertEquals(5, report.samples)
        assertEquals(0.4f, report.accuracy, 1e-6f)
        assertTrue(report.summary().contains("4→9 1"))
    }
}
