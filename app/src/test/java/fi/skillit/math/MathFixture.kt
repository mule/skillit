package fi.skillit.math

import fi.skillit.engine.pack.ContentPack
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackUnit
import fi.skillit.engine.pack.SourceRef
import fi.skillit.mathgen.MathGen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The development stand-in for #23's reviewed chapter 2 pack: one unit per workbook lesson, built only
 * from mathgen families. Titles and family choices are placeholders until the real pack lands at
 * [MathCourse.PACK_ASSET]. Regenerate with `UPDATE_MATH_FIXTURE=1` (see [MathFixtureTest]).
 */
object MathFixture {
    private fun p(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
        k to when (v) { is Int -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
    })
    private data class Mix(val family: String, val params: JsonObject, val count: Int)
    private fun mix(family: String, count: Int, vararg params: Pair<String, Any>) = Mix(family, p(*params), count)

    private val lessons: List<Triple<Int, String, List<Mix>>> = listOf(
        Triple(14, "Kertotaulut", listOf(mix("tables", 6), mix("product-grid", 4, "size" to 3), mix("multiples-of", 3), mix("missing-factor", 4))),
        Triple(18, "Kertolaskuja kymmenillä ja sadoilla", listOf(mix("tens-hundreds", 6), mix("tens-hundreds", 5, "zeros" to 2), mix("tens-hundreds", 5, "zeros" to 2, "split" to true))),
        Triple(19, "Monta tekijää", listOf(mix("multi-factor", 8), mix("multi-factor", 8, "digits" to 2))),
        Triple(20, "Allekkain kertominen", listOf(mix("column", 4, "digits" to 2, "carries" to 0), mix("column", 4, "digits" to 3, "carries" to 0),
            mix("column", 4, "digits" to 2, "carries" to 1), mix("column", 4, "digits" to 3, "carries" to 1))),
        Triple(21, "Muistinumerot", listOf(mix("column", 6, "digits" to 3, "carries" to 2), mix("column", 5, "digits" to 3, "carries" to 1, "overflow" to true),
            mix("column", 5, "digits" to 2, "carries" to 1, "overflow" to true))),
        Triple(22, "Kertominen kymmenillä allekkain", listOf(mix("column-tens", 8, "digits" to 2, "carries" to 1), mix("column-tens", 8, "digits" to 3, "carries" to 1))),
        Triple(26, "Laskujärjestys ja sanalliset tehtävät", listOf(mix("order-of-ops", 8), mix("word", 8))),
        Triple(27, "Kappaleen koe", listOf(mix("tables", 2), mix("tens-hundreds", 2), mix("multi-factor", 2), mix("column", 3, "digits" to 3, "carries" to 2),
            mix("column-tens", 2), mix("order-of-ops", 2), mix("word", 2), mix("missing-factor", 2, "digits" to 2))),
    )

    fun build(): ContentPack {
        var seed = 1000L
        val units = lessons.map { (lesson, title, mixes) ->
            val exercises = mixes.flatMap { m -> List(m.count) { MathGen.generate(GeneratorRef(m.family, m.params, seed++)).exercise } } +
                // Two letter codes per lesson: one is the reward word at the end of each session.
                List(2) { MathGen.generate(GeneratorRef("letter-code", p("word" to ((lesson + it) % 4)), seed++)).exercise }
            PackUnit(if (lesson == MathCourse.CHAPTER_TEST) "lesson-27-test" else "lesson-$lesson", title.replace("­", ""),
                SourceRef(2, lesson), exercises)
        }
        return ContentPack(1, "math-ch2-fixture", "math", 4, 1, "Kertolaskua (kehitysversio)", units = units)
    }
}
