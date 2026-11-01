package fi.skillit.nature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NatureChaptersTest {
    /** Trip order and answers, read from the book pages 4–13. */
    private val reviewedTripKey = listOf(
        "species-paivankakkara" to "päivänkakkara", "species-pietaryrtti" to "pietaryrtti", "species-koiranputki" to "koiranputki",
        "habitat" to "metsässä",
        "species-vuohenputki" to "vuohenputki", "species-siankarsamo" to "siankärsämö", "species-pujo" to "pujo",
        "reindeer" to "tunturin",
        "species-pihatatar" to "pihatatar", "species-pihasaunio" to "pihasaunio", "species-kissankello" to "kissankello",
        "human" to "rakennetussa ympäristössä",
        "species-harakankello" to "harakankello", "species-niittyleinikki" to "niittyleinikki", "species-maitohorsma" to "maitohorsma",
        "kingdom" to "sieniin",
        "species-heinasirkka" to "heinäsirkka", "species-hepokatti" to "hepokatti", "species-kastemato" to "kastemato",
        "backbone" to "sammakkoeläimiin",
        "species-leppakerttu" to "leppäkerttu", "species-sitruunaperhonen" to "sitruunaperhonen", "species-amiraaliperhonen" to "amiraaliperhonen",
        "meadow" to "valoa",
        "species-kimalainen" to "kimalainen", "species-kontiainen" to "kontiainen",
        "pollination" to "siitepölyä",
    )

    /** Independent key from the source audit of the scanned book; do not regenerate from the pack. */
    private val reviewedPages14To33Key = listOf(
        "peat" to "kuolleista kasvien osista",
        "bog-plant" to "rahkasammal",
        "tree-parts" to "juuret, varsi ja lehdet",
        "pine-needles" to "pareittain",
        "tree-needs" to "valoa, vettä ja ravinteita",
        "species-kuusi" to "kuusi",
        "species-manty" to "mänty",
        "species-kataja" to "kataja",
        "species-pihlaja" to "pihlaja",
        "species-rauduskoivu" to "rauduskoivu",
        "species-hieskoivu" to "hieskoivu",
        "species-haapa" to "haapa",
        "species-harmaaleppa" to "harmaaleppä",
        "forest-renewal" to "Jotta kaadettujen puiden tilalle kasvaisi uusia puita.",
        "forest-thinning" to "Jäljelle jääville puille jää enemmän valoa, vettä ja ravinteita.",
        "deadwood" to "Ne tarjoavat elinpaikkoja ja pesäkoloja muille eliöille.",
        "mammal" to "Nisäkkään emo ruokkii poikasia maidolla.",
        "territory" to "Eläimen tai eläinryhmän omaa aluetta.",
        "hoof" to "Jalan kärki, jossa on kaksi isoon kynteen päättyvää varvasta.",
        "mammal-footprints" to "Metsäjäniksellä on tassunjäljet, hirvellä sorkanjäljet.",
        "species-metsamyyra" to "metsämyyrä",
        "species-metsajanis" to "metsäjänis",
        "species-hirvi" to "hirvi",
        "species-valkohantakauris" to "valkohäntäkauris",
        "species-mayra" to "mäyrä",
        "species-kettu" to "kettu",
        "species-ilves" to "ilves",
        "species-susi" to "susi",
        "courtship" to "Koiras houkuttelee naarasta lisääntymään esimerkiksi ääntelemällä.",
        "woodpecker" to "Se tekee pesäkoloja ja etsii hyönteisten toukkia puusta.",
        "winter-flock" to "Parvessa ne huomaavat saalistajat helpommin.",
        "species-metso" to "metso",
        "species-teeri" to "teeri",
        "species-kapytikka" to "käpytikka",
        "species-huuhkaja" to "huuhkaja",
        "species-peippo" to "peippo",
        "species-rakattirastas" to "räkättirastas",
        "species-homotiainen" to "hömötiainen",
        "species-hippiainen" to "hippiäinen",
        "ant-roles" to "Kuningattaret, työläiset ja koiraat.",
        "spider-not-insect" to "Hämähäkillä on kahdeksan jalkaa; hyönteisellä kuusi.",
        "food-chain-order" to "apila → metsäjänis → ilves",
        "food-start" to "Kasveista, jotka valmistavat ravintoa auringonvalon avulla.",
        "arrows" to "Mihin suuntaan ravinto siirtyy eliöltä toiselle.",
        "herbivore" to "Kasveja tai niiden osia.",
        "decomposers" to "Hajottaa kuollutta eloperäistä ainesta; esimerkkejä ovat sienet ja bakteerit.",
    )

    private val openQuestions get() = NaturePack.chapters.flatMap { it.questions }

    @Test fun tripKeepsItsOrderAndReviewedAnswers() {
        assertEquals(reviewedTripKey, NaturePack.questions.map { it.id to it.answer })
        assertEquals(NaturePack.questions, NaturePack.trip.questions)
    }

    @Test fun allFifteenBookSpreadsAreOpenInOrder() {
        assertEquals(
            listOf(
                "Suomessa on monenlaisia elinympäristöjä" to "4–5", "Eliökunnan luokittelu" to "6–7",
                "Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja" to "8–9",
                "Niityn ja pientareen kasveja" to "10–11", "Niityn ja pientareen eläimiä" to "12–13",
                "Suomi on metsäinen ja soinen maa" to "14–15", "Puut kasvavat korkeiksi" to "16–17", "Puita" to "18–19",
                "Metsissä kasvatetaan puita" to "20–21", "Metsän nisäkkäitä" to "22–23",
                "Metsän nisäkkäitä: lajikortit" to "24–25", "Metsän lintuja" to "26–27",
                "Metsän lintuja: lajikortit" to "28–29", "Hyönteiset ja hämähäkit ovat selkärangattomia" to "30–31",
                "Ravintoketju alkaa kasveista" to "32–33",
            ),
            NaturePack.chapters.map { it.title to it.pages },
        )
    }

    @Test fun chaptersHoldTheReviewedQuestionCounts() {
        assertEquals(listOf(3, 2, 2, 12, 8, 2, 3, 8, 3, 3, 9, 3, 8, 2, 5), NaturePack.chapters.map { it.questions.size })
        assertEquals(73, openQuestions.size)
        assertEquals(listOf("kingdom", "backbone"), NaturePack.chapters[1].questions.map { it.id })
    }

    @Test fun everyOpenQuestionMatchesTheReviewedKey() {
        assertEquals(reviewedPages14To33Key, NaturePack.chapters.drop(5).flatMap { it.questions }.map { it.id to it.answer })
        assertEquals((reviewedTripKey + reviewedPages14To33Key).toMap(), openQuestions.associate { it.id to it.answer })
    }

    @Test fun treePartsTeachesTheBooksAnswerNotTheEarlierOne() {
        val question = openQuestions.single { it.id == "tree-parts" }
        assertEquals("juuret, varsi ja lehdet", question.answer)
        assertTrue("juuret, runko ja latvus" !in question.choices)
        assertEquals(17, question.sourcePage)
    }

    @Test fun readingUsesTheBooksTermsAndNotTheEarlierMisreadings() {
        val bog = NaturePack.chapters.single { it.id == "suot" }
        assertEquals(listOf("metsä", "havupuu", "lehtipuu", "suo", "sammal", "rahkasammal", "turve"), bog.terms)
        val tree = NaturePack.chapters.single { it.id == "puun-rakenne" }
        assertEquals(listOf("juuret", "varsi", "lehdet", "ravinteet", "runko", "tyvi", "latva", "kuori", "neulaset", "kanto", "vuosirenkaat"), tree.terms)
        val allText = NaturePack.chapters.flatMap { it.summary + it.terms }.joinToString(" ")
        listOf("räme", "korpi", "avosuo", "suomarja", "latvus").forEach { assertFalse(it, allText.contains(it)) }
    }

    @Test fun everySpeciesIsShownWithItsOwnPictureAndHint() {
        val species = NaturePack.chapters.flatMap { it.species }
        assertEquals(44, species.size)
        assertEquals(44, species.map { it.illustration }.distinct().size)
        species.forEach { item ->
            val question = openQuestions.single { it.id == "species-${item.id}" }
            assertNotNull(item.id, item.illustration)
            assertEquals(item.id, item.illustration, question.illustration)
            assertEquals(item.id, "Mikä laji kuvassa on?", question.prompt)
            assertEquals(item.id, item.clue, question.hint)
            assertFalse(item.id, item.clue.contains(item.name, ignoreCase = true))
        }
    }

    @Test fun everyOpenQuestionIsWellFormedAndOnItsSpread() {
        assertEquals(openQuestions.size, openQuestions.map { it.id }.distinct().size)
        NaturePack.chapters.forEach { chapter ->
            assertTrue(chapter.title, chapter.summary.isNotEmpty())
            chapter.questions.forEach { question ->
                assertTrue(question.id, question.sourcePage in chapter.firstPage..chapter.lastPage)
                assertEquals(question.id, 4, question.choices.distinct().size)
                assertEquals(question.id, 1, question.choices.count { it == question.answer })
                assertTrue(question.id, question.explanation.isNotBlank())
            }
        }
        assertEquals(4, NaturePack.chapters.drop(5).flatMap { it.questions }.map { it.choices.indexOf(it.answer) }.distinct().size)
    }

    @Test fun unapprovedContentStaysHidden() {
        val approved = NatureQuestion("approved", "?", listOf("a", "b", "c", "d"), "a", "e", 40)
        val unapproved = approved.copy(id = "unapproved")
        val species = NatureSpecies("uusi", "uusi", "puu", null, "vihje", "tieto", 40)
        val chapter = NatureChapter("uusi-luku", "Uusi", 40, 41, listOf("s"), emptyList(), listOf(species), listOf(approved, unapproved))
        assertEquals(emptyList<NatureChapter>(), activeChapters(listOf(chapter), emptySet(), setOf("approved", "species-uusi")))
        val open = activeChapters(listOf(chapter), setOf("uusi-luku"), setOf("approved")).single()
        assertEquals(listOf("approved"), open.questions.map { it.id })
        assertEquals(emptyList<NatureSpecies>(), open.species)
    }
}
