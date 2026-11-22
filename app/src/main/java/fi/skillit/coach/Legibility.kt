package fi.skillit.coach

import fi.skillit.ink.InkStroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What the coach looks at in one written digit. Every score is 0..1, higher is clearer. */
enum class Metric(val label: String) {
    Inside("Ruudun sisällä"),
    Size("Sopiva koko"),
    Baseline("Alaviivalla"),
    Loops("Suljetut renkaat"),
    Distinct("Erottuu parista"),
    /** One digit, not a correction written over an old one. */
    Single("Ei päällekirjoitusta"),
    Confidence("Tabletti tunnisti"),
}

/**
 * Where a path comes back to itself (a loop is closed, or nearly). [gap] is the remaining opening in units
 * of the digit's size (0 = touching or crossing). [x], [y] and [loopCenterY] are relative to the digit's
 * bounding box (0 = top/left, 1 = bottom/right); [loopCenterY] is the middle of the enclosed part.
 */
data class Closure(val gap: Float, val x: Float, val y: Float, val loopCenterY: Float)

/**
 * The legibility of one written digit. Only these numbers (never the strokes) may leave the coach:
 * [LegibilityStore] keeps per-day aggregates of them, which are the only handwriting data that may ever sync.
 */
data class LegibilityReport(
    val digit: Int,
    val scores: Map<Metric, Float>,
    /** Height of the digit as a share of the box height. */
    val height: Float,
    /** Lowest ink as a share of the box height; the baseline is [WritingLines.BASELINE]. */
    val bottom: Float,
    val closures: List<Closure>,
) {
    val overall: Float get() = scores.values.average().toFloat()
    /** The one thing worth a tip, or null when everything looks clear. */
    val weakest: Metric? get() = scores.filterValues { it < TIP_BELOW }.minByOrNull { it.value }?.key

    /** One gentle, concrete tip for [weakest], in Finnish. */
    val tip: String? get() = when (weakest) {
        null -> null
        Metric.Inside -> "Yritä pitää numero kokonaan ruudun sisällä."
        Metric.Size -> if (height < IDEAL_HEIGHT.start) "Kirjoita vähän isommin, niin numero näkyy hyvin."
            else "Vähän pienemmin, niin numero mahtuu ruutuun."
        Metric.Baseline -> if (bottom < WritingLines.BASELINE) "Anna numeron istua alaviivalla."
            else "Numero saa jäädä alaviivan päälle."
        Metric.Loops -> when (digit) {
            0 -> "Sulje nollan rengas."
            6 -> "Sulje kuutosen alarengas."
            8 -> "Sulje kahdeksikon molemmat renkaat."
            else -> "Sulje ysin ylärengas, niin se ei näytä neloselta."
        }
        Metric.Distinct -> when (digit) {
            1 -> "Ykkönen on kapea: pieni hattu ja suora viiva alas, niin se ei näytä seiskalta."
            7 -> "Seiskalla on pitkä katto, niin sen erottaa ykkösestä."
            3 -> "Jätä kolmosen vasen puoli auki, niin se ei näytä kahdeksikolta."
            4 -> "Jätä nelosen yläpää auki, niin se ei näytä ysiltä."
            5 -> "Viitosella on suora hattu ja avoin vatsa, niin se ei näytä kuutoselta."
            6 -> "Tee kuutosen rengas alas ja sulje se, niin se ei näytä viitoselta."
            9 -> "Sulje ysin rengas ylhäällä, niin se ei näytä neloselta."
            else -> null
        }
        Metric.Single -> "Jos haluat korjata, pyyhi vanha numero ensin (Pyyhi-nappi tai kynän sivunappi)."
        Metric.Confidence -> "Tabletti ei ollut ihan varma tästä. Kirjoita rauhassa ja selvästi."
    }

    companion object {
        const val TIP_BELOW = 0.75f
        val IDEAL_HEIGHT = 0.5f..0.85f
    }
}

