package fi.skillit.mathgen

import fi.skillit.engine.*
import fi.skillit.engine.pack.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MathGenTest {
    @Test fun tables() = property("tables")
    @Test fun tensHundreds() = property("tens-hundreds")
    @Test fun multiFactor() = property("multi-factor")
    @Test fun column() = property("column")
    @Test fun columnTens() = property("column-tens")
    @Test fun orderOfOps() = property("order-of-ops")
    @Test fun missingFactor() = property("missing-factor")
    @Test fun productGrid() = property("product-grid")
    @Test fun multiplesOf() = property("multiples-of")
    @Test fun word() = property("word")
    @Test fun letterCode() = property("letter-code")

    private fun property(family: String) {
        repeat(10_000) { seed ->
            val params = buildJsonObject {
                when (family) {
                    "tables" -> { put("table", 2 + seed % 9); put("count", 2 + seed / 9 % 9) }
                    "tens-hundreds" -> { put("zeros", 1 + seed % 2); put("split", seed % 4 == 1) }
                    "multi-factor", "missing-factor", "order-of-ops" -> {
                        val digits = 1 + seed % 2
                        val count = if (family == "multi-factor") 3 + seed / 2 % 2 else 2 + seed / 2 % 3
                        put("digits", digits)
                        put("factorCount", count)
                        if (family != "order-of-ops") put("tensFactors", if (digits == 1) 0 else seed / 6 % (count + 1))
                    }
                    "column", "column-tens" -> {
                        val digits = 2 + seed % 2
                        put("digits", digits); put("carries", seed / 2 % digits)
                        put("overflow", seed / 6 % 2 == 1)
                    }
                    "product-grid" -> put("size", 2 + seed % 4)
                    "multiples-of" -> put("maxTable", 3 + seed % 8)
                    "word" -> { put("digits", 1 + seed % 2); put("template", seed / 2 % 3) }
                    "letter-code" -> put("word", seed % 4)
                }
            }
            val ref = GeneratorRef(family, params, seed.toLong())
            val item = MathGen.generate(ref)
            assertEquals("$ref", item, MathGen.generate(ref))
            assertEquals("$ref", emptyList<String>(), MathGen.problems(item))
            assertEquals(item.exercise.answer, MathGen.recomputers.getValue(family).recompute(ref))
            oracle(item)
            assertTrue(MathGen.problems(item.exercise).isEmpty())
            assertFalse(item.exercise.prompt.contains('*'))
            assertFalse(item.exercise.prompt.contains('/'))
            if (family in setOf("multi-factor", "missing-factor", "order-of-ops")) {
                assertTrue(Expression.parse(requireNotNull(item.exercise.expression)).steps().all { it in 0..10_000 })
                if (family != "order-of-ops") assertEquals(params.getValue("tensFactors").jsonPrimitive.int,
                    item.factors.count { it % 10 == 0 })
            }
            if (family == "column" || family == "column-tens") {
                assertEquals(params.getValue("carries").jsonPrimitive.int, item.carries)
                assertEquals(params.getValue("overflow").jsonPrimitive.boolean, item.overflow)
                assertEquals(params.getValue("digits").jsonPrimitive.int, item.factors.first().toString().length)
            }
        }
    }

    /** Independent arithmetic checks: no call to the recomputer under test. */
    private fun oracle(item: GeneratedExercise) {
        val ex = item.exercise
        when (val answer = ex.answer) {
            is AnswerSpec.Numeric -> assertEquals(item.factors.fold(1, Int::times), answer.expected)
            is AnswerSpec.ColumnMultiplication -> {
                val product = item.factors[0] * item.factors[1]
                val digits = answer.cells.filter { it.kind == DigitKind.RESULT }.sortedBy { it.column }
                assertEquals(product.toString().reversed(), digits.joinToString("") { "${it.expected}" })
                val multiplier = item.factors[1].let { if (it >= 10) it / 10 else it }
                var incoming = 0
                var carries = 0
                val top = item.factors[0].toString().reversed()
                top.forEachIndexed { i, c ->
                    val outgoing = ((c - '0') * multiplier + incoming) / 10
                    if (i < top.lastIndex && outgoing != 0) carries++
                    incoming = outgoing
                }
                assertEquals(carries, item.carries)
                assertEquals(incoming != 0, item.overflow)
                assertEquals(carries, answer.cells.count { it.kind == DigitKind.CARRY })
            }
            is AnswerSpec.MissingFactor -> {
                assertEquals(item.factors.reduce(Int::times), answer.product)
                assertEquals(item.factors[answer.factors.indexOf(null)], answer.expected)
                assertTrue(answer.expected > 0)
            }
            is AnswerSpec.Steps -> {
                // Form is k + f0 * ... * (last + n); derive a separate school-order oracle.
                val text = requireNotNull(ex.expression)
                val numbers = Regex("[0-9]+").findAll(text).map { it.value.toInt() }.toList()
                var value = numbers[numbers.lastIndex - 1] + numbers.last()
                val expected = mutableListOf(value)
                val factors = numbers.subList(1, numbers.lastIndex - 1) + value
                value = factors.first()
                factors.drop(1).forEach { value *= it; expected += value }
                expected += numbers.first() + value
                assertEquals(expected, answer.values)
            }
            is AnswerSpec.FillTable -> when (ex.generator!!.family) {
                "product-grid" -> answer.rows.drop(1).forEach { row ->
                    val a = (row[0] as TableEntry.Given).text.toInt()
                    row.drop(1).forEachIndexed { j, entry ->
                        val b = (answer.rows[0][j + 1] as TableEntry.Given).text.toInt()
                        assertEquals(a * b, (entry as TableEntry.Blank).expected)
                    }
                }
                "tables" -> answer.rows[0].zip(answer.rows[1]).forEach { (label, blank) ->
                    val factors = (label as TableEntry.Given).text.split(" · ").map(String::toInt)
                    assertEquals(factors[0] * factors[1], (blank as TableEntry.Blank).expected)
                }
                "multiples-of" -> {
                    val n = Regex("luku ([0-9]+)").find(ex.prompt)!!.groupValues[1].toInt()
                    answer.blanks.forEach { blank ->
                        val table = blank.id.drop(1).toInt()
                        assertEquals(if ((1..10).any { it * table == n }) 1 else 0, blank.expected)
                    }
                    assertTrue(answer.blanks.any { it.expected == 1 })
                }
                "letter-code" -> {
                    val mapping = Regex("([0-9]+)=([A-ZÄÖ])").findAll(ex.prompt)
                        .associate { it.groupValues[1].toInt() to it.groupValues[2] }
                    assertEquals(item.rewardWord!!.toSet().size, mapping.size)
                    assertEquals(item.rewardWord, answer.blanks.joinToString("") { mapping.getValue(it.expected) })
                    answer.rows[0].zip(answer.blanks).forEach { (label, blank) ->
                        val factors = (label as TableEntry.Given).text.split(" · ").map(String::toInt)
                        assertEquals(factors[0] * factors[1], blank.expected)
                    }
                }
            }
            else -> fail("Unexpected answer type")
        }
        if (ex.generator!!.family == "multi-factor") {
            assertEquals(item.factors.sorted(), item.smartOrder.sorted())
            assertTrue(item.smartOrder.take(2) in listOf(listOf(2, 5), listOf(4, 25), listOf(2, 10),
                listOf(4, 10), listOf(20, 10), listOf(10, 10)))
        }
    }

    @Test fun carryExamples() {
        assertEquals(CarryCount(2, true), MathGen.countCarries(234, 5))
        assertEquals(CarryCount(0, false), MathGen.countCarries(123, 2))
        assertEquals(CarryCount(0, true), MathGen.countCarries(50, 2))
        assertEquals(CarryCount(1, true), MathGen.countCarries(99, 9))
    }

    @Test fun rejectsMalformedParamsAndUnknownFamilies() {
        MathGen.families.forEach { family ->
            listOf("{\"unknown\":1}", "{\"digits\":null}").forEach { json ->
                rejects { MathGen.recomputers.getValue(family).recompute(GeneratorRef(family, Json.parseToJsonElement(json).jsonObject, 0)) }
            }
        }
        listOf("{\"digits\":\"3\"}", "{\"digits\":true}", "{\"digits\":[]}", "{\"digits\":3.0}",
            "{\"digits\":4}", "{\"carries\":3}", "{\"carries\":-1}", "{\"overflow\":1}",
            "{\"overflow\":\"true\"}", "{\"digits\":2,\"carries\":2}").forEach { json ->
            rejects { MathGen.generate(GeneratorRef("column", Json.parseToJsonElement(json).jsonObject, 0)) }
        }
        rejects { MathGen.generate(GeneratorRef("unknown", seed = 0)) }
        rejects { MathGen.recomputers.getValue("column").recompute(GeneratorRef("tables", seed = 0)) }
        rejects { MathGen.countCarries(1000, 2) }
    }

    @Test fun customWordTemplateUsesComputedExpression() {
        val params = Json.parseToJsonElement("""{
            "slots":{"count":4,"amount":12},
            "text":"Ostat {count} pakkausta, joissa on {amount} kynää. Montako kynää saat?",
            "expression":"{count} * {amount} + 2"
        }""").jsonObject
        val item = MathGen.generate(GeneratorRef("word", params, 42))
        assertEquals(AnswerSpec.Numeric(50), item.exercise.answer)
        assertEquals(mapOf("count" to 4, "amount" to 12), item.slots)
        assertTrue(item.exercise.prompt.contains("4 pakkausta"))
        assertEquals(emptyList<String>(), MathGen.problems(item))
        listOf(
            "{\"text\":\"{missing}\"}", "{\"expression\":\"1 / 0\"}",
            "{\"slots\":{\"count\":0}}", "{\"slots\":{\"count\":\"4\"}}",
            "{\"text\":\"{count\"}", "{\"slots\":[]}"
        ).forEach { json -> rejects { MathGen.generate(GeneratorRef("word", Json.parseToJsonElement(json).jsonObject, 0)) } }
    }

    @Test fun idsIncludeParamsAndIgnoreJsonObjectKeyOrder() {
        val a = Json.parseToJsonElement("{\"digits\":3,\"carries\":1}").jsonObject
        val b = Json.parseToJsonElement("{\"carries\":1,\"digits\":3}").jsonObject
        val c = Json.parseToJsonElement("{\"digits\":3,\"carries\":2}").jsonObject
        val first = MathGen.generate(GeneratorRef("column", a, 42))
        assertEquals(first, MathGen.generate(GeneratorRef("column", b, 42)))
        assertNotEquals(first.exercise.id, MathGen.generate(GeneratorRef("column", c, 42)).exercise.id)
    }

    @Test fun fullLongSeeds() {
        MathGen.families.forEach { family ->
            listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L).forEach { seed ->
                assertTrue(MathGen.problems(MathGen.generate(GeneratorRef(family, seed = seed))).isEmpty())
            }
        }
    }

    @Test fun generatedSampleRoundTripsAndMatchesCommittedArtifact() {
        val pack = MathGen.samplePack()
        val json = ContentPack.encode(pack)
        assertEquals(pack, MathGen.parsePack(json))
        assertEquals(json + "\n", File(System.getProperty("mathgenSample")).readText())
        val schemaFile = File(System.getProperty("mathgenSample")).parentFile.parentFile.resolve("pack.v1.json")
        val schema = com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.readText())
        assertTrue(schema.validate(com.fasterxml.jackson.databind.ObjectMapper().readTree(json)).isEmpty())
        rejects { ContentPack.parse(json) }
        val ex = pack.units[0].exercises[1]
        val tampered = ex.copy(answer = AnswerSpec.Numeric((ex.answer as AnswerSpec.Numeric).expected + 1))
        val bad = pack.copy(units = listOf(pack.units[0].copy(exercises = listOf(tampered))))
        rejects { ContentPack.parse(ContentPack.encode(bad), MathGen.recomputers) }
    }

    @Test fun languagePassMayChangeWordingButNotNumbersOrLetters() {
        MathGen.families.forEach { family ->
            val ex = MathGen.generate(GeneratorRef(family, seed = 42)).exercise
            assertTrue(MathGen.problems(ex.copy(prompt = "Harjoittele: ${ex.prompt}")).isEmpty())
            val changed = Regex("[0-9]+").replaceFirst(ex.prompt, "9999")
            if (changed != ex.prompt) assertTrue(family, MathGen.problems(ex.copy(prompt = changed)).isNotEmpty())
        }
        val code = MathGen.generate(GeneratorRef("letter-code", seed = 42)).exercise
        val badCode = code.copy(prompt = code.prompt.replace(Regex("=[A-ZÄÖ]"), "=X"))
        assertTrue(MathGen.problems(badCode).any { it.contains("letter-code") })
        assertTrue(MathGen.problems(code.copy(prompt = code.prompt.replace(Regex("=[A-ZÄÖ]"), "=XX"))).isNotEmpty())
        val pack = MathGen.samplePack()
        val unit = pack.units.single()
        val ex = unit.exercises.first { it.generator!!.family == "multiples-of" }
        val bad = ex.copy(prompt = ex.prompt.replace(Regex("[0-9]+"), "9999"))
        val tampered = pack.copy(units = listOf(unit.copy(exercises = listOf(bad))))
        // The new builder path must reject a prompt even though the engine's answer check accepts it.
        val json = ContentPack.encode(tampered)
        ContentPack.parse(json, MathGen.recomputers)
        rejects { MathGen.parsePack(json) }
    }

    @Test fun mixedWorkbookExamplesAndSmartPairsAreReachable() {
        val missing = (0L..500L).map { seed -> MathGen.generate(GeneratorRef("missing-factor",
            buildJsonObject { put("digits", 2); put("tensFactors", 1) }, seed)) }
        assertTrue(missing.any { it.exercise.prompt.contains("40 · ☐ = 360") })
        val multi = (0L..1000L).map { seed -> MathGen.generate(GeneratorRef("multi-factor",
            buildJsonObject { put("digits", 2); put("tensFactors", 2) }, seed)) }
        assertTrue(multi.any { it.factors.sorted() == listOf(4, 10, 20) })
        // Adjacent small java.util.Random seeds have correlated first draws; spread the seed space.
        val pairs = (0L..100L).map { seed -> MathGen.generate(GeneratorRef("multi-factor",
            buildJsonObject { put("digits", 2); put("tensFactors", 0) }, seed * 1_000_003L)).smartOrder.take(2) }.toSet()
        assertTrue(listOf(2, 5) in pairs)
        assertTrue(listOf(4, 25) in pairs)
        assertTrue(multi.any { 10 in it.smartOrder.take(2) })
        rejects { MathGen.generate(GeneratorRef("multi-factor", buildJsonObject { put("tensFactors", 1) }, 0)) }
    }

    @Test fun customWordPromptsUseFinnishOperators() {
        val ex = MathGen.generate(GeneratorRef("word", buildJsonObject {
            put("text", "Laske {count} * {amount} / 2.")
        }, 42)).exercise
        assertTrue('·' in ex.prompt && ':' in ex.prompt)
        assertTrue('*' !in ex.prompt && '/' !in ex.prompt)
    }

    @Test fun metadataAndPromptTamperingAreRejected() {
        val item = MathGen.generate(GeneratorRef("column", seed = 7))
        assertTrue(MathGen.problems(item.copy(carries = 9)).isNotEmpty())
        assertTrue(MathGen.problems(item.copy(overflow = !item.overflow)).isNotEmpty())
        assertTrue(MathGen.problems(item.copy(exercise = item.exercise.copy(prompt = "Väärä tehtävä"))).isNotEmpty())
    }

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }
}
