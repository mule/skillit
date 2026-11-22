package fi.skillit.engine.pack

/**
 * Integer arithmetic used to recompute stored answers: ASCII digits, `+ - * /`, parentheses and ASCII spaces.
 * `×`, `·` and `:` (the Finnish school division sign) are accepted too. Division must be exact; every value,
 * including intermediate ones, must fit an Int.
 */
class Expression private constructor(private val tree: Node, private val tokens: List<Token>) {
    val value: Int get() = tree.value

    /**
     * Intermediate results in Finnish school order, one reduction per step: the innermost (first-closing)
     * parentheses first; inside a group, multiplication and division left to right, then addition and
     * subtraction left to right. Parentheses that hold a single number are dropped without a step.
     * `30 + 5 * (3 + 6)` gives `[9, 45, 75]`. A bare number gives `[value]`.
     */
    fun steps(): List<Int> {
        val work = tokens.toMutableList()
        val steps = mutableListOf<Int>()
        while (work.size > 1) {
            val close = work.indexOfFirst { it == Token.Close }
            val open = if (close < 0) -1 else (close - 1 downTo 0).first { work[it] == Token.Open }
            val from = open + 1
            val to = if (close < 0) work.size else close
            if (to - from == 1) { // "(n)" -> "n"
                work.removeAt(close); work.removeAt(open); continue
            }
            val segment = from until to
            val op = segment.firstOrNull { (work[it] as? Token.Op)?.symbol?.let { s -> s in MUL_DIV } == true }
                ?: segment.first { work[it] is Token.Op }
            val a = (work[op - 1] as Token.Num).value
            val b = (work[op + 1] as Token.Num).value
            val result = apply((work[op] as Token.Op).symbol, a, b)
            steps += result
            repeat(3) { work.removeAt(op - 1) }
            work.add(op - 1, Token.Num(result))
        }
        return steps.ifEmpty { listOf(value) }
    }

    /** The operands of a pure multiplication chain such as `2 * 3 * 4`, or null for anything else. */
    fun factors(): List<Int>? = tree.factors()

    private sealed interface Node {
        val value: Int
        data class Number(override val value: Int) : Node
        data class Binary(val op: Char, val left: Node, val right: Node) : Node {
            override val value: Int = apply(op, left.value, right.value)
        }
        fun factors(): List<Int>? = when {
            this is Binary && op == '*' -> {
                val l = if (left is Number) listOf(left.value) else left.factors()
                val r = if (right is Number) listOf(right.value) else right.factors()
                if (l == null || r == null) null else l + r
            }
            else -> null
        }
    }

    private sealed interface Token {
        data class Num(val value: Int) : Token
        data class Op(val symbol: Char) : Token
        data object Open : Token
        data object Close : Token
    }

    companion object {
        const val MAX_LENGTH = 200
        const val MAX_DEPTH = 16

        /** Exactly the schema's alphabet: ASCII digits and space only, so no Unicode digit or space can hide in a number. */
        const val ALPHABET = "0123456789+-*/():×· "
        private const val MUL_DIV = "*/"

        /** Throws [IllegalArgumentException] for anything that is not a valid exact integer expression. */
        fun parse(text: String): Expression {
            val parser = Parser(normalize(text))
            val tree = parser.expression()
            require(parser.atEnd()) { "Unexpected '${parser.rest()}'" }
            return Expression(tree, parser.tokens)
        }

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

    /** Recursive descent with bounded nesting; also records the tokens so [steps] follows the written grouping. */
    private class Parser(val s: String) {
        var i = 0
        var depth = 0
        val tokens = mutableListOf<Token>()
        fun atEnd() = i == s.length
        fun rest() = s.substring(i)
        fun expression(): Node = chain("+-") { term() }
        fun term(): Node = chain("*/") { factor() }
        private inline fun chain(ops: String, next: () -> Node): Node {
            var left = next()
            while (!atEnd() && s[i] in ops) {
                val op = s[i++]
                tokens += Token.Op(op)
                left = Node.Binary(op, left, next())
            }
            return left
        }
        fun factor(): Node {
            require(!atEnd()) { "Expression ends too early" }
            if (s[i] == '(') {
                // Bounded so a hostile pack cannot overflow the stack (an Error the validator would not catch).
                require(++depth <= MAX_DEPTH) { "Parentheses nested too deeply" }
                i++; tokens += Token.Open
                val inner = expression()
                depth--
                require(!atEnd() && s[i] == ')') { "Missing ')'" }
                i++; tokens += Token.Close
                return inner
            }
            val start = i
            while (!atEnd() && s[i] in '0'..'9') i++
            require(i > start && i - start <= 9) { "Expected a number at '${rest()}'" }
            return Node.Number(s.substring(start, i).toInt()).also { tokens += Token.Num(it.value) }
        }
    }
}
