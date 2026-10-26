package fi.skillit.music

/** Treble clef: staffStep is a half-space above the bottom E4 line. */
enum class Pitch(val nameFi: String, val staffStep: Int, val positionFi: String) {
    C4("C", -2, "alimmalla apuviivalla"), D4("D", -1, "viivaston alla, ilman apuviivaa"),
    E4("E", 0, "alimmalla viivalla"), F4("F", 1, "ensimmäisessä välissä alhaalta"),
    G4("G", 2, "toisella viivalla alhaalta"), A4("A", 3, "toisessa välissä alhaalta"),
    H4("H", 4, "keskimmäisellä viivalla"), C5("C", 5, "kolmannessa välissä alhaalta");
}
enum class Duration(val eighths: Int, val noteFi: String, val restFi: String) {
    WHOLE(8, "kokonuotti", "kokotauko"), HALF(4, "puolinuotti", "puolitauko"),
    QUARTER(2, "neljäsosanuotti", "neljäsosatauko"), EIGHTH(1, "kahdeksasosanuotti", "kahdeksasosatauko");
}
sealed interface Symbol { val duration: Duration }
data class Note(val pitch: Pitch, override val duration: Duration) : Symbol
data class Rest(override val duration: Duration) : Symbol
enum class Meter(val beats: Int) { TWO_FOUR(2), FOUR_FOUR(4) }
data class Bar(val meter: Meter, val symbols: List<Symbol>) {
    val eighths: Int get() = symbols.sumOf { it.duration.eighths }
    val valid: Boolean get() = eighths == meter.beats * 2
    val missingEighths: Int get() = meter.beats * 2 - eighths
}
fun beatsFi(eighths: Int): String = when {
    eighths % 2 == 0 -> (eighths / 2).toString()
    eighths == 1 -> "½"
    else -> "${eighths / 2}½"
}
