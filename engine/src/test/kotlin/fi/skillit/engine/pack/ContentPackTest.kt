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
            "generator only" to withAnswer(0, 3) { it.copy(expression = null) },
            "duplicate id" to withAnswer(0, 1) { it.copy(id = "tt-7x8") },
        )
        broken.forEach { (name, pack) ->
            assertNotEquals(name, emptyList<String>(), pack.problems())
            assertThrows(name, IllegalArgumentException::class.java) { ContentPack.parse(ContentPack.encode(pack)) }
        }
        assertTrue(broken.getValue("numeric").problems().single().contains("7 * 8 = 56, not 54"))
    }

    @Test fun registeredGeneratorsRecomputeAnswers() {
        val generatorOnly = withAnswer(0, 3) { it.copy(expression = null) }
        val timesTable = AnswerRecomputer { g -> AnswerSpec.Numeric(g.params.getValue("table").toString().toInt() * 6) }
        assertEquals(emptyList<String>(), generatorOnly.problems(mapOf("times-table" to timesTable)))
        val wrong = AnswerRecomputer { AnswerSpec.Numeric(55) }
        assertTrue(generatorOnly.problems(mapOf("times-table" to wrong)).single().contains("differs from generator"))
        // With both an expression and a generator, both must agree.
        assertFalse(sample.problems(mapOf("times-table" to wrong)).isEmpty())
        val failing = AnswerRecomputer { throw IllegalArgumentException("bad params") }
        assertTrue(generatorOnly.problems(mapOf("times-table" to failing)).single().contains("bad params"))
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
