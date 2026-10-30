package fi.skillit.nature

import fi.skillit.R

data class NatureSpecies(
    val id: String,
    val name: String,
    val group: String,
    val illustration: Int,
    val clue: String,
    val fact: String,
    val sourcePage: Int,
)

data class NatureQuestion(
    val id: String,
    val prompt: String,
    val choices: List<String>,
    val answer: String,
    val explanation: String,
    val sourcePage: Int,
    val illustration: Int? = null,
    val hint: String? = null,
)

object NaturePack {
    const val id = "finnish-nature-grade4-pages-4-13"

    val species = listOf(
        NatureSpecies("paivankakkara", "päivänkakkara", "kasvi", R.drawable.nature_paivankakkara,
            "Kukassa on keltainen keskusta ja valkoiset terälehdet.", "Päivänkakkara kukkii kesä–elokuussa.", 10),
        NatureSpecies("pietaryrtti", "pietaryrtti", "kasvi", R.drawable.nature_pietaryrtti,
            "Keltaisissa kukinnoissa on pieniä kukkia.", "Pietaryrtti kukkii heinä–syyskuussa.", 10),
        NatureSpecies("koiranputki", "koiranputki", "kasvi", R.drawable.nature_koiranputki,
            "Valkoisessa kukinnossa on paljon pieniä kukkia.", "Koiranputki on korkea niitty- ja pientarekasvi.", 10),
        NatureSpecies("vuohenputki", "vuohenputki", "kasvi", R.drawable.nature_vuohenputki,
            "Valkoisten kukkien alla on koiranputkea leveämmät lehdet.", "Vuohenputki leviää voimakkaasti.", 10),
        NatureSpecies("siankarsamo", "siankärsämö", "kasvi", R.drawable.nature_siankarsamo,
            "Tukevan varren päässä on paljon valkoisia kukkia.", "Siankärsämö kukkii heinä–syyskuussa.", 10),
        NatureSpecies("pujo", "pujo", "kasvi", R.drawable.nature_pujo,
            "Pitkässä kasvissa on tukeva ruskea varsi.", "Pujon siitepöly aiheuttaa monille allergiaa.", 10),
        NatureSpecies("pihatatar", "pihatatar", "kasvi", R.drawable.nature_pihatatar,
            "Kasvaa lähes maanpintaa pitkin.", "Pihatattarella on pienet vaaleanpunaiset kukat.", 11),
        NatureSpecies("pihasaunio", "pihasaunio", "kasvi", R.drawable.nature_pihasaunio,
            "Rihmamaisten lehtien yllä on pyöreitä kukintoja.", "Pihasauniota käytetään yrttikasvina.", 11),
        NatureSpecies("kissankello", "kissankello", "kasvi", R.drawable.nature_kissankello,
            "Kukat ovat sinisiä, suippoja ja kellomaisia.", "Kissankello kukkii kesä–elokuussa.", 11),
        NatureSpecies("harakankello", "harakankello", "kasvi", R.drawable.nature_harakankello,
            "Kukat ovat sinivioletit ja kellomaiset.", "Harakankellon kukat kääntyvät auringon valoa kohti.", 11),
        NatureSpecies("niittyleinikki", "niittyleinikki", "kasvi", R.drawable.nature_niittyleinikki,
            "Kukat ovat keltaiset.", "Niittyleinikki on lievästi myrkyllinen kasvi.", 11),
        NatureSpecies("maitohorsma", "maitohorsma", "kasvi", R.drawable.nature_maitohorsma,
            "Pitkällä kasvilla on purppuranpunaiset kukat.", "Maitohorsmalla on kapeat, suipot lehdet.", 11),
        NatureSpecies("heinasirkka", "heinäsirkka", "eläin", R.drawable.nature_heinasirkka,
            "Takajalat sopivat hyppäämiseen.", "Heinäsirkka syö kasveja.", 12),
        NatureSpecies("hepokatti", "hepokatti", "eläin", R.drawable.nature_hepokatti,
            "Pitkät tuntosarvet ja hyppyjalat.", "Hepokatti syö hyönteisiä.", 12),
        NatureSpecies("kastemato", "kastemato", "eläin", R.drawable.nature_kastemato,
            "Pitkä, jaokkeinen ruumis ilman jalkoja.", "Kastemato syö maahan pudonneita lehtiä ja kuolleita kasveja.", 12),
        NatureSpecies("leppakerttu", "leppäkerttu", "eläin", R.drawable.nature_leppakerttu,
            "Punaisissa peitinsiivissä on mustia pilkkuja.", "Leppäkerttu syö kirvoja ja punkkeja.", 12),
        NatureSpecies("sitruunaperhonen", "sitruunaperhonen", "eläin", R.drawable.nature_sitruunaperhonen,
            "Keltaiset siivet muistuttavat lehtiä.", "Sitruunaperhonen on perhonen, jonka kuvassa siivet ovat keltaiset.", 13),
        NatureSpecies("amiraaliperhonen", "amiraaliperhonen", "eläin", R.drawable.nature_amiraaliperhonen,
            "Tummissa siivissä on oranssinpunainen kuvio.", "Amiraaliperhonen lentää talveksi etelään.", 13),
        NatureSpecies("kimalainen", "kimalainen", "eläin", R.drawable.nature_kimalainen,
            "Karvainen, musta-keltainen pölyttäjä.", "Kimalainen käyttää ravinnokseen mettä ja siitepölyä.", 13),
        NatureSpecies("kontiainen", "kontiainen", "eläin", R.drawable.nature_kontiainen,
            "Tumma maanalainen nisäkäs, jonka etujalat sopivat kaivamiseen.", "Kontiainen syö kastematoja ja toukkia.", 13),
    )

