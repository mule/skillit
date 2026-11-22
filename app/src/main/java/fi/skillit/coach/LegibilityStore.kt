package fi.skillit.coach

import android.content.Context
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One digit's legibility on one day, as sums so days can be merged. This aggregate is the only handwriting
 * data the app keeps outside [fi.skillit.ink.AttemptInkStore], and the only kind that may ever sync
 * (epic #31, Release B). It holds no strokes, no answers and no timestamps finer than a day.
 */
data class DigitDay(
    val day: Long,
    val digit: Int,
    val count: Int,
    val scoreSum: Float,
    val heightSum: Float,
    val heightSqSum: Float,
    /** Per metric: how many writings had it, and the sum of its scores. */
    val metrics: Map<Metric, Pair<Int, Float>> = emptyMap(),
) {
    init { require(digit in 0..9 && count > 0) }
    val score: Float get() = scoreSum / count

    operator fun plus(r: LegibilityReport): DigitDay = copy(
        count = count + 1, scoreSum = scoreSum + r.overall, heightSum = heightSum + r.height, heightSqSum = heightSqSum + r.height * r.height,
        metrics = (metrics.keys + r.scores.keys).associateWith { m ->
            val (n, s) = metrics[m] ?: (0 to 0f)
            r.scores[m]?.let { (n + 1) to (s + it) } ?: (n to s)
        },
    )

    companion object {
        fun of(day: Long, r: LegibilityReport) = DigitDay(day, r.digit, 1, r.overall, r.height, r.height * r.height, r.scores.mapValues { 1 to it.value })
    }
}

/**
 * Per-day, per-digit legibility aggregates in app-private, no-backup storage. Writes go to a temporary
 * file that is renamed over the old one, so a full disk never leaves a half file. Calls block; run them off
 * the main thread.
 */
class LegibilityStore(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun today(): Long = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate().toEpochDay()

    @Synchronized fun days(): List<DigitDay> = if (!file.exists()) emptyList() else decode(file.readText())

    /** Adds [reports] to today's aggregates. Old days beyond [KEEP_DAYS] are dropped. */
    @Synchronized fun record(reports: List<LegibilityReport>) {
        if (reports.isEmpty()) return
        val today = today()
        val byKey = days().filter { it.day > today - KEEP_DAYS }.associateBy { it.day to it.digit }.toMutableMap()
        reports.forEach { r -> byKey[today to r.digit] = byKey[today to r.digit]?.plus(r) ?: DigitDay.of(today, r) }
        val dir = file.parentFile
        if (dir != null && !dir.isDirectory && !dir.mkdirs()) throw IOException("could not create $dir")
        val tmp = File(file.path + ".tmp")
        try {
            tmp.writeText(encode(byKey.values.sortedWith(compareBy({ it.day }, { it.digit }))))
            if (!tmp.renameTo(file)) throw IOException("could not write $file")
        } finally { if (tmp.exists()) tmp.delete() }
    }

    fun record(report: LegibilityReport) = record(listOf(report))

    @Synchronized fun deleteAll() { file.delete(); File(file.path + ".tmp").delete() }

    companion object {
        const val KEEP_DAYS = 180

        fun open(context: Context) = LegibilityStore(File(context.noBackupFilesDir, "handwriting/legibility.v1.tsv"))

        /** `v1` header, then one line per day and digit: `day digit count scoreSum heightSum heightSqSum metric=n:sum,...`. */
        fun encode(days: List<DigitDay>): String = buildString {
            append("v1\n")
            days.forEach { d ->
                append(d.day).append('\t').append(d.digit).append('\t').append(d.count).append('\t')
                    .append(d.scoreSum).append('\t').append(d.heightSum).append('\t').append(d.heightSqSum).append('\t')
                    .append(d.metrics.entries.joinToString(",") { (m, v) -> "${m.name}=${v.first}:${v.second}" }).append('\n')
            }
        }

        /** Unreadable lines are skipped, so one bad line never hides the whole trend. */
        fun decode(text: String): List<DigitDay> {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.firstOrNull() != "v1") return emptyList()
            return lines.drop(1).mapNotNull { line ->
                runCatching {
                    val f = line.split("\t")
                    val metrics = f.getOrNull(6).orEmpty().split(",").filter { it.isNotEmpty() }.mapNotNull { e ->
                        val (name, value) = e.split("=")
                        val (n, s) = value.split(":")
                        runCatching { Metric.valueOf(name) }.getOrNull()?.let { it to (n.toInt() to s.toFloat()) }
                    }.toMap()
                    DigitDay(f[0].toLong(), f[1].toInt(), f[2].toInt(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), metrics)
                }.getOrNull()
            }
        }
    }
}

