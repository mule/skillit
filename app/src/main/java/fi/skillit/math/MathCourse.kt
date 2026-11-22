package fi.skillit.math

import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.Question
import fi.skillit.engine.adaptive.AdaptiveSelector
import fi.skillit.engine.adaptive.AttemptEvent
import fi.skillit.engine.pack.ContentPack
import fi.skillit.engine.pack.PackExercise
import fi.skillit.engine.pack.PackUnit
import fi.skillit.mathgen.MathGen

/**
 * Kertolaskuseikkailu course: one map stop per pack unit, in pack order. The workbook lesson number
 * comes from the unit's `sourceRef.lesson` (14, 18, 19, 20, 21, 22, 26 and the chapter test 27).
 * Pure Kotlin, so the session rules are JVM-tested without the UI.
 */
class MathCourse(val pack: ContentPack) {
    data class Stop(val unit: PackUnit, val lesson: Int?, val test: Boolean) {
        val id: String get() = unit.id
        val title: String get() = unit.title
        val label: String get() = when {
            test -> "Kappaleen koe"
            lesson != null -> "Oppitunti $lesson"
            else -> unit.title
        }
    }

    val stops: List<Stop> = pack.units.map { unit ->
        Stop(unit, unit.sourceRef?.lesson, unit.id.endsWith("-test") || unit.sourceRef?.lesson == CHAPTER_TEST)
    }

    fun stop(id: String): Stop = requireNotNull(stops.find { it.id == id }) { "No lesson $id" }

    /** A started lesson: the items in play order, plus the parent-facing explanation of the choice. */
    data class Plan(val stopId: String, val itemIds: List<String>, val explanation: String)

    /**
     * ~12 items: up to [count]−1 practice items drawn by [AdaptiveSelector] from the lesson pool, then one
     * letter-code item last, so every lesson ends with its reward word.
     */
    fun plan(stopId: String, history: List<AttemptEvent>, now: Long, seed: Long, count: Int = SESSION_SIZE): Plan {
        val exercises = stop(stopId).unit.exercises
        val (codes, practice) = exercises.partition { it.generator?.family == LETTER_CODE }
        val reward = codes.getOrNull(Math.floorMod(seed, maxOf(1, codes.size)).toInt())
        val practiceCount = if (reward == null) count else count - 1
        val selection = if (practice.isEmpty()) null
            else AdaptiveSelector(seed).select(pack.id, pack.version, practice, history, now, practiceCount)
        val items = selection?.items.orEmpty() + listOfNotNull(reward)
        require(items.isNotEmpty()) { "Lesson $stopId has no exercises" }
        return Plan(stopId, items.map { it.id }, selection?.explanation ?: "Monipuolista harjoittelua.")
    }

    /** Null when the pack no longer has every planned item (a new pack version replaced the lesson). */
    fun exercises(plan: Plan): List<PackExercise>? {
        val byId = stops.find { it.id == plan.stopId }?.unit?.exercises?.associateBy { it.id } ?: return null
        return plan.itemIds.map { byId[it] ?: return null }
    }

    /** All at level 0: a missed item comes back at the end of the session, like a music island. */
    fun questions(exercises: List<PackExercise>): List<Question> = exercises.map { it.toQuestion(0) }

    fun session(exercises: List<PackExercise>, events: List<fi.skillit.engine.SessionEvent>): ExerciseSession =
        ExerciseSession.restore(questions(exercises), events)

    companion object {
        const val SESSION_SIZE = 12
        const val CHAPTER_TEST = 27
        const val LETTER_CODE = "letter-code"
        /** #23's reviewed pack. Until it lands, the generated fixture next to it is used. */
        const val PACK_ASSET = "packs/math-ch2.v1.json"
        const val FIXTURE_ASSET = "packs/math-ch2-fixture.v1.json"

        /** Every pack goes through mathgen's import path: answers recomputed and prompt numbers checked. */
        fun parse(text: String): MathCourse = MathCourse(MathGen.parsePack(text))

        /** The reward word of a solved letter-code exercise, regenerated from its seed (never stored in the pack). */
        fun rewardWord(exercise: PackExercise): String? {
            val ref = exercise.generator?.takeIf { it.family == LETTER_CODE } ?: return null
            return try { MathGen.generate(ref).rewardWord } catch (_: IllegalArgumentException) { null }
        }
    }
}
