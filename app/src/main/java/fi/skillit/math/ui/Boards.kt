package fi.skillit.math.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.CheckResult
import fi.skillit.engine.DigitKind
import fi.skillit.engine.TableEntry
import fi.skillit.engine.pack.Expression
import fi.skillit.engine.pack.PackExercise

/** Finnish place names, column 0 = ones. */
internal val placeNames = listOf("ykköset", "kymmenet", "sadat", "tuhannet", "kymmenettuhannet", "sadattuhannet", "miljoonat")
internal fun placeName(column: Int) = placeNames.getOrElse(column) { "sarake ${column + 1}" }

/** What a board is: the cells in solving order, which must be filled, and which board draws them. */
sealed interface BoardKind {
    val cells: List<InputCell>
    val required: Set<String>

    data class Column(val spec: AnswerSpec.ColumnMultiplication) : BoardKind {
        override val cells = spec.cells.map { c ->
            val single = spec.factors[1] < 10
            val what = when (c.kind) {
                DigitKind.CARRY -> if (single) "Muistinumero" else "Muistinumero, ${c.row + 1}. osatulo"
                DigitKind.SUM_CARRY -> "Yhteenlaskun muistinumero"
                DigitKind.PARTIAL -> "${c.row + 1}. osatulo"
                DigitKind.RESULT -> "Tulos"
            }
            InputCell(c.id, "$what, ${placeName(c.column)}", 1, small = c.kind == DigitKind.CARRY || c.kind == DigitKind.SUM_CARRY)
        }
        override val required = spec.cells.filter { it.required }.map { it.id }.toSet()
    }
    data class Steps(val spec: AnswerSpec.Steps) : BoardKind {
        override val cells = spec.values.indices.map { i ->
            val label = spec.labels.getOrNull(i)?.takeIf { it.isNotBlank() } ?: if (i == spec.values.lastIndex) "Vastaus" else "Vaihe ${i + 1}"
            InputCell("s$i", label, NUMBER_DIGITS)
        }
        override val required = cells.map { it.id }.toSet()
    }
    data class Table(val spec: AnswerSpec.FillTable) : BoardKind {
        override val cells = spec.rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, entry ->
            (entry as? TableEntry.Blank)?.let { blank ->
                val column = (spec.rows.getOrNull(0)?.getOrNull(c) as? TableEntry.Given)?.text?.takeIf { r > 0 }
                val rowHead = (row.getOrNull(0) as? TableEntry.Given)?.text?.takeIf { c > 0 }
                val label = when {
                    rowHead != null && column != null -> "$rowHead · $column"
                    column != null -> column
                    rowHead != null -> rowHead
                    else -> "Ruutu ${r + 1}, ${c + 1}"
                }
                // As many boxes as the longest answer in the table (1 for a 1/0 table): one shared column width.
                val digits = spec.blanks.maxOf { it.expected.toString().length }
                InputCell(blank.id, label, digits)
            }
        } }
        override val required = cells.map { it.id }.toSet()
    }
    data class Missing(val spec: AnswerSpec.MissingFactor) : BoardKind {
        override val cells = listOf(InputCell(AnswerSpec.MissingFactor.CELL, "Puuttuva luku", NUMBER_DIGITS))
        override val required = setOf(AnswerSpec.MissingFactor.CELL)
    }
    /** A single number: a plain calculation, or a word problem with its "V: ___" answer line. */
    data class Number(val word: Boolean) : BoardKind {
        override val cells = listOf(InputCell(AnswerSpec.Numeric.CELL, if (word) "Vastaus" else "Tulos", NUMBER_DIGITS))
        override val required = setOf(AnswerSpec.Numeric.CELL)
    }

    companion object {
        const val NUMBER_DIGITS = 5
        fun of(exercise: PackExercise): BoardKind = when (val a = exercise.answer) {
            is AnswerSpec.ColumnMultiplication -> Column(a)
            is AnswerSpec.Steps -> Steps(a)
            is AnswerSpec.FillTable -> Table(a)
            is AnswerSpec.MissingFactor -> Missing(a)
            is AnswerSpec.Numeric -> Number(word = exercise.generator?.family == "word")
            is AnswerSpec.Choice -> error("Math boards do not use multiple choice")
        }
    }
}

