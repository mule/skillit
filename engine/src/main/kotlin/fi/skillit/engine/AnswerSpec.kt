package fi.skillit.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a correct answer looks like. Every expected value is stored in the spec and, where the spec carries
 * its own inputs (factors, products), is re-derived from them by [validate], so a pack can never ship an
 * answer that the engine has not computed itself.
 *
 * Non-choice answers are checked per cell. Cells are listed in solving order, so the first wrong cell in
 * [CheckResult.cells] is the one the child should look at first.
 */
/** Size limits shared by the model and `content-schema/pack.v1.json`; keep both in sync. */
object PackLimits {
    const val TITLE = 200
    const val PROMPT = 500
    const val HINT = 1000
    const val EXPLANATION = 1000
    const val SUBJECT = 32
    const val UNITS = 50
    const val EXERCISES = 100
    const val PAGES = 20
    const val PARAMS = 20
    const val PARAMS_JSON = 1000
    const val CHOICES = 10
    const val LABEL = 200
    const val TABLE_SIDE = 20
    const val CELL_TEXT = 50
    const val CELL_ID = 32
    const val DIGIT_CELLS = 200
}

@Serializable
sealed interface AnswerSpec {
    /** Throws [IllegalArgumentException] when the spec is inconsistent. */
    fun validate()

    /** Null when [input] does not fit this spec (wrong input type or unknown cell); the session ignores it. */
    fun check(input: AnswerInput): CheckResult?

    /** Multiple choice: the M1/M2 behaviour, used by music. */
    @Serializable @SerialName("choice")
    data class Choice(val choices: List<fi.skillit.engine.Choice>, val correctId: String) : AnswerSpec {
        override fun validate() {
            require(choices.size in 2..PackLimits.CHOICES && choices.map { it.id }.distinct().size == choices.size) { "Choices must be 2..${PackLimits.CHOICES} with unique ids" }
            require(choices.all { it.id.length in 1..PackLimits.CELL_ID && it.label.length in 1..PackLimits.LABEL }) { "Choice id or label too long" }
            require(choices.count { it.id == correctId } == 1) { "Exactly one choice must be correct" }
        }
        override fun check(input: AnswerInput): CheckResult? {
            if (input !is AnswerInput.Choice || choices.none { it.id == input.id }) return null
            return CheckResult(input.id == correctId, emptyList())
        }
    }

    /** One integer, written with the pen or typed. */
    @Serializable @SerialName("numeric")
    data class Numeric(val expected: Int) : AnswerSpec {
        override fun validate() = Unit
        override fun check(input: AnswerInput) = checkCells(listOf(ExpectedCell(CELL, expected)), input)
        companion object { const val CELL = "answer" }
    }

    /**
     * Column multiplication `factors[0] × factors[1]`. [cells] hold one digit each; see [layout] for the
     * standard Finnish written layout and cell ids. Carry cells are scratch work and are optional by default.
     */
    @Serializable @SerialName("column-multiplication")
    data class ColumnMultiplication(val factors: List<Int>, val cells: List<DigitCell>) : AnswerSpec {
        override fun validate() {
            require(factors.size == 2 && factors.all { it in 1..99_999 }) { "Column multiplication needs two factors 1..99999" }
            require(factors[0].toLong() * factors[1] <= Int.MAX_VALUE) { "Product is too large" }
            require(cells.size in 1..PackLimits.DIGIT_CELLS && cells.map { it.id }.distinct().size == cells.size) { "Cell ids must be unique" }
            val reference = layout(factors[0], factors[1]).cells.associateBy { it.id }
            cells.forEach { cell ->
                val ref = requireNotNull(reference[cell.id]) { "Unknown cell ${cell.id}" }
                require(cell.kind == ref.kind && cell.row == ref.row && cell.column == ref.column && cell.expected == ref.expected) {
                    "Cell ${cell.id} does not match the computed layout"
                }
            }
            val resultIds = reference.values.filter { it.kind == DigitKind.RESULT }.map { it.id }.toSet()
            require(cells.filter { it.kind == DigitKind.RESULT && it.required }.map { it.id }.toSet() == resultIds) {
                "Every result digit must be a required cell"
            }
        }
        override fun check(input: AnswerInput) =
            checkCells(cells.map { ExpectedCell(it.id, it.expected, it.required) }, input)

