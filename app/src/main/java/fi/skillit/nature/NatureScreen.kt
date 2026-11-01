package fi.skillit.nature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun NatureScreen(onQuestionChanged: () -> Unit, onExit: () -> Unit) {
    var chapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var practising by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var hintShown by rememberSaveable { mutableStateOf(false) }
    // The shell owns scrolling; notify it on every route change and advancement.
    LaunchedEffect(chapterId, practising, index) { onQuestionChanged() }
    fun restart() { index = 0; correct = 0; selected = null; hintShown = false }
    fun open(id: String?, practise: Boolean = false) { restart(); chapterId = id; practising = practise }
    val chapter = chapterId?.let(NaturePack::chapter)
    fun leave() {
        // The continuous trip has no reading view of its own.
        if (practising && chapter != NaturePack.trip) open(chapterId) else open(null)
    }
    BackHandler(enabled = chapter != null) { leave() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(chapter?.title ?: "Ympäristöoppi", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        when {
            chapter == null -> ChapterList { open(it, practise = it == NaturePack.trip.id) }
            !practising -> ChapterReading(chapter) { open(chapter.id, practise = true) }
            index == chapter.questions.size -> {
                val questions = chapter.questions
                Text("Retki valmis!", style = MaterialTheme.typography.headlineMedium)
                Text("Tunnistit oikein $correct/${questions.size} tehtävää. Voit tutkia lajeja uudelleen omaan tahtiisi.")
                Button(onClick = { restart() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Uusi retki") }
                val following = NaturePack.chapters.getOrNull(NaturePack.chapters.indexOf(chapter) + 1)
                if (chapter != NaturePack.trip && following != null) {
                    OutlinedButton(onClick = { open(following.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Seuraava luku: ${following.title}")
                    }
                }
            }
            else -> {
                val questions = chapter.questions
                val question = questions[index]
                Text("Tehtävä ${index + 1}/${questions.size} · $correct oikein")
                LinearProgressIndicator(progress = { index.toFloat() / questions.size },
                    modifier = Modifier.fillMaxWidth())
                Text(question.prompt, style = MaterialTheme.typography.titleLarge)
                question.illustration?.let { image ->
                    Card(Modifier.fillMaxWidth()) {
                        Image(painter = painterResource(image), contentDescription = question.hint ?: "Tunnistettava lajin kuvitus",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(220.dp).padding(8.dp))
                    }
                }
                if (selected == null && question.hint != null) {
                    if (hintShown) Text(question.hint)
                    else TextButton(onClick = { hintShown = true }) { Text("Vihje") }
                }
                question.choices.forEach { choice ->
                    OutlinedButton(onClick = {
                        if (selected == null) {
                            selected = choice
                            if (choice == question.answer) correct++
                        }
                    }, enabled = selected == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(choice)
                    }
                }
                if (selected != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (selected == question.answer) "Oikein!" else "Oikea vastaus: ${question.answer}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Text(question.explanation)
                            Text("Lähde: käyttäjän toimittama ympäristöopin sivu ${question.sourcePage}.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { index++; selected = null; hintShown = false },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Jatka") }
                }
            }
        }
        if (chapter != null) TextButton(onClick = { open(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Kaikki luvut") }
        TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
    }
}

@Composable
private fun ChapterList(onOpen: (String) -> Unit) {
    Text("Valitse luku. Voit lukea ja harjoitella omaan tahtiisi.")
    (listOf(NaturePack.trip) + NaturePack.chapters).forEach { chapter ->
        OutlinedButton(onClick = { onOpen(chapter.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(chapter.title, style = MaterialTheme.typography.titleMedium)
                Text(if (chapter.questions.isEmpty()) "Sivut ${chapter.pages}" else "Sivut ${chapter.pages} · ${chapter.questions.size} tehtävää")
            }
        }
    }
}

@Composable
private fun ChapterReading(chapter: NatureChapter, onPractise: () -> Unit) {
    Text("Kirjan sivut ${chapter.pages}")
    Text("Tiivistelmä on kirjoitettu omin sanoin. Tarkemmat tiedot löydät kirjasta.", style = MaterialTheme.typography.bodySmall)
    chapter.summary.forEach { Text(it) }
    if (chapter.terms.isNotEmpty()) Text("Käsitteet: ${chapter.terms.joinToString(", ")}")
    if (chapter.questions.isNotEmpty()) Button(onClick = onPractise, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text("Aloita harjoitus (${chapter.questions.size} tehtävää)")
    }
    chapter.species.forEach { species ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                species.illustration?.let { image ->
                    Image(painter = painterResource(image), contentDescription = species.name,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
                Text(species.name, style = MaterialTheme.typography.titleMedium)
                Text(species.clue)
                Text(species.fact)
            }
        }
    }
}
