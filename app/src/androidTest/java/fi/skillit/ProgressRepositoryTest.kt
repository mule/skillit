package fi.skillit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONArray
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.engine.ExerciseSession
import fi.skillit.music.MusicPack
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProgressRepositoryTest {
    @Test fun reopeningDataStoreRestoresHintFeedbackRetryXpAndMuteAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "restore-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var repository = ProgressRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        val session = ExerciseSession(MusicPack.questions)
        session.showHint("m1:0")
        session.answer("m1:0", MusicPack.questions.first().choices.first { it.id != MusicPack.questions.first().correctId }.id)
        val saved = SavedProgress(started = true, muted = true, events = session.events)
        repository.save(saved)
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = ProgressRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(saved, repository.load())
        val restored = ExerciseSession.restore(MusicPack.questions, repository.load().events)
        assertTrue(restored.hintVisible)
        assertFalse(restored.feedback!!.correct)
        assertEquals(0, restored.xp)
        restored.next("m1:0")
        repeat(4) {
            val attempt = restored.current!!
            restored.answer(attempt.token, attempt.question.correctId)
            restored.next(attempt.token)
        }
        assertEquals("m1:1", restored.current!!.token)
        restored.answer("m1:1", restored.current!!.question.correctId)
        repository.save(saved.copy(events = restored.events))
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = ProgressRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(45, ExerciseSession.restore(MusicPack.questions, repository.load().events).xp)
        repository.reset(muted = true)
        assertEquals(SavedProgress(muted = true), repository.load())
        scope.coroutineContext.job.cancelAndJoin()
        file.delete()
        Unit
    }
    @Test fun corruptDataStoreBytesCanBeExplicitlyReset() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "corrupt-${System.nanoTime()}.preferences_pb")
        file.writeBytes(byteArrayOf(0x0f, 0x7f, 0x7f))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = ProgressRepository(PreferenceDataStoreFactory.create(
            scope = scope, corruptionHandler = ProgressRepository.corruptionHandler,
        ) { file })
        try {
            repository.load()
            fail("Corruption must be surfaced to the child before reset")
        } catch (_: org.json.JSONException) { /* UI presents recovery. */ }
        repository.reset(muted = false)
        assertEquals(SavedProgress(), repository.load())
        scope.coroutineContext.job.cancelAndJoin()
        file.delete()
        Unit
    }
    @Test fun staleVersionsAndRemovedQuestionsRequireRecoveryWithoutOverwritingTheSave() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "stale-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repository = ProgressRepository(store)
        val base = """{"schema":1,"pack":"music-grade4-fi","version":1,"started":true,"muted":true,"events":[]}"""
        val incompatible = listOf(
            JSONObject(base).put("schema", 0),
            JSONObject(base).put("schema", 3),
            JSONObject(base).put("pack", "removed-pack"),
            JSONObject(base).put("version", 0),
            JSONObject(base).put("version", 2),
            JSONObject(base).put("events", JSONArray("""[{"action":"ANSWER","token":"removed:0","choice":"o0"}]""")),
        )
        try {
            incompatible.forEach { json ->
                val raw = json.toString()
                store.edit { it[ProgressRepository.key] = raw }
                val failure = runCatching { repository.load() }.exceptionOrNull()
                assertTrue("Incompatible save must request explicit recovery: $json", failure is IllegalArgumentException)
                assertEquals(raw, store.data.first()[ProgressRepository.key])
            }
            // A forged aggregate cannot award XP: accepted history is the only source.
            store.edit { it[ProgressRepository.key] = JSONObject(base).put("xp", 999).toString() }
            val resumed = repository.load()
            assertTrue(resumed.muted)
            assertEquals(0, ExerciseSession.restore(MusicPack.questions, resumed.events).xp)
            repository.reset(muted = true)
            assertEquals(SavedProgress(muted = true), repository.load())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            file.delete()
        }
        Unit
    }
    @Test fun installedV1SaveMigratesWithoutChangingFeedbackQueueChoicesMuteOrXp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "migrate-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val raw = """{"schema":1,"pack":"music-grade4-fi","version":1,"started":true,"muted":true,"events":[{"action":"HINT","token":"m1:0","choice":""},{"action":"ANSWER","token":"m1:0","choice":"o0"}]}"""
        try {
            store.edit { it[ProgressRepository.key] = raw }
            var repo = ProgressRepository(store)
            val migrated = repo.load()
            val before = ExerciseSession.restore(MusicPack.questions, migrated.events)
            assertEquals("m1:0", before.current!!.token)
            assertEquals("o0", before.feedback!!.choiceId)
            assertTrue(before.hintVisible)
            assertTrue(migrated.muted)
            assertEquals(.6f, migrated.volume)
            assertEquals(2, JSONObject(store.data.first()[ProgressRepository.key]!!).getInt("schema"))
            val completed = ExerciseSession(MusicPack.questions)
            while (!completed.complete) {
                val a = completed.current!!; completed.answer(a.token, a.question.correctId); completed.next(a.token)
            }
            // A genuine v1-format completed event log unlocks all islands on first M2 load.
            val old = JSONObject(raw).put("events", JSONArray().apply { completed.events.forEach { e ->
                put(JSONObject().put("action", e.action.name).put("token", e.token).put("choice", e.choiceId))
            } })
            store.edit { it[ProgressRepository.key] = old.toString() }
            val saved = repo.load()
            assertTrue(fi.skillit.music.Adventure.islands(ExerciseSession.restore(MusicPack.questions, saved.events)).all { it == fi.skillit.music.IslandState.COMPLETED })
            val replay = ExerciseSession(fi.skillit.music.Adventure.questions(2))
            val a = replay.current!!; replay.answer(a.token, a.question.correctId)
            val withPractice = saved.copy(volume = .3f, practice = mapOf(2 to replay.events))
            repo.save(withPractice)
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = scope) { file }
            repo = ProgressRepository(store)
            assertEquals(withPractice, repo.load())
            assertEquals(200, ExerciseSession.restore(MusicPack.questions, repo.load().events).xp)
            assertEquals(10, ExerciseSession.restore(fi.skillit.music.Adventure.questions(2), repo.load().practice.getValue(2)).xp)
        } finally { scope.coroutineContext.job.cancelAndJoin(); file.delete() }
        Unit
    }
    @Test fun literalM1RetryFeedbackKeepsQueueXpAndChoiceIdentityDuringUpgrade() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "migrate-retry-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repo = ProgressRepository(store)
        // Literal installed M1 encoding: four resolved answers, followed by another miss on m1.
        val raw = """{"schema":1,"pack":"music-grade4-fi","version":1,"started":true,"muted":true,"events":[
          {"action":"ANSWER","token":"m1:0","choice":"o0"},{"action":"NEXT","token":"m1:0","choice":""},
          {"action":"ANSWER","token":"m2:0","choice":"o2"},{"action":"NEXT","token":"m2:0","choice":""},
          {"action":"ANSWER","token":"m3:0","choice":"o1"},{"action":"NEXT","token":"m3:0","choice":""},
          {"action":"ANSWER","token":"m4:0","choice":"o0"},{"action":"NEXT","token":"m4:0","choice":""},
          {"action":"ANSWER","token":"m5:0","choice":"o3"},{"action":"NEXT","token":"m5:0","choice":""},
          {"action":"HINT","token":"m1:1","choice":""},{"action":"ANSWER","token":"m1:1","choice":"o0"}]}"""
        try {
            store.edit { it[ProgressRepository.key] = raw }
            val saved = repo.load()
            val resumed = ExerciseSession.restore(MusicPack.questions, saved.events)
            assertTrue(saved.muted)
            assertEquals("m1:1", resumed.current!!.token)
            assertEquals(40, resumed.xp)
            assertEquals(4, resumed.resolvedCount)
            assertTrue(resumed.hintVisible)
            assertTrue(resumed.retryIsNext)
            assertFalse(resumed.feedback!!.correct)
            assertEquals("o0", resumed.feedback!!.choiceId)
            assertEquals("puolinuotti", resumed.current!!.question.choices.single { it.id == "o0" }.label)
            assertEquals(fi.skillit.music.IslandState.LOCKED, fi.skillit.music.Adventure.islands(resumed)[1])
            // A second v1 fixture ends on accepted retry feedback, before NEXT to island 2.
            val accepted = JSONObject(raw)
            accepted.getJSONArray("events")
                .put(JSONObject("""{"action":"NEXT","token":"m1:1","choice":""}"""))
                .put(JSONObject("""{"action":"ANSWER","token":"m1:2","choice":"o3"}"""))
            store.edit { it[ProgressRepository.key] = accepted.toString() }
            val finalFeedback = ExerciseSession.restore(MusicPack.questions, repo.load().events)
            assertEquals(45, finalFeedback.xp)
            assertEquals("m1:2", finalFeedback.current!!.token)
            assertEquals(5, finalFeedback.feedback!!.xp)
            assertEquals(fi.skillit.music.IslandState.CURRENT, fi.skillit.music.Adventure.islands(finalFeedback)[1])
            assertTrue(finalFeedback.next("m1:2"))
            assertEquals("m6:0", finalFeedback.current!!.token)
        } finally { scope.coroutineContext.job.cancelAndJoin(); file.delete() }
        Unit
    }
    @Test fun longRepeatedReviewSurvivesDataStoreReopenAndEventuallyCompletesForFiveXp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "many-retries-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store = PreferenceDataStoreFactory.create(scope = scope) { file }
        var repository = ProgressRepository(store)
        try {
            val session = ExerciseSession(MusicPack.questions)
            repeat(1000) {
                val attempt = session.current!!
                session.showHint(attempt.token)
                session.answer(attempt.token, attempt.question.choices.first { it.id != attempt.question.correctId }.id)
                session.next(attempt.token)
            }
            assertEquals("m1:200", session.current!!.token)
            assertFalse(session.complete)
            assertEquals(0, session.resolvedCount)
            repository.save(SavedProgress(started = true, muted = true, events = session.events))
            assertTrue(store.data.first()[ProgressRepository.key]!!.length > 100_000)
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = scope) { file }
            repository = ProgressRepository(store)
            val saved = repository.load()
            val restored = ExerciseSession.restore(MusicPack.questions, saved.events)
            assertTrue(saved.muted)
            assertEquals("m1:200", restored.current!!.token)
            assertEquals(0, restored.xp)
            assertFalse(restored.answer("m1:199", restored.current!!.question.correctId))
            while (!restored.complete) {
                val attempt = restored.current!!
                restored.answer(attempt.token, attempt.question.correctId)
                assertEquals(if (attempt.retry) 5 else 10, restored.feedback!!.xp)
                restored.next(attempt.token)
            }
            assertEquals(175, restored.xp)
            assertEquals(5, restored.retryCount)
            repository.save(saved.copy(events = restored.events))
            assertTrue(ExerciseSession.restore(MusicPack.questions, repository.load().events).complete)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            file.delete()
        }
        Unit
    }
}
