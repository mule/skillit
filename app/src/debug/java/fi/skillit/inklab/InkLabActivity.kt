package fi.skillit.inklab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fi.skillit.SkillitTheme
import fi.skillit.ink.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Debug-only spike screen for #26: try the pen, record labelled samples and measure accuracy on the device. */
class InkLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = InkSampleStore(File(filesDir, "ink-lab"))
        setContent { SkillitTheme { Surface(Modifier.fillMaxSize()) { InkLab(store, recognizerFactory = { MlKitDigitRecognizer(it) }) } } }
    }
}

/** Appends samples to app-private storage. Pull on a debug build with: adb shell run-as fi.skillit cat files/ink-lab/samples-v1.txt */
class InkSampleStore(private val dir: File) {
    private val file get() = File(dir, "samples-v1.txt")
    fun load(): List<InkSample> = if (!file.exists()) emptyList() else file.readLines().mapNotNull(InkSampleCodec::decode)
    fun append(s: InkSample) { ensureDir(); file.appendText(InkSampleCodec.encode(s) + "\n") }
    /** Deletes the sample file itself, not just its contents (security review F3). */
    fun deleteAll() { if (file.exists() && !file.delete()) throw IOException("could not delete $file") }
    /** Writes a temporary file and renames it, so a full disk can't wipe the existing samples. */
    fun replaceAll(all: List<InkSample>) {
        ensureDir()
        val tmp = File(dir, "samples-v1.txt.tmp")
        tmp.writeText(all.joinToString("") { InkSampleCodec.encode(it) + "\n" })
        if (!tmp.renameTo(file)) { tmp.delete(); throw IOException("could not replace $file") }
    }
    private fun ensureDir() { if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create $dir") }
}

const val SAMPLES_PER_DIGIT = 10
val LAB_MODELS = listOf("en-US", "fi-FI")

/** Sample box side in px for re-recognition; the recorder normalises every sample into this square. */
const val LAB_BOX_PX = 400f

@Composable
fun InkLab(store: InkSampleStore, recognizerFactory: (String) -> DigitRecognizer) {
    val scope = rememberCoroutineScope()
    var modelTag by rememberSaveable { mutableStateOf(LAB_MODELS[0]) }
    val recognizer = remember(modelTag) { recognizerFactory(modelTag) }
    val modelState by recognizer.state.collectAsState()
    // Only the default model downloads by itself; any other model needs an explicit tap (security review F5).
    LaunchedEffect(recognizer) { recognizer.prepare(allowDownload = modelTag == LAB_MODELS[0]) }
    var mode by rememberSaveable { mutableStateOf(InputMode.Pen) }
    var samples by remember { mutableStateOf(emptyList<InkSample>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = modelState == ModelState.Ready

    /**
     * Runs one recognise/save/delete operation at a time. [busy] is set before the coroutine starts, so a
     * second tap in the same frame is refused. Failures become a retryable message; cancellation still cancels.
     */
    fun runBusy(what: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "$what epäonnistui (${e.message ?: e::class.simpleName}). Yritä uudelleen." }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { runBusy("Näytteiden lukeminen") { samples = withContext(Dispatchers.IO) { store.load() } } }

    Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ink lab · #26", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Kehitystyökalu. Käsiala tallentuu vain tähän tablettiin.", style = MaterialTheme.typography.bodyMedium)

        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tunnistusmalli", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LAB_MODELS.forEach { tag -> FilterChip(selected = tag == modelTag, enabled = !busy, onClick = { modelTag = tag }, label = { Text(tag) }) }
            }
            Text(when (val s = modelState) {
                ModelState.Checking -> "Tarkistetaan mallia…"
                ModelState.NotDownloaded -> "Mallia $modelTag ei ole ladattu (noin 20 Mt, vain Wi-Fi)."
                ModelState.Downloading -> WIFI_WAIT_MESSAGE
                ModelState.Ready -> "Malli valmis. Tunnistus toimii ilman verkkoa."
                is ModelState.Failed -> s.message
            }, modifier = Modifier.testTag("model-state"))
            if (modelState is ModelState.Failed) Button(onClick = { scope.launch { recognizer.prepare() } }) { Text("Yritä uudelleen") }
            if (modelState == ModelState.NotDownloaded) Button(onClick = { scope.launch { recognizer.prepare(allowDownload = true) } }) { Text("Lataa malli $modelTag") }
        } }

