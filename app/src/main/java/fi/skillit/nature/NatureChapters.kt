package fi.skillit.nature

data class NatureChapter(
    val id: String,
    val title: String,
    val firstPage: Int,
    val lastPage: Int,
    val summary: List<String>,
    val terms: List<String>,
    val species: List<NatureSpecies>,
    val questions: List<NatureQuestion>,
) {
    val pages: String get() = "$firstPage–$lastPage"
}

/**
 * Printed pages 14–33, staged and NOT shown to the child. PR17 records these from paraphrased notes that
 * no one has compared with the book. See docs/nature-answer-key-review.md for what each item still needs.
 * To enable content after it is validated, add its IDs to the two sets below. No other code changes.
 */
internal object NatureStaging {
    val approvedChapterIds = setOf("elinymparistot", "eliokunta", "niityt", "niittykasvit", "niittyelaimet")
    val approvedQuestionIds = emptySet<String>()

    val species = listOf(
        NatureSpecies("kuusi", "kuusi", "puu", null,
            "Lyhyet neulaset kasvavat oksalla yksittäin.", "Kuusi menestyy varjossa ja riittävän kosteilla paikoilla.", 18),
        NatureSpecies("manty", "mänty", "puu", null,
            "Pitkät neulaset ovat pareittain ja latvus on usein rungon yläosassa.", "Mänty kestää kuivuutta, mutta tarvitsee runsaasti valoa.", 18),
        NatureSpecies("kataja", "kataja", "puu", null,
            "Usein pensasmainen ja piikikäs, marjamaiset kävyt.", "Kataja kasvaa valoisilla kasvupaikoilla.", 18),
        NatureSpecies("pihlaja", "pihlaja", "puu", null,
            "Lehdet koostuvat useista lehdyköistä; syksyllä punaisia marjoja.", "Pihlajan marjat ovat monen linnun ravintoa.", 18),
        NatureSpecies("rauduskoivu", "rauduskoivu", "puu", null,
            "Lehti on kolmiomainen ja kahteen kertaan sahalaitainen.", "Rauduskoivu menestyy kuivemmilla paikoilla kuin hieskoivu.", 19),
        NatureSpecies("hieskoivu", "hieskoivu", "puu", null,
            "Lehti on pyöreämpi ja vain kerran sahalaitainen.", "Hieskoivu kasvaa kosteilla kasvupaikoilla.", 19),
        NatureSpecies("haapa", "haapa", "puu", null,
            "Pyöreähkö lehti ja vihertävänharmaa runko.", "Haavan lehdet havisevat herkästi tuulessa.", 19),
        NatureSpecies("harmaaleppa", "harmaaleppä", "puu", null,
            "Suipot lehdet ja harmaa runko.", "Harmaaleppä viihtyy usein kosteilla paikoilla.", 19),
        NatureSpecies("metsamyyra", "metsämyyrä", "nisäkäs", null,
            "Pieni ruskea jyrsijä, pitkä häntä.", "Metsämyyrä syö muun muassa kasvien osia ja marjoja.", 24),
        NatureSpecies("metsajanis", "metsäjänis", "nisäkäs", null,
            "Talvella valkoinen, kesällä ruskea turkki; pitkät takajalat.", "Metsäjänis syö kesällä heiniä ja talvella muun muassa puiden kuorta.", 24),
        NatureSpecies("hirvi", "hirvi", "nisäkäs", null,
            "Pitkät jalat ja sorkat; koiraalla sarvet.", "Hirvi on kasvinsyöjä ja liikkuu myös soisessa maastossa.", 24),
        NatureSpecies("valkohantakauris", "valkohäntäkauris", "nisäkäs", null,
            "Ruskea hirvieläin, jonka häntään liittyy selvä valkoinen alue.", "Valkohäntäkauris on Suomeen istutettu laji.", 24),
        NatureSpecies("mayra", "mäyrä", "nisäkäs", null,
            "Mustavalkoiset kasvojuovat ja lyhyet jalat.", "Mäyrä tekee pesänsä maan alle ja on kaikkiruokainen.", 25),
        NatureSpecies("kettu", "kettu", "nisäkäs", null,
            "Punertava turkki, suipot korvat ja tuuhea häntä.", "Kettu syö muun muassa pieniä nisäkkäitä.", 25),
        NatureSpecies("ilves", "ilves", "nisäkäs", null,
            "Täplikäs turkki, lyhyt häntä ja korvatupsut.", "Ilves saalistaa esimerkiksi jäniksiä ja myyriä.", 25),
        NatureSpecies("susi", "susi", "nisäkäs", null,
            "Suuri koiraeläin, harmaanruskea turkki.", "Sudet voivat elää laumassa omalla reviirillään.", 25),
        NatureSpecies("metso", "metso", "lintu", null,
            "Suuri metsäkanalintu; koiras tumma ja levittää pyrstönsä viuhkaksi.", "Metso syö vuodenajan mukaan muun muassa silmuja, marjoja ja neulasia.", 28),
        NatureSpecies("teeri", "teeri", "lintu", null,
            "Koiraalla tumma höyhenpuku ja lyyranmuotoinen pyrstö.", "Teerellä on kevättalvella ryhmäsoidin.", 28),
        NatureSpecies("kapytikka", "käpytikka", "lintu", null,
            "Mustavalkoinen tikka, jolla on punaista höyhenpuvussa.", "Käpytikka hakkaa nokallaan pesäkoloja puuhun.", 28),
        NatureSpecies("huuhkaja", "huuhkaja", "lintu", null,
            "Suuri ruskeankirjava pöllö, jolla on korvatupsut.", "Huuhkaja saalistaa usein öisin.", 28),
        NatureSpecies("peippo", "peippo", "lintu", null,
            "Koiraalla punertava rinta ja päässä siniharmaata; siivissä valkoiset juovat.", "Peippo syö siemeniä ja pieniä selkärangattomia.", 29),
        NatureSpecies("rakattirastas", "räkättirastas", "lintu", null,
            "Harmahtava pää, täplikäs rinta ja ruskeat siivet.", "Räkättirastas syö matoja, hyönteisiä ja marjoja.", 29),
        NatureSpecies("homotiainen", "hömötiainen", "lintu", null,
            "Musta päälaki ja leukalappu, harmaa selkä.", "Hömötiainen liikkuu talvella usein tiaisparvissa.", 29),
        NatureSpecies("hippiainen", "hippiäinen", "lintu", null,
            "Hyvin pieni vihertävä lintu, jolla on päälaella värikäs juova.", "Hippiäinen syö hämähäkkejä ja hyönteisiä.", 29),
    )

