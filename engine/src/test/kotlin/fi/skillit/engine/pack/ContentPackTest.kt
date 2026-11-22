package fi.skillit.engine.pack

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.DigitCell
import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.TableEntry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ContentPackTest {
    private val dir = File(System.getProperty("contentSchemaDir"))
    private val sampleText = File(dir, "examples/math-sample.v1.json").readText()
    private val mapper = ObjectMapper()
    private val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(File(dir, "pack.v1.json").readText())
    private fun schemaErrors(json: String) = schema.validate(mapper.readTree(json)).map { it.message }
    private val sample = ContentPack.parse(sampleText)

    @Test fun sampleValidatesAgainstSchemaAndModel() {
        assertEquals(emptyList<String>(), schemaErrors(sampleText))
        assertEquals(emptyList<String>(), sample.problems())
        assertEquals(listOf("times-tables", "column", "order"), sample.units.map { it.id })
        val types = sample.units.flatMap { u -> u.exercises.map { it.answer::class } }.toSet()
        assertEquals(6, types.size) // every answer type appears in the fixture
    }

    @Test fun roundTripKeepsTheModelAndStaysSchemaValid() {
        val encoded = ContentPack.encode(sample)
        assertEquals(emptyList<String>(), schemaErrors(encoded))
        assertEquals(sample, ContentPack.parse(encoded))
        assertEquals(encoded, ContentPack.encode(ContentPack.parse(encoded))) // canonical form is stable
        // A model built in Kotlin also produces schema-valid JSON.
        val built = sample.copy(units = listOf(sample.units[1].copy(exercises = listOf(sample.units[1].exercises[0].copy(
            id = "col-76x58", expression = "76 * 58", answer = AnswerSpec.ColumnMultiplication.layout(76, 58))))))
        val builtText = ContentPack.encode(built)
        assertEquals(emptyList<String>(), schemaErrors(builtText))
        assertEquals(built, ContentPack.parse(builtText))
    }

    @Test fun unitsBecomePlayableSessions() {
        sample.units.forEach { unit ->
            val session = ExerciseSession(sample.questions(unit.id))
            assertEquals(unit.exercises.size, session.questions.size)
        }
        val session = ExerciseSession(sample.questions("order"))
        assertTrue(session.submit("ord-1:0", AnswerInput.steps(20, 23)))
        assertEquals(10, session.xp)
        assertThrows(IllegalArgumentException::class.java) { sample.questions("missing") }
    }

    private fun withAnswer(unit: Int, exercise: Int, change: (PackExercise) -> PackExercise): ContentPack =
        sample.copy(units = sample.units.mapIndexed { u, it ->
            if (u != unit) it else it.copy(exercises = it.exercises.mapIndexed { e, ex -> if (e == exercise) change(ex) else ex })
        })

    @Test fun storedAnswersThatDisagreeWithTheirExpressionAreRejected() {
        val broken = mapOf(
            "numeric" to withAnswer(0, 0) { it.copy(answer = AnswerSpec.Numeric(54)) },
            "numeric expression" to withAnswer(0, 0) { it.copy(expression = "7 * 7") },
            "missing factor" to withAnswer(0, 1) { it.copy(answer = AnswerSpec.MissingFactor(listOf(6, null), 48)) },
            "missing factor expression" to withAnswer(0, 1) { it.copy(expression = "6 * 8") },
            "table blank" to withAnswer(0, 2) { ex ->
                val table = ex.answer as AnswerSpec.FillTable
                ex.copy(answer = table.copy(rows = table.rows.map { row -> row.map { if (it is TableEntry.Blank && it.id == "b5x6") it.copy(expected = 35) else it } }))
            },
            "table without expressions" to withAnswer(0, 2) { ex ->
                val table = ex.answer as AnswerSpec.FillTable
                ex.copy(answer = table.copy(rows = table.rows.map { row -> row.map { if (it is TableEntry.Blank) it.copy(expression = null) else it } }))
            },
            "column digit" to withAnswer(1, 0) { ex ->
                val col = ex.answer as AnswerSpec.ColumnMultiplication
                ex.copy(answer = col.copy(cells = col.cells.map { if (it.id == "r1") it.copy(expected = 8) else it }))
            },
            "column expression" to withAnswer(1, 0) { it.copy(expression = "23 * 5") },
            "steps" to withAnswer(2, 0) { it.copy(answer = AnswerSpec.Steps(listOf(35, 23))) },
            "steps expression" to withAnswer(2, 0) { it.copy(expression = "(3 + 4) * 5") },
            "no source" to withAnswer(2, 0) { it.copy(expression = null) },
            "bad expression" to withAnswer(0, 0) { it.copy(expression = "7 / 0") },
            "no expression" to withAnswer(0, 3) { it.copy(expression = null) },
            "unknown generator" to withAnswer(0, 0) { it.copy(generator = GeneratorRef("unregistered", seed = 1)) },
            "duplicate id" to withAnswer(0, 1) { it.copy(id = "tt-7x8") },
        )
        broken.forEach { (name, pack) ->
            assertNotEquals(name, emptyList<String>(), pack.problems())
            assertThrows(name, IllegalArgumentException::class.java) { ContentPack.parse(ContentPack.encode(pack)) }
        }
        assertTrue(broken.getValue("numeric").problems().single().contains("7 * 8 = 56, not 54"))
    }

    private val nineTimes = GeneratorRef("times-table", kotlinx.serialization.json.buildJsonObject { put("table", kotlinx.serialization.json.JsonPrimitive(9)) }, 42)
    private val timesTable = AnswerRecomputer { g -> AnswerSpec.Numeric(g.params.getValue("table").toString().toInt() * 6) }

    @Test fun registeredGeneratorsRecomputeAnswers() {
        val generatorOnly = withAnswer(0, 3) { it.copy(expression = null, generator = nineTimes) }
        assertEquals(emptyList<String>(), generatorOnly.problems(mapOf("times-table" to timesTable)))
        val wrong = AnswerRecomputer { AnswerSpec.Numeric(55) }
        assertTrue(generatorOnly.problems(mapOf("times-table" to wrong)).any { "differs from generator" in it })
        // With both an expression and a generator, both must agree.
        val both = withAnswer(0, 3) { it.copy(generator = nineTimes) }
        assertEquals(emptyList<String>(), both.problems(mapOf("times-table" to timesTable)))
        assertFalse(both.problems(mapOf("times-table" to wrong)).isEmpty())
    }

    @Test fun recomputerExceptionsBecomePackProblems() {
        val generatorOnly = withAnswer(0, 3) { it.copy(expression = null, generator = nineTimes) }
        listOf<RuntimeException>(IllegalArgumentException("bad params"), IllegalStateException("not ready"),
            ArithmeticException("/ by zero"), ClassCastException("not a number")).forEach { error ->
            val failing = AnswerRecomputer { throw error }
            val problems = generatorOnly.problems(mapOf("times-table" to failing))
            assertTrue(problems.toString(), problems.any { error.message!! in it })
            assertThrows(IllegalArgumentException::class.java) { ContentPack.parse(ContentPack.encode(generatorOnly), mapOf("times-table" to failing)) }
        }
    }

    @Test fun unknownGeneratorFamilyIsAlwaysAProblem() {
        // Codex MUST2: an unregistered generator plus an unrelated expression must not excuse anything.
        val numeric = withAnswer(0, 0) { it.copy(generator = GeneratorRef("unregistered", seed = 1)) }
        assertTrue(numeric.problems().any { "unknown generator family 'unregistered'" in it })
        val table = withAnswer(0, 2) { ex ->
            val t = ex.answer as AnswerSpec.FillTable
            ex.copy(expression = "1", generator = GeneratorRef("unregistered", seed = 1),
                answer = t.copy(rows = t.rows.map { row -> row.map { if (it is TableEntry.Blank) it.copy(expected = 999, expression = null) else it } }))
        }
        val problems = table.problems()
        assertTrue(problems.any { "unknown generator family" in it })
        assertTrue(problems.any { "blank b3x4 needs an expression" in it })
        assertTrue(problems.any { "checked per blank" in it })
        assertNotEquals(emptyList<String>(), schemaErrors(ContentPack.encode(table)))
        assertThrows(IllegalArgumentException::class.java) { ContentPack.parse(ContentPack.encode(table)) }
        // A recomputer that is registered but rebuilds something else does not excuse the blanks either.
        val other = AnswerRecomputer { AnswerSpec.Numeric(1) }
        assertTrue(table.copy().problems(mapOf("unregistered" to other)).any { "blank b3x4 needs an expression" in it })
        // Only a registered recomputer that rebuilds exactly this table may stand in for per-blank expressions.
        val bare = withAnswer(0, 2) { ex ->
            val t = ex.answer as AnswerSpec.FillTable
            ex.copy(generator = GeneratorRef("table", seed = 3), answer = t.copy(rows = t.rows.map { row -> row.map { if (it is TableEntry.Blank) it.copy(expression = null) else it } }))
        }
        val rebuilt = (bare.units[0].exercises[2].answer)
        assertEquals(emptyList<String>(), bare.problems(mapOf("table" to AnswerRecomputer { rebuilt })))
    }

    @Test fun mathChoiceKeyMustMatchTheComputedValue() {
        // Codex MUST1: changing only correctId must be caught.
        val wrongKey = withAnswer(2, 1) { ex -> ex.copy(answer = (ex.answer as AnswerSpec.Choice).copy(correctId = "a")) }
        assertEquals(emptyList<String>(), schemaErrors(ContentPack.encode(wrongKey))) // schema cannot know; the model must
        assertTrue(wrongKey.problems().single().contains("correctId is a, but 3 * 4 + 2 * 2 = 16 is choice b"))
        assertThrows(IllegalArgumentException::class.java) { ContentPack.parse(ContentPack.encode(wrongKey)) }
        val choice = sample.units[2].exercises[1].answer as AnswerSpec.Choice
        val cases = mapOf(
            "missing value" to choice.copy(choices = choice.choices.map { if (it.id == "c") it.copy(value = null) else it }),
            "duplicate value" to choice.copy(choices = choice.choices.map { if (it.id == "c") it.copy(value = 16) else it }),
            "no matching value" to choice.copy(choices = choice.choices.map { if (it.id == "b") it.copy(value = 17) else it }),
        )
        cases.forEach { (name, spec) -> assertNotEquals(name, emptyList<String>(), withAnswer(2, 1) { it.copy(answer = spec) }.problems()) }
        assertTrue(withAnswer(2, 1) { it.copy(expression = null) }.problems().single().contains("cannot check answer"))
        // A Choice without values still works in a session, as music and nature use it.
        assertNull(fi.skillit.engine.Choice("o0", "C").value)
    }

    @Test fun deeplyNestedJsonIsRejectedBeforeDecoding() {
        listOf(3000, 10_000).forEach { depth ->
            val nested = "[".repeat(depth) + "0" + "]".repeat(depth)
            val text = sampleText.replaceFirst("\"answer\": { \"type\": \"numeric\", \"expected\": 56 },",
                "\"answer\": { \"type\": \"numeric\", \"expected\": 56 }, \"generator\": { \"family\": \"g\", \"seed\": 1, \"params\": { \"p\": $nested } },")
            assertNotEquals(sampleText, text)
            val error = assertThrows(IllegalArgumentException::class.java) { ContentPack.parse(text) }
            assertTrue(error.message!!.contains("nested deeper"))
        }
        // Brackets inside strings (escaped quotes included) do not count.
        val prompt = "\\\"" + "[".repeat(100)
        fi.skillit.engine.JsonDepth.require("{\"a\": \"$prompt\"}")
        assertThrows(IllegalArgumentException::class.java) { fi.skillit.engine.JsonDepth.require("[".repeat(33)) }
        fi.skillit.engine.JsonDepth.require("[".repeat(32) + "]".repeat(32))
    }

    @Test fun oversizedContentIsRejected() {
        val ex = sample.units[0].exercises[0]
        val cases = mapOf(
            "prompt" to withAnswer(0, 0) { it.copy(prompt = "x".repeat(501)) },
            "hint" to withAnswer(0, 0) { it.copy(hint = "x".repeat(1001)) },
            "explanation" to withAnswer(0, 0) { it.copy(explanation = "x".repeat(1001)) },
            "units" to sample.copy(units = (1..51).map { n -> sample.units[0].copy(id = "u$n", exercises = listOf(ex.copy(id = "e$n"))) }),
            "params" to withAnswer(0, 0) { it.copy(generator = GeneratorRef("g", kotlinx.serialization.json.JsonObject((1..21).associate { n -> "p$n" to kotlinx.serialization.json.JsonPrimitive(n) }), 1)) },
            "params json" to withAnswer(0, 0) { it.copy(generator = GeneratorRef("g", kotlinx.serialization.json.JsonObject(mapOf("p" to kotlinx.serialization.json.JsonPrimitive("x".repeat(1000)))), 1)) },
            "choices" to withAnswer(2, 1) { it.copy(answer = AnswerSpec.Choice((1..11).map { n -> fi.skillit.engine.Choice("c$n", "$n", n) }, "c16")) },
            "pages" to sample.copy(sourceRef = SourceRef(2, pages = (1..21).toList())),
            "title" to sample.copy(title = "x".repeat(201)),
        )
        cases.forEach { (name, pack) ->
            assertTrue(name, pack.problems().isNotEmpty())
            // JSON Schema cannot measure the serialized size of params; the model enforces that one alone.
            if (name != "params json") assertNotEquals(name, emptyList<String>(), schemaErrors(ContentPack.encode(pack)))
        }
    }

    @Test fun schemaRejectsMalformedPacks() {
        val tree = { mapper.readTree(sampleText) as ObjectNode }
        val exercise = { t: ObjectNode ->
            t.get("units").get(0).get("exercises").get(0) as ObjectNode
        }
        val cases = listOf<(ObjectNode) -> Unit>(
            { it.put("schema", 2) },
            { it.remove("units") },
            { it.put("unexpected", true) },
            { exercise(it).remove("hint") },
            { exercise(it).remove("expression") },
            { (exercise(it).get("answer") as ObjectNode).put("type", "essay") },
            { (exercise(it).get("answer") as ObjectNode).put("expected", "56") },
            { exercise(it).put("expression", "import os") },
            { (it.get("sourceRef") as ObjectNode).put("text", "copied page content") },
        )
        cases.forEachIndexed { i, mutate ->
            val t = tree(); mutate(t)
            val json = mapper.writeValueAsString(t)
            assertNotEquals("case $i", emptyList<String>(), schemaErrors(json))
            // kotlinx accepts a quoted integer ("56") even in strict mode; only the schema catches case 6.
            if (i != 6) assertThrows("case $i", IllegalArgumentException::class.java) { ContentPack.parse(json) }
        }
        assertTrue(ContentPack.parse(sampleText).units[1].exercises[0].answer is AnswerSpec.ColumnMultiplication)
        assertEquals(DigitCell("c0.1", fi.skillit.engine.DigitKind.CARRY, 0, 1, 1, required = false),
            (sample.units[1].exercises[0].answer as AnswerSpec.ColumnMultiplication).cells[1])
    }
}
