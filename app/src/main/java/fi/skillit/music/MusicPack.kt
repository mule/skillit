package fi.skillit.music

import fi.skillit.engine.Choice
import fi.skillit.engine.Question

/** A task supplies both its notation and the domain-derived answer. */
sealed interface MusicTask {
    val symbols: List<Symbol>
    val meter: Meter? get() = null
    val answerLabel: String
    fun accepts(label: String): Boolean = label == answerLabel
}
data class PitchName(val pitch: Pitch) : MusicTask {
    override val symbols = listOf(Note(pitch, Duration.QUARTER))
    override val answerLabel = pitch.nameFi
}
data class SymbolName(val symbol: Symbol) : MusicTask {
    override val symbols = listOf(symbol)
    override val answerLabel = if (symbol is Rest) symbol.duration.restFi else symbol.duration.noteFi
}
data class CountBeats(override val symbols: List<Symbol>) : MusicTask {
    override val answerLabel = beatsFi(symbols.sumOf { it.duration.eighths })
}
data class MissingBeats(val bar: Bar) : MusicTask {
    override val symbols = bar.symbols
    override val meter = bar.meter
    override val answerLabel = beatsFi(bar.missingEighths)
}
data class MeterBeats(override val meter: Meter) : MusicTask {
    override val symbols = emptyList<Symbol>()
    override val answerLabel = meter.beats.toString()
}
data class FullBar(val bar: Bar) : MusicTask {
    override val symbols = bar.symbols
    override val meter = bar.meter
    override val answerLabel = if (bar.valid) "Kyllä" else "Ei"
}
data class MusicItem(val question: Question, val task: MusicTask)

object MusicPack {
    const val id = "music-grade4-fi"
    const val version = 1
    const val source = "Alkuperäinen sisältö; ei vertailtu alkuperäisiin tehtävämonisteisiin."
    val levels = listOf("Nuottininja", "Sävelsalapoliisi", "Rytmimestari", "Musiikkimestari")
    private fun note(d: Duration, p: Pitch = Pitch.G4) = Note(p, d)
    private fun item(number: Int, task: MusicTask, prompt: String, hint: String, explanation: String, vararg wrong: String): MusicItem {
        val labels = (listOf(task.answerLabel) + wrong).let { values ->
            val shift = number % values.size
            values.drop(shift) + values.take(shift)
        }
        require(labels.distinct().size == labels.size)
        val choices = labels.mapIndexed { i, label -> Choice("o$i", label) }
        return MusicItem(Question("m$number", (number - 1) / 5, prompt, choices,
            choices.single { task.accepts(it.label) }.id, hint, explanation), task)
    }
    private fun pitch(number: Int, p: Pitch) = item(number, PitchName(p),
        "Mikä on sävelen nimi?", "Alin viiva on E. Etene siitä: E F G A H C. Apuviivalla alhaalla on C.",
        "Sävel on ${p.positionFi}. Sen nimi on ${p.nameFi}.",
        *listOf("C", "D", "E", "F", "G", "A", "H").filter { it != p.nameFi }.take(3).toTypedArray())
    val items: List<MusicItem> = listOf(
        item(1, SymbolName(note(Duration.QUARTER)), "Mikä on tämän nuotin aika-arvon nimi?",
            "Musta nuotinpää ja varsi, ei lippua.", "Tämä on neljäsosanuotti. Sen kesto on 1 isku.",
            "puolinuotti", "kokonuotti", "kahdeksasosanuotti"),
        item(2, CountBeats(listOf(note(Duration.HALF))), "Kuinka monta iskua nuotti kestää?",
            "Avoin nuotinpää ja varsi tarkoittavat puolinuottia.", "Puolinuotin kesto on 2 iskua.", "1", "4", "½"),
        item(3, CountBeats(listOf(note(Duration.WHOLE))), "Kuinka monta iskua nuotti kestää?",
            "Avoin nuotinpää ilman vartta tarkoittaa kokonuottia.", "Kokonuotin kesto on 4 iskua.", "1", "2", "½"),
        item(4, CountBeats(listOf(note(Duration.EIGHTH))), "Kuinka monta iskua nuotti kestää?",
            "Varren lippu puolittaa neljäsosanuotin keston.", "Kahdeksasosanuotin kesto on ½ iskua.", "1", "2", "4"),
        item(5, CountBeats(listOf(Rest(Duration.HALF))), "Kuinka monta iskua tauko kestää?",
            "Viivan päällä lepäävä suorakulmio on puolitauko.", "Puolitauon kesto on 2 iskua.", "1", "4", "½"),
        pitch(6, Pitch.C4), pitch(7, Pitch.E4), pitch(8, Pitch.G4), pitch(9, Pitch.H4), pitch(10, Pitch.C5),
        item(11, CountBeats(listOf(note(Duration.EIGHTH), note(Duration.EIGHTH))), "Kuinka monta iskua nuotit kestävät yhteensä?",
            "Kumpikin nuotti kestää puoli iskua.", "½ + ½ = 1 isku.", "2", "½", "4"),
        item(12, MissingBeats(Bar(Meter.TWO_FOUR, listOf(note(Duration.QUARTER)))), "Kuinka monta iskua 2/4-tahdista puuttuu?",
            "2/4-tahtiin mahtuu 2 iskua. Laske jo näkyvät iskut.", "2 − 1 = 1 isku puuttuu.", "2", "4", "½"),
        item(13, MissingBeats(Bar(Meter.FOUR_FOUR, listOf(note(Duration.QUARTER), Rest(Duration.QUARTER)))), "Kuinka monta iskua 4/4-tahdista puuttuu?",
            "Myös tauko lasketaan mukaan. Tahtiin mahtuu 4 iskua.", "4 − (1 + 1) = 2 iskua puuttuu.", "1", "4", "½"),
        item(14, MeterBeats(Meter.TWO_FOUR), "Kuinka monta neljäsosaiskua on yhdessä 2/4-tahdissa?",
            "Tahtiosoituksen ylempi numero kertoo iskujen määrän.", "Ylempi numero 2 kertoo, että tahdissa on 2 iskua.", "1", "4", "½"),
        item(15, FullBar(Bar(Meter.FOUR_FOUR, listOf(note(Duration.HALF), note(Duration.QUARTER), Rest(Duration.QUARTER)))), "Onko 4/4-tahti täynnä?",
            "Laske nuottien ja tauon kestot yhteen.", "Kyllä: 2 + 1 + 1 = 4 iskua.", "Ei"),
        pitch(16, Pitch.D4), pitch(17, Pitch.F4), pitch(18, Pitch.A4),
        item(19, CountBeats(listOf(Rest(Duration.WHOLE))), "Kuinka monta iskua tämä kokotauko kestää tässä aika-arvotehtävässä?",
            "Viivasta riippuva suorakulmio on kokotauko.", "Kokotauon aika-arvo on 4 iskua.", "1", "2", "½"),
        item(20, CountBeats(listOf(Rest(Duration.QUARTER), Rest(Duration.EIGHTH))), "Kuinka monta iskua tauot kestävät yhteensä?",
            "Neljäsosatauko on 1 isku. Kahdeksasosatauko on puoli iskua.", "1 + ½ = 1½ iskua.", "1", "2", "½"),
    )
    val questions: List<Question> get() = items.map { it.question }
    fun item(id: String): MusicItem = items.single { it.question.id == id }
}