    private fun concept(id: String, page: Int, prompt: String, answer: String, wrong: List<String>, explanation: String) =
        NatureQuestion(id, prompt, listOf(answer) + wrong, answer, explanation, page)

    private val concepts = listOf(
        // p14–17: from statements in the spread-level notes; there is no structured source record.
        concept("peat", 14, "Mistä turve muodostuu?", "kuolleista kasvien osista",
            listOf("hiekasta ja kivistä", "eläinten luista", "järven vedestä"), "Kuolleita kasvien osia kertyy suolle turpeeksi."),
        concept("bog-plant", 14, "Mikä kasvi kuuluu suon kasveihin?", "rahkasammal",
            listOf("päivänkakkara", "koiranputki", "kissankello"), "Rahkasammalet ja suomarjat kuuluvat suon kasveihin."),
        concept("tree-parts", 16, "Mitkä ovat puun kolme pääosaa?", "juuret, runko ja latvus",
            listOf("kukat, siemenet ja marjat", "kävyt, neulaset ja silmut", "kuori, oksat ja lehdet"), "Puun osat ovat juuret, runko ja latvus."),
        concept("pine-needles", 16, "Miten männyn neulaset kasvavat?", "pareittain",
            listOf("yksittäin", "kolmen ryhmissä", "viiden ryhmissä"), "Männyllä neulaset kasvavat pareittain, kuusella yksittäin."),
        concept("tree-needs", 16, "Mitä puu tarvitsee kasvaakseen?", "valoa, vettä ja ravinteita",
            listOf("vain lunta ja tuulta", "vain hiekkaa", "suolavettä ja pimeyttä"), "Puu tarvitsee kasvaakseen valoa, vettä ja ravinteita."),
        // p20–33: prompts and answers are the PR17 concept records; the wrong choices are new.
        concept("forest-renewal", 20, "Miksi metsään istutetaan puuntaimia?", "Jotta kaadettujen puiden tilalle kasvaisi uusia puita.",
            listOf("Jotta metsään tulisi vähemmän varjoa.", "Jotta maasta katoaisivat ravinteet.", "Jotta eläimet lähtisivät metsästä."),
            "Puu kasvaa hitaasti, joten metsän uudistumista tuetaan istuttamalla taimia."),
        concept("forest-thinning", 21, "Miksi metsää harvennetaan?", "Jäljelle jääville puille jää enemmän valoa, vettä ja ravinteita.",
            listOf("Metsä halutaan muuttaa pelloksi tai niityksi.", "Kaikki metsän eläimet halutaan pois.", "Puut kasvavat parhaiten aivan vierekkäin."),
            "Kun osa puista poistetaan, kasvamaan jäävät puut saavat enemmän kasvuun tarvitsemaansa."),
        concept("deadwood", 21, "Miksi metsässä tarvitaan myös kuolleita puita?", "Ne tarjoavat elinpaikkoja ja pesäkoloja muille eliöille.",
            listOf("Ne estävät uusien puiden kasvun.", "Ne pitävät eläimet poissa metsästä.", "Ne tekevät metsästä valoisamman yöllä."),
            "Kolot ja lahopuu ovat monien metsän eliöiden elinpaikkoja."),
        concept("mammal", 23, "Miten nisäkäs ruokkii poikasiaan?", "Nisäkkään emo ruokkii poikasia maidolla.",
            listOf("Emo tuo poikasille vain siemeniä.", "Poikaset etsivät heti itse ruokansa.", "Emo munii ravintoa poikasille."),
            "Maidolla ruokkiminen on nisäkkäille yhteinen piirre."),
        concept("territory", 23, "Mitä reviiri tarkoittaa?", "Eläimen tai eläinryhmän omaa aluetta.",
            listOf("Eläimen maan alle kaivamaa pesää.", "Eläimen paksua talviturkkia.", "Reittiä, jota eläin muuttaa etelään."),
            "Esimerkiksi susilaumalla voi olla oma reviiri."),
        concept("hoof", 23, "Mikä on hirven sorkka?", "Jalan kärki, jossa on kaksi isoon kynteen päättyvää varvasta.",
            listOf("Hirven koiraan päässä kasvava sarvi.", "Hirven pitkä ja tuuhea häntä.", "Hirven talvella syömä puun kuori."),
            "Sorkkien avulla hirvi liikkuu metsissä ja pehmeällä maalla."),
        concept("mammal-footprints", 24, "Miten metsäjäniksen ja hirven jälkiä voi erottaa?", "Metsäjäniksellä on tassunjäljet, hirvellä sorkanjäljet.",
            listOf("Metsäjäniksellä on sorkanjäljet, hirvellä tassunjäljet.", "Molemmilla on samanlaiset sorkanjäljet.", "Kumpikaan ei jätä jälkiä lumeen."),
            "Nisäkkäitä voi erottaa toisistaan sorkan- ja tassunjälkien avulla."),
        concept("courtship", 27, "Mitä lintujen soidin tarkoittaa?", "Koiras houkuttelee naarasta lisääntymään esimerkiksi ääntelemällä.",
            listOf("Linnut muuttavat yhdessä talveksi etelään.", "Poikaset opettelevat lentämään pesän lähellä.", "Linnut etsivät talvella ravintoa parvessa."),
            "Esimerkiksi metso kokoontuu soitimelle."),
        concept("woodpecker", 27, "Mihin käpytikka käyttää nokkaansa?", "Se tekee pesäkoloja ja etsii hyönteisten toukkia puusta.",
            listOf("Se kaivaa maahan pesän ja etsii matoja.", "Se pyydystää kaloja järvestä.", "Se saalistaa öisin suuria nisäkkäitä."),
            "Käpytikka hakkaa puuhun koloja nokallaan."),
        concept("winter-flock", 27, "Miksi tiaiset liikkuvat talvella parvissa?", "Parvessa ne huomaavat saalistajat helpommin.",
            listOf("Parvessa niiden ei tarvitse syödä lainkaan.", "Parvi lämmittää koko metsän.", "Parvessa ne voivat rakentaa pesiä talvella."),
            "Hömötiainen liittyy talvisin usein tiaisten sekaparviin."),
        concept("ant-roles", 31, "Mitkä ovat muurahaisyhdyskunnan ryhmät?", "Kuningattaret, työläiset ja koiraat.",
            listOf("Vain kuningattaret ja poikaset.", "Metsästäjät, uimarit ja lentäjät.", "Emot, isät ja mummot."),
            "Muurahaiset elävät yhdyskunnissa, joissa eri yksilöillä on eri tehtäviä."),
        concept("spider-not-insect", 31, "Mikä erottaa hämähäkin hyönteisestä?", "Hämähäkillä on kahdeksan jalkaa; hyönteisellä kuusi.",
            listOf("Hämähäkillä on kuusi jalkaa; hyönteisellä kahdeksan.", "Hämähäkillä on selkäranka; hyönteisellä ei ole.", "Hämähäkki on kasvi; hyönteinen on eläin."),
            "Jalkojen määrä on helppo tapa erottaa hämähäkki hyönteisestä."),
        concept("food-chain-order", 32, "Mikä ravintoketju on oikeassa järjestyksessä?", "apila → metsäjänis → ilves",
            listOf("ilves → metsäjänis → apila", "metsäjänis → apila → ilves", "apila → ilves → metsäjänis"),
            "Ketju alkaa kasvista, jatkuu kasvinsyöjään ja päättyy petoon."),
        concept("food-start", 33, "Mistä ravintoketju alkaa?", "Kasveista, jotka valmistavat ravintoa auringonvalon avulla.",
            listOf("Pedoista, jotka syövät muita eläimiä.", "Sienistä, jotka hajottavat kuolleita eliöitä.", "Linnuista, jotka syövät marjoja."),
            "Kasvit valmistavat ravintoa auringonvalon avulla, ja kasvinsyöjät syövät kasveja."),
        concept("arrows", 33, "Mitä ravintoketjun nuolet näyttävät?", "Mihin suuntaan ravinto siirtyy eliöltä toiselle.",
            listOf("Mihin suuntaan eläimet muuttavat talveksi.", "Kuinka suuri kukin eläin on.", "Missä metsän osassa eliö elää."),
            "Nuoli kuvaa ravinnon siirtymistä seuraavalle eliölle."),
        concept("herbivore", 33, "Mitä kasvinsyöjä syö?", "Kasveja tai niiden osia.",
            listOf("Vain toisia eläimiä.", "Vain kuolleita eläimiä.", "Ei mitään, vaan se valmistaa ravintonsa itse."),
            "Kasvinsyöjät syövät kasveja, ja pedot syövät muita eläimiä."),
        concept("decomposers", 33, "Mikä on hajottajien tehtävä?", "Hajottaa kuollutta eloperäistä ainesta; esimerkkejä ovat sienet ja bakteerit.",
            listOf("Saalistaa eläimiä; esimerkkejä ovat ilves ja susi.", "Pölyttää kukkia; esimerkkejä ovat kimalaiset ja perhoset.", "Valmistaa ravintoa auringonvalosta; esimerkkejä ovat puut."),
            "Sienet ja bakteerit hajottavat kuollutta eloperäistä ainesta."),
    )

