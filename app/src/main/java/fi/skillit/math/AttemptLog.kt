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
 * that tail. Committed malformed records fail closed and are never silently dropped/replaced.
 */
class AttemptLog internal constructor(private val directory: File) {
    fun read(): List<AttemptEvent> = locked { journal -> readCommitted(journal) }

    /** False for an identical replay; a reused UUID with different contents is rejected. */
    fun append(event: AttemptEvent): Boolean {
        val encoded = AttemptCodec.encode(event).toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_LINE_BYTES)
        return locked { journal ->
            val existing = readCommitted(journal).find { it.eventId == event.eventId }
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

    private fun <T> locked(block: (RandomAccessFile) -> T): T = synchronized(monitor) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create attempt log directory")
        // The process monitor also prevents OverlappingFileLockException across separate store instances.
        RandomAccessFile(File(directory, "attempts.v1.lock"), "rw").use { lock ->
            lock.channel.lock().use {
                RandomAccessFile(File(directory, "attempts.v1.jsonl"), "rw").use(block)
            }
        }
    }

    private fun readCommitted(journal: RandomAccessFile): List<AttemptEvent> {
        journal.seek(0)
        val events = linkedMapOf<String, AttemptEvent>()
        val line = ByteArrayOutputStream()
        var committedEnd = 0L
        while (journal.filePointer < journal.length()) {
            val byte = journal.read()
            if (byte == '\n'.code) {
                val event = try { AttemptCodec.decode(line.toString("UTF-8")) }
                catch (e: IllegalArgumentException) { throw IOException("Invalid committed attempt record", e) }
                val previous = events.putIfAbsent(event.eventId, event)
                if (previous != null) throw IOException("Duplicate UUID in committed attempt log")
                line.reset()
                committedEnd = journal.filePointer
            } else {
                if (line.size() >= MAX_LINE_BYTES) throw IOException("Attempt record exceeds size limit")
                line.write(byte)
            }
        }
        if (journal.length() != committedEnd) {
            journal.setLength(committedEnd)
            journal.fd.sync()
        }
        return events.values.toList()
    }

    companion object {
        private val monitor = Any()
        private const val MAX_LINE_BYTES = 65536
        /** noBackupFilesDir is excluded from both cloud backup and device-to-device restore. */
        fun open(context: Context): AttemptLog = AttemptLog(File(context.noBackupFilesDir, "math-attempts"))
    }
}
