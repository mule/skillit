package fi.skillit.nature

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
fun NatureScreen(onExit: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var correct by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var hintShown by rememberSaveable { mutableStateOf(false) }
    val questions = NaturePack.questions
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Niittyetsivä", style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        if (index == questions.size) {
            Text("Retki valmis!", style = MaterialTheme.typography.headlineMedium)
            Text("Tunnistit oikein $correct/${questions.size} tehtävää. Voit tutkia lajeja uudelleen omaan tahtiisi.")
            Button(onClick = { index = 0; correct = 0; selected = null; hintShown = false },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Uusi retki") }
        } else {
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
        TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 48.dp)) { Text("Etusivu") }
    }
}
