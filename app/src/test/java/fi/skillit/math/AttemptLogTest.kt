package fi.skillit.math

import fi.skillit.engine.AnswerInput
import fi.skillit.engine.AnswerSpec
import fi.skillit.engine.adaptive.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class AttemptLogTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun event() = AttemptEvent.record("math", 1, "i1", "table", JsonObject(emptyMap()),
        AnswerSpec.Numeric(63), AnswerInput.number(62), 1, true, InputMethod.PEN, 0.9, 123)

    @Test fun allFieldsRoundTripAndReplayIsIdempotentAcrossReopen() {
        val dir = temporary.newFolder()
        val e = event()
        assertTrue(AttemptLog(dir).append(e))
        val bytes = File(dir, "attempts.v1.jsonl").readBytes()
        assertFalse(AttemptLog(dir).append(e))
        assertArrayEquals(bytes, File(dir, "attempts.v1.jsonl").readBytes())
        assertEquals(listOf(e), AttemptLog(dir).read())
        assertThrows(IllegalArgumentException::class.java) { AttemptLog(dir).append(e.copy(timestamp = 124)) }
    }
    @Test fun concurrentInstancesCannotDuplicateOrInterleaveRecords() {
        val dir = temporary.newFolder()
        val events = (1..20).map { event() }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll((events + events).map { e -> java.util.concurrent.Callable { AttemptLog(dir).append(e) } })
            assertEquals(20, results.count { it.get() })
            assertEquals(events.toSet(), AttemptLog(dir).read().toSet())
        } finally { executor.shutdownNow() }
    }
    @Test fun crashTailRecoveryKeepsEveryCommittedByte() {
        val dir = temporary.newFolder()
        val log = AttemptLog(dir)
        val first = event()
        log.append(first)
        val file = File(dir, "attempts.v1.jsonl")
        val committed = file.readBytes()
        file.appendText("{\"eventId\":\"incomplete")
        assertEquals(listOf(first), log.read())
        assertArrayEquals(committed, file.readBytes())
        assertTrue(log.append(event()))
        assertEquals(2, log.read().size)
    }
    @Test fun committedCorruptionFailsWithoutReplacingHistory() {
        val dir = temporary.newFolder()
        val file = File(dir, "attempts.v1.jsonl")
        file.writeText("broken\n")
        assertThrows(IOException::class.java) { AttemptLog(dir).append(event()) }
        assertEquals("broken\n", file.readText())
    }
    @Test fun mathLogLeavesLegacyMusicAndNatureSaveFilesByteIdentical() {
        val root = temporary.newFolder()
        val data = File(root, "files/datastore").apply { mkdirs() }
        val fixture = javaClass.getResourceAsStream("/saves/music-v1.json")!!.use { it.readBytes() }
        val music = File(data, "skillit-progress.preferences_pb").apply { writeBytes(fixture) }
        val nature = File(data, "nature-progress.preferences_pb").apply { writeText("legacy-nature-progress-fixture") }
        val before = listOf(music, nature).associateWith { it.readBytes() }
        val dir = File(root, "no_backup/math-attempts")
        val log = AttemptLog(dir)
        log.append(event())
        log.read()
        AttemptLog(dir).append(event())
        before.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }
    @Test fun invalidMetadataAndDeepParamsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { AttemptLog(temporary.newFolder()).append(event().copy(eventId = "bad")) }
        assertThrows(IllegalArgumentException::class.java) { AttemptCodec.encode(event().copy(recognizerConfidence = Double.NaN)) }
        assertThrows(IllegalArgumentException::class.java) { AttemptCodec.encode(event().copy(schema = 2)) }
        assertThrows(IllegalArgumentException::class.java) { AttemptCodec.decode("[".repeat(10000)) }
    }
}