/** The child's work on one item. Kept across a retry, so the child fixes the marked cell instead of starting over. */
class AnswerDraft(val kind: BoardKind) {
    val values = mutableStateMapOf<String, CellValue>()
    var focused by mutableStateOf(kind.cells.firstOrNull()?.id)

    val pendingAsk: InputCell? get() = kind.cells.firstOrNull { values[it.id]?.ask != null }
    val complete: Boolean get() = kind.required.all { values[it]?.value != null } && pendingAsk == null

    /** Every cell, empty ones as explicit nulls: the attempt log keeps the whole submitted map. */
    fun input(): AnswerInput.Cells = AnswerInput.Cells(kind.cells.associate { it.id to values[it.id]?.value })

    /** Lowest pen confidence among written cells; null for the pad. */
    fun confidence(): Double? = values.values.mapNotNull { it.confidence }.minOrNull()

    fun set(id: String, value: CellValue) { values[id] = value }

    /** Puts back a submitted answer (the given values of [check]); focus goes to its first wrong cell. */
    fun restore(check: CheckResult) {
        check.cells.forEach { c -> if (c.given != null && kind.cells.any { it.id == c.id }) values[c.id] = CellValue(c.given) }
        focused = check.firstWrong?.id ?: focused
    }
    fun next() {
        val cells = kind.cells
        val i = cells.indexOfFirst { it.id == focused }
        // Skip ahead to the next empty cell, wrapping once; stay put when everything is filled.
        val order = cells.drop(i + 1) + cells.take(i + 1)
        focused = (order.firstOrNull { values[it.id]?.value == null } ?: order.firstOrNull())?.id
    }
    /** Solving-order position of the next empty required cell: the column to work on now. */
    fun activeCell(): InputCell? = kind.cells.firstOrNull { it.id in kind.required && values[it.id]?.value == null }
}

