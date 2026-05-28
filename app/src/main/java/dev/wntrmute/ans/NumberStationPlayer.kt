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
 * Every pass opens with an 800 Hz tone to key a VOX-enabled radio. The first
 * pass also speaks a time-of-day greeting ("Good morning" / etc.) after that
 * tone; subsequent repeats omit the greeting and jump straight to digits.
 * Digits are spoken one at a time with a short pause between five-digit
 * groups. Between repeats, a 432 Hz tone marks the end of the repeat. After
 * the final pass in a finite broadcast, a 432 Hz tone plays just before
 * "Good bye for now" as a signoff. Loop-until-stopped mode has no signoff
 * (the user terminates).
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

    // Tracks the first pass of a broadcast so the spoken greeting fires once
    // at the head and is omitted from subsequent repeats.
    private var firstPass = true

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val parts = utteranceId?.split(':') ?: return
            if (parts.size < 2) return
            val session = parts[0].toIntOrNull() ?: return
            if (session != sessionId) return
            when (parts[1]) {
                LEAD_TONE_TAG -> tonePlayer.play(LEAD_HZ, LEAD_TONE_MS)
                END_MARKER -> tonePlayer.play(END_HZ, END_TONE_MS)
                END_TONE_TAG -> tonePlayer.play(END_HZ, BYE_LEAD_TONE_MS)
                // LEAD_TAG (greeting voice) and BYE_TAG (signoff voice) no
                // longer fire tones — the tone now plays before the voice via
                // a dedicated silent-utterance trigger. END_LAST_MARKER fires
                // no tone either; END_TONE_TAG just before it already did.
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
        firstPass = true
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
        val isFirst = firstPass
        firstPass = false

        // Lead-in: 800 Hz tone always (keys VOX for this pass), then a brief
        // gap inside VOX hang. The spoken greeting fires only on the first
        // pass of the broadcast; subsequent repeats jump straight to digits.
        tts.playSilentUtterance(LEAD_TONE_MS.toLong(), TextToSpeech.QUEUE_FLUSH, "$session:$LEAD_TONE_TAG")
        tts.playSilentUtterance(LEAD_TONE_GAP_MS, TextToSpeech.QUEUE_ADD, "$session:leadGap")
        if (isFirst) {
            tts.speak(currentGreeting(), TextToSpeech.QUEUE_ADD, null, "$session:$LEAD_TAG")
            tts.playSilentUtterance(LEAD_SILENCE_MS, TextToSpeech.QUEUE_ADD, "$session:leadSilence")
        }

        // Digits.
        groups.forEachIndexed { i, group ->
            // Comma-separate so the engine reads digits individually
            // ("1, 2, 3") rather than as a single number.
            val spoken = group.toCharArray().joinToString(separator = ", ")
            tts.speak(spoken, TextToSpeech.QUEUE_ADD, null, "$session:g$i")
            tts.playSilentUtterance(GROUP_PAUSE_MS, TextToSpeech.QUEUE_ADD, "$session:p$i")
        }

        if (isLast) {
            // Final pass: 432 Hz end tone, brief gap, then the signoff voice,
            // then a short silent marker whose onDone drives passComplete ->
            // stop.
            tts.playSilentUtterance(END_TONE_AND_GAP_MS, TextToSpeech.QUEUE_ADD, "$session:$END_TONE_TAG")
            tts.speak(GOODBYE_TEXT, TextToSpeech.QUEUE_ADD, null, "$session:$BYE_TAG")
            tts.playSilentUtterance(BYE_TAIL_MS, TextToSpeech.QUEUE_ADD, "$session:$END_LAST_MARKER")
        } else {
            // Non-final pass: silent utterance whose onStart fires the 432 Hz
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

        // Lead-in: 800 Hz tone, brief gap (kept inside typical VOX hang so the
        // radio stays keyed), then the greeting voice, then a short pause
        // before digits. Tone is shorter than the previous behind-voice
        // version because it stands alone now — just enough to key VOX.
        const val LEAD_TONE_TAG = "leadTone"
        const val LEAD_TAG = "lead"
        const val LEAD_HZ = 800
        const val LEAD_TONE_MS = 700
        const val LEAD_TONE_GAP_MS = 200L
        const val LEAD_SILENCE_MS = 400L

        // Inter-group spacing inside a pass.
        const val GROUP_PAUSE_MS = 700L

        // End-of-repeat: 432 Hz tone. For non-final passes the tone-and-pause
        // silent doubles as the pass-complete marker; for the final pass the
        // tone plays via a separate trigger right before the signoff voice.
        // The pre-goodbye tone is 200 ms longer than the between-repeats tone
        // so VOX is solidly keyed before the signoff voice arrives (the
        // signoff was getting clipped at 500 ms).
        const val END_HZ = 432
        const val END_TONE_MS = 500
        const val END_TONE_AND_PAUSE_MS = 1000L
        const val BYE_LEAD_TONE_MS = 700
        const val END_TONE_AND_GAP_MS = 900L
        const val END_MARKER = "end"
        const val END_TONE_TAG = "endTone"

        // Signoff (final pass only): voice, then a short silent marker whose
        // onDone drives passComplete -> stop.
        const val BYE_TAG = "bye"
        const val BYE_TAIL_MS = 500L
        const val END_LAST_MARKER = "endLast"

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
