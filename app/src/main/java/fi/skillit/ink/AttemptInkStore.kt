package fi.skillit.ink

import java.io.File
import java.io.IOException
import java.util.Base64

/** The ink of one answer attempt: strokes and what was read, per cell. Input for the handwriting coach (#28). */
data class AttemptInk(val key: String, val savedAtMs: Long, val cells: Map<Int, CellInk>)

data class CellInk(val strokes: List<InkStroke>, val digit: Int?)

/**
 * Stores pen strokes per attempt in app-private storage ([dir] should be under `Context.filesDir`).
 * Nothing here is uploaded: `allowBackup=false` and `data_extraction_rules.xml` exclude all files from
 * cloud backup and device transfer, and the app has no upload code (CI's privacy gate checks imports).
 * Only aggregate legibility scores may ever be synced (epic #31), never these files.
 *
 * One file per attempt, written to a temporary file and renamed, so a full disk can't leave a half file.
 * [prune] keeps storage bounded; #28 decides the retention.
 */
class AttemptInkStore(
    private val dir: File,
    /** Test hook for disk-full and similar failures. */
    internal val writer: (File, String) -> Unit = { f, text -> f.writeText(text) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun save(key: String, cells: Map<Int, CellInk>): AttemptInk {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create $dir")
        val attempt = AttemptInk(key, clock(), cells.filterValues { it.strokes.isNotEmpty() })
        val file = fileOf(key)
        val tmp = File(dir, file.name + ".tmp")
        try {
            writer(tmp, encode(attempt))
            if (!tmp.renameTo(file)) throw IOException("could not write $file")
        } finally {
            // Never leave a half-written copy of the child's strokes behind (security F1).
            if (tmp.exists()) tmp.delete()
        }
        return attempt
    }

    fun load(key: String): AttemptInk? = fileOf(key).takeIf { it.exists() }?.let { decode(it.readText()) }

    /** All stored attempts, oldest first. Unreadable files are skipped. */
    fun all(): List<AttemptInk> = files().mapNotNull { runCatching { decode(it.readText()) }.getOrNull() }.sortedBy { it.savedAtMs }

    fun delete(key: String) { fileOf(key).delete() }

    /** Deletes every attempt, including temporary files left by a crash mid-write. */
    fun deleteAll() { files().forEach { it.delete() }; leftovers().forEach { it.delete() } }

    /** Keeps the newest [maxAttempts] attempts and deletes the rest. */
    fun prune(maxAttempts: Int) {
        val keep = all().takeLast(maxAttempts).map { fileOf(it.key).name }.toSet()
        files().filter { it.name !in keep }.forEach { it.delete() }
        leftovers().forEach { it.delete() }
    }

    private fun files() = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty().toList()
    /** `*.ink.tmp` from a write that was interrupted (process killed between write and rename). */
    private fun leftovers() = dir.listFiles { f -> f.isFile && f.name.endsWith("$EXT.tmp") }.orEmpty().toList()

    /** File names come from the key, base64url-encoded, so any key is a safe, collision-free name. */
    private fun fileOf(key: String) = File(dir, Base64.getUrlEncoder().withoutPadding().encodeToString(key.toByteArray()) + EXT)

    companion object {
        private const val EXT = ".ink"

        /**
         * `v1<TAB>base64url(key)<TAB>savedAtMs`, then one line per cell: `cell<TAB>digit-or-empty<TAB>strokes`
         * (strokes as in [InkSampleCodec.encodeStrokes]).
         */
        fun encode(a: AttemptInk): String = buildString {
            append("v1\t").append(Base64.getUrlEncoder().withoutPadding().encodeToString(a.key.toByteArray())).append('\t').append(a.savedAtMs).append('\n')
            a.cells.toSortedMap().forEach { (cell, c) ->
                append(cell).append('\t').append(c.digit?.toString() ?: "").append('\t').append(InkSampleCodec.encodeStrokes(c.strokes)).append('\n')
            }
        }

        fun decode(text: String): AttemptInk? = runCatching {
            val lines = text.lines().filter { it.isNotEmpty() }
            val head = lines.first().split("\t")
            if (head.size != 3 || head[0] != "v1") return null
            val key = String(Base64.getUrlDecoder().decode(head[1]))
            val cells = lines.drop(1).associate { line ->
                val f = line.split("\t")
                f[0].toInt() to CellInk(InkSampleCodec.decodeStrokes(f[2]), f[1].takeIf(String::isNotEmpty)?.toInt())
            }
            AttemptInk(key, head[2].toLong(), cells)
        }.getOrNull()
    }
}

/** Saves what [state] holds for [key] (pen mode only; a number-pad answer has no ink). */
fun AttemptInkStore.save(key: String, state: DigitRowState): AttemptInk? {
    if (state.mode != InputMode.Pen) return null
    val digits = state.digits
    val cells = state.strokesByCell().mapValues { (i, s) -> CellInk(s, digits.getOrNull(i)) }
    return if (cells.isEmpty()) null else save(key, cells)
}
