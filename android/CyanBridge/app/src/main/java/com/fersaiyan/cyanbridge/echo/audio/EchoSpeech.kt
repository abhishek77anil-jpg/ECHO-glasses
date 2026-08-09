package com.fersaiyan.cyanbridge.echo.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Central speech service.
 *
 * Two rules, both learned the hard way in screen-reader testing and carried
 * over verbatim from the JS build:
 *
 *  1. NEVER speak twice. The original build spoke through the TTS engine *and*
 *     posted an accessibility announcement every time. With TalkBack running
 *     that plays every sentence twice, in two different voices, a beat apart —
 *     unusable. So: if a screen reader is active it owns the voice channel and
 *     we only post announcements to it, at the user's own rate and volume. If
 *     it is not, we drive TextToSpeech ourselves. Do not "fix" this by adding
 *     the other path back.
 *
 *  2. Ambient chatter must never talk over an answer the user asked for.
 *     Live-awareness events fire on a timer; a result fires because the user
 *     double-tapped. [Priority] keeps the timer from stomping the answer.
 */
enum class Priority(val level: Int) {
    /** live awareness feed, ambient */
    LOW(0),

    /** navigation, settings ticks */
    NORMAL(1),

    /** results, errors, help — anything the user directly asked for */
    HIGH(2),
}

class EchoSpeech(context: Context) {

    private val appContext = context.applicationContext
    private val accessibility =
        appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private var tts: TextToSpeech? = null
    private var ready = false

    @Volatile private var speaking = false
    @Volatile private var currentPriority = -1
    private val token = AtomicLong(0)

    /** Set by the UI so announcements can be posted through a real view. */
    var announceForAccessibility: ((CharSequence) -> Unit)? = null

    /** Rate and volume are read at speak time, never captured at construction. */
    var rate: Float = 0.9f
    var volume: Float = 1.0f

    /**
     * True when TalkBack (or any touch-exploration service) owns the channel.
     * Read live rather than cached — the user can toggle TalkBack while the
     * app is foregrounded and we must hand the channel over immediately.
     */
    val screenReaderOn: Boolean
        get() = accessibility.isEnabled && accessibility.isTouchExplorationEnabled

    val screenReaderName: String = "TalkBack"

    fun init() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = settle(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) =
                        settle(utteranceId)

                    // The no-code overload is abstract on the base class, so it
                    // must be implemented even though it is deprecated.
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) = settle(utteranceId)

                    override fun onError(utteranceId: String?, errorCode: Int) =
                        settle(utteranceId)
                })
            }
        }
    }

    private fun settle(utteranceId: String?) {
        // A newer utterance already owns the channel — ignore this late callback.
        if (utteranceId != token.get().toString()) return
        speaking = false
        currentPriority = -1
    }

    fun say(text: String?, priority: Priority = Priority.NORMAL) {
        if (text.isNullOrBlank()) return

        if (screenReaderOn) {
            // Rule 1: the screen reader speaks, we do not.
            stopTts()
            announceForAccessibility?.invoke(text)
            return
        }

        // Rule 2: something more important is mid-sentence — drop this rather
        // than cut it off.
        if (speaking && priority.level < currentPriority) return

        val engine = tts ?: return
        if (!ready) return

        val id = token.incrementAndGet().toString()
        speaking = true
        currentPriority = priority.level

        engine.setSpeechRate(rate)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        // QUEUE_FLUSH replaces whatever is playing; the token check in settle()
        // makes the resulting onStop for the previous utterance a no-op.
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) {
            // No usable TTS engine — stay silent rather than wedge the state.
            speaking = false
            currentPriority = -1
        }
    }

    fun stop() {
        token.incrementAndGet()
        speaking = false
        currentPriority = -1
        stopTts()
    }

    private fun stopTts() {
        runCatching { tts?.stop() }
    }

    fun release() {
        stop()
        runCatching {
            tts?.shutdown()
        }
        tts = null
        ready = false
        announceForAccessibility = null
    }
}