/**
 * Pure legibility metrics over a digit's strokes. All functions take strokes relative to the box's
 * top-left corner (as [fi.skillit.ink.DigitRowState.strokesByCell] gives them) and the box size in the
 * same units, so they work for answer cells, carry cells and the studio's big box alike.
 */
object Legibility {
    /** Digits with closed loops, and how many. */
    val expectedLoops = mapOf(0 to 1, 6 to 1, 8 to 2, 9 to 1)
    /** Pairs that are easy to mix up, in the workbook and for the recogniser. */
    val confusedPairs = listOf(4 to 9, 1 to 7, 3 to 8, 5 to 6)
    fun partnerOf(digit: Int): Int? = confusedPairs.firstNotNullOfOrNull { (a, b) -> if (a == digit) b else if (b == digit) a else null }

    fun toUnit(strokes: List<InkStroke>, boxWidth: Float, boxHeight: Float): List<List<UnitPoint>> {
        require(boxWidth > 0f && boxHeight > 0f)
        return strokes.filter { it.points.isNotEmpty() }.map { s -> s.points.map { UnitPoint(it.x / boxWidth, it.y / boxHeight) } }
    }

    /**
     * Measures one digit. [confidence] is the recogniser's share (0..1) for [digit], when there was a reading.
     * Returns null for an empty box.
     */
    fun measure(digit: Int, strokes: List<InkStroke>, boxWidth: Float, boxHeight: Float, confidence: Float? = null): LegibilityReport? =
        measureUnit(digit, toUnit(strokes, boxWidth, boxHeight), confidence)

    fun measureUnit(digit: Int, strokes: List<List<UnitPoint>>, confidence: Float? = null): LegibilityReport? {
        require(digit in 0..9)
        val points = strokes.flatten()
        if (points.isEmpty()) return null
        val box = Bounds.of(points)
        val closures = closures(strokes)
        val scores = linkedMapOf<Metric, Float>()
        scores[Metric.Inside] = insideScore(points)
        scores[Metric.Size] = sizeScore(box.height)
        scores[Metric.Baseline] = baselineScore(box.maxY)
        loopScore(digit, closures)?.let { scores[Metric.Loops] = it }
        distinctScore(digit, strokes, box, closures)?.let { scores[Metric.Distinct] = it }
        scores[Metric.Single] = singleScore(digit, strokes, box)
        confidence?.let { scores[Metric.Confidence] = ramp(it, 0.2f, 0.8f) }
        return LegibilityReport(digit, scores, box.height, box.maxY, closures)
    }

    /** Share of the ink inside the box, sharpened: a small spill already shows. */
    fun insideFraction(points: List<UnitPoint>): Float =
        points.count { it.x in 0f..1f && it.y in 0f..1f }.toFloat() / points.size
    internal fun insideScore(points: List<UnitPoint>) = ramp(insideFraction(points), 0.7f, 0.98f)

    internal fun sizeScore(height: Float): Float = when {
        height < LegibilityReport.IDEAL_HEIGHT.start -> ramp(height, 0.2f, LegibilityReport.IDEAL_HEIGHT.start)
        height > LegibilityReport.IDEAL_HEIGHT.endInclusive -> 1f - ramp(height, LegibilityReport.IDEAL_HEIGHT.endInclusive, 1.1f)
        else -> 1f
    }

    internal fun baselineScore(bottom: Float): Float = 1f - ramp(abs(bottom - WritingLines.BASELINE), 0.08f, 0.3f)

    /**
     * Size consistency across several writings of digits (heights as from [LegibilityReport.height]):
     * 1 when all are the same size, 0 when they vary by 30 % or more (coefficient of variation).
     */
    fun sizeConsistency(heights: List<Float>): Float? {
        if (heights.size < 2) return null
        val mean = heights.average()
        if (mean <= 0.0) return 0f
        val sd = sqrt(heights.sumOf { (it - mean) * (it - mean) } / heights.size)
        return 1f - ramp((sd / mean).toFloat(), 0f, 0.3f)
    }

