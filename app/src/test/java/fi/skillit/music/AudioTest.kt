package fi.skillit.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AudioTest {
    @Test fun pcmHasExactBarBoundariesSilenceForRestsAndConcertPitch() {
        val pcm = MusicAudio.render(listOf(Note(Pitch.A4, Duration.QUARTER), Rest(Duration.QUARTER)), bpm = 120)
        assertEquals(24000, pcm.size) // Two beats at 120 BPM = one second.
        assertTrue(pcm.take(12000).any { abs(it.toInt()) > 1000 })
        assertTrue(pcm.drop(12000).all { it == 0.toShort() })
        val crossings = (2400 until 9600).count { pcm[it - 1] <= 0 && pcm[it] > 0 }
        assertEquals(132, crossings) // 0.3 seconds of concert A (440 Hz).
        assertEquals(0.toShort(), pcm.first())
        assertEquals(0.toShort(), pcm[11999])
        assertTrue(pcm.all { abs(it.toInt()) <= 8000 })
        assertEquals(48000, MusicAudio.render(List(8) { Note(Pitch.C4, Duration.EIGHTH) }, 120).size)
    }
}

class PlaybackTest {
    private class Speaker : PcmOutput {
        var active = 0
        var peak = 0
        var volume = 0f
        var finished: () -> Unit = {}
        override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) {
            require(samples.any { it != 0.toShort() } && sampleRate == 24000)
            active++; peak = maxOf(peak, active); this.volume = volume; finished = onFinished
        }
        override fun stop() { active = 0 }
    }
    @Test fun replaceMuteStopAndCloseNeverLeaveOverlappingOrLatePlayback() {
        val speaker = Speaker()
        val controller = PlaybackController(speaker)
        val tone = MusicAudio.render(listOf(Note(Pitch.C4, Duration.QUARTER)))
        assertTrue(controller.play(tone))
        val stale = speaker.finished
        assertTrue(controller.play(tone))
        stale()
        assertTrue(controller.playing)
        assertEquals(1, speaker.peak)
        controller.configure(true, .8f)
        assertEquals(0, speaker.active)
        assertFalse(controller.play(tone))
        controller.configure(false, 9f)
        assertTrue(controller.play(tone))
        assertEquals(1f, speaker.volume)
        speaker.finished()
        assertFalse(controller.playing)
        controller.close()
        assertFalse(controller.play(tone))
        assertEquals(0, speaker.active)
    }
    @Test fun badTimingAndNonfiniteVolumeAreRejectedAndOutputFailureIsRecoverable() {
        assertThrows(IllegalArgumentException::class.java) { MusicAudio.render(listOf(Rest(Duration.WHOLE)), 0) }
        assertThrows(IllegalArgumentException::class.java) { MusicAudio.render(List(5) { Rest(Duration.WHOLE) }) }
        val controller = PlaybackController(object : PcmOutput {
            override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) { error("Output unavailable") }
            override fun stop() {}
        })
        assertThrows(IllegalArgumentException::class.java) { controller.configure(false, Float.NaN) }
        assertFalse(controller.play(MusicAudio.metronome(Meter.TWO_FOUR)))
        assertFalse(controller.playing)
        assertTrue(controller.failure)
    }
}