        companion object {
            /**
             * Standard layout, cells in solving order. Ids: `p{row}.{col}` partial-product digit, `c{row}.{col}`
             * carry written above column `col` while multiplying by multiplier digit `row`, `s{col}` carry
             * while adding the partial products, `r{col}` result digit. Column 0 is the ones place.
             * A one-digit multiplier has no partial rows: its digits are the result.
             */
            fun layout(multiplicand: Int, multiplier: Int): ColumnMultiplication {
                require(multiplicand > 0 && multiplier > 0)
                val top = multiplicand.toString().reversed().map { it - '0' }
                val bottom = multiplier.toString().reversed().map { it - '0' }
                val cells = mutableListOf<DigitCell>()
                val single = bottom.size == 1
                bottom.forEachIndexed { row, digit ->
                    var carry = 0
                    val partial = (multiplicand.toLong() * digit).toString().reversed().map { it - '0' }
                    partial.forEachIndexed { i, value ->
                        val column = row + i
                        cells += if (single) DigitCell("r$column", DigitKind.RESULT, 0, column, value)
                            else DigitCell("p$row.$column", DigitKind.PARTIAL, row, column, value)
                        if (i < top.size) {
                            carry = (top[i] * digit + carry) / 10
                            if (carry > 0 && i + 1 < top.size) cells += DigitCell("c$row.${column + 1}", DigitKind.CARRY, row, column + 1, carry, required = false)
                        }
                    }
                }
                if (!single) {
                    val partials = bottom.mapIndexed { row, digit -> multiplicand.toLong() * digit * pow10(row) }
                    val result = (multiplicand.toLong() * multiplier).toString().reversed().map { it - '0' }
                    var carry = 0
                    result.forEachIndexed { column, value ->
                        cells += DigitCell("r$column", DigitKind.RESULT, 0, column, value)
                        val sum = partials.sumOf { digitAt(it, column) } + carry
                        carry = sum / 10
                        val more = partials.any { digitAt(it, column + 1) > 0 }
                        if (carry > 0 && more) cells += DigitCell("s${column + 1}", DigitKind.SUM_CARRY, 0, column + 1, carry, required = false)
                    }
                }
                return ColumnMultiplication(listOf(multiplicand, multiplier), cells)
            }
            private fun pow10(n: Int) = (1..n).fold(1L) { acc, _ -> acc * 10 }
            private fun digitAt(value: Long, column: Int) = ((value / pow10(column)) % 10).toInt()
        }
    }

    /** Stepwise evaluation such as order of operations; `values.last()` is the final answer. Cells `s0`, `s1`, … */
    @Serializable @SerialName("steps")
    data class Steps(val values: List<Int>, val labels: List<String> = emptyList()) : AnswerSpec {
        override fun validate() {
            require(values.isNotEmpty() && values.size <= 10) { "Steps need 1..10 values" }
            require(labels.isEmpty() || labels.size == values.size) { "Give a label for every step or none" }
            require(labels.all { it.length <= PackLimits.LABEL }) { "Step label too long" }
        }
        override fun check(input: AnswerInput) =
            checkCells(values.mapIndexed { i, value -> ExpectedCell("s$i", value) }, input)
    }

    /** A grid such as a times table with some blanks; blanks are checked in row-major order. */
    @Serializable @SerialName("fill-table")
    data class FillTable(val rows: List<List<TableEntry>>) : AnswerSpec {
        val blanks: List<TableEntry.Blank> get() = rows.flatten().filterIsInstance<TableEntry.Blank>()
        override fun validate() {
            require(rows.isNotEmpty() && rows.all { it.size == rows[0].size && it.isNotEmpty() }) { "Table must be rectangular" }
            require(rows.size <= PackLimits.TABLE_SIDE && rows[0].size <= PackLimits.TABLE_SIDE) { "Table is larger than ${PackLimits.TABLE_SIDE}×${PackLimits.TABLE_SIDE}" }
            require(rows.flatten().all { when (it) {
                is TableEntry.Given -> it.text.length <= PackLimits.CELL_TEXT
                is TableEntry.Blank -> it.id.length in 1..PackLimits.CELL_ID
            } }) { "Table text or blank id too long" }
            require(blanks.isNotEmpty() && blanks.map { it.id }.distinct().size == blanks.size) { "Table needs blanks with unique ids" }
        }
        override fun check(input: AnswerInput) = checkCells(blanks.map { ExpectedCell(it.id, it.expected) }, input)
    }

