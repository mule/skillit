package fi.skillit.music

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
fun RhythmScreen(audio: PlaybackController, suspended: Boolean, onBack: () -> Unit) {
    var meterIndex by rememberSaveable { mutableStateOf(0) }
    var seed by rememberSaveable { mutableStateOf(0) }
    var encoded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var feedback by rememberSaveable { mutableStateOf("") }
    var timing by remember { mutableStateOf(false) }
    var pulse by remember { mutableStateOf("") }
    val meter = Meter.entries[meterIndex]
    val challenge = remember(meter, seed) { RhythmChallenge.generate(meter, seed) }
    val added = encoded.map { code ->
        val duration = Duration.valueOf(code.drop(1))
        if (code.first() == 'n') Note(Pitch.C4, duration) else Rest(duration)
    }
    val taps = remember(challenge) { TapSession(challenge.target) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun stop() { timing = false; taps.cancel(); audio.stop(); pulse = "" }
    DisposableEffect(lifecycle, taps) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { timing = false; taps.cancel(); audio.stop() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); taps.cancel(); audio.stop() }
    }
    LaunchedEffect(suspended) { if (suspended) stop() }
    LaunchedEffect(timing) {
        if (timing) {
            for (count in 3 downTo 1) { pulse = "Valmistaudu: $count"; delay(600) }
            val start = SystemClock.elapsedRealtime()
            taps.start(start)
            audio.play(MusicAudio.render(challenge.target.symbols))
            while (SystemClock.elapsedRealtime() - start < taps.durationMs) {
                val elapsed = SystemClock.elapsedRealtime() - start
                pulse = "Isku ${elapsed / 600 + 1}/${meter.beats}"
                delay(30)
            }
            val result = taps.finish()
            feedback = "Kokeilu valmis: ${result.matched}/${result.expected} äänen alkua lähellä sykettä. Muut napautukset: ${result.extra}. Voit kokeilla uudelleen omaan tahtiin."
            timing = false; pulse = ""
        }
    }
    fun add(symbol: Symbol) {
        if (encoded.size >= 16) { feedback = "Tahti on jo pitkä. Poista merkkejä ja kokeile uudelleen."; return }
        stop()
        encoded = encoded + ((if (symbol is Note) "n" else "r") + symbol.duration.name)
        feedback = ""
    }
    Text("Rytmipaja", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text("Täydennä tahti nuoteilla tai tauoilla. Napauta merkkiä tai pidä sitä painettuna ja vedä tahtiin. Ei aikarajaa eikä XP-vaatimusta.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Meter.entries.forEachIndexed { index, value ->
        FilterChip(selected = meterIndex == index, onClick = { stop(); meterIndex = index; encoded = emptyList(); feedback = "" },
            label = { Text("${value.beats}/4") }, modifier = Modifier.heightIn(min = 48.dp))
    } }
    var dropBounds by remember { mutableStateOf(Rect.Zero) }
    var dragging by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().onGloballyPositioned { dropBounds = it.boundsInRoot() }.semantics { contentDescription = "Täydennettävä tahti" }) {
        Column(Modifier.padding(12.dp)) {
            Text("${meter.beats}/4 · ${beatsFi((challenge.prefix + added).sumOf { it.duration.eighths })}/${meter.beats} iskua")
            RhythmStaff(Bar(meter, challenge.prefix + added))
            Text("Alkumerkit säilyvät. Pudota merkki tähän tai lisää se painikkeella.")
            if (added.isNotEmpty()) Text("Lisätyt: " + added.joinToString(" · ") { if (it is Rest) it.duration.restFi else it.duration.noteFi })
        }
    }
    listOf(false, true).forEach { rest ->
        Duration.entries.filter { it.eighths <= meter.beats * 2 && !(rest && it == Duration.WHOLE && meter == Meter.TWO_FOUR) }.forEach { duration ->
            val symbol = if (rest) Rest(duration) else Note(Pitch.C4, duration)
            var origin by remember { mutableStateOf(Offset.Zero) }
            var drag by remember { mutableStateOf(Offset.Zero) }
            OutlinedButton(onClick = { add(symbol) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(symbol, challenge, encoded) {
                    detectDragGesturesAfterLongPress(onDragStart = { dragging = true; drag = origin + it },
                        onDrag = { change, amount -> change.consume(); drag += amount },
                        onDragEnd = { dragging = false; if (dropBounds.contains(drag)) add(symbol) }, onDragCancel = { dragging = false })
                }) { Text("Lisää ${if (rest) duration.restFi else duration.noteFi} · ${beatsFi(duration.eighths)} iskua") }
        }
    }
    OutlinedButton(onClick = { stop(); encoded = encoded.dropLast(1); feedback = "" }, enabled = encoded.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Poista viimeinen") }
    Button(onClick = {
        stop()
        feedback = when (challenge.evaluate(added)) {
            FillResult.CORRECT -> "Hienoa! Tahti on täynnä. Kuuntele oma rytmisi."
            FillResult.UNDERFILLED -> "Tahtiin mahtuu vielä. Lisää nuotti tai tauko."
            FillResult.OVERFLOW -> "Tahti on liian pitkä. Poista merkki ja kokeile uudelleen."
        }
        if (challenge.evaluate(added) == FillResult.CORRECT) audio.play(MusicAudio.render(challenge.prefix + added))
    }, modifier = Modifier.heightIn(min = 56.dp)) { Text("Tarkista tahti") }
    if (feedback.isNotEmpty()) Text(feedback, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    OutlinedButton(onClick = { stop(); if (!audio.play(MusicAudio.render(challenge.target.symbols))) feedback = "Kuuntelu ei käynnistynyt. Tarkista ääniasetukset." }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kuuntele esimerkkirytmi") }
    Text("Esimerkkirytmi: taukojen kohdalla ei napauteta. Voit myös vain kuunnella ja lukea.")
    RhythmStaff(challenge.target)
    Text(challenge.target.symbols.joinToString(" · ") { if (it is Rest) it.duration.restFi else it.duration.noteFi })
    Button(onClick = { stop(); timing = true }, enabled = !timing && !suspended, modifier = Modifier.heightIn(min = 56.dp)) { Text("Kokeile mukana naputtelua") }
    if (timing) {
        Text(pulse)
        Button(onClick = { taps.tap(SystemClock.elapsedRealtime()) }, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)) { Text("Napauta") }
    }
    OutlinedButton(onClick = { stop(); feedback = "Pysäytetty. Voit aloittaa uudelleen." }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pysäytä harjoitus") }
    OutlinedButton(onClick = { stop(); seed = if (seed == Int.MAX_VALUE) 0 else seed + 1; encoded = emptyList(); feedback = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Uusi rytmi") }
    TextButton(onClick = { stop(); onBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Takaisin kartalle") }
}

/** Keep dense and deliberately overfilled bars readable without changing M1 notation. */
@Composable
private fun RhythmStaff(bar: Bar) {
    val width = maxOf(280, 138 + bar.symbols.size * 30).dp
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Staff(FullBar(bar), Modifier.width(width))
    }
}