    private val speciesQuestions = species.map { item ->
        val group = species.filter { it.group == item.group }
        val wrong = (1..3).map { offset -> group[(group.indexOf(item) + offset) % group.size].name }
        NatureQuestion("species-${item.id}", "Mikä laji on kyseessä? ${item.clue}",
            listOf(item.name) + wrong, item.name, item.fact, item.sourcePage)
    }

    val questions: List<NatureQuestion> = (concepts + speciesQuestions).mapIndexed { index, question ->
        val shift = index % question.choices.size
        question.copy(choices = question.choices.drop(shift) + question.choices.take(shift))
    }
}

/** Only approved chapters and questions reach the child. A species card follows its question. */
internal fun activeChapters(all: List<NatureChapter>, chapterIds: Set<String>, questionIds: Set<String>) =
    all.filter { it.id in chapterIds }.map { chapter ->
        chapter.copy(species = chapter.species.filter { "species-${it.id}" in questionIds },
            questions = chapter.questions.filter { it.id in questionIds })
    }

/** The fifteen spreads of the book, in own words. Pages 4–13 were read from the supplied photos. */
internal fun natureChapters(allSpecies: List<NatureSpecies>, allQuestions: List<NatureQuestion>): List<NatureChapter> {
    fun chapter(id: String, title: String, first: Int, summary: List<String>, terms: List<String> = emptyList()) = NatureChapter(
        id, title, first, first + 1, summary, terms,
        allSpecies.filter { it.sourcePage in first..first + 1 },
        allQuestions.filter { it.sourcePage in first..first + 1 },
    )
    return listOf(
        chapter("elinymparistot", "Suomessa on monenlaisia elinympäristöjä", 4, listOf(
            "Eri elinympäristöissä kasvaa erilaisia kasveja ja elää erilaisia eläimiä.",
            "Tunturit ovat korkeita mäkiä, joiden huipulla ei kasva puita. Tunturialueella elää paljon poroja.",
            "Suurin osa Suomesta on metsää. Metsät tarjoavat eläimille suojaa ja pesäpaikkoja.",
            "Pellot ja rakennettu ympäristö ovat paikkoja, joissa ihmisen vaikutus näkyy eniten.",
        ), listOf("tunturi", "metsä", "suo", "pelto", "niitty", "rakennettu ympäristö")),
        chapter("eliokunta", "Eliökunnan luokittelu", 6, listOf(
            "Kaikki eliöt tarvitsevat ravintoa, lisääntyvät, kasvavat ja lopulta kuolevat.",
            "Eliöitä ovat eläimet, kasvit, sienet, alkueliöt ja bakteerit.",
            "Eläimet jaetaan selkärankaisiin ja selkärangattomiin.",
        ), listOf("eliökunta", "selkärankaiset", "selkärangattomat", "hyönteiset")),
        chapter("niityt", "Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja", 8, listOf(
            "Niityn kasvit tarvitsevat paljon valoa. Myös pihat ja teiden pientareet ovat valoisia kasvupaikkoja.",
            "Hyönteiset saavat kukista ravinnokseen mettä ja siitepölyä.",
            "Pölytyksessä siitepöly kulkee kukasta toiseen hyönteisten tai tuulen mukana. Sen jälkeen kukkiin alkaa kehittyä siemeniä.",
            "Niittyjä on nykyään vähemmän kuin ennen. Niittäminen antaa niityn kasveille valoa ja tilaa.",
        ), listOf("niitty", "piennar", "mesi", "siitepöly", "pölytys")),
        chapter("niittykasvit", "Niityn ja pientareen kasveja", 10, listOf(
            "Lajikorteissa kerrotaan kasvin tuntomerkit, korkeus ja kukinta-aika.",
        )),
        chapter("niittyelaimet", "Niityn ja pientareen eläimiä", 12, listOf(
            "Lajikorteissa kerrotaan eläimen pituus, ravinto ja lisääntyminen.",
        )),
        chapter("suot", "Suomi on metsäinen ja soinen maa", 14, listOf(
            "Suomessa on paljon metsiä ja soita. Suolla maaperä on märkä.",
            "Kuolleita kasvien osia kertyy suolle turpeeksi.",
            "Suon kasveihin kuuluvat esimerkiksi rahkasammalet ja suomarjat.",
        ), listOf("suo", "turve", "rahkasammal", "räme", "korpi", "avosuo")),
        chapter("puun-rakenne", "Puut kasvavat korkeiksi", 16, listOf(
            "Puun osat ovat juuret, runko ja latvus.",
            "Männyllä neulaset kasvavat pareittain, kuusella yksittäin.",
            "Puu kasvaa pituutta ja paksuutta. Rungon poikkileikkauksessa näkyvät vuosirenkaat.",
            "Puu tarvitsee kasvaakseen valoa, vettä ja ravinteita.",
        ), listOf("juuret", "runko", "latvus", "neulaset", "käpy", "vuosirengas")),
        chapter("puut", "Puita", 18, listOf(
            "Puita voi tunnistaa neulasista, lehdistä, marjoista ja rungosta.",
        )),
        chapter("metsanhoito", "Metsissä kasvatetaan puita", 20, listOf(
            "Puu kasvaa hitaasti. Metsän uudistumista tuetaan istuttamalla taimia.",
            "Kasvavaa metsää harvennetaan, jotta puille jää valoa, vettä ja ravinteita.",
            "Puuta käytetään esimerkiksi rakentamiseen ja paperin valmistukseen.",
            "Kolot ja lahopuu tarjoavat elinpaikkoja muille eliöille.",
        ), listOf("taimi", "harvennus", "lahopuu")),
        chapter("nisakkaat", "Metsän nisäkkäitä", 22, listOf(
            "Nisäkkään emo ruokkii poikasia maidolla.",
            "Hirvi liikkuu metsissä ja pehmeällä maalla sorkkiensa avulla.",
            "Susi on peto, ja susilaumalla voi olla oma reviiri.",
            "Metsämyyrä on pieni ja nopeasti lisääntyvä jyrsijä, joka on monille pedoille tärkeää ravintoa.",
        ), listOf("nisäkäs", "kasvinsyöjä", "peto", "jyrsijä", "sorkka", "reviiri")),
        chapter("nisakaslajit", "Metsän nisäkkäitä: lajikortit", 24, listOf(
            "Nisäkkäitä voi erottaa esimerkiksi jäljistä, ravinnosta ja väristä.",
        )),
        chapter("linnut", "Metsän lintuja", 26, listOf(
            "Metso on suuri metsäkanalintu, joka kokoontuu soitimelle.",
            "Käpytikka hakkaa puuhun pesäkoloja ja etsii puusta hyönteisten toukkia.",
            "Hömötiainen liittyy talvisin usein tiaisten sekaparviin. Huuhkaja on öisin saalistava suuri pöllö.",
        ), listOf("soidin", "pesäkolo", "reviiri", "petolintu")),
        chapter("lintulajit", "Metsän lintuja: lajikortit", 28, listOf(
            "Lintuja voi tunnistaa erityisesti väreistä, nokan muodosta ja koosta.",
        )),
        chapter("selkarangattomat", "Hyönteiset ja hämähäkit ovat selkärangattomia", 30, listOf(
            "Metsässä on runsaasti pieniä selkärangattomia eläimiä.",
            "Hyönteisillä on kuusi jalkaa ja hämähäkeillä kahdeksan.",
            "Muurahaiset elävät yhdyskunnissa, joissa yksilöillä on eri tehtäviä.",
        ), listOf("selkärangaton", "hyönteinen", "hämähäkki", "yhdyskunta")),
        chapter("ravintoketju", "Ravintoketju alkaa kasveista", 32, listOf(
            "Kasvit valmistavat ravintoa auringonvalon avulla.",
            "Kasvinsyöjät syövät kasveja, ja pedot syövät muita eläimiä.",
            "Sienet ja bakteerit hajottavat kuollutta eloperäistä ainesta.",
            "Esimerkkiketju: apila → metsäjänis → ilves. Nuoli näyttää, mihin ravinto siirtyy.",
        ), listOf("ravintoketju", "kasvinsyöjä", "peto", "hajottaja")),
    )
}