/** Board for any math item. [check] marks the first wrong cell after an answer, or from the previous try. */
@Composable
fun MathBoard(exercise: PackExercise, draft: AnswerDraft, input: DigitInput, check: CheckResult?, enabled: Boolean) {
    val firstWrong = check?.firstWrong?.id
    val cellOf: @Composable (InputCell, Modifier) -> Unit = { cell, modifier ->
        input.Cell(
            exercise.id, cell, draft.values[cell.id] ?: CellValue.Empty, draft.focused == cell.id,
            if (cell.id == firstWrong) CellMark.FIRST_WRONG else CellMark.NONE, enabled,
            onFocus = { draft.focused = cell.id }, onValue = { draft.set(cell.id, it) }, modifier = modifier,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val kind = draft.kind) {
            is BoardKind.Column -> ColumnMultiplicationBoard(kind, draft, cellOf)
            is BoardKind.Steps -> StepsBoard(kind, exercise, cellOf)
            is BoardKind.Table -> TableBoard(kind, input::width, cellOf)
            is BoardKind.Missing -> MissingFactorBoard(kind, cellOf)
            is BoardKind.Number -> if (kind.word) WordProblemCard(exercise, kind, cellOf) else NumberBoard(kind, cellOf)
        }
        firstWrong?.let { id -> draft.kind.cells.find { it.id == id } }?.let { cell ->
            Text("Katso vielä ruutua: ${cell.label}.", color = CellStyle.wrong, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        draft.pendingAsk?.let { cell ->
            val ask = draft.values.getValue(cell.id).ask!!
            ConfirmDigit(cell, ask) { digit ->
                draft.set(cell.id, input.confirm(exercise.id, cell, draft.values.getValue(cell.id), digit))
            }
        }
        val focusedCell = draft.kind.cells.find { it.id == draft.focused }
        input.Controls(focusedCell, focusedCell?.let { draft.values[it.id] }, enabled,
            onValue = { v -> focusedCell?.let { draft.set(it.id, v) } }, onNext = { draft.next() })
    }
}

/**
 * Squared paper: carry row(s) above, the factors, a line, partial products for a two-digit multiplier, then
 * the result. The column being worked on (ones → tens → hundreds) is highlighted and named.
 */
@Composable
fun ColumnMultiplicationBoard(kind: BoardKind.Column, draft: AnswerDraft, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    val spec = kind.spec
    val (top, bottom) = spec.factors
    val cells = spec.cells.associateBy { it.id }
    val inputs = kind.cells.associateBy { it.id }
    val width = maxOf(spec.cells.maxOf { it.column } + 1, top.toString().length, bottom.toString().length) + 1 // +1: the "·" column
    val active = draft.activeCell()?.let { cells[it.id] }
    val single = bottom < 10
    // Each grid row: column index → a given digit (text) or an input cell (cell id). null = a line.
    val rows = buildList<Map<Int, Pair<String?, String?>>?> {
        fun cellRow(filter: (fi.skillit.engine.DigitCell) -> Boolean) {
            val row = spec.cells.filter(filter)
            if (row.isNotEmpty()) add(row.associate { it.column to (null to it.id) })
        }
        fun given(number: Int, sign: Boolean) = add(buildMap {
            number.toString().reversed().forEachIndexed { col, ch -> put(col, "$ch" to null) }
            if (sign) put(width - 1, "·" to null)
        })
        val multiplierRows = if (single) listOf(0) else bottom.toString().indices.toList()
        multiplierRows.reversed().forEach { r -> cellRow { it.kind == DigitKind.CARRY && it.row == r } }
        given(top, false)
        given(bottom, true)
        add(null)
        if (!single) {
            multiplierRows.forEach { r -> cellRow { it.kind == DigitKind.PARTIAL && it.row == r } }
            cellRow { it.kind == DigitKind.SUM_CARRY }
            add(null)
        }
        cellRow { it.kind == DigitKind.RESULT }
    }
    Text("Laske allekkain", style = MaterialTheme.typography.titleMedium)
    active?.let {
        val where = if (it.kind == DigitKind.PARTIAL) "${it.row + 1}. osatulo, " else ""
        Text("Nyt: $where${placeName(it.column)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Column(Modifier.horizontalScroll(rememberScrollState()).background(Color(0xFFFFFFFF), RoundedCornerShape(8.dp)).padding(8.dp).semantics(mergeDescendants = false) {
        contentDescription = "Allekkain laskettava $top kertaa $bottom"
    }) {
        rows.forEach { row ->
            if (row == null) HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
            else Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (width - 1 downTo 0).forEach { col ->
                    val highlight = active != null && col == active.column
                    Box(Modifier.size(CellStyle.digit).background(if (highlight) CellStyle.activeColumn else Color.Transparent), contentAlignment = Alignment.Center) {
                        val (text, id) = row[col] ?: (null to null)
                        if (text != null) GivenDigit(text, Modifier)
                        if (id != null) cellOf(inputs.getValue(id), Modifier)
                    }
                }
            }
        }
    }
}

@Composable
private fun GivenDigit(text: String, modifier: Modifier) {
    Text(text, modifier, fontSize = 28.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
}

/** Order of operations and several factors: one step per line, in school order. */
@Composable
fun StepsBoard(kind: BoardKind.Steps, exercise: PackExercise, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    exercise.expression?.let { Text(it.replace('*', '·').replace('/', ':'), style = MaterialTheme.typography.headlineSmall) }
    Text("Laske yksi vaihe kerrallaan: ensin sulut, sitten kertolaskut, lopuksi yhteenlaskut.")
    kind.cells.forEachIndexed { i, cell ->
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (i == kind.cells.lastIndex) "V:" else "${i + 1}.", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(40.dp))
            Text("=", style = MaterialTheme.typography.titleLarge)
            cellOf(cell, Modifier)
        }
    }
}

