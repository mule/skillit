package fi.skillit.music

import org.junit.Assert.*
import org.junit.Test

class MusicTest {
    @Test fun finnishTreblePitchesAndDurationsMatchKnownMusicFacts() {
        assertEquals(listOf("C", "D", "E", "F", "G", "A", "H", "C"), Pitch.entries.map { it.nameFi })
        // Diatonic half-spaces above the bottom E4 line.
        assertEquals(listOf(-2, -1, 0, 1, 2, 3, 4, 5), Pitch.entries.map { it.staffStep })
        assertEquals(listOf(8, 4, 2, 1), Duration.entries.map { it.eighths })
        assertTrue(Bar(Meter.TWO_FOUR, listOf(Note(Pitch.C4, Duration.QUARTER), Rest(Duration.QUARTER))).valid)
        assertTrue(Bar(Meter.FOUR_FOUR, listOf(Note(Pitch.C5, Duration.WHOLE))).valid)
        assertFalse(Bar(Meter.TWO_FOUR, listOf(Note(Pitch.C5, Duration.WHOLE))).valid)
        assertEquals(1, Bar(Meter.TWO_FOUR, listOf(Rest(Duration.QUARTER), Rest(Duration.EIGHTH))).missingEighths)
    }
    @Test fun originalFinnishPackHasTwentyIndependentlyCheckedKeys() {
        assertEquals("music-grade4-fi", MusicPack.id)
        assertEquals(1, MusicPack.version)
        assertEquals(listOf(5, 5, 5, 5), MusicPack.items.groupBy { it.question.level }.values.map { it.size })
        val expected = listOf(
            "neljäsosanuotti", "2", "4", "½", "2",
            "C", "E", "G", "H", "C",
            "1", "1", "2", "2", "Kyllä",
            "D", "F", "A", "4", "1½",
        )
        assertEquals(expected, MusicPack.items.map { item -> item.question.choices.single { it.id == item.question.correctId }.label })
        MusicPack.items.forEach { item ->
            val q = item.question
            assertEquals(1, q.choices.count { item.task.accepts(it.label) })
            assertTrue(q.explanation.contains(item.task.answerLabel))
            assertFalse(q.choices.any { it.label == "B" })
        }
        assertEquals(20, MusicPack.items.map { it.question.id }.distinct().size)
        assertTrue(MusicPack.items.map { it.question.choices.indexOfFirst { c -> c.id == it.question.correctId } }.distinct().size > 1)
    }
}
