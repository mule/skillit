package fi.skillit.music

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*

@Composable
fun SoundLab(audio: PlaybackController, silent: Boolean) {
    var status by remember { mutableStateOf("Valitse sävel tai tahtilaji ja kuuntele.") }
    val playing by audio.playingState.collectAsState()
    fun play(samples: ShortArray) {
        status = if (audio.play(samples)) "Soitetaan…" else if (audio.failure) "Ääntä ei voitu toistaa. Voit yrittää uudelleen." else "Äänet ovat pois päältä. Muuta asetuksia kuunnellaksesi."
    }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sävelpaja", style = MaterialTheme.typography.titleLarge)
        Pitch.entries.chunked(4).forEach { pitches -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            pitches.forEach { pitch -> OutlinedButton(onClick = { play(MusicAudio.render(listOf(Note(pitch, Duration.QUARTER)))) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Kuuntele ${pitch.nameFi}${if (pitch == Pitch.C5) "5" else "4"}" }) {
                Text("${pitch.nameFi}${if (pitch == Pitch.C5) "5" else "4"}")
            } }
        } }
        Meter.entries.forEach { meter -> OutlinedButton(onClick = { play(MusicAudio.metronome(meter)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kuuntele ${meter.beats}/4 syke") } }
        Button(onClick = { audio.stop(); status = "Pysäytetty." }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pysäytä ääni") }
        Text(if (silent) "Äänet ovat pois päältä." else if (playing) status else if (status == "Soitetaan…") "Kuuntelu valmis." else status,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    } }
}
