package fi.skillit.ink

import kotlin.math.exp

/** One raw recogniser candidate, best first. ML Kit scores are costs: lower is better; null when the model has none. */
data class RawCandidate(val text: String, val score: Float? = null)

/** A digit with its share (0..1) of the recogniser's belief. */
data class DigitScore(val digit: Int, val confidence: Float)

/** What one box says after recognition. The game never marks an answer wrong from an [Ask] or [Unreadable]. */
sealed interface DigitReading {
    val ranked: List<DigitScore>
    /** Sure enough to check the answer. */
    data class Accepted(val digit: Int, val confidence: Float, override val ranked: List<DigitScore>) : DigitReading
    /** Two digits are close: ask the child, e.g. “Tarkoititko 4 vai 9?”. [first] < [second]. */
    data class Ask(val first: Int, val second: Int, override val ranked: List<DigitScore>) : DigitReading {
        val question get() = "Tarkoititko $first vai $second?"
    }
    /** No usable digit: ask the child to write it again. */
    data class Unreadable(override val ranked: List<DigitScore>) : DigitReading {
        val message get() = "En saanut numerosta selvää. Kirjoita se uudelleen."
    }
    val top: Int? get() = ranked.firstOrNull()?.digit
}

object DigitRules {
    /** Pairs children (and recognisers) mix up. They need a wider margin before we trust the top digit. */
    val confusablePairs = setOf(setOf(4, 9), setOf(1, 7))

    const val ACCEPT = 0.6f
    const val MARGIN = 0.25f
    const val CONFUSABLE_MARGIN = 0.4f
    const val SINGLE_ACCEPT = 0.35f
    const val ASK_FLOOR = 0.1f
    private const val LOOKALIKE_WEIGHT = 0.5f

    /** Letters the recogniser returns for handwritten digits. Used at half weight. */
    private val lookalikes = mapOf(
        'l' to 1, 'I' to 1, 'i' to 1, '|' to 1, '!' to 1,
        'O' to 0, 'o' to 0, 'D' to 0,
        'Z' to 2, 'z' to 2,
        'S' to 5, 's' to 5,
        'b' to 6, 'G' to 6,
        'B' to 8,
        'g' to 9, 'q' to 9,
    )

    /** Restricts a candidate to one digit 0–9. Returns the digit and whether it came from a look-alike letter. */
    fun digitOf(text: String): Pair<Int, Boolean>? {
        val t = text.filterNot { it.isWhitespace() }
        if (t.length != 1) return null
        val c = t[0]
        if (c in '0'..'9') return (c - '0') to false
        return lookalikes[c]?.let { it to true }
    }

    /**
     * Turns raw candidates into per-digit confidence. With scores, weights are exp(-cost); without,
     * rank weights 1, ½, ¼… Non-digit candidates keep their weight in the denominator, so a box that
     * looks more like a letter than a digit gets low confidence.
     */
    fun rank(candidates: List<RawCandidate>): List<DigitScore> {
        if (candidates.isEmpty()) return emptyList()
        val scored = candidates.all { it.score != null }
        val best = if (scored) candidates.minOf { it.score!! } else 0f
        val weights = candidates.mapIndexed { i, c ->
            if (scored) exp((best - c.score!!).toDouble()).toFloat() else 1f / (1 shl i.coerceAtMost(20))
        }
        val total = weights.sum()
        val byDigit = LinkedHashMap<Int, Float>()
        candidates.forEachIndexed { i, c ->
            val (digit, lookalike) = digitOf(c.text) ?: return@forEachIndexed
            val w = weights[i] * if (lookalike) LOOKALIKE_WEIGHT else 1f
            byDigit[digit] = (byDigit[digit] ?: 0f) + w
        }
        return byDigit.map { (d, w) -> DigitScore(d, w / total) }.sortedByDescending { it.confidence }
    }

    fun read(candidates: List<RawCandidate>): DigitReading {
        val ranked = rank(candidates)
        val first = ranked.getOrNull(0) ?: return DigitReading.Unreadable(ranked)
        val second = ranked.getOrNull(1)
        if (second == null || second.confidence < ASK_FLOOR) {
            return if (first.confidence >= SINGLE_ACCEPT) DigitReading.Accepted(first.digit, first.confidence, ranked)
            else DigitReading.Unreadable(ranked)
        }
        val margin = if (setOf(first.digit, second.digit) in confusablePairs) CONFUSABLE_MARGIN else MARGIN
        return if (first.confidence >= ACCEPT && first.confidence - second.confidence >= margin)
            DigitReading.Accepted(first.digit, first.confidence, ranked)
        else DigitReading.Ask(minOf(first.digit, second.digit), maxOf(first.digit, second.digit), ranked)
    }
}
