package fi.skillit

import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.music.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioOutputTest {
    @Test fun realAudioTrackAcceptsPcmAdvancesAndReleasesOnCompletionAndStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        val output = AndroidPcmOutput()
        val pcm = MusicAudio.render(listOf(Note(Pitch.A4, Duration.QUARTER), Rest(Duration.QUARTER)), 120)
        instrumentation.runOnMainSync {
            output.start(pcm, MusicAudio.sampleRate, .2f) { finished.countDown() }
            assertEquals(24000, output.framesWritten)
            assertTrue(output.active)
        }
        Thread.sleep(250)
        instrumentation.runOnMainSync { assertTrue("PCM reached the platform playback head", output.playbackHead > 0) }
        assertTrue("Finite track completes", finished.await(5, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            assertFalse(output.active)
            val controller = PlaybackController(output)
            controller.play(pcm)
            assertTrue(output.active)
            controller.configure(true, .5f)
            assertFalse(output.active)
            controller.configure(false, .5f)
            controller.play(pcm)
            controller.close()
            assertFalse(output.active)
        }
    }
}