    private fun closeness(gap: Float) = 1f - ramp(gap, 0.06f, 0.3f)

    internal fun loopScore(digit: Int, closures: List<Closure>): Float? {
        val expected = expectedLoops[digit] ?: return null
        val fitting = when (digit) {
            6 -> closures.filter { it.loopCenterY > 0.45f }
            9 -> closures.filter { it.loopCenterY < 0.6f }
            else -> closures
        }.sortedBy { it.gap }
        return (0 until expected).sumOf { i -> fitting.getOrNull(i)?.let { closeness(it.gap).toDouble() } ?: 0.0 }.toFloat() / expected
    }

    /** Share of the digit's height that the ink spans horizontally in its top fifth: a "roof" or "hat". */
    internal fun roof(strokes: List<List<UnitPoint>>, box: Bounds): Float {
        val top = strokes.flatten().filter { it.y <= box.minY + 0.2f * box.height }
        if (top.isEmpty() || box.height <= 0f) return 0f
        return (top.maxOf { it.x } - top.minOf { it.x }) / box.height
    }

    /**
     * Ink length against the model digit's, both relative to their height. A correction written on top of an
     * old digit (seen in the workbook) has far more ink than one digit needs.
     */
    internal fun singleScore(digit: Int, strokes: List<List<UnitPoint>>, box: Bounds): Float {
        if (box.height <= 0f) return 1f
        val step = max(box.width, box.height) / 30f
        val ink = strokes.sumOf { pathLength(resample(it, step)).toDouble() }.toFloat() / box.height
        val model = guideLength.getValue(digit)
        return 1f - ramp(ink / model, 1.45f, 2.1f)
    }

    private fun pathLength(p: List<UnitPoint>) = p.zipWithNext().sumOf { (a, b) -> dist(a, b).toDouble() }.toFloat()
    private val guideLength: Map<Int, Float> by lazy {
        DigitGuides.all.associate { g -> g.digit to g.strokes.sumOf { pathLength(it).toDouble() }.toFloat() / Bounds.of(g.strokes.flatten()).height }
    }

    /** How clearly the digit differs from the one it is most often mixed up with. Null for digits without a pair. */
    internal fun distinctScore(digit: Int, strokes: List<List<UnitPoint>>, box: Bounds, closures: List<Closure>): Float? {
        val tight = closures.filter { it.gap <= 0.15f }
        return when (digit) {
            // An open top: a closed one reads as 9.
            4 -> if (tight.any { it.y < 0.7f }) 0.2f else 1f
            9 -> closures.filter { it.loopCenterY < 0.6f }.maxOfOrNull { closeness(it.gap) } ?: 0f
            // Narrow, with a small hat; a long roof reads as 7.
            1 -> min(1f - ramp(box.width / max(box.height, 1e-3f), 0.45f, 0.85f), 1f - ramp(roof(strokes, box), 0.35f, 0.6f))
            7 -> ramp(roof(strokes, box), 0.15f, 0.45f)
            // Open on the left: closed loops read as 8.
            3 -> max(0f, 1f - 0.5f * tight.size)
            // A flat hat and an open belly; a closed belly reads as 6.
            5 -> (ramp(roof(strokes, box), 0.1f, 0.35f) + if (tight.any { it.y > 0.4f }) 0f else 1f) / 2f
            6 -> closures.filter { it.loopCenterY > 0.45f }.maxOfOrNull { closeness(it.gap) } ?: 0f
            else -> null
        }
    }

