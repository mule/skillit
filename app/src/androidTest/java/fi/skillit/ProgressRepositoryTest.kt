package fi.skillit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
}
