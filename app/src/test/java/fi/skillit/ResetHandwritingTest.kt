package fi.skillit

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import fi.skillit.coach.HandwritingData
import fi.skillit.coach.LegibilityReport
import fi.skillit.coach.LegibilityStore
import fi.skillit.coach.Metric
import fi.skillit.ink.CellInk
import fi.skillit.ink.InkPoint
import fi.skillit.ink.InkStroke
import fi.skillit.music.PcmOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Security review F1: "Nollaa edistyminen" also removes the handwriting coach's data, off the main thread. */
@RunWith(RobolectricTestRunner::class)
class ResetHandwritingTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val tmp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun seed() {
        LegibilityStore.open(context).record(LegibilityReport(4, mapOf(Metric.Inside to .5f), .6f, .85f, emptyList()))
        HandwritingData.attemptInk(context).save("a1", mapOf(0 to CellInk(listOf(InkStroke(listOf(InkPoint(1f, 2f, 3L)))), 4)))
    }

    @Test fun deleteAllRemovesAggregatesAndStrokes() {
        seed()
        assertEquals(1, LegibilityStore.open(context).days().size)
        assertEquals(1, HandwritingData.attemptInk(context).all().size)
        HandwritingData.deleteAll(context)
        assertTrue(LegibilityStore.open(context).days().isEmpty())
        assertTrue(HandwritingData.attemptInk(context).all().isEmpty())
    }

    @Test fun resetProgressDeletesHandwritingDataOffTheMainThread() {
        seed()
        val repo = ProgressRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { File(tmp.root, "p.preferences_pb") })
        val silent = object : PcmOutput {
            override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) {}
            override fun stop() {}
        }
        var onMain: Boolean? = null
        compose.setContent { SkillitTheme { SkillitApp(repo, silent, deleteHandwriting = {
            onMain = Looper.myLooper() == Looper.getMainLooper()
            HandwritingData.deleteAll(context)
        }) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Asetukset").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Asetukset").performClick()
        compose.onNodeWithText("Nollaa edistyminen").performClick()
        compose.onNodeWithText("Seikkailu ja harjoituskierrokset nollataan, ja käsialan harjoitustiedot (numeroiden kirjoitus) poistetaan. Ääniasetukset säilyvät.").assertExists()
        compose.onNodeWithText("Nollaa").performClick()
        compose.waitUntil(5_000) { onMain != null }
        assertEquals("deleted on an IO thread", false, onMain)
        compose.waitUntil(5_000) { LegibilityStore.open(context).days().isEmpty() && HandwritingData.attemptInk(context).all().isEmpty() }
        compose.onAllNodesWithText("Käsialatietoja ei voitu poistaa", substring = true).assertCountEquals(0)
    }
}
