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
            JSONObject(base).put("schema", 2),
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
}
