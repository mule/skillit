package fi.skillit.mathgen

import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.TableEntry
import fi.skillit.engine.pack.*
import kotlinx.serialization.json.*
import java.util.Random

/** Carry boxes written by the learner, and the final extra leading digit (not a carry box). */
data class CarryCount(val carries: Int, val overflow: Boolean)

data class GeneratedExercise(
    val exercise: PackExercise,
    val carries: Int = 0,
    val overflow: Boolean = false,
    val factors: List<Int> = emptyList(),
    /** Suggested mental multiplication order; the displayed factors retain their original order. */
    val smartOrder: List<Int> = factors,
    val rewardWord: String? = null,
    val slots: Map<String, Int> = emptyMap(),
)

/**
 * Offline, version-1 deterministic multiplication families. java.util.Random's specified algorithm is
 * shared by Android/JVM; changing draw order or family algorithms requires a new family version/ID.
 * Unknown keys, wrong JSON types and impossible difficulty requests throw IllegalArgumentException.
 */
object MathGen {
    val families: Set<String> = linkedSetOf(
        "tables", "tens-hundreds", "multi-factor", "column", "column-tens", "order-of-ops",
        "missing-factor", "product-grid", "multiples-of", "word", "letter-code",
    )
    val recomputers: Map<String, AnswerRecomputer> = families.associateWith { family ->
        AnswerRecomputer { ref ->
            require(ref.family == family) { "Wrong family for recomputer $family" }
            generate(ref).exercise.answer
        }
    }

    /** Counts nonzero carries into existing columns, plus a separate final overflow. */
    fun countCarries(multiplicand: Int, digit: Int): CarryCount {
        require(multiplicand in 10..999 && digit in 1..9)
        val digits = multiplicand.toString().reversed()
        var carry = 0
        var count = 0
        digits.forEachIndexed { index, c ->
            carry = ((c - '0') * digit + carry) / 10
            if (carry > 0 && index < digits.lastIndex) count++
        }
        return CarryCount(count, carry > 0)
    }

    private data class ColumnKey(val digits: Int, val carries: Int, val overflow: Boolean)
    // Finite exhaustive pools: generation never relies on an unbounded rejection loop.
    private val columns: Map<ColumnKey, List<Pair<Int, Int>>> by lazy {
        (10..999).flatMap { a -> (2..9).map { b -> a to b } }.groupBy { (a, b) ->
            val count = countCarries(a, b)
            ColumnKey(a.toString().length, count.carries, count.overflow)
        }
    }