        error?.let {
            Text(it, color = Color(0xFFB3261E), modifier = Modifier.testTag("lab-error").semantics { liveRegion = LiveRegionMode.Polite })
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Syöttötapa:")
            FilterChip(selected = mode == InputMode.Pen, onClick = { mode = InputMode.Pen }, label = { Text("Kynä") })
            FilterChip(selected = mode == InputMode.NumberPad, onClick = { mode = InputMode.NumberPad }, label = { Text("Numeronäppäimet") })
        }

        TrySection(mode, recognizer, ready)

        val counts = samples.groupingBy { it.label }.eachCount()
        val recorder = remember { InkBoxesController() }
        var digit by rememberSaveable { mutableStateOf(0) }
        var last by remember { mutableStateOf<String?>(null) }
        var hasInk by remember { mutableStateOf(false) }
        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nauhoita näytteitä", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..9).forEach { d ->
                    FilterChip(selected = d == digit, enabled = !busy, onClick = { digit = d; recorder.clear() },
                        label = { Text("$d · ${counts[d] ?: 0}/$SAMPLES_PER_DIGIT") }, modifier = Modifier.testTag("digit-$d"))
                }
            }
            Text("Kirjoita numero $digit ruutuun ja paina Tallenna. (${counts[digit] ?: 0}/$SAMPLES_PER_DIGIT)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("record-prompt"))
            InkBoxes(1, recorder, boxSize = 200.dp, onInk = { hasInk = it.isNotEmpty() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = ready && hasInk && !busy, modifier = Modifier.testTag("save"), onClick = {
                    // Snapshot everything that defines the sample before anything suspends (Codex MUST2).
                    val row = recorder.row ?: return@Button
                    val label = digit
                    val written = recorder.strokes
                    val scale = LAB_BOX_PX / row.boxWidth
                    val strokes = written.map { s -> InkStroke(s.points.map { it.copy(x = it.x * scale, y = it.y * scale) }) }
                    val height = row.boxHeight * scale
                    val r = recognizer
                    runBusy("Tallennus") {
                        // A failed inference throws here, so it never becomes a sample with no candidates.
                        val candidates = r.recognize(strokes, LAB_BOX_PX, height)
                        val sample = InkSample(label, strokes, candidates, r.modelTag)
                        withContext(Dispatchers.IO) { store.append(sample) }
                        samples = samples + sample
                        last = "Tallennettu $label: luettiin " + when (val reading = sample.reading) {
                            is DigitReading.Accepted -> "${reading.digit} (${pct(reading.confidence)})"
                            is DigitReading.Ask -> reading.question
                            is DigitReading.Unreadable -> "ei luettu"
                        } + " · ehdokkaat " + candidates.take(4).joinToString { it.text + (it.score?.let { s -> "/%.2f".format(s) } ?: "") }
                        // Remove only the ink this sample came from; anything written since stays.
                        recorder.remove(written)
                        val done = samples.count { it.label == label }
                        if (digit == label && done >= SAMPLES_PER_DIGIT) {
                            (1..10).map { (label + it) % 10 }.firstOrNull { d -> samples.count { it.label == d } < SAMPLES_PER_DIGIT }?.let { digit = it }
                        }
                    }
                }) { Text(if (busy) "Tallennetaan…" else "Tallenna") }
                OutlinedButton(onClick = { recorder.clear() }) { Text("Pyyhi") }
            }
            last?.let { Text(it, modifier = Modifier.testTag("last-sample")) }
        } }

        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tulokset", style = MaterialTheme.typography.titleMedium)
            val report = remember(samples) { AccuracyReport.of(samples) }
            Text(report.summary(), fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("report"))
            Text("Mallit näytteissä: " + samples.groupingBy { it.model }.eachCount().entries.joinToString { "${it.key} ${it.value}" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy && ready && samples.isNotEmpty(), onClick = {
                    val r = recognizer
                    val before = samples
                    runBusy("Uudelleentunnistus") {
                        val redone = before.map { s -> s.copy(candidates = r.recognize(s.strokes, LAB_BOX_PX, LAB_BOX_PX), model = r.modelTag) }
                        withContext(Dispatchers.IO) { store.replaceAll(redone) }
                        samples = redone
                    }
                }) { Text("Tunnista kaikki uudelleen ($modelTag)") }
                var confirm by remember { mutableStateOf(false) }
                OutlinedButton(enabled = !busy && samples.isNotEmpty(), onClick = { confirm = true }) { Text("Poista näytteet") }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false },
                    confirmButton = { TextButton(onClick = {
                        confirm = false
                        runBusy("Poistaminen") { withContext(Dispatchers.IO) { store.deleteAll() }; samples = emptyList() }
                    }) { Text("Poista") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Peru") } },
                    text = { Text("Poistetaanko kaikki ${samples.size} näytettä tästä tabletista?") })
            }
        } }
    }
}

