package fi.skillit.coach

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A point in unit box coordinates: (0,0) is the box's top-left corner, (1,1) its bottom-right. y grows down. */
data class UnitPoint(val x: Float, val y: Float)

/**
 * How one digit is written, as taught in Finnish schools: the strokes in writing order, each a polyline
 * from its start (the green dot in the studio) to its end (the arrow tip).
 */
data class DigitGuide(val digit: Int, val strokes: List<List<UnitPoint>>, val tip: String) {
    val start: UnitPoint get() = strokes.first().first()
}

/**
 * Writing lines inside one box. Digits sit on the baseline and reach up to the top line, like the
 * squares of the child's workbook. The metrics in [Legibility] use the same lines.
 */
object WritingLines {
    const val TOP = 0.15f
    const val BASELINE = 0.85f
    const val HEIGHT = BASELINE - TOP
}

object DigitGuides {
    private const val T = WritingLines.TOP
    private const val B = WritingLines.BASELINE

    /**
     * Ellipse arc. Angles in degrees, 90° = top. Increasing angles run counter-clockwise on screen
     * (top → left → bottom → right); decreasing angles run clockwise.
     */
    internal fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float, steps: Int = 24): List<UnitPoint> =
        (0..steps).map { i ->
            val a = (from + (to - from) * i / steps) * PI.toFloat() / 180f
            UnitPoint(cx + rx * cos(a), cy - ry * sin(a))
        }

    internal fun line(vararg p: Pair<Float, Float>, steps: Int = 8): List<UnitPoint> =
        p.toList().zipWithNext().flatMapIndexed { i, (a, b) ->
            (if (i == 0) 0..steps else 1..steps).map { s ->
                UnitPoint(a.first + (b.first - a.first) * s / steps, a.second + (b.second - a.second) * s / steps)
            }
        }

    private fun join(vararg parts: List<UnitPoint>) = parts.reduce { acc, p -> acc + if (acc.last() == p.first()) p.drop(1) else p }

    val all: List<DigitGuide> = listOf(
        DigitGuide(0, listOf(arc(.5f, .5f, .22f, .35f, 90f, 450f, 40)),
            "Aloita ylhäältä ja kierrä vasemmalle. Sulje rengas samaan kohtaan."),
        DigitGuide(1, listOf(line(.36f to .33f, .56f to T, .56f to B)),
            "Pieni hattu ylös ja suora viiva alas."),
        DigitGuide(2, listOf(join(arc(.5f, .33f, .2f, .18f, 160f, -20f), line(.69f to .39f, .27f to B, .75f to B))),
            "Kaari ylhäälle, vino viiva alas ja suora pohja."),
        DigitGuide(3, listOf(join(arc(.49f, .32f, .2f, .17f, 150f, -90f), arc(.49f, .67f, .22f, .18f, 90f, -150f))),
            "Kaksi kaarta oikealle. Vasen puoli jää auki."),
        DigitGuide(4, listOf(line(.45f to T, .27f to .6f, .77f to .6f), line(.63f to .35f, .63f to B)),
            "Ensin vino viiva ja poikkiviiva, sitten pysty viiva. Yläpää jää auki."),
        DigitGuide(5, listOf(join(line(.32f to T, .3f to .47f), arc(.49f, .65f, .22f, .2f, 145f, -150f)), line(.32f to T, .73f to T)),
            "Ensin pysty viiva ja pyöreä vatsa, lopuksi suora hattu."),
        DigitGuide(6, listOf(join(arc(.66f, .66f, .4f, .49f, 85f, 180f, 16), arc(.48f, .66f, .2f, .19f, 180f, 540f, 32))),
            "Kaari alas ja rengas pohjalle. Sulje rengas."),
        DigitGuide(7, listOf(line(.26f to T, .75f to T, .42f to B)),
            "Pitkä katto ja vino viiva alas."),
        DigitGuide(8, listOf(join(arc(.5f, .32f, .17f, .17f, 90f, 270f, 16), arc(.5f, .67f, .21f, .18f, 90f, -270f, 32), arc(.5f, .32f, .17f, .17f, 270f, 450f, 16))),
            "Aloita ylhäältä, piirrä S ja palaa ylös. Kaksi suljettua rengasta."),
        DigitGuide(9, listOf(join(arc(.5f, .33f, .2f, .18f, 0f, 360f, 32), line(.7f to .33f, .66f to B))),
            "Rengas ylhäälle, sulje se, ja suora viiva alas."),
    )

    fun of(digit: Int): DigitGuide = all[digit.also { require(it in 0..9) }]
}
