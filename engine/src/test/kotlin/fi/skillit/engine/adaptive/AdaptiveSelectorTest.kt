package fi.skillit.engine.adaptive

import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackExercise
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class AdaptiveSelectorTest {
    private fun item(id: Int, family: String = "table", fact: Int = id) = PackExercise("i$id", "Laske", "Vinkki", "Selitys",
        AnswerSpec.MissingFactor(listOf(7, 9)), generator = GeneratorRef(family, JsonObject(mapOf("opaque" to JsonPrimitive(fact))), 0))
    private val pool = (0..29).map { item(it, if (it < 10) "weak" else "strong") }
    private fun event(item: PackExercise, correct: Boolean = false, at: Long = 0, retry: Int = 0, hinted: Boolean = false) =
        AttemptEvent.record("math", 1, item.id, item.generator!!.family, item.generator.params, item.answer,
            AnswerInput.Cells(mapOf("missing" to if (correct) 63 else 62)), retry, hinted, InputMethod.PAD, null, at,
            UUID.nameUUIDFromBytes("${item.id}:$at:$retry:$correct:$hinted".toByteArray()).toString())

    @Test fun seededSelectionIsUniqueBoundedAndOrderIndependent() {
        val selector = AdaptiveSelector(123)
        val a = selector.select("math", 1, pool, emptyList(), 0)
        assertEquals(a, selector.select("math", 1, pool.reversed(), emptyList(), 0))
        assertEquals(12, a.items.size)
        assertEquals(12, a.items.map { it.id }.distinct().size)
        assertEquals(3, selector.select("math", 1, pool.take(3), emptyList(), 0).items.size)
        assertTrue(selector.select("math", 1, emptyList(), emptyList(), 0).items.isEmpty())
        assertNotEquals(a.items, AdaptiveSelector(456).select("math", 1, pool, emptyList(), 0).items)
    }
    @Test fun weakFamiliesAreWeightedAcrossSeeds() {
        val history = pool.take(5).map { event(it) }
        val weakWithHistory = (0L..99).sumOf { AdaptiveSelector(it).select("math", 1, pool, history, 1).items.count { it.generator!!.family == "weak" } }
        val weakWithout = (0L..99).sumOf { AdaptiveSelector(it).select("math", 1, pool, emptyList(), 1).items.count { it.generator!!.family == "weak" } }
        assertTrue("$weakWithHistory vs $weakWithout", weakWithHistory > weakWithout + 150)
    }
    @Test fun opaqueMatchingFactsReceiveExtraWeight() {
        val factPool = (0..29).map { item(it, "same", if (it == 1) 0 else it) }
        val history = listOf(event(factPool[0]))
        val counts = IntArray(30)
        (0L..999).forEach { seed -> AdaptiveSelector(seed).select("math", 1, factPool, history, 1, 1).items.forEach { counts[it.id.removePrefix("i").toInt()]++ } }
        assertTrue(counts.contentToString(), counts[1] > counts.drop(2).average() * 1.25)
    }
    @Test fun spacedReviewIsReservedAndSuccessfulReviewLengthensInterval() {
        val miss = event(pool[0])
        val selector = AdaptiveSelector(1)
        assertTrue(selector.select("math", 1, pool, listOf(miss), AdaptiveSelector.DAY - 1).reviewItemIds.isEmpty())
        assertEquals(setOf("i0"), selector.select("math", 1, pool, listOf(miss), AdaptiveSelector.DAY).reviewItemIds)
        val success = event(pool[0], true, AdaptiveSelector.DAY)
        assertTrue(selector.select("math", 1, pool, listOf(miss, success), 3 * AdaptiveSelector.DAY).reviewItemIds.isEmpty())
        assertEquals(setOf("i0"), selector.select("math", 1, pool, listOf(miss, success), 4 * AdaptiveSelector.DAY).reviewItemIds)
        val retry = event(pool[0], true, AdaptiveSelector.DAY, retry = 1)
        assertEquals(setOf("i0"), selector.select("math", 1, pool, listOf(miss, retry), 2 * AdaptiveSelector.DAY).reviewItemIds)
    }
    @Test fun unrelatedVersionsPacksAndFutureAttemptsHaveNoEffect() {
        val base = AdaptiveSelector(2).select("math", 1, pool, emptyList(), 0)
        val unrelated = listOf(event(pool[0]).copy(packId = "other"), event(pool[1]).copy(packVersion = 2), event(pool[2], at = 1))
        assertEquals(base, AdaptiveSelector(2).select("math", 1, pool, unrelated, 0))
    }
    @Test fun parentExplanationIsFinnishAndIncludesReasons() {
        val col = item(0).copy(answer = AnswerSpec.ColumnMultiplication.layout(27, 3))
        val miss = AttemptEvent.record("math", 1, col.id, col.generator!!.family, col.generator.params, col.answer,
            AnswerInput.Cells(mapOf("r0" to 1, "r1" to 6)), 0, false, InputMethod.PAD, null, 0)
        val selection = AdaptiveSelector(1).select("math", 1, listOf(col), listOf(miss), AdaptiveSelector.DAY)
        assertTrue(selection.explanation.contains("muistinumeroita"))
        assertTrue(selection.explanation.contains("kertausta"))
    }
    private fun miss(item: PackExercise, answer: AnswerInput) = AttemptEvent.record("math", 1, item.id,
        item.generator!!.family, item.generator.params, item.answer, answer, 0, false, InputMethod.PAD, null, 0)

    @Test fun tableExplanationNamesOnlyTheMissedFactWithinABroadFamily() {
        val tables = (2..10).map { table -> item(table, "times-table", table).copy(answer = AnswerSpec.MissingFactor(listOf(table, 8))) }
        val missed = tables.single { it.id == "i7" }
        val history = listOf(miss(missed, AnswerInput.Cells(mapOf("missing" to 55))))
        val result = AdaptiveSelector(1).select("math", 1, tables, history, 1)
        assertEquals("Harjoitellaan lisää: kertotauluja (7, 8).", result.explanation)
        // Another item id with exactly the same opaque fact can use that evidence too.
        val alias = missed.copy(id = "alias")
        assertEquals(result.explanation, AdaptiveSelector(1).select("math", 1, listOf(alias), history, 1).explanation)
        // Even a reused item id must not inherit a different fact's diagnosis.
        val different = missed.copy(generator = missed.generator!!.copy(params = JsonObject(emptyMap())))
        assertFalse(AdaptiveSelector(1).select("math", 1, listOf(different), history, 1).explanation.contains("kertotauluja"))
    }
    @Test fun carryAndPlaceValueMissesDoNotClaimTableWeaknessAndTenIsExcluded() {
        val col = item(1).copy(answer = AnswerSpec.ColumnMultiplication.layout(27, 3))
        val carry = miss(col, AnswerInput.Cells(mapOf("r0" to 1, "r1" to 6)))
        val carryReason = AdaptiveSelector(1).select("math", 1, listOf(col), listOf(carry), 1).explanation
        assertTrue(carryReason.contains("muistinumeroita"))
        assertFalse(carryReason.contains("kertotauluja"))
        val ten = item(2).copy(answer = AnswerSpec.MissingFactor(listOf(7, 10)))
        val shift = miss(ten, AnswerInput.Cells(mapOf("missing" to 7)))
        val shiftReason = AdaptiveSelector(1).select("math", 1, listOf(ten), listOf(shift), 1).explanation
        assertTrue(shiftReason.contains("lukujen paikkoja"))
        assertFalse(shiftReason.contains("kertotauluja"))
        val fact = miss(ten, AnswerInput.Cells(mapOf("missing" to 69)))
        assertEquals("Harjoitellaan lisää: kertotauluja (7).",
            AdaptiveSelector(1).select("math", 1, listOf(ten), listOf(fact), 1).explanation)
    }

}
