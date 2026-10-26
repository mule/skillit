package fi.skillit.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.skillit.engine.ExerciseSession

@Composable
fun IslandMap(adventure: ExerciseSession, practiceLevels: Set<Int>, busy: Boolean, open: (Int, Boolean) -> Unit) {
    val islands = Adventure.islands(adventure)
    Text("Saaret", style = MaterialTheme.typography.titleLarge)
    Text("Seikkailun pisteet: ${adventure.xp} XP. Harjoituskierrosten pisteet ovat erilliset.")
    islands.forEachIndexed { i, state ->
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${i + 1}. ${MusicPack.levels[i]}", style = MaterialTheme.typography.titleLarge)
            Text(when (state) {
                IslandState.LOCKED -> "Lukittu · ratkaise edellisen saaren kaikki tehtävät."
                IslandState.CURRENT -> "Nykyinen saari · 5 tehtävää omaan tahtiin."
                IslandState.COMPLETED -> "Valmis · saaren sävelmerkki ansaittu!"
            })
            AnimatedVisibility(visible = state == IslandState.COMPLETED, enter = fadeIn() + expandVertically()) {
                Text("♪ Hienoa harjoittelua!", style = MaterialTheme.typography.titleMedium)
            }
            if (state == IslandState.COMPLETED) Button(onClick = { open(i, true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (i in practiceLevels) "Jatka saaren ${i + 1} harjoitusta" else "Harjoittele saarta ${i + 1}")
            }
        } }
    }
}