@Composable
private fun TrySection(mode: InputMode, recognizer: DigitRecognizer, ready: Boolean) {
    val scope = rememberCoroutineScope()
    val controller = remember { InkBoxesController() }
    var readings by remember { mutableStateOf(mapOf<Int, DigitReading>()) }
    var chosen by remember { mutableStateOf(mapOf<Int, Int>()) }
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Each request carries this number; ink changes, clears, mode and model changes bump it (Codex SHOULD1).
    var request by remember { mutableStateOf(0) }
    val currentRecognizer by rememberUpdatedState(recognizer)
    LaunchedEffect(mode, recognizer) { request++; readings = emptyMap(); chosen = emptyMap() }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kokeile", style = MaterialTheme.typography.titleMedium)
        if (mode == InputMode.NumberPad) {
            Text("Vastaus: $typed", style = MaterialTheme.typography.headlineSmall)
            NumberPad(onDigit = { if (typed.length < 4) typed += it }, onErase = { typed = typed.dropLast(1) })
            return@Column
        }
        Text("Kirjoita numero jokaiseen ruutuun. Kynän sivunappi pyyhkii.")
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            InkBoxes(4, controller, onInk = { request++; readings = emptyMap(); chosen = emptyMap(); error = null })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = ready, onClick = {
                val row = controller.row ?: return@Button
                val boxes = controller.strokesByBox()
                val id = ++request
                val revision = controller.revision
                val r = recognizer
                scope.launch {
                    try {
                        val result = boxes.mapValues { (_, s) -> DigitRules.read(r.recognize(s, row.boxWidth, row.boxHeight)) }
                        if (id == request && revision == controller.revision && r === currentRecognizer) { readings = result; chosen = emptyMap(); error = null }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { if (id == request) error = "Tunnistus epäonnistui (${e.message ?: e::class.simpleName}). Yritä uudelleen." }
                }
            }) { Text("Tunnista") }
            OutlinedButton(onClick = { controller.clear() }) { Text("Tyhjennä") }
        }
        error?.let { Text(it, color = Color(0xFFB3261E), modifier = Modifier.testTag("try-error")) }
        readings.toSortedMap().forEach { (box, r) ->
            val label = "Ruutu ${box + 1}: "
            when (r) {
                is DigitReading.Accepted -> Text(label + "${r.digit} (${pct(r.confidence)})" + alternatives(r))
                is DigitReading.Unreadable -> Text(label + r.message + alternatives(r))
                is DigitReading.Ask -> chosen[box]?.let { Text(label + "$it (valittu)") } ?: Column {
                    Text(label + r.question + alternatives(r))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(r.first, r.second).forEach { d -> OutlinedButton(onClick = { chosen = chosen + (box to d) }) { Text("$d") } }
                    }
                }
            }
        }
    } }
}

private fun pct(f: Float) = "%.0f %%".format(f * 100)
private fun alternatives(r: DigitReading) = r.ranked.drop(1).take(2).takeIf { it.isNotEmpty() }?.joinToString(prefix = " · muut: ") { "${it.digit} ${pct(it.confidence)}" } ?: ""
