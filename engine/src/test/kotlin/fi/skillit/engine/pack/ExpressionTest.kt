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
        assertEquals(1, Expression.parse("(".repeat(16) + "1" + ")".repeat(16)).value)
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("(".repeat(17) + "1" + ")".repeat(17)) }
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("(".repeat(100_000)) }
        assertThrows(IllegalArgumentException::class.java) { Expression.parse("1+".repeat(100) + "1") }
    }
}
