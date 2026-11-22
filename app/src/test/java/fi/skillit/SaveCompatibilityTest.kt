package fi.skillit

import fi.skillit.engine.Action
import fi.skillit.engine.ExerciseSession
import fi.skillit.engine.Feedback
import fi.skillit.engine.SessionEvent
import fi.skillit.music.Adventure
import fi.skillit.music.IslandState
import fi.skillit.music.MusicPack
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

/**
 * Saves written by the M1/M2 app (schema 1 and 2, captured before the AnswerSpec change) must replay
 * unchanged. The expected numbers were recorded from the pre-change engine.
 */
class SaveCompatibilityTest {
    private fun load(name: String): JsonObject =
        Json.parseToJsonElement(javaClass.getResource("/saves/$name")!!.readText()).jsonObject
    private fun events(array: JsonArray) = array.map { element ->
        val e = element.jsonObject
        SessionEvent(Action.valueOf(e.getValue("action").jsonPrimitive.content), e.getValue("token").jsonPrimitive.content,
            e.getValue("choice").jsonPrimitive.content)
    }

    @Test fun schemaOneHistoryReplaysToTheSameStateAndEvents() {
        val save = load("music-v1.json")
        assertEquals(1, save.getValue("schema").jsonPrimitive.int)
        assertEquals(MusicPack.id, save.getValue("pack").jsonPrimitive.content)
        val history = events(save.getValue("events").jsonArray)
        val session = ExerciseSession.restore(MusicPack.questions, history)
        assertEquals(history, session.events)
        assertEquals(20, history.size)
        assertEquals(75, session.xp)
        assertEquals("m8:0", session.current!!.token)
        assertEquals(Feedback(correct = true, xp = 10, choiceId = "o0", check = session.feedback!!.check), session.feedback)
        assertEquals(8, session.resolvedCount)
        assertEquals(1, session.retryCount)
        assertTrue(session.next("m8:0"))
    }

    @Test fun schemaTwoAdventureAndPracticeRunsReplay() {
        val save = load("music-v2.json")
        assertEquals(2, save.getValue("schema").jsonPrimitive.int)
        val history = events(save.getValue("events").jsonArray)
        val adventure = ExerciseSession.restore(MusicPack.questions, history)
        assertEquals(history, adventure.events)
        assertEquals(56, history.size)
        assertTrue(adventure.complete)
        assertEquals(180, adventure.xp)
        assertEquals(4, adventure.retryCount)
        assertTrue(Adventure.islands(adventure).all { it == IslandState.COMPLETED })
        val practice = save.getValue("practice").jsonArray.associate {
            val run = it.jsonObject
            run.getValue("level").jsonPrimitive.int to events(run.getValue("events").jsonArray)
        }
        val p0 = ExerciseSession.restore(Adventure.questions(0), practice.getValue(0))
        assertTrue(p0.complete)
        assertEquals(40, p0.xp)
        val p2 = ExerciseSession.restore(Adventure.questions(2), practice.getValue(2))
        assertEquals(30, p2.xp)
        assertEquals("m14:0", p2.current!!.token)
        assertEquals("o2", p2.feedback!!.choiceId)
        assertTrue(p2.feedback!!.correct)
        assertEquals(practice.getValue(2), p2.events)
    }
}
