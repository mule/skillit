package fi.skillit.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Main-thread owned static track: no writer thread can outlive stop or release. */
class AndroidPcmOutput : PcmOutput {
    private var track: AudioTrack? = null
    val active: Boolean get() = track != null
    var framesWritten: Int = 0
        private set
    val playbackHead: Int get() = track?.playbackHeadPosition ?: 0

    override fun start(samples: ShortArray, sampleRate: Int, volume: Float, onFinished: () -> Unit) {
        stop()
        require(sampleRate == MusicAudio.sampleRate && samples.isNotEmpty() && samples.size <= sampleRate * 16)
        require(volume.isFinite() && volume in 0f..1f)
        val next = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        track = next
        try {
            check(next.state == AudioTrack.STATE_NO_STATIC_DATA)
            framesWritten = next.write(samples, 0, samples.size)
            check(framesWritten == samples.size)
            next.setVolume(volume)
            next.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(audioTrack: AudioTrack) {
                    if (track === audioTrack) { stop(); onFinished() }
                }
                override fun onPeriodicNotification(audioTrack: AudioTrack) {}
            }, Handler(Looper.getMainLooper()))
            check(next.setNotificationMarkerPosition(samples.size) == AudioTrack.SUCCESS)
            next.play()
        } catch (e: Exception) { stop(); throw e }
    }
    override fun stop() {
        track?.let { old ->
            track = null
            old.setPlaybackPositionUpdateListener(null)
            try { if (old.playState == AudioTrack.PLAYSTATE_PLAYING) old.pause() }
            finally { old.release() }
        }
    }
}
