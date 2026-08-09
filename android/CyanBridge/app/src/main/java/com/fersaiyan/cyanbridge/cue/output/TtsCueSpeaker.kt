package com.fersaiyan.cyanbridge.cue.output

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Speaks Tier 1 and Tier 2 output through Android TTS (PRD §5.1).
 *
 * Wraps an engine owned by the host Activity rather than creating its own, because
 * `TextToSpeech` is expensive to initialize and the app already holds one.
 *
 * The completion callback is the important part. [OutputArbiter] tracks whether Cue is
 * currently talking, and it only ever learns that speech ended from here — a dropped
 * callback leaves the arbiter convinced it is still speaking, which silently wedges every
 * later whisper. So every exit path fires the callback exactly once.
 */
class TtsCueSpeaker(
    private val engineProvider: () -> TextToSpeech?,
    /**
     * PRD §3: the user listens at 300-500 words per minute. This multiplies the rate the
     * arbiter asks for, so a user-facing speed preference has one place to live.
     */
    @Volatile var rateMultiplier: Float = 1.0f,
) : CueSpeaker {

    private val pending = ConcurrentHashMap<String, () -> Unit>()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            complete(utteranceId)
        }

        @Deprecated("Required by the base class for API < 21 compatibility")
        override fun onError(utteranceId: String?) {
            complete(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            complete(utteranceId)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            complete(utteranceId)
        }
    }

    /**
     * Installs the progress listener. Call once after the TTS engine reports ready.
     *
     * Note this claims the engine's single listener slot: anything else in the app that sets
     * its own `UtteranceProgressListener` will displace this one and break the arbiter.
     */
    fun attach() {
        val engine = engineProvider() ?: run {
            Log.w(TAG, "No TTS engine available to attach to")
            return
        }
        engine.setOnUtteranceProgressListener(progressListener)
    }

    override fun speak(text: String, rate: Float, utteranceId: String, onDone: () -> Unit) {
        val engine = engineProvider() ?: run {
            // No engine means no speech and no callback from the platform, so fire it here
            // or the arbiter waits forever for an utterance that never started.
            onDone()
            return
        }

        pending[utteranceId] = onDone
        val result = runCatching {
            engine.setSpeechRate(rate * rateMultiplier)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrElse { error ->
            Log.w(TAG, "TTS speak failed", error)
            TextToSpeech.ERROR
        }

        if (result != TextToSpeech.SUCCESS) {
            complete(utteranceId)
        }
    }

    override fun stop() {
        val engine = engineProvider()
        if (engine == null) {
            drainAll()
            return
        }
        runCatching { engine.stop() }
            .onFailure { Log.w(TAG, "TTS stop failed", it) }

        // `stop()` does not reliably deliver onStop for every engine, so settle anything
        // still outstanding rather than trusting the platform to do it.
        drainAll()
    }

    private fun complete(utteranceId: String?) {
        val id = utteranceId ?: return
        pending.remove(id)?.invoke()
    }

    private fun drainAll() {
        val ids = pending.keys.toList()
        ids.forEach { id -> pending.remove(id)?.invoke() }
    }

    private companion object {
        const val TAG = "CueSpeaker"
    }
}
