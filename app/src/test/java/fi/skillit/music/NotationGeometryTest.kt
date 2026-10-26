package fi.skillit.music

import org.junit.Assert.*
import org.junit.Test

class NotationGeometryTest {
    @Test fun staffAnchorsLedgerLinesStemsAndBeamsFollowTrebleConvention() {
        val layout = NotationGeometry(bottom = 100f, space = 12f)
        assertEquals(listOf(112f, 106f, 100f, 94f, 88f, 82f, 76f, 70f), Pitch.entries.map { layout.noteY(it) })
        assertEquals(listOf(112f), layout.ledgerLines(Pitch.C4))
        assertTrue(layout.ledgerLines(Pitch.D4).isEmpty())
        assertFalse(layout.stemDown(Pitch.A4))
        assertTrue(layout.stemDown(Pitch.H4))
        assertEquals(64f, layout.restY(Duration.WHOLE))
        assertEquals(76f, layout.restY(Duration.HALF))
        val eighth = Note(Pitch.G4, Duration.EIGHTH)
        assertEquals(setOf(0, 2), beamStarts(listOf(eighth, eighth, eighth, eighth)))
        assertEquals(emptySet<Int>(), beamStarts(listOf(eighth, Rest(Duration.EIGHTH), eighth)))
        // A pair must start on a beat, never bridge from one beat into the next.
        assertEquals(emptySet<Int>(), beamStarts(listOf(Rest(Duration.EIGHTH), eighth, eighth)))
    }
}
