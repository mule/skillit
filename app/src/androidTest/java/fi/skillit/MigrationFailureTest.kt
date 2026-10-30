package fi.skillit

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import fi.skillit.engine.Action
import fi.skillit.engine.SessionEvent
import fi.skillit.music.MusicPack
import fi.skillit.music.notationDescription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException

@OptIn(ExperimentalTestApi::class)
class MigrationFailureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val raw = """{"schema":1,"pack":"music-grade4-fi","version":1,"started":true,"muted":true,"events":[{"action":"HINT","token":"m1:0","choice":""},{"action":"ANSWER","token":"m1:0","choice":"o0"}]}"""
    private val expected = SavedProgress(started = true, muted = true, events = listOf(
        SessionEvent(Action.HINT, "m1:0", ""), SessionEvent(Action.ANSWER, "m1:0", "o0"),
    ))

    private class Fixture(context: android.content.Context) {
        val file = File(context.cacheDir, "migration-failure-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var backing = PreferenceDataStoreFactory.create(scope = scope) { file }
        suspend fun reopen() {
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            backing = PreferenceDataStoreFactory.create(scope = scope) { file }
        }
        suspend fun close() { scope.coroutineContext.job.cancelAndJoin(); file.delete() }
    }

    @Test fun migrationWriteIOExceptionKeepsInstalledHistoryPlayableAndNextSaveUpgrades() = runBlocking {
        val fixture = Fixture(compose.activity.applicationContext)
        fixture.backing.edit { it[ProgressRepository.key] = raw }
        val failing = object : DataStore<Preferences> {
            override val data = fixture.backing.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                throw IOException("Controlled migration write failure")
            }
        }
        try {
            val repository = ProgressRepository(failing)
            assertEquals(expected, repository.load())
            assertEquals(.6f, repository.load().volume)
            assertEquals(raw, fixture.backing.data.first()[ProgressRepository.key])
            // Dispose the activity's original app before asserting against the injected store.
            compose.runOnUiThread { compose.activity.setContent { Text("Migraatiotestin valmistelu") } }
            compose.onNodeWithText("Migraatiotestin valmistelu").assertIsDisplayed()
            compose.runOnUiThread { compose.activity.setContent { SkillitTheme { SkillitApp(repository) } } }
            compose.waitUntilAtLeastOneExists(hasText(MusicPack.questions.first().prompt), 10_000)
            compose.onNodeWithText(MusicPack.questions.first().prompt).assertIsDisplayed()
            compose.onNodeWithContentDescription(notationDescription(MusicPack.item("m1").task)).assertIsDisplayed()
            compose.onNodeWithText("Tallennusta ei voitu lukea. Voit yrittää uudelleen tai aloittaa alusta.").assertDoesNotExist()
            compose.onNodeWithText("Valitsit tämän", substring = true).assertExists()
            // Synchronize the rendered app surface before taking the native screenshot.
            compose.onRoot().captureToImage()
            val screenshot = File(compose.activity.getExternalFilesDir(null), "migration-write-failure.png")
            screenshot.outputStream().use {
                checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertEquals(raw, fixture.backing.data.first()[ProgressRepository.key])
            compose.runOnUiThread { compose.activity.setContent { Text("Testi valmis") } }
            // Ordinary writes already encode schema 2, even if migration could not write.
            val next = expected.copy(volume = .3f)
            ProgressRepository(fixture.backing).save(next)
            assertEquals(2, JSONObject(fixture.backing.data.first()[ProgressRepository.key]!!).getInt("schema"))
            fixture.reopen()
            assertEquals(next, ProgressRepository(fixture.backing).load())
        } finally {
            compose.runOnUiThread { compose.activity.setContent { Text("Testi valmis") } }
            fixture.close()
        }
    }

    @Test fun migrationConcurrentChangeStillFailsAndPreservesNewerRaw() = runBlocking {
        val fixture = Fixture(compose.activity.applicationContext)
        val newerRaw = raw.replace("\"muted\":true", "\"muted\":false")
        try {
            fixture.backing.edit { it[ProgressRepository.key] = raw }
            val racing = object : DataStore<Preferences> {
                override val data = fixture.backing.data
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    fixture.backing.edit { it[ProgressRepository.key] = newerRaw }
                    return fixture.backing.updateData(transform)
                }
            }
            try {
                ProgressRepository(racing).load()
                fail("A concurrently changed history must not return stale progress")
            } catch (e: IllegalStateException) {
                assertEquals("Progress changed during migration", e.message)
            }
            assertEquals(newerRaw, fixture.backing.data.first()[ProgressRepository.key])
        } finally { fixture.close() }
    }

    @Test fun migrationCancellationPropagatesWithoutChangingInstalledRaw() = runBlocking {
        val fixture = Fixture(compose.activity.applicationContext)
        val cancelled = CancellationException("Controlled migration cancellation")
        try {
            fixture.backing.edit { it[ProgressRepository.key] = raw }
            val cancelling = object : DataStore<Preferences> {
                override val data = fixture.backing.data
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw cancelled
            }
            try {
                ProgressRepository(cancelling).load()
                fail("Migration cancellation must propagate")
            } catch (e: CancellationException) { assertSame(cancelled, e) }
            assertEquals(raw, fixture.backing.data.first()[ProgressRepository.key])
        } finally { fixture.close() }
    }

    @Test fun readIOExceptionStillPropagatesWithoutAttemptingMigration() = runBlocking {
        val failure = IOException("Controlled read failure")
        val unreadable = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw failure }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                error("No migration may run after a failed read")
            }
        }
        try {
            ProgressRepository(unreadable).load()
            fail("Read errors must still require recovery")
        } catch (e: IOException) { assertSame(failure, e) }
    }
}
