package fi.skillit.music

import org.junit.Assert.*
import org.junit.Test

class RhythmTest {
    @Test fun generatedBarsAndCompletionUseExactEighthUnits() {
        for (meter in Meter.entries) for (seed in 0..150) {
            val challenge = RhythmChallenge.generate(meter, seed)
            assertTrue(challenge.target.valid)
            assertEquals(challenge, RhythmChallenge.generate(meter, seed))
            assertTrue(challenge.target.symbols.any { it is Note })
            assertTrue(challenge.target.symbols.any { it is Rest })
            assertEquals(FillResult.UNDERFILLED, challenge.evaluate(emptyList()))
            assertEquals(FillResult.CORRECT, challenge.evaluate(challenge.solution))
            assertEquals(FillResult.OVERFLOW, challenge.evaluate(List(9) { Note(Pitch.C4, Duration.QUARTER) }))
            assertTrue(challenge.target.symbols.none { it is Rest && it.duration == Duration.WHOLE && meter == Meter.TWO_FOUR })
        }
    }
    @Test fun tapPracticeIgnoresStraysDebouncesAndRestartsWithoutScorePressure() {
        val bar = Bar(Meter.TWO_FOUR, listOf(Note(Pitch.C4, Duration.EIGHTH), Rest(Duration.EIGHTH), Note(Pitch.C4, Duration.QUARTER)))
        val taps = TapSession(bar, bpm = 120)
        assertFalse(taps.tap(1000))
        taps.start(1000)
        assertFalse(taps.tap(999))
        assertTrue(taps.tap(1000))
        assertFalse(taps.tap(1001))
        assertTrue(taps.tap(1500))
        assertFalse(taps.tap(2100))
        assertEquals(TapFeedback(2, 2, 0), taps.finish())
        taps.start(3000)
        assertEquals(TapFeedback(0, 2, 0), taps.finish())
        taps.start(5000); taps.cancel()
        assertFalse(taps.tap(5000))
    }
}
