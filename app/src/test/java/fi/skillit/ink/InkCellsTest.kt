package fi.skillit.ink

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InkCellsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun stroke(vararg xy: Pair<Float, Float>) = InkStroke(xy.mapIndexed { i, (x, y) -> InkPoint(x, y, 1000L + i, .4f) })

    @Test fun attemptInkRoundTripsAndStaysInItsDirectory() {
        val dir = tmp.newFolder("attempt-ink")
        val store = AttemptInkStore(dir) { 42L }
        val key = "ch2-l18/q7/../../evil key ä"
        val cells = mapOf(0 to CellInk(listOf(stroke(1f to 2f, 3f to 4f)), 4), 2 to CellInk(listOf(stroke(5f to 6f)), null), 1 to CellInk(emptyList(), 9))
        store.save(key, cells)
        val loaded = store.load(key)!!
        assertEquals(key, loaded.key)
        assertEquals(42L, loaded.savedAtMs)
        assertEquals("empty cells are not stored", setOf(0, 2), loaded.cells.keys)
        assertEquals(cells[0], loaded.cells[0])
        assertEquals(null, loaded.cells[2]!!.digit)
        assertTrue("file name can't escape the directory", dir.listFiles()!!.all { it.parentFile == dir && !it.name.contains("/") })
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun pruneKeepsTheNewestAttempts() {
        var now = 0L
        val store = AttemptInkStore(tmp.newFolder("p")) { ++now }
        (1..5).forEach { store.save("a$it", mapOf(0 to CellInk(listOf(stroke(0f to 0f)), it))) }
        store.prune(2)
        assertEquals(listOf("a4", "a5"), store.all().map { it.key })
        store.delete("a4")
        assertNull(store.load("a4"))
        store.deleteAll()
        assertTrue(store.all().isEmpty())
    }

    @Test fun failedWriteLeavesNoTemporaryStrokes() {
        val dir = tmp.newFolder("full")
        val store = AttemptInkStore(dir, writer = { f, text -> f.writeText(text.take(10)); throw java.io.IOException("No space left on device") })
        assertThrows(java.io.IOException::class.java) { store.save("k", mapOf(0 to CellInk(listOf(stroke(1f to 1f)), 1))) }
        assertTrue("no .tmp after a failed write: ${dir.list()!!.toList()}", dir.listFiles()!!.isEmpty())
    }

    @Test fun crashLeftTemporaryFilesAreDeletedByDeleteAllAndPrune() {
        val dir = tmp.newFolder("crashed")
        val store = AttemptInkStore(dir)
        store.save("kept", mapOf(0 to CellInk(listOf(stroke(1f to 1f)), 1)))
        File(dir, "a2V5.ink.tmp").writeText("v1\tx\t1\n0\t1\t1.0 1.0 1 1.0\n")
        store.prune(10)
        assertEquals(listOf("kept"), store.all().map { it.key })
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        File(dir, "b.ink.tmp").writeText("strokes")
        store.deleteAll()
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun corruptAttemptFilesAreSkipped() {
        assertNull(AttemptInkStore.decode("nonsense"))
        assertNull(AttemptInkStore.decode(""))
    }

    @Test fun numberPadFillsCellsLeftToRight() {
        val row = DigitRowState(3).apply { mode = InputMode.NumberPad }
        assertNull(row.answer)
        row.type(1); row.type(0); row.type(8)
        assertEquals("108", row.answer)
        row.eraseTyped()
        assertEquals(listOf(1, 0, null), row.digits)
        assertNull(row.answer)
        row.selected = 0; row.type(7)
        row.clear()
        assertEquals(List(3) { null }, row.digits)
    }

    @Test fun leadingEmptyCellsAreAllowedButGapsAreNot() {
        val row = DigitRowState(4).apply { mode = InputMode.NumberPad }
        row.selected = 2; row.type(4); row.type(2)
        assertEquals("42", row.answer)
        row.selected = 1; row.type(5)
        assertEquals("542", row.answer)
        val gap = DigitRowState(3).apply { mode = InputMode.NumberPad }
        gap.type(1); gap.selected = 2; gap.type(3)
        assertNull(gap.answer)
    }

    @Test fun carryCellsAreSmaller() {
        assertTrue(CellKind.Carry.scale < CellKind.Answer.scale)
    }
}
