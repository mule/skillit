package fi.skillit.ink

/** One labelled handwriting sample from the Ink lab recorder: the child wrote [label]. */
data class InkSample(val label: Int, val strokes: List<InkStroke>, val candidates: List<RawCandidate>, val model: String) {
    val reading: DigitReading get() = DigitRules.read(candidates)
}

/**
 * Line format, one sample per line, so the file can be appended and read without a JSON library:
 * `v1<TAB>label<TAB>model<TAB>candidates<TAB>strokes`
 * candidates = `text:score` joined by `,` (text percent-encoded, score empty if null)
 * strokes = strokes joined by `;`, points `x y t p` joined by `,`.
 */
object InkSampleCodec {
    fun encode(s: InkSample): String = listOf(
        "v1", s.label.toString(), esc(s.model),
        s.candidates.joinToString(",") { esc(it.text) + ":" + (it.score?.toString() ?: "") },
        s.strokes.joinToString(";") { st -> st.points.joinToString(",") { "${it.x} ${it.y} ${it.t} ${it.pressure}" } },
    ).joinToString("\t")

    fun decode(line: String): InkSample? = runCatching {
        val f = line.split("\t")
        if (f.size != 5 || f[0] != "v1") return null
        val candidates = if (f[3].isEmpty()) emptyList() else f[3].split(",").map {
            val i = it.lastIndexOf(':')
            RawCandidate(unesc(it.substring(0, i)), it.substring(i + 1).takeIf(String::isNotEmpty)?.toFloat())
        }
        val strokes = if (f[4].isEmpty()) emptyList() else f[4].split(";").map { st ->
            InkStroke(st.split(",").map { p -> p.split(" ").let { InkPoint(it[0].toFloat(), it[1].toFloat(), it[2].toLong(), it[3].toFloat()) } })
        }
        InkSample(f[1].toInt().also { require(it in 0..9) }, strokes, candidates, unesc(f[2]))
    }.getOrNull()

    private fun esc(s: String) = buildString { s.forEach { c -> if (c.isLetterOrDigit() && c.code < 128) append(c) else append('%').append("%04x".format(c.code)) } }
    private fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) { if (s[i] == '%') { append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 5 } else append(s[i++]) }
    }
}

/** Per-digit accuracy of the spike, including the 4/9 and 1/7 mix-ups that decide pen vs number pad. */
data class DigitStats(val digit: Int, val samples: Int, val accepted: Int, val correct: Int, val asked: Int, val askedIncludingLabel: Int, val unreadable: Int) {
    /** Correct and accepted without asking. */
    val accuracy get() = if (samples == 0) 0f else correct.toFloat() / samples
    /** Accepted but wrong: these would mark a right answer wrong, the worst outcome for the child. */
    val wrongAccepted get() = accepted - correct
}

data class AccuracyReport(val perDigit: List<DigitStats>, val confusion: Array<IntArray>) {
    val samples get() = perDigit.sumOf { it.samples }
    val accuracy get() = if (samples == 0) 0f else perDigit.sumOf { it.correct }.toFloat() / samples
    /** Count of samples labelled [label] whose top digit was [predicted]. */
    fun confused(label: Int, predicted: Int) = confusion[label][predicted]
    override fun equals(other: Any?) = other is AccuracyReport && perDigit == other.perDigit && confusion.contentDeepEquals(other.confusion)
    override fun hashCode() = perDigit.hashCode() * 31 + confusion.contentDeepHashCode()

    fun summary(): String = buildString {
        appendLine("Näytteitä $samples, oikein ilman kysymistä ${"%.0f".format(accuracy * 100)} %")
        perDigit.filter { it.samples > 0 }.forEach {
            appendLine("${it.digit}: ${it.correct}/${it.samples} oikein, kysyttiin ${it.asked}, väärin ${it.wrongAccepted}, ei luettu ${it.unreadable}")
        }
        append("4→9 ${confused(4, 9)}, 9→4 ${confused(9, 4)}, 1→7 ${confused(1, 7)}, 7→1 ${confused(7, 1)}")
    }

    companion object {
        fun of(samples: List<InkSample>): AccuracyReport {
            val confusion = Array(10) { IntArray(10) }
            val stats = (0..9).map { d ->
                val mine = samples.filter { it.label == d }
                var accepted = 0; var correct = 0; var asked = 0; var askedLabel = 0; var unreadable = 0
                mine.forEach { s ->
                    val r = s.reading
                    r.top?.let { confusion[d][it]++ }
                    when (r) {
                        is DigitReading.Accepted -> { accepted++; if (r.digit == d) correct++ }
                        is DigitReading.Ask -> { asked++; if (d == r.first || d == r.second) askedLabel++ }
                        is DigitReading.Unreadable -> unreadable++
                    }
                }
                DigitStats(d, mine.size, accepted, correct, asked, askedLabel, unreadable)
            }
            return AccuracyReport(stats, confusion)
        }
    }
}
