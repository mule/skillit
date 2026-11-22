package fi.skillit.engine

/**
 * Stack-safe preflight before any JSON is decoded: kotlinx.serialization builds JSON trees (such as generator
 * `params`) recursively, so deeply nested arrays or objects would throw StackOverflowError, an Error that
 * callers do not catch. This scan is iterative and ignores brackets inside strings (escapes included).
 */
object JsonDepth {
    const val MAX = 32

    /** Throws [IllegalArgumentException] when brackets nest deeper than [max]. */
    fun require(text: String, max: Int = MAX) {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            when {
                inString -> when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                c == '"' -> inString = true
                c == '[' || c == '{' -> require(++depth <= max) { "JSON is nested deeper than $max levels" }
                c == ']' || c == '}' -> depth--
            }
        }
    }
}
