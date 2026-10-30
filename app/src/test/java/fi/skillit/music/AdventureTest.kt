package fi.skillit.music

import fi.skillit.engine.ExerciseSession
import org.junit.Assert.*
import org.junit.Test

class AdventureTest {
    @Test fun onlyCorrectlyResolvedAdventureQuestionsUnlockTheNextIsland() {
        val session = ExerciseSession(MusicPack.questions)
        assertEquals(listOf(IslandState.CURRENT, IslandState.LOCKED, IslandState.LOCKED, IslandState.LOCKED), Adventure.islands(session))
        repeat(5) {
            val a = session.current!!
            session.answer(a.token, a.question.choices.first { it.id != a.question.correctId }.id)
            session.next(a.token)
        }
        assertEquals(IslandState.LOCKED, Adventure.islands(session)[1])
        repeat(5) {
            val a = session.current!!
            session.answer(a.token, a.question.correctId)
            session.next(a.token)
        }
        assertEquals(listOf(IslandState.COMPLETED, IslandState.CURRENT, IslandState.LOCKED, IslandState.LOCKED), Adventure.islands(session))
        val original = session.events
        val replay = ExerciseSession(Adventure.questions(0))
        while (!replay.complete) {
            val a = replay.current!!; replay.answer(a.token, a.question.correctId); replay.next(a.token)
        }
        assertEquals(50, replay.xp)
        assertEquals(25, session.xp)
        assertEquals(original, session.events)
    }
}
