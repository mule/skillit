package fi.skillit.math.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.CheckResult
import fi.skillit.engine.TableEntry
import fi.skillit.engine.pack.Expression
import fi.skillit.engine.pack.GeneratorRef
import fi.skillit.engine.pack.PackExercise
import fi.skillit.mathgen.MathGen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Each board driven with the number pad, the way a child would after the parent unlocks it. */
@RunWith(RobolectricTestRunner::class)
class BoardsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(exercise: PackExercise, check: CheckResult? = null): AnswerDraft {
        val draft = AnswerDraft(BoardKind.of(exercise))
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            MathBoard(exercise, draft, PadInput, check, enabled = true)
        } } }
        return draft
    }
    /** The cell whose TalkBack label is exactly [label] (its description is "label, value"). */
    private fun cell(label: String) = compose.onNode(SemanticsMatcher("cell $label") { node ->
        node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.any { it.startsWith(label) } == true
    })
    private fun type(vararg digits: Int) = digits.forEach { compose.onNodeWithContentDescription("Numero $it").performScrollTo().performClick() }
    private fun typeInto(label: String, number: Int) {
        cell("$label, ").performScrollTo().performClick()
        type(*number.toString().map { it - '0' }.toIntArray())
    }
    private fun exercise(answer: AnswerSpec, expression: String? = null, prompt: String = "Laske.") =
        PackExercise("x1", prompt, "Vihje.", "Selitys.", answer, expression)

    @Test fun columnBoardGuidesOnesTensHundredsAndChecksDigitByDigit() {
        val spec = AnswerSpec.ColumnMultiplication.layout(234, 5)
        val draft = show(exercise(spec, "234 * 5"))
        compose.onNodeWithText("Nyt: ykköset").assertExists()
        // Focus starts on the ones digit; each typed digit moves on in solving order (digit, then its carry).
        type(0)
        assertEquals("c0.1", draft.focused)
        type(2)
        compose.onNodeWithText("Nyt: kymmenet").assertExists()
        type(7, 1)
        compose.onNodeWithText("Nyt: sadat").assertExists()
        type(1, 1)
        assertTrue(draft.complete)
        val input = draft.input()
        assertEquals(mapOf("r0" to 0, "c0.1" to 2, "r1" to 7, "c0.2" to 1, "r2" to 1, "r3" to 1), input.values)
        assertTrue(spec.check(input)!!.correct)
        compose.onNodeWithContentDescription("Tulos, tuhannet, 1").assertExists()
        cell("Muistinumero, kymmenet").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun carriesAreOptionalScratchWork() {
        val draft = show(exercise(AnswerSpec.ColumnMultiplication.layout(234, 5), "234 * 5"))
        typeInto("Tulos, ykköset", 0); typeInto("Tulos, kymmenet", 7); typeInto("Tulos, sadat", 1); typeInto("Tulos, tuhannet", 1)
        assertTrue(draft.complete)
        assertEquals(null, draft.input().values["c0.1"])
        assertTrue(draft.input().values.containsKey("c0.1"))
    }

    @Test fun firstWrongCellGetsAGentleHint() {
        val spec = AnswerSpec.ColumnMultiplication.layout(234, 5)
        val check = spec.check(AnswerInput.Cells(mapOf("r0" to 0, "r1" to 6, "r2" to 1, "r3" to 1)))!!
        assertFalse(check.correct)
        show(exercise(spec, "234 * 5"), check)
        compose.onNodeWithText("Katso vielä ruutua: Tulos, kymmenet.").assertExists()
        cell("Tulos, kymmenet").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
            listOf("Tulos, kymmenet, tyhjä, katso tätä vielä")))
    }

    @Test fun twoDigitMultiplierShowsPartialRowsAndSumCarries() {
        val spec = AnswerSpec.ColumnMultiplication.layout(47, 36)
        val draft = show(exercise(spec, "47 * 36"))
        compose.onNodeWithText("Nyt: 1. osatulo, ykköset").assertExists()
        spec.cells.forEach { c -> typeInto(draft.kind.cells.first { it.id == c.id }.label, c.expected) }
        assertTrue(spec.check(draft.input())!!.correct)
    }

    @Test fun stepsBoardHasOneLinePerStep() {
        val expression = "3 + 4 * (2 + 5)"
        val spec = AnswerSpec.Steps(Expression.parse(expression).steps())
        val draft = show(exercise(spec, expression))
        compose.onNodeWithText("3 + 4 · (2 + 5)").assertExists()
        typeInto("Vaihe 1", 7); typeInto("Vaihe 2", 28); typeInto("Vastaus", 31)
        assertEquals(AnswerInput.steps(7, 28, 31), draft.input())
        assertTrue(spec.check(draft.input())!!.correct)
        // Backspace fixes a multi-digit cell without retyping the whole line.
        cell("Vastaus, 31").performClick()
        compose.onNodeWithText("Pyyhi").performScrollTo().performClick()
        cell("Vastaus, 3").assertExists()
    }

    @Test fun productGridFillsEveryBlank() {
        val g = { t: String -> TableEntry.Given(t) }
        val spec = AnswerSpec.FillTable(listOf(
            listOf(g("·"), g("3"), g("4")),
            listOf(g("2"), TableEntry.Blank("a", 6, "2 * 3"), TableEntry.Blank("b", 8, "2 * 4")),
            listOf(g("5"), TableEntry.Blank("c", 15, "5 * 3"), TableEntry.Blank("d", 20, "5 * 4")),
        ))
        val draft = show(exercise(spec))
        typeInto("2 · 3", 6); typeInto("2 · 4", 8); typeInto("5 · 3", 15); typeInto("5 · 4", 20)
        assertTrue(spec.check(draft.input())!!.correct)
    }

    @Test fun missingFactorTable() {
        val spec = AnswerSpec.MissingFactor(listOf(7, null), 56)
        val draft = show(exercise(spec, "7 * 8"))
        compose.onNodeWithText("56").assertExists()
        typeInto("Puuttuva luku", 8)
        assertTrue(spec.check(draft.input())!!.correct)
    }

    @Test fun wordProblemHasBarModelHelperAndAnswerLine() {
        val generated = MathGen.generate(GeneratorRef("word", seed = 3)).exercise
        val draft = show(generated)
        compose.onNodeWithText("V:").assertExists()
        compose.onNodeWithText("Merkitse tiedot").performClick()
        compose.onNodeWithText("osaa · yhteensä ?", substring = true).assertExists()
        typeInto("Vastaus", (generated.answer as AnswerSpec.Numeric).expected)
        assertTrue(generated.answer.check(draft.input())!!.correct)
    }

    @Test fun unsurePenReadingAsksBeforeTheAnswerCanBeChecked() {
        val spec = AnswerSpec.Numeric(9)
        val draft = show(exercise(spec, "3 * 3"))
        compose.runOnIdle { draft.set(AnswerSpec.Numeric.CELL, CellValue(null, .45, 4 to 9)) }
        compose.onNodeWithText("Tarkoititko 4 vai 9?").assertExists()
        assertFalse(draft.complete)
        compose.onNodeWithContentDescription("Valitse 9").performClick()
        compose.onNodeWithText("Tarkoititko 4 vai 9?").assertDoesNotExist()
        assertTrue(draft.complete)
        assertEquals(.45, draft.confidence()!!, 1e-9)
    }
}