    /**
     * Finds the places where the pen path returns to itself. The strokes are joined in writing order (pen
     * lifts are not drawn, but still count as "the path went on"), so a 4 whose last stroke closes the top
     * counts as closed. A return only counts when the part in between is long enough and encloses an area,
     * so retracing a line (a 1's hat) is not a loop.
     */
    fun closures(strokes: List<List<UnitPoint>>): List<Closure> {
        val all = strokes.flatten()
        if (all.size < 2) return emptyList()
        val box = Bounds.of(all)
        val size = max(max(box.width, box.height), 1e-3f)
        val step = size / 30f
        // Resampled path: point, stroke id and arc length along the whole path (pen lifts add nothing).
        val path = mutableListOf<UnitPoint>(); val strokeOf = mutableListOf<Int>(); val arc = mutableListOf<Float>()
        var length = 0f
        strokes.map { resample(it, step) }.forEachIndexed { s, pts ->
            pts.forEachIndexed { i, p ->
                if (i > 0) length += dist(pts[i - 1], p)
                path += p; strokeOf += s; arc += length
            }
        }
        val segments = (0 until path.size - 1).filter { strokeOf[it] == strokeOf[it + 1] }
        fun closure(d: Float, mx: Float, my: Float, loop: Bounds) =
            Closure(d / size, rel(mx, box.minX, box.width), rel(my, box.minY, box.height), rel((loop.minY + loop.maxY) / 2f, box.minY, box.height))
        // Within one stroke: the pen came back to where it was.
        val within = mutableListOf<Closure>()
        // Between two strokes: places where they touch, per stroke pair.
        val contacts = HashMap<Pair<Int, Int>, MutableList<Closure>>()
        for (a in segments.indices) for (b in a + 1 until segments.size) {
            val i = segments[a]; val j = segments[b]
            val same = strokeOf[i] == strokeOf[j]
            if (same && arc[j] - arc[i + 1] < 0.6f * size) continue
            val (d, mx, my) = segmentDistance(path[i], path[i + 1], path[j], path[j + 1])
            if (d / size > MAX_GAP) continue
            if (same) {
                val loop = Bounds.of(path.subList(i + 1, j + 1))
                if (loop.width < 0.25f * size || loop.height < 0.25f * size) continue
                within += closure(d, mx, my, loop)
            } else {
                contacts.getOrPut(strokeOf[i] to strokeOf[j]) { mutableListOf() } += closure(d, mx, my, Bounds.of(listOf(path[i], path[j])))
            }
        }
        fun distinctPlaces(cs: List<Closure>): List<Closure> {
            val kept = mutableListOf<Closure>()
            cs.sortedBy { it.gap }.forEach { c -> if (kept.none { hypot((it.x - c.x) * box.width, (it.y - c.y) * box.height) < 0.25f * size }) kept += c }
            return kept
        }
        // Two strokes that cross or touch once (a 4's crossbar and stem, a 5's hat) enclose nothing. Touching in
        // two places closes a loop between them (a 4 whose stem starts where its slant began): every contact
        // after the first closes one loop, and the loosest of each pair is its gap.
        val between = contacts.values.flatMap { cs ->
            val places = distinctPlaces(cs)
            places.drop(1).map { c ->
                val first = places.first()
                c.copy(loopCenterY = (first.y + c.y) / 2f)
            }
        }
        return distinctPlaces(within + between)
    }

    private const val MAX_GAP = 0.35f

    internal fun resample(points: List<UnitPoint>, step: Float): List<UnitPoint> {
        if (points.size < 2) return points
        val out = mutableListOf(points.first())
        var carry = 0f
        for (k in 1 until points.size) {
            val a = points[k - 1]; val b = points[k]
            val d = dist(a, b)
            if (d == 0f) continue
            var t = step - carry
            while (t <= d) {
                out += UnitPoint(a.x + (b.x - a.x) * t / d, a.y + (b.y - a.y) * t / d)
                t += step
            }
            carry = d - (t - step)
        }
        if (out.last() != points.last()) out += points.last()
        return out
    }

    internal fun dist(a: UnitPoint, b: UnitPoint) = hypot(a.x - b.x, a.y - b.y)

