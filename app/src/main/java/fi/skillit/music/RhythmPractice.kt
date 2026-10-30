package fi.skillit.music

import kotlin.math.abs
import kotlin.random.Random

enum class FillResult { UNDERFILLED, CORRECT, OVERFLOW }
data class RhythmChallenge(val target: Bar, val prefix: List<Symbol>, val solution: List<Symbol>) {
    fun evaluate(added: List<Symbol>): FillResult {
        val eighths = (prefix + added).sumOf { it.duration.eighths }
        return when {
            eighths < target.meter.beats * 2 -> FillResult.UNDERFILLED
            eighths == target.meter.beats * 2 -> FillResult.CORRECT
            else -> FillResult.OVERFLOW
        }
    }
    companion object {
        fun generate(meter: Meter, seed: Int): RhythmChallenge {
            val random = Random(seed)
            // Begin with a sounded eighth and a silent eighth; then fill the exact remainder.
            val symbols = mutableListOf<Symbol>(Note(Pitch.C4, Duration.EIGHTH), Rest(Duration.EIGHTH))
            var remaining = meter.beats * 2 - 2
            while (remaining > 0) {
                val duration = Duration.entries.filter { it.eighths <= remaining }.random(random)
                symbols += if (random.nextBoolean()) Note(Pitch.C4, duration) else Rest(duration)
                remaining -= duration.eighths
            }
            val split = random.nextInt(1, symbols.size)
            return RhythmChallenge(Bar(meter, symbols), symbols.take(split), symbols.drop(split))
        }
    }
}

data class TapFeedback(val matched: Int, val expected: Int, val extra: Int)
/** Optional timing feedback only. Clock values are monotonic milliseconds supplied by the caller. */
class TapSession(private val bar: Bar, val bpm: Int = 100) {
    init { require(bar.valid && bpm in 60..180) }
    val durationMs: Long = MusicAudio.frameAt(bar.eighths, bpm) * 1000L / MusicAudio.sampleRate
    private val onsets = buildList<Long> {
        var eighths = 0
        bar.symbols.forEach {
            if (it is Note) add(MusicAudio.frameAt(eighths, bpm) * 1000L / MusicAudio.sampleRate)
            eighths += it.duration.eighths
        }
    }
    private var started: Long? = null
    private val taps = mutableListOf<Long>()
    fun start(now: Long) { require(now >= 0); taps.clear(); started = now }
    fun cancel() { started = null; taps.clear() }
    fun tap(now: Long): Boolean {
        val start = started ?: return false
        if (now < start) return false
        val relative = now - start
        if (relative >= durationMs || taps.lastOrNull()?.let { relative - it < 80 } == true) return false
        taps += relative
        return true
    }
    fun finish(): TapFeedback {
        val remaining = onsets.toMutableList()
        var matched = 0
        taps.forEach { tap ->
            val nearest = remaining.minByOrNull { abs(it - tap) }
            if (nearest != null && abs(nearest - tap) <= 140) { remaining.remove(nearest); matched++ }
        }
        started = null
        return TapFeedback(matched, onsets.size, taps.size - matched)
    }
}
