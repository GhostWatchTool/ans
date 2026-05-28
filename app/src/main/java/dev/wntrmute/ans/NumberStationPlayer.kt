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
 * A *pass* is one complete reading of the message. Each pass begins with an
 * 800 Hz tone played behind a time-of-day greeting ("Good morning" / etc.) so
 * a VOX-enabled radio is keyed before any content arrives. Digits are then
 * spoken one at a time with a short pause between five-digit groups. Between
 * repeats, a short 800 Hz tone marks the end of the repeat. After the final
 * pass in a finite broadcast, an 800 Hz tone plays behind "Good bye for now"
 * to sign off. Loop-until-stopped mode has no signoff (the user terminates).
 *
 * TTS delivers progress callbacks on a binder thread; anything that touches
 * state or invokes listeners is marshalled to the main thread via [main].
 * Tones play concurrently with TTS through a separate [TonePlayer]; the
 * paths mix at the system audio mixer.
 */
class NumberStationPlayer(context: Context) {

    /** Invoked on the main thread whenever playback starts or stops. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** Invoked on the main thread when the engine cannot be used. */
    var onError: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val tonePlayer = TonePlayer()

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
        override fun onStart(utteranceId: String?) {
            val parts = utteranceId?.split(':') ?: return
            if (parts.size < 2) return
            val session = parts[0].toIntOrNull() ?: return
            if (session != sessionId) return
            when (parts[1]) {
                LEAD_TAG -> tonePlayer.play(TONE_HZ, LEAD_TONE_MS)
                BYE_TAG -> tonePlayer.play(TONE_HZ, FINAL_TONE_MS)
                END_MARKER -> tonePlayer.play(TONE_HZ, END_TONE_MS)
                // END_LAST_MARKER intentionally fires no tone — the BYE_TAG
                // utterance just before it already played the final tone.
            }
        }

        override fun onDone(utteranceId: String?) {
            val parts = utteranceId?.split(':') ?: return
            if (parts.size < 2) return
            val session = parts[0].toIntOrNull() ?: return
            if (session != sessionId || !playing) return
            if (parts[1] == END_MARKER || parts[1] == END_LAST_MARKER) {
                main.post { onPassComplete() }
            }
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

    /** Route synthesized speech and tones through the given audio attributes. */
    fun setAudioAttributes(attributes: AudioAttributes) {
        audioAttributes = attributes
        tonePlayer.audioAttributes = attributes
        if (ready) tts.setAudioAttributes(attributes)
    }

    /**
     * Begin playback of [groups]. When [repeat] is false the message is read
     * once; otherwise it loops forever if [loopForever], or is read [plays]
     * times. A play call before the engine finishes initialising is deferred
     * and runs automatically once it is ready.
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
        tonePlayer.stop()
        setPlaying(false)
    }

    /** Release engine resources. */
    fun shutdown() {
        pendingPlay = null
        if (ready) tts.stop()
        tonePlayer.stop()
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
        // Last pass when exactly one finite reading remains. Infinite plans
        // never set this, so loop-until-stopped mode skips the signoff.
        val isLast = !plan.isInfinite && plan.remaining == 1

        // Lead-in: greeting voice with a tone behind it (tone is triggered by
        // the greeting utterance's onStart, plays concurrently via AudioTrack).
        // The following silent utterance gives the tone tail time to fade
        // before digits begin.
        tts.speak(currentGreeting(), TextToSpeech.QUEUE_FLUSH, null, "$session:$LEAD_TAG")
        tts.playSilentUtterance(LEAD_SILENCE_MS, TextToSpeech.QUEUE_ADD, "$session:leadSilence")

        // Digits.
        groups.forEachIndexed { i, group ->
            // Comma-separate so the engine reads digits individually
            // ("1, 2, 3") rather than as a single number.
            val spoken = group.toCharArray().joinToString(separator = ", ")
            tts.speak(spoken, TextToSpeech.QUEUE_ADD, null, "$session:g$i")
            tts.playSilentUtterance(GROUP_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$session:p$i")
        }

        if (isLast) {
            // Final pass: tone behind the signoff voice, then a short silent
            // marker whose onDone drives passComplete -> stop.
            tts.speak(GOODBYE_TEXT, TextToSpeech.QUEUE_ADD, null, "$session:$BYE_TAG")
            tts.playSilentUtterance(BYE_TAIL_MS, TextToSpeech.QUEUE_ADD, "$session:$END_LAST_MARKER")
        } else {
            // Non-final pass: a silent utterance whose onStart fires the
            // end-of-repeat tone, and whose onDone drives the next pass. Its
            // duration covers the tone plus a brief inter-pass pause.
            tts.playSilentUtterance(END_TONE_AND_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$session:$END_MARKER")
        }
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

        // Lead-in: tone behind greeting, then short silence before digits.
        const val LEAD_TAG = "lead"
        const val LEAD_TONE_MS = 1500
        const val LEAD_SILENCE_MS = 700L

        // Inter-group spacing inside a pass.
        const val GROUP_PAUSE_MS = 700L

        // Non-final pass end: silent utterance with end-of-repeat tone on its
        // onStart. Duration covers the tone (500 ms) plus an inter-pass pause
        // (800 ms) — the next pass's lead-in tone re-keys VOX.
        const val END_TONE_MS = 500
        const val END_TONE_AND_PAUSE_MS = 1300L
        const val END_MARKER = "end"

        // Final pass: tone behind signoff voice, then a short silent marker.
        const val BYE_TAG = "bye"
        const val FINAL_TONE_MS = 1500
        const val BYE_TAIL_MS = 500L
        const val END_LAST_MARKER = "endLast"

        const val TONE_HZ = 800
        const val GOODBYE_TEXT = "Good bye for now"

        // Known Google en-GB female voice ids, preferred when available. The
        // en-GB default is already female, so this is a best-effort refinement.
        val PREFERRED_FEMALE_VOICES = setOf(
            "en-gb-x-gba-local", "en-gb-x-gba-network",
            "en-gb-x-gbc-local", "en-gb-x-gbc-network",
            "en-gb-x-gbg-local", "en-gb-x-gbg-network",
            "en-gb-x-fis-local", "en-gb-x-fis-network",
        )
    }
}
