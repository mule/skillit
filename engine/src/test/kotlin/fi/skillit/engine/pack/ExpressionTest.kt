package fi.skillit.engine.pack

import org.junit.Assert.*
import org.junit.Test

class ExpressionTest {
    @Test fun schoolOrderOfOperations() {
        assertEquals(23, Expression.parse("3 + 4 * 5").value)
        assertEquals(listOf(20, 23), Expression.parse("3 + 4 * 5").steps())
        assertEquals(listOf(6, 20, 26), Expression.parse("2 · 3 + 4 × 5").steps())
        assertEquals(listOf(7, 35), Expression.parse("(3 + 4) * 5").steps())
        assertEquals(listOf(6, 2), Expression.parse("10 - 4 - 4").let { listOf(it.steps()[0], it.value) })
        assertEquals(4, Expression.parse("24 : 3 : 2").value)
        assertEquals(listOf(42), Expression.parse("42").steps())
    }

    @Test fun stepsFollowFinnishSchoolOrderOneReductionAtATime() {
        val cases = mapOf(
            "1 + 2 + 3 * 4" to listOf(12, 3, 15),
            "3 + 4 * 5 + 6 * 7" to listOf(20, 42, 23, 65),
            "30 + 5 * (3 + 6)" to listOf(9, 45, 75),
            "2 * (10 - 4) + 1" to listOf(6, 12, 13),
            "48 : (2 + 6) - 3" to listOf(8, 6, 3),
            "100 - (2 * (3 + 4))" to listOf(7, 14, 86),
            "(1 + 2) * (3 + 4)" to listOf(3, 7, 21),
            "(1 + 2) + 3 * 4" to listOf(3, 12, 15), // written parentheses are kept, not re-derived
            "12 : 3 * 2 + 1" to listOf(4, 8, 9),
            "20 - 6 - 4" to listOf(14, 10),
            "(7)" to listOf(7),
            "((2 + 3))" to listOf(5),
        )
        cases.forEach { (text, steps) ->
            val expression = Expression.parse(text)
            assertEquals(text, steps, expression.steps())
            assertEquals(text, steps.last(), expression.value)
        }
    }

    @Test fun factorsOnlyForPureProducts() {
        assertEquals(listOf(2, 3, 4), Expression.parse("2 * 3 * 4").factors())
        assertEquals(listOf(2, 3, 4), Expression.parse("2 * (3 * 4)").factors())
        assertNull(Expression.parse("2 * 3 + 1").factors())
        assertNull(Expression.parse("7").factors())
    }

    @Test fun rejectsInexactOrMalformedInput() {
        listOf("7 / 2", "1 / 0", "", "3 +", "(1 + 2", "1 + 2)", "x", "1 2", "9999999999", "99999 * 99999").forEach {
            assertThrows("'$it'", IllegalArgumentException::class.java) { Expression.parse(it) }
        }
        // Only the schema alphabet: no Unicode spaces or digits that could hide a different number.
        listOf("7\u00A08", "7\u20038", "7\u30008", "7\t8", "\u0663+\u0664", "\uFF17\uFF18", "7\u00A0+ 8", "7 + 8\n", "7^2", "7 % 2").forEach {
            assertThrows("'$it'", IllegalArgumentException::class.java) { Expression.parse(it) }
        }
        assertEquals(15, Expression.parse(" 7 + 8 ").value)
        // Int.MIN_VALUE / -1 overflows Int; it must be rejected, not wrap.
        assertEquals(Int.MIN_VALUE, Expression.parse("(0-999999999-999999999-147483650)").value)
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("(0-999999999-999999999-147483650)/(0-1)") }
        assertEquals(1, Expression.parse("(".repeat(16) + "1" + ")".repeat(16)).value)
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("(".repeat(17) + "1" + ")".repeat(17)) }
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("(".repeat(100_000)) }
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("1+".repeat(100) + "1") }
    }
}