data class DayScore(val day: Long, val score: Float, val count: Int)

/** A digit's legibility over time, for the parent view. Scores are 0..1. */
data class DigitTrend(
    val digit: Int,
    val days: List<DayScore>,
    /** Mean over the last 7 days, or null without writing. */
    val recent: Float?,
    /** Mean over the 3 weeks before that. */
    val earlier: Float?,
    /** How even the digit's size is (see [Legibility.sizeConsistency]). */
    val consistency: Float?,
    val samples: Int,
    /** The metric that is lowest on average in the window, when it is low enough to matter. */
    val weakestMetric: Metric?,
) {
    val change: Float? get() = if (recent != null && earlier != null) recent - earlier else null
}

object LegibilityStats {
    private const val UNKNOWN = 0.55f

    private fun mean(days: List<DigitDay>): Float? = days.sumOf { it.count }.takeIf { it > 0 }?.let { n -> days.sumOf { it.scoreSum.toDouble() }.toFloat() / n }

    /** Size consistency from summed heights: 1 = always the same size (needs 2+ writings). */
    fun consistency(days: List<DigitDay>): Float? {
        val n = days.sumOf { it.count }
        if (n < 2) return null
        val mean = days.sumOf { it.heightSum.toDouble() } / n
        if (mean <= 0.0) return 0f
        val variance = max(0.0, days.sumOf { it.heightSqSum.toDouble() } / n - mean * mean)
        return 1f - Legibility.ramp((sqrt(variance) / mean).toFloat(), 0f, 0.3f)
    }

    fun trend(days: List<DigitDay>, today: Long, window: Int = 28): List<DigitTrend> = (0..9).map { d ->
        val mine = days.filter { it.digit == d && it.day in (today - window + 1)..today }.sortedBy { it.day }
        val weakest = Metric.entries.mapNotNull { m ->
            val (n, s) = mine.mapNotNull { it.metrics[m] }.fold(0 to 0f) { (n, s), (a, b) -> (n + a) to (s + b) }
            if (n == 0) null else m to s / n
        }.filter { it.second < LegibilityReport.TIP_BELOW }.minByOrNull { it.second }?.first
        DigitTrend(
            digit = d,
            days = mine.map { DayScore(it.day, it.score, it.count) },
            recent = mean(mine.filter { it.day > today - 7 }),
            earlier = mean(mine.filter { it.day <= today - 7 }),
            consistency = consistency(mine),
            samples = mine.sumOf { it.count },
            weakestMetric = weakest,
        )
    }

    /**
     * How clear each digit has been lately (last [window] days): the mean score, nudged by size consistency
     * once there are 3+ writings. Digits with fewer than 2 writings get a middling score, so the warm-up
     * visits them too.
     */
    fun digitScores(days: List<DigitDay>, today: Long, window: Int = 14): Map<Int, Float> = (0..9).associateWith { d ->
        val mine = days.filter { it.digit == d && it.day in (today - window + 1)..today }
        val n = mine.sumOf { it.count }
        val m = mean(mine)
        val c = consistency(mine)
        when {
            n < 2 || m == null -> UNKNOWN
            n >= 3 && c != null -> 0.85f * m + 0.15f * c
            else -> m
        }
    }
}

object WarmUp {
    const val SIZE = 5
    private const val WEAK = 0.8f

    /**
     * The day's warm-up digits: the weakest first, the weakest digit's look-alike partner (4/9, 1/7, 3/8, 5/6)
     * so the two are practised side by side, then the next weakest. Ties rotate by [today], so a fresh
     * start doesn't always begin with 0. Always [count] different digits.
     */
    fun pick(days: List<DigitDay>, today: Long, count: Int = SIZE): List<Int> {
        require(count in 1..10)
        val scores = LegibilityStats.digitScores(days, today)
        val order = (0..9).sortedWith(compareBy<Int>({ scores.getValue(it) }, { Math.floorMod(it - today, 10L) }))
        val picked = mutableListOf(order.first())
        // A weak digit is practised next to its look-alike, so the difference is felt, not just told.
        Legibility.partnerOf(order.first())?.let { if (count > 1 && scores.getValue(order.first()) < WEAK) picked += it }
        order.forEach { if (picked.size < count && it !in picked) picked += it }
        return picked.take(count)
    }
}