    private val conceptQuestions = listOf(
        NatureQuestion("habitat", "Missä Suomen ympäristössä kasvaa paljon puita?",
            listOf("metsässä", "tunturin huipulla", "järvessä", "pellolla"), "metsässä",
            "Metsät ovat puiden kasvupaikkoja ja eläinten elinympäristöjä.", 4),
        NatureQuestion("reindeer", "Minkä ympäristön eläin poro on tässä aineistossa?",
            listOf("tunturin", "pellon", "järven", "kaupungin"), "tunturin",
            "Tunturialueella elää paljon poroja.", 4),
        NatureQuestion("human", "Missä ympäristössä ihmisen vaikutus näkyy eniten?",
            listOf("rakennetussa ympäristössä", "suolla", "tunturilla", "metsässä"), "rakennetussa ympäristössä",
            "Rakennetussa ympäristössä on ihmisten tekemiä teitä ja rakennuksia.", 5),
        NatureQuestion("kingdom", "Mihin eliökuntaan sieni kuuluu?",
            listOf("sieniin", "kasveihin", "eläimiin", "bakteereihin"), "sieniin",
            "Sienet ovat oma eliöryhmänsä eläinten, kasvien, alkueliöiden ja bakteerien rinnalla.", 6),
        NatureQuestion("backbone", "Mihin selkärankaisten ryhmään sammakko kuuluu?",
            listOf("sammakkoeläimiin", "kaloihin", "matelijoihin", "lintuihin"), "sammakkoeläimiin",
            "Sammakko on selkärankainen sammakkoeläin.", 7),
        NatureQuestion("meadow", "Mitä niittykasvit tarvitsevat runsaasti?",
            listOf("valoa", "varjoa", "suolavettä", "lunta"), "valoa",
            "Niitty, piha ja piennar ovat valoisia kasvupaikkoja.", 8),
        NatureQuestion("pollination", "Mitä pölytyksessä kulkeutuu kukasta toiseen?",
            listOf("siitepölyä", "siemeniä", "multaa", "vettä"), "siitepölyä",
            "Hyönteiset ja tuuli voivat kuljettaa siitepölyä kukasta toiseen.", 9),
    )

    private val rotatedConceptQuestions = conceptQuestions.mapIndexed { index, question ->
        val shift = (index + 1) % question.choices.size
        question.copy(choices = question.choices.drop(shift) + question.choices.take(shift))
    }

    private val speciesQuestions = species.mapIndexed { index, item ->
        val group = species.filter { it.group == item.group }
        val distractors = (1..3).map { offset -> group[(group.indexOf(item) + offset) % group.size].name }
        val labels = listOf(item.name) + distractors
        val shift = index % labels.size
        NatureQuestion("species-${item.id}", "Mikä laji kuvassa on?",
            labels.drop(shift) + labels.take(shift), item.name, item.fact, item.sourcePage, item.illustration, item.clue)
    }

    val questions: List<NatureQuestion> = buildList {
        speciesQuestions.forEachIndexed { index, question ->
            add(question)
            if (index % 3 == 2) rotatedConceptQuestions.getOrNull(index / 3)?.let { add(it) }
        }
        add(rotatedConceptQuestions.last())
    }
}
