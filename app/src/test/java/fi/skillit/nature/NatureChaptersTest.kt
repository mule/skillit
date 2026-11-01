package fi.skillit.nature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NatureChaptersTest {
    /** Trip order and answers, checked by hand against photos of printed pages 4–13. */
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

    @Test fun tripKeepsItsOrderAndReviewedAnswers() {
        assertEquals(reviewedTripKey, NaturePack.questions.map { it.id to it.answer })
        assertEquals(NaturePack.questions, NaturePack.trip.questions)
    }

    @Test fun onlyPagesReadFromTheBookAreOpenToTheChild() {
        assertEquals(
            listOf("Suomessa on monenlaisia elinympäristöjä" to "4–5", "Eliökunnan luokittelu" to "6–7",
                "Niityt, pihat ja pientareet ovat valoisia kasvupaikkoja" to "8–9",
                "Niityn ja pientareen kasveja" to "10–11", "Niityn ja pientareen eläimiä" to "12–13"),
            NaturePack.chapters.map { it.title to it.pages },
        )
        assertEquals(reviewedTripKey.map { it.first }.toSet(), NaturePack.chapters.flatMap { it.questions }.map { it.id }.toSet())
        assertEquals(20, NaturePack.chapters.flatMap { it.species }.size)
    }

    @Test fun chaptersGroupQuestionsByTheirSpread() {
        assertEquals(listOf(3, 2, 2, 12, 8), NaturePack.chapters.map { it.questions.size })
        assertEquals(listOf("kingdom", "backbone"), NaturePack.chapters[1].questions.map { it.id })
    }

    @Test fun stagedStructureCoversAllFifteenSpreads() {
        assertEquals(
            listOf(4 to 5, 6 to 7, 8 to 9, 10 to 11, 12 to 13, 14 to 15, 16 to 17, 18 to 19, 20 to 21,
                22 to 23, 24 to 25, 26 to 27, 28 to 29, 30 to 31, 32 to 33),
            NaturePack.stagedChapters.map { it.firstPage to it.lastPage },
        )
        assertEquals(15, NaturePack.stagedChapters.map { it.title }.distinct().size)
        NaturePack.stagedChapters.forEach { assertTrue(it.title, it.summary.isNotEmpty()) }
    }

    @Test fun everyStagedQuestionIsWellFormedSoItCanBeEnabled() {
        val all = NaturePack.stagedChapters.flatMap { it.questions }
        assertEquals(all.size, all.map { it.id }.distinct().size)
        all.forEach { question ->
            assertEquals(question.id, 4, question.choices.distinct().size)
            assertEquals(question.id, 1, question.choices.count { it == question.answer })
            assertTrue(question.id, question.explanation.isNotBlank())
        }
        val staged = all.filter { it.sourcePage >= 14 }
        assertEquals(4, staged.map { it.choices.indexOf(it.answer) }.distinct().size)
    }

    @Test fun approvingContentEnablesExactlyThatContent() {
        val active = activeChapters(NaturePack.stagedChapters, setOf("puut", "ravintoketju"), setOf("species-kuusi", "herbivore"))
        assertEquals(listOf("Puita", "Ravintoketju alkaa kasveista"), active.map { it.title })
        assertEquals(listOf("species-kuusi"), active[0].questions.map { it.id })
        assertEquals(listOf("kuusi"), active[0].species.map { it.name })
        assertEquals(listOf("herbivore"), active[1].questions.map { it.id })
    }
}
