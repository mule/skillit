package fi.skillit.engine.pack

/**
 * Integer arithmetic used to recompute stored answers: digits, `+ - * /`, parentheses and spaces. `×`, `·`
 * and `:` (the Finnish school division sign) are accepted too. Division must be exact; values must fit an Int.
 * Precedence and left-to-right order follow school rules, so [steps] is the order a child evaluates in.
 */
sealed interface Expression {
    val value: Int

    data class Number(override val value: Int) : Expression
    data class Binary(val op: Char, val left: Expression, val right: Expression) : Expression {
        override val value: Int = apply(op, left.value, right.value)
    }

    /** Intermediate results in evaluation order (one per operator); the last one is the final value. */
    fun steps(): List<Int> = when (this) {
        is Number -> listOf(value)
        is Binary -> (left.steps().takeIf { left is Binary }.orEmpty()) +
            (right.steps().takeIf { right is Binary }.orEmpty()) + value
    }

    /** The operands of a pure multiplication chain such as `2 * 3 * 4`, or null for anything else. */
    fun factors(): List<Int>? = when {
        this is Binary && op == '*' -> {
            val l = if (left is Number) listOf(left.value) else left.factors()
            val r = if (right is Number) listOf(right.value) else right.factors()
            if (l == null || r == null) null else l + r
        }
        else -> null
    }

    companion object {
        const val MAX_LENGTH = 200
        const val MAX_DEPTH = 16

        /** Throws [IllegalArgumentException] for anything that is not a valid exact integer expression. */
        fun parse(text: String): Expression = Parser(normalize(text)).run { expression().also { require(atEnd()) { "Unexpected '${rest()}'" } } }

        /** Exactly the schema's alphabet: ASCII digits and space only, so no Unicode digit or space can hide in a number. */
        const val ALPHABET = "0123456789+-*/():×· "

        private fun normalize(text: String): String {
            require(text.length <= MAX_LENGTH) { "Expression is too long" }
            require(text.all { it in ALPHABET }) { "Only digits, + - * / ( ) × · : and spaces are allowed" }
            require(!Regex("[0-9] +[0-9]").containsMatchIn(text)) { "Space inside a number" }
            return text.replace('×', '*').replace('·', '*').replace(':', '/').replace(" ", "")
        }

        private fun apply(op: Char, a: Int, b: Int): Int {
            val result: Long = when (op) {
                '+' -> a.toLong() + b
                '-' -> a.toLong() - b
                '*' -> a.toLong() * b
                '/' -> { require(b != 0 && a % b == 0) { "$a / $b is not exact" }; a.toLong() / b }
                else -> throw IllegalArgumentException("Unknown operator $op")
            }
            require(result in Int.MIN_VALUE..Int.MAX_VALUE) { "Value out of range" }
            return result.toInt()
        }
    }

    private class Parser(val s: String) {
        var i = 0
        var depth = 0
        fun atEnd() = i == s.length
        fun rest() = s.substring(i)
        fun expression(): Expression = chain("+-") { term() }
        fun term(): Expression = chain("*/") { factor() }
        private inline fun chain(ops: String, next: () -> Expression): Expression {
            var left = next()
            while (!atEnd() && s[i] in ops) { val op = s[i++]; left = Binary(op, left, next()) }
            return left
        }
        fun factor(): Expression {
            require(!atEnd()) { "Expression ends too early" }
            if (s[i] == '(') {
                // Bounded so a hostile pack cannot overflow the stack (an Error the validator would not catch).
                require(++depth <= MAX_DEPTH) { "Parentheses nested too deeply" }
                i++; val inner = expression(); depth--
                require(!atEnd() && s[i] == ')') { "Missing ')'" }; i++; return inner
            }
            val start = i
            while (!atEnd() && s[i] in '0'..'9') i++
            require(i > start && i - start <= 9) { "Expected a number at '${rest()}'" }
            return Number(s.substring(start, i).toInt())
        }
    }
}
