package fi.skillit.engine.pack

import fi.skillit.engine.AnswerSpec

/**
 * Rebuilds the answer of a generated exercise from its [GeneratorRef]. `mathgen` (#21) registers one per family;
 * the engine itself ships none, so a generator-only exercise is rejected until its family is registered.
 */
fun interface AnswerRecomputer {
    fun recompute(generator: GeneratorRef): AnswerSpec
}

/** Recomputes every stored expected value and reports any that disagree. Answers are never trusted as written. */
internal object AnswerCheck {
    fun problems(exercise: PackExercise, generators: Map<String, AnswerRecomputer>): List<String> = buildList {
        val answer = exercise.answer
        exercise.generator?.let { generator ->
            val recomputer = generators[generator.family]
            when {
                recomputer != null -> {
                    val rebuilt = try { recomputer.recompute(generator) } catch (e: IllegalArgumentException) {
                        add("generator ${generator.family} failed: ${e.message}"); null
                    }
                    if (rebuilt != null && rebuilt != answer) add("answer differs from generator ${generator.family} (seed ${generator.seed})")
                }
                exercise.expression == null && !(answer is AnswerSpec.FillTable && answer.blanks.all { it.expression != null }) ->
                    add("cannot check answer: no recomputer for generator family '${generator.family}'")
            }
        }
        if (answer is AnswerSpec.FillTable) {
            answer.blanks.forEach { blank ->
                val text = blank.expression
                if (text == null) { if (exercise.generator == null) add("blank ${blank.id} needs an expression") }
                else evaluate(text, "blank ${blank.id}")?.let { if (it.value != blank.expected) add("blank ${blank.id}: $text = ${it.value}, not ${blank.expected}") }
            }
        }
        val text = exercise.expression ?: return@buildList
        val expression = evaluate(text, "expression") ?: return@buildList
        when (answer) {
            is AnswerSpec.Numeric -> if (expression.value != answer.expected) add("$text = ${expression.value}, not ${answer.expected}")
            is AnswerSpec.Steps -> if (expression.steps() != answer.values) add("steps of $text are ${expression.steps()}, not ${answer.values}")
            is AnswerSpec.MissingFactor -> {
                val filled = answer.factors.map { it ?: answer.expected }
                if (expression.factors() != filled) add("$text is not the product ${filled.joinToString(" * ")}")
                if (answer.product != null && expression.value != answer.product) add("$text = ${expression.value}, not ${answer.product}")
            }
            is AnswerSpec.ColumnMultiplication -> if (expression.factors() != answer.factors) add("$text does not match factors ${answer.factors}")
            is AnswerSpec.FillTable, is AnswerSpec.Choice -> Unit // blanks are checked above; choices are text
        }
    }

    private fun MutableList<String>.evaluate(text: String, what: String): Expression? =
        try { Expression.parse(text) } catch (e: IllegalArgumentException) { add("$what '$text' is invalid: ${e.message}"); null }
}
