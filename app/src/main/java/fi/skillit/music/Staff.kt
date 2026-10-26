package fi.skillit.music

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.skillit.R

/** Geometry descriptions avoid naming the answer in pitch and symbol exercises. */
fun notationDescription(task: MusicTask): String = buildString {
    append("G-avain, viisi viivaa. ")
    task.meter?.let { append("Tahtiosoitus ${it.beats}/4. ") }
    task.symbols.forEach { symbol ->
        when (symbol) {
            is Note -> {
                append("Nuotinpää ${symbol.pitch.positionFi}, ")
                append(if (symbol.duration == Duration.WHOLE || symbol.duration == Duration.HALF) "avoin" else "musta")
                if (symbol.duration != Duration.WHOLE) append(", varsi")
                if (symbol.duration == Duration.EIGHTH) append(", lippu tai palkki")
                append(". ")
            }
            is Rest -> append(when (symbol.duration) {
                Duration.WHOLE -> "Suorakulmio riippuu toiseksi ylimmästä viivasta. "
                Duration.HALF -> "Suorakulmio lepää keskimmäisellä viivalla. "
                Duration.QUARTER -> "Mutkitteleva taukomerkki. "
                Duration.EIGHTH -> "Taukomerkissä yksi koukku ja vino varsi. "
            })
        }
    }
    if (task.meter != null) append("Lopussa tahtiviiva.")
}

@Composable
fun Staff(task: MusicTask, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typeface = remember { context.resources.getFont(R.font.bravura) }
    Canvas(modifier.fillMaxWidth().height(180.dp).background(Color.White)
        .semantics { contentDescription = notationDescription(task) }) {
        val s = 12.dp.toPx()
        val bottom = size.height / 2 + s * 2
        val geometry = NotationGeometry(bottom, s)
        val stroke = 1.3.dp.toPx()
        val ink = Color(0xFF14202B)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(20, 32, 43)
            this.typeface = typeface
            textSize = s * 4 // SMuFL em = four staff spaces.
        }
        fun glyph(text: String, x: Float, y: Float) = drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
        repeat(5) { i ->
            drawLine(ink, Offset(10.dp.toPx(), bottom - i * s), Offset(size.width - 10.dp.toPx(), bottom - i * s), stroke)
        }
        glyph("\uE050", 18.dp.toPx(), bottom - s) // G clef wraps the G4 line.
        if (task.meter != null) {
            glyph(if (task.meter!!.beats == 2) "\uE082" else "\uE084", 53.dp.toPx(), bottom - 3 * s)
            glyph("\uE084", 53.dp.toPx(), bottom - s)
        }
        val start = if (task.meter == null) 90.dp.toPx() else 102.dp.toPx()
        val end = size.width - 36.dp.toPx()
        val count = task.symbols.size
        val step = (end - start) / count.coerceAtLeast(1)
        fun x(i: Int) = if (count == 1) (start + end) / 2 else start + step * (i + 0.5f)
        val beamStarts = beamStarts(task.symbols)
        val beamed = beamStarts.flatMap { listOf(it, it + 1) }.toSet()
        val headW = s * 1.25f
        val headH = s * 0.86f
        fun stemX(i: Int, down: Boolean) = x(i) + if (down) -headW * 0.44f else headW * 0.44f
        fun stemEnd(i: Int, note: Note): Float {
            val pairStart = if (i in beamStarts) i else if (i - 1 in beamStarts) i - 1 else null
            val down = geometry.stemDown(note.pitch)
            val base = if (pairStart != null) {
                val other = task.symbols[pairStart + if (i == pairStart) 1 else 0] as Note
                if (down) maxOf(geometry.noteY(note.pitch), geometry.noteY(other.pitch))
                else minOf(geometry.noteY(note.pitch), geometry.noteY(other.pitch))
            } else geometry.noteY(note.pitch)
            return base + if (down) s * 3.5f else -s * 3.5f
        }
        task.symbols.forEachIndexed { i, symbol ->
            val cx = x(i)
            when (symbol) {
                is Note -> {
                    val cy = geometry.noteY(symbol.pitch)
                    geometry.ledgerLines(symbol.pitch).forEach { y ->
                        drawLine(ink, Offset(cx - s, y), Offset(cx + s, y), stroke)
                    }
                    val hollow = symbol.duration == Duration.WHOLE || symbol.duration == Duration.HALF
                    val w = if (symbol.duration == Duration.WHOLE) s * 1.65f else headW
                    rotate(if (symbol.duration == Duration.WHOLE) 0f else -20f, Offset(cx, cy)) {
                        drawOval(if (hollow) Color.White else ink, Offset(cx - w / 2, cy - headH / 2), Size(w, headH))
                        if (hollow) drawOval(ink, Offset(cx - w / 2, cy - headH / 2), Size(w, headH), style = Stroke(2.dp.toPx()))
                    }
                    if (symbol.duration != Duration.WHOLE) {
                        val down = geometry.stemDown(symbol.pitch)
                        val sx = stemX(i, down)
                        val sy = stemEnd(i, symbol)
                        drawLine(ink, Offset(sx, cy), Offset(sx, sy), 1.6.dp.toPx())
                        if (symbol.duration == Duration.EIGHTH && i !in beamed) {
                            glyph(if (down) "\uE241" else "\uE240", sx, sy)
                        }
                    }
                }
                is Rest -> {
                    val y = geometry.restY(symbol.duration)
                    when (symbol.duration) {
                        Duration.WHOLE -> drawRect(ink, Offset(cx - s * 0.65f, y), Size(s * 1.3f, s * 0.5f))
                        Duration.HALF -> drawRect(ink, Offset(cx - s * 0.65f, y - s * 0.5f), Size(s * 1.3f, s * 0.5f))
                        else -> glyph(if (symbol.duration == Duration.QUARTER) "\uE4E5" else "\uE4E6", cx - s * 0.5f, y)
                    }
                }
            }
        }
        beamStarts.forEach { i ->
            val note = task.symbols[i] as Note
            val down = geometry.stemDown(note.pitch)
            val y = stemEnd(i, note)
            val x1 = stemX(i, down)
            val x2 = stemX(i + 1, down)
            val thickness = if (down) -4.dp.toPx() else 4.dp.toPx()
            drawPath(Path().apply { moveTo(x1, y); lineTo(x2, y); lineTo(x2, y + thickness); lineTo(x1, y + thickness); close() }, ink)
        }
        if (task.meter != null) {
            drawLine(ink, Offset(size.width - 16.dp.toPx(), bottom), Offset(size.width - 16.dp.toPx(), bottom - s * 4), stroke)
        }
    }
}