    /**
     * column/column-tens: digits=2..3, carries=0..digits-1, overflow=boolean. `carries` counts
     * written carry cells, excluding the final leading digit: 234×5 has two carries and overflow.
     * column-tens uses the same carry profile for the nonzero multiplier digit (234×50).
     */
    fun generate(ref: GeneratorRef): GeneratedExercise {
        require(ref.family in families) { "Unknown family ${ref.family}" }
        require(ref.params.size <= 20 && ref.params.toString().length <= 1000) { "Generator params too large" }
        val p = Params(ref.params)
        val rng = Random(ref.seed)
        fun draw(min: Int, max: Int) = min + rng.nextInt(max - min + 1)
        var answer: AnswerSpec
        var expression: String? = null
        var prompt: String
        var hint = "Kerro luvut."
        var factors = emptyList<Int>()
        var smart = emptyList<Int>()
        var carry = CarryCount(0, false)
        var reward: String? = null
        var slots = emptyMap<String, Int>()
        fun numeric(text: String): AnswerSpec.Numeric {
            expression = text
            return AnswerSpec.Numeric(Expression.parse(text).value)
        }
        when (ref.family) {
            "tables" -> {
                val table = p.int("table", 2..10, draw(2, 10))
                val count = p.int("count", 2..10, 5)
                val start = draw(1, 11 - count)
                answer = AnswerSpec.FillTable(listOf(
                    (start until start + count).map { TableEntry.Given("$table · $it") },
                    (start until start + count).map { TableEntry.Blank("b$it", table * it, "$table * $it") },
                ))
                prompt = "Täydennä $table:n kertotaulun lukujono."
                hint = "Lisää aina $table edelliseen tuloon."
            }
            "tens-hundreds" -> {
                val zeros = p.int("zeros", 1..2, 1)
                val split = p.bool("split", false)
                require(!split || zeros == 2) { "split needs zeros=2" }
                val scale = if (zeros == 1) 10 else 100
                factors = if (split) listOf(draw(2, 9) * 10, draw(2, 9) * 10)
                    else listOf(draw(2, 9), draw(2, 9) * scale)
                prompt = "Laske ${factors.joinToString(" · ")}."
                answer = numeric(factors.joinToString(" * "))
            }
            "multi-factor" -> {
                val count = p.int("factorCount", 3..4, 3)
                val digits = p.int("digits", 1..2, 1)
                val scale = if (digits == 1) 1 else 10
                factors = (listOf(2 * scale, 5 * scale) + List(count - 2) { draw(2, 9) * scale }).shuffled(rng)
                smart = listOf(2 * scale, 5 * scale) + factors.toMutableList().apply { remove(2 * scale); remove(5 * scale) }
                prompt = "Laske ${factors.joinToString(" · ")}. Valitse helppo laskujärjestys."
                hint = "Kerro ensin ${2 * scale} ja ${5 * scale}."
                answer = numeric(factors.joinToString(" * "))
            }
            "column", "column-tens" -> {
                val digits = p.int("digits", 2..3, 3)
                val carries = p.int("carries", 0 until digits, 1)
                val overflow = p.bool("overflow", false)
                val pool = columns[ColumnKey(digits, carries, overflow)]
                require(!pool.isNullOrEmpty()) { "Impossible carry profile" }
                val (a, b) = pool[rng.nextInt(pool.size)]
                carry = countCarries(a, b)
                val multiplier = b * if (ref.family == "column-tens") 10 else 1
                factors = listOf(a, multiplier)
                answer = AnswerSpec.ColumnMultiplication.layout(a, multiplier)
                expression = "$a * $multiplier"
                prompt = "Laske allekkain $a · $multiplier."
                hint = "Aloita ykkösistä ja merkitse muistinumerot."
            }
            "order-of-ops" -> {
                val count = p.int("factorCount", 2..4, 2)
                val digits = p.int("digits", 1..2, 1)
                val min = if (digits == 1) 2 else 10
                val max = if (digits == 1) 9 else 20
                factors = List(count) { draw(min, max) }
                expression = "${draw(1, 9)} + " + factors.dropLast(1).joinToString(" * ") + " * (${factors.last()} + ${draw(1, 9)})"
                answer = AnswerSpec.Steps(Expression.parse(expression).steps())
                prompt = "Laske vaiheittain $expression."
                hint = "Laske ensin sulut, sitten kertolaskut ja lopuksi yhteenlaskut."
            }
            "missing-factor" -> {
                val count = p.int("factorCount", 2..4, 2)
                val digits = p.int("digits", 1..2, 1)
                factors = List(count) { draw(2, 9) * if (digits == 1) 1 else 10 }
                val blank = rng.nextInt(count)
                answer = AnswerSpec.MissingFactor(factors.mapIndexed { i, n -> if (i == blank) null else n }, factors.reduce(Int::times))
                expression = factors.joinToString(" * ")
                prompt = "Mikä luku puuttuu? ${factors.mapIndexed { i, n -> if (i == blank) "☐" else "$n" }.joinToString(" · ")} = ${answer.product}"
                hint = "Jaa tulo tunnettujen tekijöiden tulolla."
            }
            "product-grid" -> {
                val size = p.int("size", 2..5, 3)
                val rows = (2..10).toList().shuffled(rng).take(size)
                val cols = (2..10).toList().shuffled(rng).take(size)
                answer = AnswerSpec.FillTable(listOf(listOf(TableEntry.Given("·")) + cols.map { TableEntry.Given("$it") }) +
                    rows.mapIndexed { i, a -> listOf(TableEntry.Given("$a")) + cols.mapIndexed { j, b -> TableEntry.Blank("b$i-$j", a * b, "$a * $b") } })
                prompt = "Täydennä kertolaskuruudukko."
                hint = "Kerro rivin alussa oleva luku sarakkeen yläpuolella olevalla luvulla."
            }
            "multiples-of" -> {
                val max = p.int("maxTable", 3..10, 10)
                val n = draw(2, max) * draw(1, 10)
                answer = AnswerSpec.FillTable(listOf(
                    (2..max).map { TableEntry.Given("$it:n kertotaulu") },
                    (2..max).map { TableEntry.Blank("t$it", if (n % it == 0 && n / it in 1..10) 1 else 0) },
                ))
                prompt = "Mihin kertotauluihin luku $n kuuluu? Merkitse 1 jos kuuluu, muuten 0."
                hint = "Käytä kunkin kertotaulun kertoimia 1–10."
            }
            "word" -> {
                val template = p.int("template", 0..2, draw(0, 2))
                val digits = p.int("digits", 1..2, 1)
                val a = draw(2, 9)
                val b = if (digits == 1) draw(2, 9) else draw(10, 99)
                slots = p.slots("slots", mapOf("count" to a, "amount" to b))
                val text = p.text("text", when (template) {
                    0 -> "Pakkauksia on {count}. Jokaisessa on {amount} kynää. Kuinka monta kynää on yhteensä?"
                    1 -> "Yksi peli maksaa {amount} euroa. Kuinka paljon {count} peliä maksaa?"
                    else -> "Luet {amount} sivua viikossa {count} viikon ajan. Kuinka monta sivua luet?"
                })
                prompt = substitute(text, slots)
                answer = numeric(substitute(p.text("expression", "{count} * {amount}"), slots))
                factors = Expression.parse(requireNotNull(expression)).factors().orEmpty()
            }
            else -> { // letter-code: each displayed product maps unambiguously to one letter.
                val words = listOf("KISSA", "KOIRA", "SAUNA", "TÄHTI")
                reward = words[p.int("word", words.indices, draw(0, words.lastIndex))]
                val products = (2..9).flatMap { a -> (2..9).map { b -> Triple(a * b, a, b) } }
                    .distinctBy { it.first }.shuffled(rng).take(reward.toSet().size)
                val mapping = reward.toSet().zip(products).toMap()
                answer = AnswerSpec.FillTable(listOf(
                    reward.map { c -> val t = mapping.getValue(c); TableEntry.Given("${t.second} · ${t.third}") },
                    reward.mapIndexed { i, c -> val t = mapping.getValue(c); TableEntry.Blank("l$i", t.first, "${t.second} * ${t.third}") },
                ))
                prompt = "Ratkaise laskut ja muodosta sana: " + mapping.entries.joinToString(", ") { (c, t) -> "${t.first}=$c" }
                hint = "Vaihda jokainen laskettu tulo sitä vastaavaksi kirjaimeksi."
            }
        }
        p.finish()
        answer.validate()
        require(prompt.length <= 500) { "Rendered prompt too long" }
        val paramsHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical(ref.params).toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }
        val exercise = PackExercise("${ref.family}-${java.lang.Long.toUnsignedString(ref.seed, 36)}-$paramsHash", prompt, hint,
            "Oikeat vastaukset saat laskemalla vihjeen mukaan.", answer, expression, ref)
        return GeneratedExercise(exercise, carry.carries, carry.overflow, factors, smart.ifEmpty { factors }, reward, slots)
    }

    /** Regenerates the complete prompt/answer/provenance as well as difficulty metadata. */
    fun problems(item: GeneratedExercise): List<String> = try {
        val ref = requireNotNull(item.exercise.generator) { "Missing generator" }
        buildList {
            if (generate(ref) != item) add("Exercise or metadata differs from generator")
            addAll(item.exercise.problems(recomputers))
        }
    } catch (e: IllegalArgumentException) { listOf(e.message ?: "Invalid generator") }

    fun samplePack(): ContentPack = ContentPack(1, "mathgen-sample", "math", 4, 1,
        "Kertolaskua – generoitu esimerkkipaketti", units = listOf(PackUnit("chapter-2", "Kertolaskua",
            exercises = families.mapIndexed { i, family -> generate(GeneratorRef(family, seed = i.toLong())).exercise })))

    private class Params(private val values: JsonObject) {
        private val used = mutableSetOf<String>()
        fun int(key: String, range: IntRange, default: Int): Int {
            used += key
            val value = values[key]?.let {
                val primitive = it as? JsonPrimitive
                require(primitive != null && !primitive.isString) { "$key must be an integer" }
                requireNotNull(primitive.intOrNull) { "$key must be an integer" }
            } ?: default
            require(value in range) { "$key must be in $range" }
            return value
        }
        fun bool(key: String, default: Boolean): Boolean {
            used += key
            return values[key]?.let {
                val primitive = it as? JsonPrimitive
                require(primitive != null && !primitive.isString) { "$key must be boolean" }
                requireNotNull(primitive.booleanOrNull) { "$key must be boolean" }
            } ?: default
        }
        fun text(key: String, default: String): String {
            used += key
            return values[key]?.let {
                val primitive = it as? JsonPrimitive
                require(primitive != null && primitive.isString) { "$key must be text" }
                primitive.content.also { value -> require(value.isNotBlank() && value.length <= 500) { "$key length out of range" } }
            } ?: default
        }
        fun slots(key: String, default: Map<String, Int>): Map<String, Int> {
            used += key
            val obj = values[key]?.let { requireNotNull(it as? JsonObject) { "$key must be an object" } } ?: return default
            require(obj.size in 1..10) { "Need 1..10 slots" }
            return obj.mapValues { (name, value) ->
                require(Regex("[a-z][a-z0-9]{0,15}").matches(name)) { "Invalid slot name" }
                val primitive = value as? JsonPrimitive
                require(primitive != null && !primitive.isString) { "Slots must be integers" }
                requireNotNull(primitive.intOrNull).also { require(it in 1..999) { "Slot must be 1..999" } }
            }
        }
        fun finish() { require(values.keys.all { it in used }) { "Unknown params: ${values.keys - used}" } }
    }

    private fun canonical(value: JsonElement): String = when (value) {
        is JsonObject -> value.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (key, v) ->
            "${JsonPrimitive(key)}:${canonical(v)}"
        }
        is JsonArray -> value.joinToString(",", "[", "]") { canonical(it) }
        else -> value.toString()
    }

    private fun substitute(template: String, slots: Map<String, Int>): String {
        val rendered = Regex("\\{([a-z][a-z0-9]{0,15})}").replace(template) { match ->
            requireNotNull(slots[match.groupValues[1]]) { "Unknown slot ${match.groupValues[1]}" }.toString()
        }
        require('{' !in rendered && '}' !in rendered) { "Malformed slot placeholder" }
        return rendered
    }

    private fun <T> List<T>.shuffled(rng: Random): List<T> = toMutableList().apply {
        for (i in lastIndex downTo 1) { val j = rng.nextInt(i + 1); val value = this[i]; this[i] = this[j]; this[j] = value }
    }
}
