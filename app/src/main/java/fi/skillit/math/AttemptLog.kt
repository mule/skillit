package fi.skillit.math

import android.content.Context
import fi.skillit.engine.adaptive.AttemptCodec
import fi.skillit.engine.adaptive.AttemptEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Local, logically append-only JSONL journal. Call on an IO dispatcher, never the UI thread.
 * A newline commits a record. A crash may leave an uncommitted tail; the next open removes only
 * that tail. Committed malformed records are quarantined intact before a fresh journal is opened.
 */
class AttemptLog internal constructor(private val directory: File) {
    fun read(): List<AttemptEvent> = locked { _, events -> events }

    /** False for an identical replay; a reused UUID with different contents is rejected. */
    fun append(event: AttemptEvent): Boolean {
        val encoded = AttemptCodec.encode(event).toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_LINE_BYTES)
        return locked { journal, events ->
            val existing = events.find { it.eventId == event.eventId }
            if (existing != null) {
                require(existing == event) { "Event UUID already belongs to a different attempt" }
                false
            } else {
                journal.seek(journal.length())
                journal.write(encoded)
                journal.write('\n'.code)
                journal.fd.sync()
                true
            }
        }
    }

    private fun <T> locked(block: (RandomAccessFile, List<AttemptEvent>) -> T): T = synchronized(monitor) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create attempt log directory")
        // The process monitor also prevents OverlappingFileLockException across separate store instances.
        RandomAccessFile(File(directory, "attempts.v1.lock"), "rw").use { lock ->
            lock.channel.lock().use lockedFile@ {
                val file = File(directory, "attempts.v1.jsonl")
                val journal = RandomAccessFile(file, "rw")
                val events = try { readCommitted(journal) } catch (_: CorruptJournal) {
                    journal.close()
                    // Keep all bytes, including good records and any tail; never overwrite a quarantine.
                    var timestamp = System.currentTimeMillis()
                    var quarantine = File(directory, "attempts.v1.corrupt-$timestamp.jsonl")
                    while (quarantine.exists()) {
                        timestamp++
                        quarantine = File(directory, "attempts.v1.corrupt-$timestamp.jsonl")
                    }
                    if (!file.renameTo(quarantine)) throw IOException("Cannot quarantine corrupt attempt journal")
                    return@lockedFile RandomAccessFile(file, "rw").use { fresh -> block(fresh, emptyList()) }
                } catch (e: Throwable) {
                    journal.close()
                    throw e
                }
                journal.use { block(it, events) }
            }
        }
    }

    private fun readCommitted(journal: RandomAccessFile): List<AttemptEvent> {
        journal.seek(0)
        val events = linkedMapOf<String, AttemptEvent>()
        val line = ByteArrayOutputStream()
        var committedEnd = 0L
        val buffer = ByteArray(8192)
        var position = 0L
        while (true) {
            val size = journal.read(buffer)
            if (size < 0) break
            for (i in 0 until size) {
                position++
                val byte = buffer[i].toInt() and 0xff
                if (byte == '\n'.code) {
                    val event = try { AttemptCodec.decode(line.toString("UTF-8")) }
                    catch (e: IllegalArgumentException) { throw CorruptJournal("Invalid committed attempt record", e) }
                    val previous = events.putIfAbsent(event.eventId, event)
                    if (previous != null) throw CorruptJournal("Duplicate UUID in committed attempt log")
                    line.reset()
                    committedEnd = position
                } else {
                    if (line.size() >= MAX_LINE_BYTES) throw CorruptJournal("Attempt record exceeds size limit")
                    line.write(byte)
                }
            }
        }
        if (journal.length() != committedEnd) {
            journal.setLength(committedEnd)
            journal.fd.sync()
        }
        return events.values.toList()
    }

    private class CorruptJournal(message: String, cause: Throwable? = null) : IOException(message, cause)

    companion object {
        private val monitor = Any()
        private const val MAX_LINE_BYTES = 65536
        /** noBackupFilesDir is excluded from both cloud backup and device-to-device restore. */
        fun open(context: Context): AttemptLog = AttemptLog(File(context.noBackupFilesDir, "math-attempts"))
    }
}
