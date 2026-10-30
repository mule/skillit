package fi.skillit.nature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturePackTest {
    @Test fun everyQuestionHasOneAnswerAndSourcePage() {
        val questions = NaturePack.questions
        assertEquals(27, questions.size)
        assertEquals(questions.size, questions.map { it.id }.distinct().size)
        questions.forEach { question ->
            assertEquals(4, question.choices.distinct().size)
            assertEquals(1, question.choices.count { it == question.answer })
            assertTrue(question.sourcePage in 4..13)
        }
    }

    @Test fun everySpeciesQuestionUsesItsOwnIllustrationAndPage() {
        NaturePack.species.forEach { species ->
            val question = NaturePack.questions.single { it.id == "species-${species.id}" }
            assertEquals(species.name, question.answer)
            assertEquals(species.illustration, question.illustration)
            assertEquals(species.sourcePage, question.sourcePage)
        }
    }
}
