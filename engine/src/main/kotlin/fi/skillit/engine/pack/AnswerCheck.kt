package fi.skillit.engine.pack

import fi.skillit.engine.AnswerSpec

/**
 * Rebuilds the answer of a generated exercise from its [GeneratorRef]. `mathgen` (#21) registers one per family;
 * the engine itself ships none, so a generator-only exercise is rejected until its family is registered.
 *
 * Contract: [recompute] is pure and deterministic for the same [GeneratorRef]. For params it cannot handle it
 * should throw [IllegalArgumentException]; any [RuntimeException] it throws is reported as a pack problem
 * (the pack is rejected, never the app crashed). It must not do I/O.
 */
fun interface AnswerRecomputer {
    fun recompute(generator: GeneratorRef): AnswerSpec
}

/**
 * Recomputes every stored expected value and reports any that disagree. Answers are never trusted as written:
 * an answer counts as verified only by an exercise expression (per-blank expressions for a table), or by a
 * registered [AnswerRecomputer] that ran and rebuilt exactly the stored answer. An unknown generator family is
 * always a problem, whatever else the exercise carries.
 */
internal object AnswerCheck {
    fun problems(exercise: PackExercise, generators: Map<String, AnswerRecomputer>): List<String> = buildList {
        val answer = exercise.answer
        var verifiedByGenerator = false
        exercise.generator?.let { generator ->
            val recomputer = generators[generator.family]
            if (recomputer == null) add("unknown generator family '${generator.family}': no recomputer is registered")
            else {
                val rebuilt = try { recomputer.recompute(generator) } catch (e: RuntimeException) {
                    add("generator ${generator.family} failed: ${e::class.simpleName}: ${e.message}"); null
                }
                if (rebuilt == answer) verifiedByGenerator = true
                else if (rebuilt != null) add("answer differs from generator ${generator.family} (seed ${generator.seed})")
            }
        }
        val text = exercise.expression
        val expression = text?.let { evaluate(it, "expression") }
        val unverified = { if (text == null && !verifiedByGenerator) add("cannot check answer: needs an expression or a registered generator") }
        when (answer) {
            is AnswerSpec.FillTable -> {
                if (text != null) add("a table is checked per blank: put the expression on each blank, not on the exercise")
                answer.blanks.forEach { blank ->
                    val blankText = blank.expression
                    if (blankText == null) { if (!verifiedByGenerator) add("blank ${blank.id} needs an expression") }
                    else evaluate(blankText, "blank ${blank.id}")?.let {
                        if (it.value != blank.expected) add("blank ${blank.id}: $blankText = ${it.value}, not ${blank.expected}")
                    }
                }
            }
            is AnswerSpec.Choice -> if (expression != null) {
                // Math choices carry the number each one stands for; the computed result picks the correct key.
                val values = answer.choices.map { it.value }
                if (values.any { it == null }) add("every choice needs a value to check against $text")
                else {
                    if (values.distinct().size != values.size) add("choice values must be unique")
                    val matching = answer.choices.filter { it.value == expression.value }
                    when {
                        matching.size != 1 -> add("exactly one choice must have the value ${expression.value} of $text")
                        matching.single().id != answer.correctId -> add("correctId is ${answer.correctId}, but $text = ${expression.value} is choice ${matching.single().id}")
                    }
                }
            } else unverified()
            is AnswerSpec.Numeric -> if (expression != null) {
                if (expression.value != answer.expected) add("$text = ${expression.value}, not ${answer.expected}")
            } else unverified()
            is AnswerSpec.Steps -> if (expression != null) {
                if (expression.steps() != answer.values) add("steps of $text are ${expression.steps()}, not ${answer.values}")
            } else unverified()
            is AnswerSpec.MissingFactor -> if (expression != null) {
                val filled = answer.factors.map { it ?: answer.expected }
                if (expression.factors() != filled) add("$text is not the product ${filled.joinToString(" * ")}")
                if (answer.product != null && expression.value != answer.product) add("$text = ${expression.value}, not ${answer.product}")
            } else unverified()
            is AnswerSpec.ColumnMultiplication -> if (expression != null) {
                if (expression.factors() != answer.factors) add("$text does not match factors ${answer.factors}")
            } else unverified()
        }
    }

    private fun MutableList<String>.evaluate(text: String, what: String): Expression? =
        try { Expression.parse(text) } catch (e: IllegalArgumentException) { add("$what '$text' is invalid: ${e.message}"); null }
}
