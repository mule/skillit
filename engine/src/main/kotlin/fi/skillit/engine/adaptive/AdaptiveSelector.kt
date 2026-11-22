package fi.skillit.engine.adaptive

import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.pack.PackExercise
import kotlin.random.Random

/** Caller supplies one lesson's pool. History from another pack/version cannot affect this session. */
class AdaptiveSelector(private val seed: Long) {
    data class Selection(val items: List<PackExercise>, val explanation: String, val reviewItemIds: Set<String>)

    fun select(
        packId: String, packVersion: Int, pool: List<PackExercise>, history: List<AttemptEvent>,
        now: Long, count: Int = 12,
    ): Selection {
        require(count in 1..100 && now >= 0 && packVersion > 0)
        require(pool.map { it.id }.distinct().size == pool.size)
        val relevant = history.filter { it.packId == packId && it.packVersion == packVersion && it.timestamp <= now }
            .distinctBy { it.eventId }.sortedWith(compareBy({ it.timestamp }, { it.eventId }))
        val byItem = relevant.groupBy { it.itemId }
        val due = pool.mapNotNull { item ->
            val attempts = byItem[item.id].orEmpty()
            val lastMiss = attempts.indexOfLast { !it.correct }
            if (lastMiss < 0) null else {
                // Only independent, unhinted first-try successes lengthen spacing, one per UTC day.
                val successes = attempts.drop(lastMiss + 1).filter { it.correct && it.retryNumber == 0 && !it.hintUsed }
                    .distinctBy { it.timestamp / DAY }.size
                val delay = listOf(1L, 3L, 7L, 14L)[successes.coerceAtMost(3)] * DAY
                val last = attempts.last().timestamp
                if (now - last >= delay) item to (last + delay) else null
            }
        }.sortedWith(compareBy({ it.second }, { it.first.id }))
        val size = minOf(count, pool.size)
        val review = due.take(minOf(size, maxOf(1, size / 3))).map { it.first }
        val remaining = pool.sortedBy { it.id }.filter { it !in review }.toMutableList()
        val random = Random(seed)
        val evidenceById = pool.associate { item ->
            val gen = item.generator
            item.id to relevant.filter { event -> event.itemId == item.id || gen != null && event.family == gen.family }
                .takeLast(40)
        }
        fun evidence(item: PackExercise): List<AttemptEvent> = evidenceById.getValue(item.id)
        fun weight(item: PackExercise): Double {
            val records = evidence(item)
            val gen = item.generator
            val familyWeakness = records.count { !it.correct }.toDouble() / maxOf(1, records.size)
            val factMisses = records.count { !it.correct && (it.itemId == item.id || gen != null && it.params == gen.params) }.coerceAtMost(5)
            return 1.0 + 3.0 * familyWeakness + 2.0 * factMisses
        }
        val weights = pool.associate { it.id to weight(it) }
        val selected = review.toMutableList()
        while (selected.size < size) {
            var ticket = random.nextDouble() * remaining.sumOf { weights.getValue(it.id) }
            var index = remaining.lastIndex
            for (i in remaining.indices) {
                ticket -= weights.getValue(remaining[i].id)
                if (ticket < 0) { index = i; break }
            }
            selected += remaining.removeAt(index)
        }
        val weak = selected.filter { item -> evidence(item).any { !it.correct } }
        fun factMisses(item: PackExercise): List<AttemptEvent> = evidence(item).filter { event ->
            !event.correct && (item.generator?.let { event.family == it.family && event.params == it.params }
                ?: (event.itemId == item.id))
        }
        val errors = selected.flatMap { factMisses(it) }.mapNotNull { it.errorClass }.toSet()
        val tables = selected.filter { item -> factMisses(item).any { it.errorClass == ErrorClass.WRONG_TABLE_FACT } }
            .flatMap { item -> when (val answer = item.answer) {
            is AnswerSpec.MissingFactor -> answer.factors.filterNotNull().filter { it in 2..9 }
            is AnswerSpec.ColumnMultiplication -> answer.factors.filter { it in 2..9 }
            else -> emptyList()
        } }.distinct().sorted()
        val reasons = buildList {
            if (tables.isNotEmpty()) add("kertotauluja (${tables.joinToString(", ")})")
            if (ErrorClass.DROPPED_CARRY in errors) add("muistinumeroita")
            if (ErrorClass.PLACE_VALUE_SHIFT in errors) add("lukujen paikkoja")
            if (ErrorClass.MISREAD_DIGIT in errors) add("numeroiden selkeää kirjoittamista")
            if (weak.isNotEmpty() && isEmpty()) add("aiemmin vaikeita tehtävätyyppejä")
            if (review.isNotEmpty()) add("aiempien virheiden kertausta")
        }
        return Selection(selected.shuffled(random), if (reasons.isEmpty()) "Monipuolista harjoittelua." else
            "Harjoitellaan lisää: ${reasons.joinToString(", ")}.", review.map { it.id }.toSet())
    }

    companion object { const val DAY = 86_400_000L }
}
