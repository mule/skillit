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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import fi.skillit.SkillitTheme
import fi.skillit.ink.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    fun append(s: InkSample) { dir.mkdirs(); file.appendText(InkSampleCodec.encode(s) + "\n") }
    fun replaceAll(all: List<InkSample>) { dir.mkdirs(); file.writeText(all.joinToString("") { InkSampleCodec.encode(it) + "\n" }) }
}

const val SAMPLES_PER_DIGIT = 10
val LAB_MODELS = listOf("en-US", "fi-FI")

@Composable
fun InkLab(store: InkSampleStore, recognizerFactory: (String) -> DigitRecognizer) {
    val scope = rememberCoroutineScope()
    var modelTag by rememberSaveable { mutableStateOf(LAB_MODELS[0]) }
    val recognizer = remember(modelTag) { recognizerFactory(modelTag) }
    val modelState by recognizer.state.collectAsState()
    LaunchedEffect(recognizer) { recognizer.prepare() }
    var mode by rememberSaveable { mutableStateOf(InputMode.Pen) }
    var samples by remember { mutableStateOf(emptyList<InkSample>()) }
    LaunchedEffect(Unit) { samples = withContext(Dispatchers.IO) { store.load() } }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ink lab · #26", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text("Kehitystyökalu. Käsiala tallentuu vain tähän tablettiin.", style = MaterialTheme.typography.bodyMedium)

        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tunnistusmalli", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LAB_MODELS.forEach { tag -> FilterChip(selected = tag == modelTag, onClick = { modelTag = tag }, label = { Text(tag) }) }
            }
            Text(when (val s = modelState) {
                ModelState.Checking -> "Tarkistetaan mallia…"
                ModelState.Downloading -> "Ladataan mallia (kerran)…"
                ModelState.Ready -> "Malli valmis. Tunnistus toimii ilman verkkoa."
                is ModelState.Failed -> s.message
            }, modifier = Modifier.testTag("model-state"))
            if (modelState is ModelState.Failed) Button(onClick = { scope.launch { recognizer.prepare() } }) { Text("Yritä uudelleen") }
        } }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Syöttötapa:")
            FilterChip(selected = mode == InputMode.Pen, onClick = { mode = InputMode.Pen }, label = { Text("Kynä") })
            FilterChip(selected = mode == InputMode.NumberPad, onClick = { mode = InputMode.NumberPad }, label = { Text("Numeronäppäimet") })
        }

        TrySection(mode, recognizer, modelState == ModelState.Ready)

        RecordSection(recognizer, modelState == ModelState.Ready, samples, busy) { sample ->
            busy = true
            scope.launch { withContext(Dispatchers.IO) { store.append(sample) }; samples = samples + sample; busy = false }
        }

        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tulokset", style = MaterialTheme.typography.titleMedium)
            val report = remember(samples) { AccuracyReport.of(samples) }
            Text(report.summary(), fontFamily = FontFamily.Monospace, modifier = Modifier.testTag("report"))
            Text("Mallit näytteissä: " + samples.groupingBy { it.model }.eachCount().entries.joinToString { "${it.key} ${it.value}" })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy && modelState == ModelState.Ready && samples.isNotEmpty(), onClick = {
                    busy = true
                    scope.launch {
                        val redone = samples.map { s ->
                            val r = BoxRow(1, LAB_BOX_PX, LAB_BOX_PX)
                            s.copy(candidates = recognizer.recognize(s.strokes, r.boxWidth, r.boxHeight), model = recognizer.modelTag)
                        }
                        withContext(Dispatchers.IO) { store.replaceAll(redone) }
                        samples = redone; busy = false
                    }
                }) { Text("Tunnista kaikki uudelleen (${modelTag})") }
                var confirm by remember { mutableStateOf(false) }
                OutlinedButton(enabled = !busy && samples.isNotEmpty(), onClick = { confirm = true }) { Text("Poista näytteet") }
                if (confirm) AlertDialog(onDismissRequest = { confirm = false },
                    confirmButton = { TextButton(onClick = { confirm = false; store.replaceAll(emptyList()); samples = emptyList() }) { Text("Poista") } },
                    dismissButton = { TextButton(onClick = { confirm = false }) { Text("Peru") } },
                    text = { Text("Poistetaanko kaikki ${samples.size} näytettä?") })
            }
        } }
    }
}

/** Sample box side in px for re-recognition; the recorder normalises every sample into this square. */
const val LAB_BOX_PX = 400f

