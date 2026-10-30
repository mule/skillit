package fi.skillit

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.music.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class VolumeFailureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun repeatedRealWriteFailuresRestoreDraftSliderControllerAndReopenedPreferences() {
        val context = compose.activity.applicationContext
        val file = File(context.cacheDir, "volume-failure-${System.nanoTime()}.preferences_pb")
        var storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backing = PreferenceDataStoreFactory.create(scope = storeScope) { file }
        class FailingStore : DataStore<Preferences> {
            var failedWrites = 0
            override val data = backing.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                failedWrites++
                throw IOException("Controlled save failure")
            }
        }
        class Output : PcmOutput {
            var volume = -1f
            override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) { this.volume = volume }
            override fun stop() {}
        }
        val output = Output()
        val seed = SavedProgress(volume = .6f)
        runBlocking { ProgressRepository(backing).save(seed) }
        val failing = FailingStore()
        val repo = ProgressRepository(failing)
        fun tap(text: String) {
            compose.waitUntilAtLeastOneExists(hasText(text) and isEnabled(), 10000)
            if (text == "Sulje") compose.onNodeWithText(text).performClick()
            else compose.onNodeWithText(text).performScrollTo().performClick()
        }
        try {
            compose.runOnUiThread { compose.activity.setContent { SkillitTheme { SkillitApp(repo, output) } } }
            compose.waitUntilAtLeastOneExists(hasText("Aloita seikkailu"),10000)
            listOf(.8f, .2f).forEachIndexed { index, requested ->
                tap("Asetukset")
                compose.onNodeWithContentDescription("Äänenvoimakkuus").performScrollTo()
                    .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(requested) }
                compose.waitUntil(10000) { failing.failedWrites == index + 1 }
                compose.waitUntilAtLeastOneExists(hasContentDescription("Äänenvoimakkuus") and isEnabled(),10000)
                compose.onNodeWithText("Äänenvoimakkuus 60 %").performScrollTo().assertIsDisplayed()
                val directory = File(compose.activity.getExternalFilesDir(null), "m2-review-fixes").apply { mkdirs() }
                File(directory, "volume-failure-${index + 1}.png").outputStream().use {
                    checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)
                }
                val slider = compose.onNodeWithContentDescription("Äänenvoimakkuus").fetchSemanticsNode()
                assertEquals(.6f, slider.config[SemanticsProperties.ProgressBarRangeInfo].current, .001f)
                assertEquals(seed, runBlocking { ProgressRepository(backing).load() })
                tap("Sulje")
                compose.onNodeWithContentDescription("Kuuntele A4").performScrollTo().performClick()
                compose.runOnIdle { assertEquals(.6f, output.volume, .001f) }
                compose.onNodeWithText("Tallennus epäonnistui. Yritä uudelleen. Edellinen tilanne on yhä tallessa.").assertExists()
            }
            // Reopen the actual preferences file, not just the controller or an in-memory stub.
            compose.runOnUiThread { compose.activity.setContent { Text("Testi valmis") } }
            runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
            storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val reopened = ProgressRepository(PreferenceDataStoreFactory.create(scope = storeScope) { file })
            assertEquals(seed, runBlocking { reopened.load() })
        } finally {
            compose.runOnUiThread { compose.activity.setContent { Text("Testi valmis") } }
            runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
            file.delete()
        }
    }
}