    /** Distance from [p] to the segment [a]–[b]. */
    internal fun pointSegment(p: UnitPoint, a: UnitPoint, b: UnitPoint): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /** Shortest distance between two segments (0 when they cross) and the midpoint of that closest approach. */
    private fun segmentDistance(a: UnitPoint, b: UnitPoint, c: UnitPoint, d: UnitPoint): Triple<Float, Float, Float> {
        if (crosses(a, b, c, d)) return Triple(0f, (a.x + b.x + c.x + d.x) / 4f, (a.y + b.y + c.y + d.y) / 4f)
        val options = listOf(
            Triple(pointSegment(a, c, d), a, nearest(a, c, d)), Triple(pointSegment(b, c, d), b, nearest(b, c, d)),
            Triple(pointSegment(c, a, b), c, nearest(c, a, b)), Triple(pointSegment(d, a, b), d, nearest(d, a, b)),
        )
        val best = options.minBy { it.first }
        return Triple(best.first, (best.second.x + best.third.x) / 2f, (best.second.y + best.third.y) / 2f)
    }

    private fun nearest(p: UnitPoint, a: UnitPoint, b: UnitPoint): UnitPoint {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0f, 1f)
        return UnitPoint(a.x + t * dx, a.y + t * dy)
    }

    private fun crosses(a: UnitPoint, b: UnitPoint, c: UnitPoint, d: UnitPoint): Boolean {
        fun side(p: UnitPoint, q: UnitPoint, r: UnitPoint) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
        val d1 = side(c, d, a); val d2 = side(c, d, b); val d3 = side(a, b, c); val d4 = side(a, b, d)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun rel(v: Float, min: Float, extent: Float) = if (extent <= 0f) 0.5f else (v - min) / extent

    /** 0 at or below [lo], 1 at or above [hi], linear in between. */
    internal fun ramp(v: Float, lo: Float, hi: Float): Float = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
}

/** How well the child followed the guide in trace mode. */
data class TraceReport(
    /** Share of the guide the ink went along. */
    val coverage: Float,
    /** 1 when the ink stays on the guide, lower the further it wanders. */
    val accuracy: Float,
    /** The first stroke began at the green start dot. */
    val startedAtDot: Boolean,
    /** The first stroke ran in the arrow's direction. */
    val rightDirection: Boolean,
) {
    val score: Float get() = (coverage + accuracy) / 2f
    val tip: String? get() = when {
        !startedAtDot || !rightDirection -> "Aloita vihreästä pisteestä ja kulje nuolen suuntaan."
        coverage < 0.75f -> "Kulje koko mallin matka, ihan loppuun asti."
        accuracy < 0.75f -> "Pysy lähellä mallin viivaa."
        else -> null
    }
}

object Trace {
    private const val NEAR = 0.08f

    fun score(guide: DigitGuide, strokes: List<List<UnitPoint>>): TraceReport? {
        val ink = strokes.filter { it.isNotEmpty() }
        if (ink.isEmpty()) return null
        val guidePoints = guide.strokes.flatMap { Legibility.resample(it, 0.02f) }
        val inkPoints = ink.flatMap { Legibility.resample(it, 0.02f) }
        val coverage = guidePoints.count { g -> distanceToPath(g, ink) <= NEAR }.toFloat() / guidePoints.size
        val mean = inkPoints.map { distanceToPath(it, guide.strokes) }.average().toFloat()
        val first = ink.first()
        val g = guide.strokes.first()
        val startedAtDot = Legibility.dist(first.first(), g.first()) <= 0.12f
        val rightDirection = first.size < 2 ||
            Legibility.dist(first.first(), g.first()) <= Legibility.dist(first.last(), g.first())
        return TraceReport(coverage, 1f - Legibility.ramp(mean, 0.03f, 0.15f), startedAtDot, rightDirection)
    }

    private fun distanceToPath(p: UnitPoint, strokes: List<List<UnitPoint>>): Float =
        strokes.minOf { s -> if (s.size == 1) Legibility.dist(p, s[0]) else s.zipWithNext().minOf { (a, b) -> Legibility.pointSegment(p, a, b) } }
}

/** Axis-aligned bounds in unit coordinates. */
data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    companion object {
        fun of(points: List<UnitPoint>) = Bounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
}