    /** `factors[0] × factors[1] × … = product` with exactly one unknown (null); the unknown is computed. */
    @Serializable @SerialName("missing-factor")
    data class MissingFactor(val factors: List<Int?>, val product: Int? = null) : AnswerSpec {
        /** The computed value of the blank. */
        val expected: Int get() {
            val known = knownProduct()
            return if (product == null) known.toInt() else (product / known).toInt()
        }
        override fun validate() {
            require(factors.size in 2..4 && (factors + product).count { it == null } == 1) { "Exactly one unknown" }
            require(factors.all { it == null || it in 0..100_000 } && (product == null || product >= 0)) { "Out of range" }
            val known = knownProduct()
            if (product != null) require(known != 0L && product % known == 0L) { "Product is not divisible by the known factors" }
        }
        override fun check(input: AnswerInput) = checkCells(listOf(ExpectedCell(CELL, expected)), input)
        /** Bounded after every step, so four large factors cannot wrap around in Long. */
        private fun knownProduct(): Long = factors.filterNotNull().fold(1L) { acc, f ->
            (acc * f).also { require(it <= Int.MAX_VALUE) { "Product is too large" } }
        }
        companion object { const val CELL = "missing" }
    }
}

@Serializable
enum class DigitKind {
    @SerialName("carry") CARRY,
    @SerialName("partial") PARTIAL,
    @SerialName("sum-carry") SUM_CARRY,
    @SerialName("result") RESULT,
}

@Serializable
data class DigitCell(
    val id: String,
    val kind: DigitKind,
    val row: Int,
    val column: Int,
    val expected: Int,
    val required: Boolean = true,
)

@Serializable
sealed interface TableEntry {
    @Serializable @SerialName("given") data class Given(val text: String) : TableEntry
    /** [expression] lets the pack validator recompute [expected] when the exercise has no generator. */
    @Serializable @SerialName("blank") data class Blank(val id: String, val expected: Int, val expression: String? = null) : TableEntry
}

/** What the child gave. Non-choice answers are a map from cell id to the written integer (null = left empty). */
@Serializable
sealed interface AnswerInput {
    @Serializable @SerialName("choice") data class Choice(val id: String) : AnswerInput
    @Serializable @SerialName("cells") data class Cells(val values: Map<String, Int?>) : AnswerInput

    companion object {
        fun number(value: Int?) = Cells(mapOf(AnswerSpec.Numeric.CELL to value))
        fun steps(vararg values: Int?) = Cells(values.withIndex().associate { "s${it.index}" to it.value })
    }
}

data class CellResult(val id: String, val expected: Int, val given: Int?, val required: Boolean = true) {
    /** An empty optional cell (an unwritten carry) is not wrong; anything written must be right. */
    val wrong: Boolean get() = if (required) given != expected else given != null && given != expected
}

/** [correct] depends only on required cells, so XP never depends on scratch work such as carries. */
data class CheckResult(val correct: Boolean, val cells: List<CellResult>) {
    /** First cell to highlight, in solving order; may be a wrong optional carry even when [correct]. */
    val firstWrong: CellResult? get() = cells.firstOrNull { it.wrong }
}

private data class ExpectedCell(val id: String, val expected: Int, val required: Boolean = true)

private fun checkCells(expected: List<ExpectedCell>, input: AnswerInput): CheckResult? {
    if (input !is AnswerInput.Cells) return null
    val ids = expected.map { it.id }.toSet()
    if (!ids.containsAll(input.values.keys)) return null
    val cells = expected.map { CellResult(it.id, it.expected, input.values[it.id], it.required) }
    return CheckResult(cells.none { it.required && it.wrong }, cells)
}
