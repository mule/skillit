package fi.skillit.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Offline mono PCM16. All onsets use cumulative integer-eighth positions. */
object MusicAudio {
    const val sampleRate = 24000
    fun frequency(pitch: Pitch): Double = when (pitch) {
        Pitch.C4 -> 261.625565; Pitch.D4 -> 293.664768; Pitch.E4 -> 329.627557; Pitch.F4 -> 349.228231
        Pitch.G4 -> 391.995436; Pitch.A4 -> 440.0; Pitch.H4 -> 493.883301; Pitch.C5 -> 523.251131
    }
    fun frameAt(eighths: Int, bpm: Int): Int {
        require(eighths in 0..32 && bpm in 60..180)
        return (eighths * sampleRate * 30.0 / bpm).roundToInt()
    }
    fun render(symbols: List<Symbol>, bpm: Int = 100): ShortArray {
        require(symbols.isNotEmpty() && symbols.size <= 32)
        val total = symbols.sumOf { it.duration.eighths }
        val samples = ShortArray(frameAt(total, bpm))
        var position = 0
        symbols.forEach { symbol ->
            val start = frameAt(position, bpm)
            position += symbol.duration.eighths
            val end = frameAt(position, bpm)
            if (symbol is Note) {
                val count = end - start
                repeat(count) { i ->
                    val envelope = min(1.0, min(i, count - 1 - i) / 120.0)
                    samples[start + i] = (7200 * envelope * sin(2 * PI * frequency(symbol.pitch) * i / sampleRate)).roundToInt().toShort()
                }
            }
        }
        return samples
    }
    fun metronome(meter: Meter): ShortArray = render(List(meter.beats * 2) { i ->
        if (i % 2 == 0) Note(if (i == 0) Pitch.C5 else Pitch.G4, Duration.EIGHTH) else Rest(Duration.EIGHTH)
    })
}

/** The only platform boundary; an output owns at most one finite PCM buffer. */
interface PcmOutput {
    fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit)
    fun stop()
}

class PlaybackController(private val output: PcmOutput) : AutoCloseable {
    private val mutablePlaying = MutableStateFlow(false)
    val playingState = mutablePlaying.asStateFlow()
    var playing: Boolean
        get() = mutablePlaying.value
        private set(value) { mutablePlaying.value = value }
    var failure: Boolean = false
        private set
    private var muted = false
    private var volume = .6f
    private var closed = false
    private var generation = 0
    fun configure(muted: Boolean, volume: Float) {
        require(volume.isFinite())
        val nextVolume = volume.coerceIn(0f, 1f)
        if (this.muted != muted || this.volume != nextVolume) stop()
        this.muted = muted
        this.volume = nextVolume
    }
    fun play(samples: ShortArray): Boolean {
        stop()
        failure = false
        require(samples.isNotEmpty() && samples.size <= MusicAudio.sampleRate * 16)
        if (closed || muted || volume == 0f) return false
        val token = generation
        return try {
            playing = true
            output.start(samples, MusicAudio.sampleRate, volume) { if (token == generation) { playing = false } }
            true
        } catch (_: Exception) {
            stop(); failure = true; false
        }
    }
    fun stop() { generation++; playing = false; output.stop() }
    override fun close() { stop(); closed = true }
}
