package dev.wntrmute.ans

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays short sine-wave tones over a parallel [AudioTrack] so they can overlap
 * the active TTS utterance — used to key a VOX-enabled radio behind the lead-in
 * greeting and to mark the end of each repeat. Each call to [play] builds a
 * one-shot static-mode track, schedules its release after [durationMs], and
 * supersedes any tone already playing. The mixer in AudioFlinger combines the
 * tone with concurrent TTS audio at the system level.
 *
 * Thread-safe: public methods marshal to the main thread.
 */
class TonePlayer {

    /** Audio attributes used for built AudioTracks; default is media + speech. */
    var audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val main = Handler(Looper.getMainLooper())
    private var current: AudioTrack? = null

    /**
     * Play a [freqHz] sine tone for [durationMs] milliseconds. Any tone already
     * playing is stopped first.
     */
    fun play(freqHz: Int, durationMs: Int) {
        if (durationMs <= 0) return
        main.post {
            releaseCurrent()
            val track = build(freqHz, durationMs) ?: return@post
            current = track
            Log.d(TAG, "play ${freqHz}Hz ${durationMs}ms")
            track.play()
            main.postDelayed({
                // Only release if this is still the active track — a later
                // play() or stop() may have replaced and released it already.
                if (current === track) {
                    current = null
                    safeRelease(track)
                }
            }, durationMs.toLong() + RELEASE_GUARD_MS)
        }
    }

    /** Stop and release any in-flight tone. Safe to call from any thread. */
    fun stop() {
        main.post { releaseCurrent() }
    }

    private fun releaseCurrent() {
        val track = current ?: return
        current = null
        safeRelease(track)
    }

    private fun safeRelease(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped/released.
        }
        try {
            track.release()
        } catch (_: IllegalStateException) {
            // Already released.
        }
    }

    private fun build(freqHz: Int, durationMs: Int): AudioTrack? {
        val numSamples = (SAMPLE_RATE.toLong() * durationMs / 1000L).toInt().coerceAtLeast(1)
        val pcm = ShortArray(numSamples)
        val rampSamples = (SAMPLE_RATE * ENVELOPE_MS / 1000).coerceAtLeast(1)
        val twoPiF = 2.0 * PI * freqHz
        val peak = (Short.MAX_VALUE * AMPLITUDE).toInt()
        for (i in 0 until numSamples) {
            // Short attack/release envelope to avoid pops.
            val env = when {
                i < rampSamples -> i.toDouble() / rampSamples
                i > numSamples - rampSamples -> (numSamples - i).toDouble() / rampSamples
                else -> 1.0
            }
            pcm[i] = (sin(twoPiF * i / SAMPLE_RATE) * peak * env).toInt().toShort()
        }
        return try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(numSamples * BYTES_PER_SAMPLE)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
                .apply { write(pcm, 0, pcm.size) }
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "AudioTrack build failed: ${e.message}")
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack build failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "TonePlayer"
        const val SAMPLE_RATE = 22050
        const val BYTES_PER_SAMPLE = 2 // PCM 16-bit mono
        const val AMPLITUDE = 0.5 // -6 dBFS — loud enough to key VOX, well clear of clipping
        const val ENVELOPE_MS = 10 // attack/release to avoid clicks
        const val RELEASE_GUARD_MS = 100L
    }
}
