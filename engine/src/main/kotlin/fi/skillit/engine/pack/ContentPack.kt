package fi.skillit.engine.pack

import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.PackLimits
import fi.skillit.engine.Question
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Content pack v1, the Kotlin side of `content-schema/pack.v1.json`. The same JSON is bundled in the APK
 * (`app/src/main/assets/packs/`) in Release A and served by the backend in Release B.
 */
@Serializable
data class ContentPack(
    val schema: Int,
    val id: String,
    val subject: String,
    val grade: Int,
    val version: Int,
    val title: String,
    val language: String = "fi",
    val sourceRef: SourceRef? = null,
    val units: List<PackUnit>,
) {
    /** Every problem found, empty when the pack is playable. Answers are re-derived by each [AnswerSpec]. */
    fun problems(generators: Map<String, AnswerRecomputer> = emptyMap()): List<String> = buildList {
        if (schema != SCHEMA) add("schema must be $SCHEMA")
        if (!ID.matches(id)) add("pack id '$id' is invalid")
        if (subject.isBlank() || title.isBlank()) add("subject and title are required")
        if (subject.length > PackLimits.SUBJECT || title.length > PackLimits.TITLE) add("subject or title too long")
        if (units.size > PackLimits.UNITS) add("more than ${PackLimits.UNITS} units")
        sourceRef?.problems()?.let(::addAll)
        if (grade !in 1..12 || version < 1) add("grade or version out of range")
        if (units.isEmpty()) add("pack has no units")
        if (units.map { it.id }.distinct().size != units.size) add("unit ids must be unique")
        val exerciseIds = units.flatMap { unit -> unit.exercises.map { it.id } }
        exerciseIds.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { add("exercise id '$it' is used twice") }
        units.forEach { unit ->
            if (!ID.matches(unit.id)) add("unit id '${unit.id}' is invalid")
            if (unit.title.isBlank() || unit.title.length > PackLimits.TITLE) add("unit ${unit.id} title is empty or too long")
            unit.sourceRef?.problems()?.forEach { add("${unit.id}: $it") }
            if (unit.exercises.isEmpty() || unit.exercises.size > PackLimits.EXERCISES) add("unit ${unit.id} needs 1..${PackLimits.EXERCISES} exercises")
            unit.exercises.forEach { exercise -> exercise.problems(generators).forEach { add("${unit.id}/${exercise.id}: $it") } }
        }
    }

    /** Questions of one unit, ready for an [fi.skillit.engine.ExerciseSession]. The unit index is the level. */
    fun questions(unitId: String): List<Question> {
        val level = units.indexOfFirst { it.id == unitId }
        require(level >= 0) { "No unit $unitId" }
        return units[level].exercises.map { it.toQuestion(level) }
    }

    companion object {
        const val SCHEMA = 1
        internal val ID = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")

        val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
            explicitNulls = false
            prettyPrint = true
        }

        /** Parses and validates; throws [IllegalArgumentException] listing every problem. */
        fun parse(text: String, generators: Map<String, AnswerRecomputer> = emptyMap()): ContentPack {
            val pack = try { json.decodeFromString(serializer(), text) } catch (e: kotlinx.serialization.SerializationException) {
                throw IllegalArgumentException("Pack JSON does not match schema v1: ${e.message}", e)
            }
            val problems = pack.problems(generators)
            require(problems.isEmpty()) { problems.joinToString("\n") }
            return pack
        }

        fun encode(pack: ContentPack): String = json.encodeToString(serializer(), pack)
    }
}

/** Where in the workbook the content comes from: chapter and pages only, never the page content. */
@Serializable
data class SourceRef(val chapter: Int, val lesson: Int? = null, val pages: List<Int> = emptyList()) {
    fun problems(): List<String> = buildList {
        if (chapter < 1 || (lesson != null && lesson < 1) || pages.any { it < 1 }) add("sourceRef numbers must be positive")
        if (pages.size > PackLimits.PAGES) add("sourceRef lists more than ${PackLimits.PAGES} pages")
    }
}

@Serializable
data class PackUnit(
    val id: String,
    val title: String,
    val sourceRef: SourceRef? = null,
    val exercises: List<PackExercise>,
)

/**
 * One exercise. It must carry an [expression] or a [generator] (or, for a table, an expression on every blank).
 * [problems] recomputes the answer from them and rejects the exercise when the stored answer disagrees.
 */
@Serializable
data class PackExercise(
    val id: String,
    val prompt: String,
    val hint: String,
    val explanation: String,
    val answer: AnswerSpec,
    val expression: String? = null,
    val generator: GeneratorRef? = null,
) {
    fun problems(generators: Map<String, AnswerRecomputer> = emptyMap()): List<String> = buildList {
        if (!ContentPack.ID.matches(id)) add("exercise id is invalid")
        if (prompt.isBlank() || hint.isBlank() || explanation.isBlank()) add("prompt, hint and explanation are required")
        if (prompt.length > PackLimits.PROMPT || hint.length > PackLimits.HINT || explanation.length > PackLimits.EXPLANATION) {
            add("prompt, hint or explanation too long")
        }
        generator?.let {
            if (!ContentPack.ID.matches(it.family)) add("generator family is invalid")
            if (it.params.size > PackLimits.PARAMS || it.params.toString().length > PackLimits.PARAMS_JSON) add("generator params too large")
        }
        try { answer.validate() } catch (e: IllegalArgumentException) { add("answer: ${e.message}"); return@buildList }
        addAll(AnswerCheck.problems(this@PackExercise, generators))
    }

    fun toQuestion(level: Int): Question = Question.of(id, level, prompt, answer, hint, explanation)
}

/** A `mathgen` family with its parameters and seed; the same triple always yields the same exercise. */
@Serializable
data class GeneratorRef(val family: String, val params: JsonObject = JsonObject(emptyMap()), val seed: Long)
