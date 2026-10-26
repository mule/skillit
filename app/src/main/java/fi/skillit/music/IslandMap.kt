package fi.skillit.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import fi.skillit.engine.ExerciseSession

@Composable
fun IslandMap(adventure: ExerciseSession, practiceLevels: Set<Int>, busy: Boolean, open: (Int, Boolean) -> Unit) {
    val islands = Adventure.islands(adventure)
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    Canvas(Modifier.fillMaxWidth().height(100.dp).clearAndSetSemantics {}) {
        drawRoundRect(Color(0xFFD9EFF1), cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()))
        val path = Path()
        islands.indices.forEach { i ->
            val x = size.width * (i + .5f) / 4
            val y = size.height * (if (i % 2 == 0) .4f else .65f)
            if (i == 0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path, Color(0xFF477B86), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        islands.forEachIndexed { i, state ->
            val x = size.width * (i + .5f) / 4
            val y = size.height * (if (i % 2 == 0) .4f else .65f)
            drawOval(Color(0xFFF2DAA2), Offset(x - 25.dp.toPx(), y - 13.dp.toPx()), Size(50.dp.toPx(), 30.dp.toPx()))
            drawCircle(when (state) { IslandState.LOCKED -> Color(0xFF687A79); IslandState.CURRENT -> Color(0xFF006A5C); IslandState.COMPLETED -> Color(0xFF8C6400) }, 16.dp.toPx(), Offset(x,y - 5.dp.toPx()))
        }
    }
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
            AnimatedVisibility(visible = revealed && state == IslandState.COMPLETED, enter = fadeIn() + expandVertically()) {
                Text("♪ Hienoa harjoittelua!", style = MaterialTheme.typography.titleMedium)
            }
            if (state == IslandState.CURRENT) OutlinedButton(onClick = { open(i, false) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Jatka saarelle ${i + 1}")
            }
            if (state == IslandState.COMPLETED) Button(onClick = { open(i, true) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(if (i in practiceLevels) "Jatka saaren ${i + 1} harjoitusta" else "Harjoittele saarta ${i + 1}")
            }
        } }
    }
}
