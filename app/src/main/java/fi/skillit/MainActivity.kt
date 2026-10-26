package fi.skillit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.skillit.engine.ExerciseSession
import fi.skillit.music.MusicPack
import fi.skillit.music.Staff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val repository = ProgressRepository(applicationContext.skillitDataStore)
        setContent { SkillitTheme { SkillitApp(repository) } }
    }
}

@Composable
fun SkillitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF006A5C), onPrimary = Color.White,
        secondary = Color(0xFF315D82), background = Color(0xFFF5F8F5),
        surface = Color(0xFFF5F8F5), onSurface = Color(0xFF162927),
        onSurfaceVariant = Color(0xFF364D47),
    ), content = content)
}

@Composable
fun SkillitApp(repository: ProgressRepository) {
    var progress by remember { mutableStateOf<SavedProgress?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var inGame by rememberSaveable { mutableStateOf(true) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val session = remember(progress) { progress?.let { ExerciseSession.restore(MusicPack.questions, it.events) } }
    fun load() {
        busy = true
        scope.launch {
            try { progress = repository.load(); error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Tallennusta ei voitu lukea. Voit yrittää uudelleen tai aloittaa alusta." }
            finally { busy = false }
        }
    }
    fun save(value: SavedProgress, after: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            try { repository.save(value); progress = value; error = null; after() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Tallennus epäonnistui. Yritä uudelleen. Edellinen tilanne on yhä tallessa." }
            finally { busy = false }
        }
    }
    fun act(action: (ExerciseSession) -> Boolean) {
        val saved = progress ?: return
        if (busy) return
        val updated = ExerciseSession.restore(MusicPack.questions, saved.events)
        if (action(updated)) save(saved.copy(events = updated.events))
    }
    LaunchedEffect(repository) { load() }
    Surface(Modifier.fillMaxSize()) {
        val scroll = rememberScrollState()
        LaunchedEffect(session?.current?.token) { scroll.scrollTo(0) }
        Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(scroll).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Skillit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { settings = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Asetukset") }
                }
                error?.let { message ->
                    Card { Column(Modifier.padding(16.dp)) {
                        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (progress == null) {
                            TextButton(onClick = { load() }, enabled = !busy) { Text("Yritä uudelleen") }
                            TextButton(onClick = { confirmReset = true }, enabled = !busy) { Text("Aloita alusta") }
                        }
                    } }
                }
                val saved = progress
                when {
                    saved == null -> if (busy) Text("Ladataan seikkailua…")
                    !saved.started || !inGame -> {
                        Text("Nuottiseikkailu", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text("Tutki säveliä ja rytmejä omaan tahtiisi. Ei kiirettä!")
                        MusicPack.levels.forEachIndexed { i, title ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text("${i + 1}. $title", style = MaterialTheme.typography.titleMedium)
                                Text("5 tehtävää")
                            } }
                        }
                        Button(onClick = { save(saved.copy(started = true)) { inGame = true } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(if (saved.started) "Jatka seikkailua" else "Aloita seikkailu")
                        }
                    }
                    session != null && session.complete -> {
                        Text("Seikkailu valmis!", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text("Hienoa harjoittelua! Kävit läpi kaikki 20 tehtävää.")
                        Text("${session.xp} XP", style = MaterialTheme.typography.headlineMedium)
                        Text("Oikein ensimmäisellä yrityksellä: ${session.firstTryCorrect}/20")
                        Text("Harjoittelit uudelleen ${session.retryCount} tehtävää.")
                        Button(onClick = { confirmReset = true }, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp)) { Text("Uusi seikkailu") }
                    }
                    session?.current != null -> {
                        val attempt = session.current!!
                        val question = attempt.question
                        Text(MusicPack.levels[question.level], style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("Taso ${question.level + 1}/4 · ${session.resolvedCount}/20 ratkaistu · ${session.xp} XP")
                        LinearProgressIndicator(progress = { session.resolvedCount / 20f }, modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Edistyminen, ${session.resolvedCount} ratkaistua tehtävää kahdestakymmenestä"
                        })
                        if (attempt.retry) Text("Uusi yritys · voit saada 5 XP")
                        Text(question.prompt, style = MaterialTheme.typography.titleLarge)
                        Staff(MusicPack.item(question.id).task)
                        val feedback = session.feedback
                        question.choices.forEach { choice ->
                            val choiceStatus = feedback?.let {
                                listOfNotNull(
                                    if (it.choiceId == choice.id) "Valitsit tämän" else null,
                                    if (question.correctId == choice.id) "Oikea vastaus" else null,
                                ).joinToString(" · ").takeIf { text -> text.isNotEmpty() }
                            }
                            OutlinedButton(onClick = { act { it.answer(attempt.token, choice.id) } }, enabled = !busy && session.feedback == null,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics {
                                    if (choiceStatus != null) stateDescription = choiceStatus
                                }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(choice.label, style = MaterialTheme.typography.titleMedium,
                                        color = if (feedback != null) MaterialTheme.colorScheme.onSurface else Color.Unspecified)
                                    if (choiceStatus != null) Text(choiceStatus, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        if (feedback == null) {
                            OutlinedButton(onClick = { act { it.showHint(attempt.token) } }, enabled = !busy && !session.hintVisible,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("Vihje") }
                            if (session.hintVisible) Text(question.hint, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        } else {
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (feedback.correct) "Hienoa! +${feedback.xp} XP" else if (session.retryIsNext) "Harjoitellaan vielä. Voit yrittää uudelleen." else "Harjoitellaan vielä. Saat uuden yrityksen tason lopussa.",
                                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                Text(question.explanation)
                            } }
                            Button(onClick = { act { it.next(attempt.token) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(if (session.retryIsNext) "Yritä uudelleen" else "Jatka") }
                        }
                        TextButton(onClick = { inGame = false }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
                    }
                }
                if (busy && progress != null) Text("Tallennetaan…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Asetukset") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Äänet mykistetty", Modifier.weight(1f))
                Switch(checked = progress?.muted ?: false, enabled = progress != null && !busy,
                    onCheckedChange = { muted -> progress?.let { save(it.copy(muted = muted)) } },
                    modifier = Modifier.semantics { contentDescription = "Äänet mykistetty" })
            }
            Text("Tässä seikkailussa ei ole ääntä.")
            TextButton(onClick = { settings = false; confirmReset = true }, enabled = !busy) { Text("Nollaa edistyminen") }
        }
    }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Sulje") } })
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Aloitetaanko alusta?") },
        text = { Text("Tämän seikkailun tehtävät ja XP nollataan. Ääniasetus säilyy.") },
        confirmButton = { TextButton(onClick = { save(SavedProgress(muted = progress?.muted ?: false)) { confirmReset = false; inGame = true } }, enabled = !busy) { Text("Nollaa") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }, enabled = !busy) { Text("Peruuta") } })
}
