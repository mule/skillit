package fi.skillit.coach

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fi.skillit.ink.AttemptInkStore
import fi.skillit.ink.CellInk
import fi.skillit.ink.DigitRecognizer
import fi.skillit.ink.DigitRules
import fi.skillit.ink.InkBoxes
import fi.skillit.ink.InkBoxesController
import fi.skillit.ink.InkStroke
import fi.skillit.ink.MlKitDigitRecognizer
import fi.skillit.ink.ModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the handwriting coach needs from outside. Tests pass a temporary store, a fake recogniser and a
 * plain Compose writing surface (androidx.ink's native code doesn't load on the JVM).
 */
class CoachEnvironment(
    val store: LegibilityStore?,
    val recognizer: DigitRecognizer?,
    val surface: CoachSurface = InkSurface,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Metric work (closure search, trace matching) never runs on the main thread (review: Codex should-fix 1). */
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    /** Raw strokes per answer attempt (#26), app-private, pruned to [AttemptInkStore.KEEP_ATTEMPTS]. Null = not kept. */
    val ink: AttemptInkStore? = null,
) {
    /** Adds reports to the aggregates. False when the disk refused; the child's play is never interrupted. */
    suspend fun record(reports: List<LegibilityReport>): Boolean = try {
        withContext(io) { store?.record(reports) }; true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /**
     * Measures and records the inked digits of one answer (see [writtenDigits]), and keeps the answer's strokes
     * under [attemptKey] in [ink], pruned to the newest [AttemptInkStore.KEEP_ATTEMPTS] attempts. Never affects XP.
     */
    suspend fun recordAnswer(digits: List<WrittenDigit>, attemptKey: String? = null): Boolean {
        val reports = withContext(compute) { digits.legibility() }
        val kept = if (attemptKey == null || ink == null) true else try {
            withContext(io) {
                val cells = digits.filter { it.strokes.isNotEmpty() }.associate { it.cell to CellInk(it.strokes, it.digit) }
                if (cells.isNotEmpty()) { ink.save(attemptKey, cells); ink.prune(AttemptInkStore.KEEP_ATTEMPTS) }
            }; true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        return record(reports) && kept
    }

    /** Trace accuracy against [guide], off the main thread. */
    suspend fun trace(guide: DigitGuide, strokes: List<List<UnitPoint>>): TraceReport? = withContext(compute) { Trace.score(guide, strokes) }

    suspend fun days(): List<DigitDay> = try { withContext(io) { store?.days().orEmpty() } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    fun today(): Long = store?.today() ?: java.time.LocalDate.now().toEpochDay()

    /** The recogniser's share for [digit], or null when the model isn't ready (the metric is then skipped). */
    suspend fun confidence(digit: Int, strokes: List<InkStroke>, width: Float, height: Float): Float? {
        val r = recognizer ?: return null
        if (r.state.value != ModelState.Ready || strokes.isEmpty()) return null
        return try { DigitRules.rank(r.recognize(strokes, width, height)).firstOrNull { it.digit == digit }?.confidence ?: 0f }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    suspend fun measure(digit: Int, pad: CoachPad): LegibilityReport? {
        // Snapshot the Compose state on the main thread; measure the snapshot in the background.
        val strokes = pad.strokes; val w = pad.boxWidth; val h = pad.boxHeight
        if (strokes.isEmpty() || w <= 0f) return null
        val confidence = confidence(digit, strokes, w, h)
        return withContext(compute) { Legibility.measure(digit, strokes, w, h, confidence) }
    }

    companion object {
        fun android(context: Context, recognizer: DigitRecognizer? = null) = CoachEnvironment(
            LegibilityStore.open(context.applicationContext), recognizer ?: MlKitDigitRecognizer(), ink = HandwritingData.attemptInk(context),
        )
    }
}

/** The one-box writing pad of the coach. Strokes are relative to the box; nothing here is saved. */
@Stable
class CoachPad {
    var strokes by mutableStateOf<List<InkStroke>>(emptyList())
    var boxWidth by mutableFloatStateOf(0f)
    var boxHeight by mutableFloatStateOf(0f)
    /** Set by the surface: wipes its own ink too. */
    var onClear: () -> Unit = {}
    fun clear() { strokes = emptyList(); onClear() }
    val unitStrokes: List<List<UnitPoint>>
        get() = if (boxWidth <= 0f || boxHeight <= 0f) emptyList() else Legibility.toUnit(strokes, boxWidth, boxHeight)
}

/** Where the child writes. Production: [InkSurface]. */
fun interface CoachSurface {
    @Composable fun Pad(pad: CoachPad, size: Dp, modifier: Modifier)
}

/** The S Pen surface from #26: low-latency ink, palm rejection, side-button eraser. */
object InkSurface : CoachSurface {
    @Composable override fun Pad(pad: CoachPad, size: Dp, modifier: Modifier) {
        val controller = remember(pad) { InkBoxesController() }
        SideEffect { pad.onClear = { controller.clear() } }
        InkBoxes(1, controller, modifier, boxSize = size, onInk = {
            controller.row?.let { r -> pad.boxWidth = r.boxWidth; pad.boxHeight = r.boxHeight }
            pad.strokes = controller.strokesByBox()[0].orEmpty()
        })
    }
}

private val GuideInk = Color(0xFF9CB8B0)
private val StartDot = Color(0xFF1E8E3E)
private val ArrowInk = Color(0xFF315D82)

/**
 * The box behind the ink: the top line (dashed) and the baseline, and when [guide] is set, the model digit
 * with a green start dot, arrows and stroke numbers.
 */
@Composable
fun GuideCanvas(guide: DigitGuide?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawLine(GuideInk, Offset(0f, WritingLines.TOP * h), Offset(w, WritingLines.TOP * h), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        drawLine(GuideInk, Offset(0f, WritingLines.BASELINE * h), Offset(w, WritingLines.BASELINE * h), 3f)
        guide ?: return@Canvas
        guide.strokes.forEachIndexed { i, stroke ->
            val path = Path().apply {
                moveTo(stroke[0].x * w, stroke[0].y * h)
                stroke.drop(1).forEach { lineTo(it.x * w, it.y * h) }
            }
            drawPath(path, GuideInk.copy(alpha = .45f), style = Stroke(width = w * .07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Arrows a third and two thirds along the stroke, and at its end.
            listOf(stroke.size / 3, 2 * stroke.size / 3, stroke.size - 1).distinct().filter { it > 0 }.forEach { k ->
                arrow(stroke[k - 1], stroke[k], w, h)
            }
            val s = stroke[0]
            drawCircle(StartDot, radius = w * .035f, center = Offset(s.x * w, s.y * h))
            if (guide.strokes.size > 1) drawContext.canvas.nativeCanvas.drawText("${i + 1}", s.x * w - w * .09f, s.y * h - w * .03f,
                android.graphics.Paint().apply { color = android.graphics.Color.rgb(0x31, 0x5D, 0x82); textSize = w * .08f; isAntiAlias = true; isFakeBoldText = true })
        }
    }
}

private fun DrawScope.arrow(from: UnitPoint, to: UnitPoint, w: Float, h: Float) {
    val a = atan2((to.y - from.y) * h, (to.x - from.x) * w)
    val tip = Offset(to.x * w, to.y * h)
    val len = w * .045f
    listOf(a + 2.6f, a - 2.6f).forEach { b -> drawLine(ArrowInk, tip, Offset(tip.x + len * cos(b), tip.y + len * sin(b)), w * .012f, StrokeCap.Round) }
}

/** A writing box with its lines and (optionally) the model digit underneath the ink. */
@Composable
fun CoachBox(env: CoachEnvironment, pad: CoachPad, guide: DigitGuide?, description: String, size: Dp = 240.dp) {
    Box(Modifier.size(size).testTag("coach-pad").semantics { contentDescription = description }) {
        GuideCanvas(guide, Modifier.matchParentSize())
        // A new pad (next step or digit) gets a fresh ink view; AndroidView would otherwise keep the old ink.
        key(pad) { env.surface.Pad(pad, size, Modifier) }
    }
}

/** Playful feedback. Always at least one star and never a wrong mark. */
@Composable
fun CoachFeedback(report: LegibilityReport?, modifier: Modifier = Modifier, trace: TraceReport? = null) {
    val score = listOfNotNull(report?.overall, trace?.score).average().toFloat().takeIf { !it.isNaN() } ?: return
    val stars = 1 + (if (score >= .6f) 1 else 0) + (if (score >= .85f) 1 else 0)
    val praise = when (stars) { 3 -> "Tosi selkeä!"; 2 -> "Hienoa, selvä numero!"; else -> "Hyvä yritys! Harjoitellaan vielä." }
    val tip = trace?.tip ?: report?.tip
    Card(modifier.fillMaxWidth().testTag("coach-feedback")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("★".repeat(stars) + "☆".repeat(3 - stars), style = MaterialTheme.typography.headlineMedium, color = Color(0xFFB8860B),
                modifier = Modifier.clearAndSetSemantics { contentDescription = "$stars tähteä kolmesta" })
            Text(praise, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            tip?.let { Text("Vinkki: $it") }
        }
    }
}

/**
 * The optional 10-second “kirjoita se vielä selvemmin” moment for one digit, shown by a math board after a
 * [ClarityPrompt]. The child can say “Ei nyt”; either way [onClose] is called and nothing about the answer
 * or its XP changes. A finished writing is measured and added to the legibility aggregates.
 */
@Composable
fun ClarityMoment(prompt: ClarityPrompt, env: CoachEnvironment, onClose: () -> Unit, seconds: Int = 10) {
    var phase by rememberSaveable(prompt) { mutableIntStateOf(0) } // 0 offer, 1 writing, 2 done
    var report by remember(prompt) { mutableStateOf<LegibilityReport?>(null) }
    val pad = remember(prompt) { CoachPad() }
    val scope = rememberCoroutineScope()
    fun finish() {
        if (phase != 1) return
        phase = 2
        scope.launch {
            report = env.measure(prompt.digit, pad)
            report?.let { env.record(listOf(it)) }
            if (report == null) onClose()
        }
    }
    Card(Modifier.fillMaxWidth().testTag("clarity-moment")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                0 -> {
                    Text("Kirjoitetaanko numero ${prompt.digit} vielä kerran selvemmin?", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text("Se vie 10 sekuntia. Vastauksesi ja pisteesi pysyvät ennallaan.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { phase = 1 }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Kokeillaan!") }
                        OutlinedButton(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) { Text("Ei nyt") }
                    }
                }
                1 -> {
                    var left by remember { mutableLongStateOf(seconds * 1000L) }
                    LaunchedEffect(Unit) {
                        while (left > 0) { delay(100); left -= 100 }
                        if (pad.strokes.isEmpty()) onClose() else finish()
                    }
                    Text("Kirjoita ${prompt.digit} rauhassa ja selvästi.", style = MaterialTheme.typography.titleMedium)
                    CoachBox(env, pad, DigitGuides.of(prompt.digit), "Kirjoitusruutu: kirjoita numero ${prompt.digit} mallin päälle", 200.dp)
                    LinearProgressIndicator(progress = { left / (seconds * 1000f) }, modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Aikaa jäljellä ${(left + 999) / 1000} sekuntia"
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { finish() }, enabled = pad.strokes.isNotEmpty(), modifier = Modifier.heightIn(min = 56.dp)) { Text("Valmis") }
                        OutlinedButton(onClick = { pad.clear() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Pyyhi") }
                    }
                }
                else -> {
                    CoachFeedback(report)
                    Button(onClick = onClose, modifier = Modifier.heightIn(min = 56.dp)) { Text("Jatka") }
                }
            }
        }
    }
}

private enum class StudioStep { HOME, TRACE, MEMORY, WARMUP, WARMUP_DONE }

/**
 * Numeropaja, the digit studio: trace a digit over its model (start dot, arrows, stroke order), then write
 * it from memory; or do the day's 5-digit warm-up, which picks the weakest digits. Nothing here gives or takes
 * XP. Open it from the math map (#27) or the main menu.
 */
@Composable
fun DigitStudio(env: CoachEnvironment, onBack: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(StudioStep.HOME) }
    var digit by rememberSaveable { mutableIntStateOf(0) }
    var warmUp by rememberSaveable { mutableStateOf("") }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var warmScores by rememberSaveable { mutableStateOf("") }
    var days by remember { mutableStateOf<List<DigitDay>?>(null) }
    var showModel by rememberSaveable(step, digit, index) { mutableStateOf(false) }
    var report by remember(step, digit, index) { mutableStateOf<LegibilityReport?>(null) }
    var trace by remember(step, digit, index) { mutableStateOf<TraceReport?>(null) }
    var checking by remember { mutableStateOf(false) }
    val pad = remember(step, digit, index) { CoachPad() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(env) { env.recognizer?.prepare(allowDownload = false) }
    LaunchedEffect(step) { if (step == StudioStep.HOME) days = env.days() }
    androidx.activity.compose.BackHandler(enabled = step != StudioStep.HOME) { step = StudioStep.HOME }

    fun check(record: Boolean, after: (LegibilityReport?) -> Unit = {}) {
        if (checking) return
        checking = true
        scope.launch {
            try {
                if (step == StudioStep.TRACE) trace = env.trace(DigitGuides.of(digit), pad.unitStrokes)
                val r = env.measure(digit, pad)
                report = r
                if (record && r != null) env.record(listOf(r))
                after(r)
            } finally { checking = false }
        }
    }

    Column(Modifier.fillMaxWidth().testTag("digit-studio"), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (step) {
            StudioStep.HOME -> {
                Text("Numeropaja", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Text("Harjoittele numeroiden kirjoittamista kynällä. Tämä on ihan omaksi iloksi: XP ja edistyminen eivät riipu käsialasta.")
                Button(onClick = {
                    warmUp = WarmUp.pick(days.orEmpty(), env.today()).joinToString(""); index = 0; warmScores = ""
                    digit = warmUp[0].digitToInt(); step = StudioStep.WARMUP
                }, enabled = days != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Päivän lämmittely · 5 numeroa") }
                Text("Valitse numero, jota haluat harjoitella:")
                (0..9).chunked(5).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        line.forEach { d ->
                            OutlinedButton(onClick = { digit = d; step = StudioStep.TRACE }, modifier = Modifier.size(72.dp).semantics { contentDescription = "Harjoittele numeroa $d" }) {
                                Text("$d", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Takaisin") }
            }
            StudioStep.TRACE, StudioStep.MEMORY, StudioStep.WARMUP -> {
                val guide = DigitGuides.of(digit)
                val title = when (step) {
                    StudioStep.TRACE -> "Numero $digit: jäljennä mallia"
                    StudioStep.MEMORY -> "Numero $digit: kirjoita muistista"
                    else -> "Lämmittely ${index + 1}/${warmUp.length}: kirjoita $digit"
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(if (step == StudioStep.TRACE) "Aloita vihreästä pisteestä ja seuraa nuolia. ${guide.tip}"
                    else "Kirjoita numero ruutuun niin, että se istuu alaviivalla.")
                val modelVisible = step == StudioStep.TRACE || showModel
                CoachBox(env, pad, guide.takeIf { modelVisible },
                    if (modelVisible) "Kirjoitusruutu, mallina numero $digit. Aloita vihreästä pisteestä." else "Kirjoitusruutu numerolle $digit")
                if (step != StudioStep.TRACE && report == null) TextButton(onClick = { showModel = !showModel }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showModel) "Piilota malli" else "Näytä malli")
                }
                if (report == null && trace == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        check(record = step != StudioStep.TRACE) { r ->
                            if (step == StudioStep.WARMUP) warmScores += (if (warmScores.isEmpty()) "" else ",") + "$digit:${r?.overall ?: 0f}"
                        }
                    }, enabled = pad.strokes.isNotEmpty() && !checking, modifier = Modifier.heightIn(min = 56.dp)) { Text("Valmis") }
                    OutlinedButton(onClick = { pad.clear() }, enabled = !checking, modifier = Modifier.heightIn(min = 56.dp)) { Text("Pyyhi") }
                } else {
                    CoachFeedback(report, trace = trace)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (step) {
                            StudioStep.TRACE -> Button(onClick = { step = StudioStep.MEMORY }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Kirjoita muistista") }
                            StudioStep.MEMORY -> Button(onClick = { report = null; pad.clear() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Uudestaan") }
                            else -> Button(onClick = {
                                if (index + 1 < warmUp.length) { index++; digit = warmUp[index].digitToInt() } else step = StudioStep.WARMUP_DONE
                            }, modifier = Modifier.heightIn(min = 56.dp)) { Text(if (index + 1 < warmUp.length) "Seuraava numero" else "Valmis!") }
                        }
                        if (step == StudioStep.TRACE) OutlinedButton(onClick = { trace = null; report = null; pad.clear() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Jäljennä uudestaan") }
                    }
                }
                TextButton(onClick = { step = StudioStep.HOME }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Numeropajan alkuun") }
            }
            StudioStep.WARMUP_DONE -> {
                val results = warmScores.split(",").filter { it.isNotEmpty() }.map { it.substringBefore(':').toInt() to it.substringAfter(':').toFloat() }
                Text("Lämmittely tehty!", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                results.maxByOrNull { it.second }?.let { (d, _) -> Text("Selkein tänään: numero $d. Hienoa!", style = MaterialTheme.typography.titleMedium) }
                Text("Kirjoitit numerot ${results.joinToString(", ") { it.first.toString() }}. Huomenna lämmittely valitsee taas sinulle sopivat numerot.")
                Button(onClick = { step = StudioStep.HOME }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Numeropajan alkuun") }
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Takaisin") }
            }
        }
    }
}

/**
 * Parent view hook (#27 parent area): legibility trend per digit, from aggregates only. Shows the last
 * 28 days as a small line, the latest week's level and the change from the weeks before.
 */
@Composable
fun LegibilityTrendSection(trends: List<DigitTrend>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("legibility-trend"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Numeroiden selkeys", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text("Tässä näkyvät vain yhteenvedot (0–100 %). Kynän jäljet pysyvät tabletilla. Käsiala ei vaikuta XP:hen.",
            style = MaterialTheme.typography.bodySmall)
        if (trends.all { it.samples == 0 }) { Text("Ei vielä kirjoitettuja numeroita."); return@Column }
        trends.filter { it.samples > 0 }.forEach { t ->
            val level = (t.recent ?: t.days.last().score).let { "${(it * 100).toInt()} %" }
            val change = t.change?.let { c -> val p = (c * 100).toInt(); if (p > 0) "nousua $p" else if (p < 0) "laskua ${-p}" else "ennallaan" }
            val description = buildString {
                append("Numero ${t.digit}: selkeys $level")
                change?.let { append(", $it prosenttiyksikköä") }
                append(", ${t.samples} kirjoitusta")
                t.weakestMetric?.let { append(", harjoiteltavaa: ${it.label.lowercase()}") }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${t.digit}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(24.dp))
                Sparkline(t.days.map { it.score }, Modifier.width(96.dp).height(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(level + (change?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    t.weakestMetric?.let { Text("Harjoiteltavaa: ${it.label.lowercase()}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** Loads the aggregates and shows [LegibilityTrendSection]. Drop this into the parent area. */
@Composable
fun LegibilityParentCard(env: CoachEnvironment, modifier: Modifier = Modifier) {
    var trends by remember { mutableStateOf<List<DigitTrend>?>(null) }
    LaunchedEffect(env) { trends = LegibilityStats.trend(env.days(), env.today()) }
    Card(modifier.fillMaxWidth()) {
        Box(Modifier.padding(16.dp)) { trends?.let { LegibilityTrendSection(it) } ?: Text("Ladataan…") }
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val pts = values.mapIndexed { i, v -> Offset(if (values.size == 1) size.width / 2 else i * size.width / (values.size - 1), size.height * (1f - v.coerceIn(0f, 1f))) }
        pts.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3f, StrokeCap.Round) }
        drawCircle(color, 4f, pts.last())
    }
}
