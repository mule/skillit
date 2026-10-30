package fi.skillit

import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fi.skillit.music.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class RhythmPlaybackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun mutedOrUnavailableCorrectBarKeepsSuccessAndUsesTheSameGuidanceAsExample() {
        val repo = ProgressRepository(compose.activity.applicationContext.skillitDataStore)
        fun tap(text: String) {
            compose.waitUntilAtLeastOneExists(hasText(text) and isEnabled(),10000)
            compose.onNodeWithText(text).performScrollTo().performClick()
        }
        for (muted in listOf(true,false)) {
            class Output : PcmOutput {
                var attempts = 0
                override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) {
                    attempts++; throw IOException("Controlled output unavailable")
                }
                override fun stop() {}
            }
            val output = Output()
            val saved = SavedProgress(muted = muted)
            runBlocking { repo.save(saved) }
            compose.runOnUiThread { compose.activity.setContent { key(muted) { SkillitTheme { SkillitApp(repo,output) } } } }
            tap("Rytmipaja")
            RhythmChallenge.generate(Meter.TWO_FOUR,0).solution.forEach { symbol ->
                tap("Lisää ${if (symbol is Rest) symbol.duration.restFi else symbol.duration.noteFi} · ${beatsFi(symbol.duration.eighths)} iskua")
            }
            tap("Tarkista tahti")
            val guidance = "Kuuntelu ei käynnistynyt. Tarkista ääniasetukset."
            compose.onNodeWithText("Hienoa! Tahti on täynnä. $guidance").performScrollTo().assertIsDisplayed()
            tap("Kuuntele esimerkkirytmi")
            compose.onNodeWithText(guidance).performScrollTo().assertIsDisplayed()
            assertEquals(if (muted) 0 else 2, output.attempts)
            assertEquals(saved, runBlocking { repo.load() })
        }
    }
}
