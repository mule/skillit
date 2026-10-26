package fi.skillit

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.music.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Captures real Canvas output for manual visual regression review, not a self-generated golden assertion. */
class NotationScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun captureNarrowPhonePitchDurationRestBeamAndMeterExamples() {
        val examples = buildList<Pair<String, MusicTask>> {
            Pitch.entries.forEach { add("pitch-${it.name}" to PitchName(it)) }
            Duration.entries.forEach { duration ->
                add("note-${duration.name}" to CountBeats(listOf(Note(Pitch.G4, duration))))
                add("rest-${duration.name}" to CountBeats(listOf(Rest(duration))))
            }
            add("eighth-pair-up" to CountBeats(listOf(Note(Pitch.G4, Duration.EIGHTH), Note(Pitch.G4, Duration.EIGHTH))))
            add("eighth-pair-down" to CountBeats(listOf(Note(Pitch.H4, Duration.EIGHTH), Note(Pitch.C5, Duration.EIGHTH))))
            add("eighth-flag-down" to CountBeats(listOf(Note(Pitch.H4, Duration.EIGHTH))))
            add("meter-2" to MissingBeats(Bar(Meter.TWO_FOUR, listOf(Note(Pitch.C4, Duration.QUARTER)))))
            add("meter-4" to FullBar(Bar(Meter.FOUR_FOUR, listOf(Note(Pitch.G4, Duration.HALF), Note(Pitch.G4, Duration.QUARTER), Rest(Duration.QUARTER)))))
        }
        val task = mutableStateOf(examples.first().second)
        compose.setContent { SkillitTheme { Box(Modifier.width(320.dp)) { Staff(task.value) } } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "notation").apply { mkdirs() }
        examples.forEach { (name, example) ->
            compose.runOnIdle { task.value = example }
            val node = compose.onNodeWithContentDescription(notationDescription(example))
            node.assertIsDisplayed()
            val bitmap = node.captureToImage().asAndroidBitmap()
            assertTrue(bitmap.width >= 320)
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertFalse(notationDescription(PitchName(Pitch.H4)).contains("H"))
        assertTrue(notationDescription(PitchName(Pitch.C4)).contains("apuviivalla"))
    }
}