/** Product grid, times-table strips, 1/0 tables and the letter code: given headers, blanks to fill. */
@Composable
fun TableBoard(kind: BoardKind.Table, cellWidth: (InputCell) -> Dp, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    val inputs = kind.cells.associateBy { it.id }
    val rows = kind.spec.rows
    // Every column is as wide as its widest cell, so headers line up with their answers; a table wider
    // than the screen scrolls sideways instead of squeezing its last cells.
    val widths = rows[0].indices.map { c ->
        maxOf(TABLE_MIN_COLUMN, rows.maxOf { row -> (row[c] as? TableEntry.Blank)?.let { cellWidth(inputs.getValue(it.id)) } ?: TABLE_MIN_COLUMN })
    }
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { c, entry ->
                    when (entry) {
                        is TableEntry.Given -> Box(Modifier.width(widths[c]).heightIn(min = 56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)).padding(4.dp),
                            contentAlignment = Alignment.Center) { Text(entry.text, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium) }
                        is TableEntry.Blank -> Box(Modifier.width(widths[c]), contentAlignment = Alignment.Center) { cellOf(inputs.getValue(entry.id), Modifier) }
                    }
                }
            }
        }
    }
}

private val TABLE_MIN_COLUMN = 72.dp

@Composable
fun MissingFactorBoard(kind: BoardKind.Missing, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    val spec = kind.spec
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())) {
        spec.factors.forEachIndexed { i, f ->
            if (i > 0) Text("·", style = MaterialTheme.typography.headlineMedium)
            if (f == null) cellOf(kind.cells.single(), Modifier) else Text("$f", style = MaterialTheme.typography.headlineMedium)
        }
        Text("=", style = MaterialTheme.typography.headlineMedium)
        Text("${spec.product ?: "?"}", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun NumberBoard(kind: BoardKind.Number, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("=", style = MaterialTheme.typography.headlineMedium)
        cellOf(kind.cells.single(), Modifier)
    }
}

/**
 * Word problem: the story, a "Merkitse tiedot" bar-model helper and the answer line "V: ___".
 * The helper is scratch work only; it is never checked and gives no XP. No read-aloud (settled in #31).
 */
@Composable
fun WordProblemCard(exercise: PackExercise, kind: BoardKind.Number, cellOf: @Composable (InputCell, Modifier) -> Unit) {
    var marking by rememberSaveable(exercise.id) { mutableStateOf(false) }
    val factors = remember(exercise.expression) {
        exercise.expression?.let { runCatching { Expression.parse(it).factors() }.getOrNull() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { marking = !marking }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (marking) "Piilota tiedot" else "Merkitse tiedot")
            }
            if (marking) BarModel(factors, exercise.prompt)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("V:", style = MaterialTheme.typography.headlineMedium)
                cellOf(kind.cells.single(), Modifier)
            }
        }
    }
}

/** n equal parts of size m and an unknown whole: the bar model for "n groups of m". */
@Composable
private fun BarModel(factors: List<Int>?, prompt: String) {
    val numbers = remember(prompt) { Regex("[0-9]+").findAll(prompt).map { it.value }.toList() }
    val pair = factors?.takeIf { it.size == 2 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = if (pair != null) "Jana: ${pair[0]} osaa, jokaisessa ${pair[1]}. Kysytään kaikkia yhteensä."
            else "Tehtävän luvut: ${numbers.joinToString(", ")}. Kysytään vastausta."
    }) {
        Text("Tiedot: ${numbers.joinToString(", ")}", style = MaterialTheme.typography.titleMedium)
        if (pair != null && pair[0] in 1..12) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(pair[0]) {
                    Box(Modifier.weight(1f).height(40.dp).background(Color(0xFFCFE8E2), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text("${pair[1]}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Text("${pair[0]} osaa · yhteensä ?", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else Text("Mitä kysytään? ?")
    }
}
