package dev.wntrmute.ans

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Speaks number-station messages through the system text-to-speech engine.
 *
 * A *pass* is one complete reading of the message. Digits are spoken one at a
 * time, with a short pause between five-digit groups and a longer pause between
 * passes. Playback runs once, a fixed number of passes, or until [stop].
 *
 * The TTS engine delivers progress callbacks on a binder thread; everything
 * that touches caller state or invokes the listeners is marshalled back to the
 * main thread via [main].
 */
class NumberStationPlayer(context: Context) {

    /** Invoked on the main thread whenever playback starts or stops. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** Invoked on the main thread when the engine cannot be used. */
    var onError: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    private var ready = false
    private var playing = false

    // Incremented on every start/stop so callbacks from a flushed queue are
    // recognised as stale and ignored.
    private var sessionId = 0

    private var groups: List<String> = emptyList()
    private var plan = RepeatPlan(repeat = false, loopForever = false, plays = 1)
    private var pendingPlay: (() -> Unit)? = null
    private var audioAttributes: AudioAttributes? = null

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            val parts = utteranceId?.split(':') ?: return
            if (parts.size < 2) return
            val session = parts[0].toIntOrNull() ?: return
            if (session != sessionId || !playing) return
            if (parts[1] == END_MARKER) main.post { onPassComplete() }
        }

        @Deprecated("Required override; superseded by onError(id, code)")
        override fun onError(utteranceId: String?) {
            main.post { onError?.invoke("Text-to-speech playback failed.") }
        }
    }

    // Declared before `tts` so the engine has a valid listener at construction;
    // the body runs later (on a binder thread) once initialisation completes.
    private val initListener = TextToSpeech.OnInitListener { status ->
        if (status != TextToSpeech.SUCCESS) {
            main.post { onError?.invoke("Text-to-speech engine is unavailable.") }
            return@OnInitListener
        }
        ready = true
        applyBritishVoice()
        tts.setSpeechRate(SPEECH_RATE)
        audioAttributes?.let { tts.setAudioAttributes(it) }
        tts.setOnUtteranceProgressListener(progressListener)
        pendingPlay?.let { main.post(it) }
        pendingPlay = null
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, initListener)

    val isPlaying: Boolean get() = playing

    /** Route synthesized speech through the given audio attributes (media stream). */
    fun setAudioAttributes(attributes: AudioAttributes) {
        audioAttributes = attributes
        if (ready) tts.setAudioAttributes(attributes)
    }

    /**
     * Begin playback of [groups]. When [repeat] is false the message is read
     * once; otherwise it loops forever if [loopForever], or is read [plays]
     * times. A play before the engine finishes initialising is deferred and
     * runs automatically once it is ready.
     */
    fun start(groups: List<String>, repeat: Boolean, loopForever: Boolean, plays: Int) {
        if (groups.isEmpty()) return
        this.groups = groups
        plan = RepeatPlan(repeat, loopForever, plays)
        val run = {
            sessionId++
            setPlaying(true)
            enqueuePass()
        }
        if (ready) run() else pendingPlay = run
    }

    fun stop() {
        pendingPlay = null
        sessionId++ // invalidate any in-flight callbacks
        if (ready) tts.stop()
        setPlaying(false)
    }

    /** Release engine resources. Call from [androidx.lifecycle.ViewModel.onCleared]. */
    fun shutdown() {
        pendingPlay = null
        if (ready) tts.stop()
        tts.shutdown()
        ready = false
        setPlaying(false)
    }

    private fun onPassComplete() {
        if (!playing) return
        if (plan.completePass()) enqueuePass() else setPlaying(false)
    }

    private fun enqueuePass() {
        val session = sessionId
        groups.forEachIndexed { i, group ->
            // Comma-separate the digits so the engine reads them individually
            // ("1, 2, 3") rather than as a single number ("twelve thousand…").
            val spoken = group.toCharArray().joinToString(separator = ", ")
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(spoken, mode, null, "$session:g$i")
            tts.playSilentUtterance(GROUP_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$session:p$i")
        }
        // Sentinel marking the end of the pass; its completion drives repeats.
        tts.playSilentUtterance(PASS_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$session:$END_MARKER")
    }

    /**
     * Default to a British (en-GB) voice. The public TTS API exposes no gender,
     * so "female" is achieved by relying on the engine's en-GB default — which
     * Google ships as female — and overriding only when a recognised female
     * voice is present. If en-GB data is missing we fall back to the default
     * locale so playback still works.
     */
    private fun applyBritishVoice() {
        val result = tts.setLanguage(Locale.UK)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault())
            main.post { onError?.invoke("British English voice not installed; using the default voice.") }
            return
        }
        val female = tts.voices.orEmpty().firstOrNull { voice ->
            voice.name in PREFERRED_FEMALE_VOICES &&
                !voice.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
        }
        if (female != null) tts.voice = female
        Log.i(TAG, "voice=${(tts.voice ?: tts.defaultVoice)?.name} locale=${(tts.voice ?: tts.defaultVoice)?.locale}")
    }

    private fun setPlaying(value: Boolean) {
        if (playing == value) return
        playing = value
        main.post { onPlayingChanged?.invoke(value) }
    }

    private companion object {
        const val TAG = "NumberStationPlayer"
        const val SPEECH_RATE = 0.9f
        const val GROUP_PAUSE_MS = 700L
        const val PASS_PAUSE_MS = 1500L
        const val END_MARKER = "end"

        // Known Google en-GB female voice ids, preferred when available. The
        // en-GB default is already female, so this is a best-effort refinement,
        // not a requirement.
        val PREFERRED_FEMALE_VOICES = setOf(
            "en-gb-x-gba-local", "en-gb-x-gba-network",
            "en-gb-x-gbc-local", "en-gb-x-gbc-network",
            "en-gb-x-gbg-local", "en-gb-x-gbg-network",
            "en-gb-x-fis-local", "en-gb-x-fis-network",
        )
    }
}