@Composable
private fun TrySection(mode: InputMode, recognizer: DigitRecognizer, ready: Boolean) {
    val scope = rememberCoroutineScope()
    val controller = remember { InkBoxesController() }
    var readings by remember { mutableStateOf(mapOf<Int, DigitReading>()) }
    var chosen by remember { mutableStateOf(mapOf<Int, Int>()) }
    var typed by remember { mutableStateOf("") }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kokeile", style = MaterialTheme.typography.titleMedium)
        if (mode == InputMode.NumberPad) {
            Text("Vastaus: $typed", style = MaterialTheme.typography.headlineSmall)
            NumberPad(onDigit = { if (typed.length < 4) typed += it }, onErase = { typed = typed.dropLast(1) })
            return@Column
        }
        Text("Kirjoita numero jokaiseen ruutuun. Kynän sivunappi pyyhkii.")
        Box(Modifier.horizontalScroll(rememberScrollState())) { InkBoxes(4, controller, onInk = { readings = emptyMap(); chosen = emptyMap() }) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = ready, onClick = {
                scope.launch {
                    val row = controller.row ?: return@launch
                    readings = controller.strokesByBox().mapValues { (_, s) -> DigitRules.read(recognizer.recognize(s, row.boxWidth, row.boxHeight)) }
                    chosen = emptyMap()
                }
            }) { Text("Tunnista") }
            OutlinedButton(onClick = { controller.clear() }) { Text("Tyhjennä") }
        }
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

@Composable
private fun RecordSection(recognizer: DigitRecognizer, ready: Boolean, samples: List<InkSample>, busy: Boolean, onSample: (InkSample) -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { InkBoxesController() }
    val counts = samples.groupingBy { it.label }.eachCount()
    var digit by rememberSaveable { mutableStateOf(0) }
    var last by remember { mutableStateOf<String?>(null) }
    var hasInk by remember { mutableStateOf(false) }
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Nauhoita näytteitä", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..9).forEach { d -> FilterChip(selected = d == digit, onClick = { digit = d; controller.clear() }, label = { Text("$d · ${counts[d] ?: 0}/$SAMPLES_PER_DIGIT") }) }
        }
        Text("Kirjoita numero $digit ruutuun ja paina Tallenna. (${counts[digit] ?: 0}/$SAMPLES_PER_DIGIT)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("record-prompt"))
        InkBoxes(1, controller, boxSize = 200.dp, onInk = { hasInk = it.isNotEmpty() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = ready && hasInk && !busy, onClick = {
                scope.launch {
                    val row = controller.row ?: return@launch
                    // Normalise into a LAB_BOX_PX square so later re-recognition sees the same geometry.
                    val scale = LAB_BOX_PX / row.boxWidth
                    val strokes = controller.strokes.map { s -> InkStroke(s.points.map { it.copy(x = it.x * scale, y = it.y * scale) }) }
                    val candidates = recognizer.recognize(strokes, LAB_BOX_PX, row.boxHeight * scale)
                    val sample = InkSample(digit, strokes, candidates, recognizer.modelTag)
                    last = "Tallennettu: luettiin " + when (val r = sample.reading) {
                        is DigitReading.Accepted -> "${r.digit} (${pct(r.confidence)})"
                        is DigitReading.Ask -> r.question
                        is DigitReading.Unreadable -> "ei luettu"
                    } + " · ehdokkaat " + candidates.take(4).joinToString { it.text + (it.score?.let { s -> "/%.2f".format(s) } ?: "") }
                    onSample(sample)
                    controller.clear()
                    if ((counts[digit] ?: 0) + 1 >= SAMPLES_PER_DIGIT) (1..10).map { (digit + it) % 10 }.firstOrNull { (counts[it] ?: 0) < SAMPLES_PER_DIGIT }?.let { digit = it }
                }
            }) { Text("Tallenna") }
            OutlinedButton(onClick = { controller.clear() }) { Text("Pyyhi") }
        }
        last?.let { Text(it, modifier = Modifier.testTag("last-sample")) }
    } }
}

private fun pct(f: Float) = "%.0f %%".format(f * 100)
private fun alternatives(r: DigitReading) = r.ranked.drop(1).take(2).takeIf { it.isNotEmpty() }?.joinToString(prefix = " · muut: ") { "${it.digit} ${pct(it.confidence)}" } ?: ""
