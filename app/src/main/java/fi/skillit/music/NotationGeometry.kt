package fi.skillit.music

class NotationGeometry(val bottom: Float, val space: Float) {
    fun noteY(pitch: Pitch): Float = bottom - pitch.staffStep * space / 2
    fun ledgerLines(pitch: Pitch): List<Float> = if (pitch == Pitch.C4) listOf(bottom + space) else emptyList()
    fun stemDown(pitch: Pitch): Boolean = pitch.staffStep >= 4
    fun restY(duration: Duration): Float = when (duration) {
        Duration.WHOLE -> bottom - space * 3
        else -> bottom - space * 2
    }
}

/** Only beam pairs occupying one complete quarter-note beat. */
fun beamStarts(symbols: List<Symbol>): Set<Int> {
    val starts = mutableSetOf<Int>()
    var eighths = 0
    symbols.forEachIndexed { i, symbol ->
        if (eighths % 2 == 0 && symbol is Note && symbol.duration == Duration.EIGHTH &&
            symbols.getOrNull(i + 1).let { it is Note && it.duration == Duration.EIGHTH && (it.pitch.staffStep >= 4) == (symbol.pitch.staffStep >= 4) }) {
            starts += i
        }
        eighths += symbol.duration.eighths
    }
    return starts
}
