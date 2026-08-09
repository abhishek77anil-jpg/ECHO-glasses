package com.fersaiyan.cyanbridge.cue.audio

/**
 * Voice activity detection, used to find the gaps a whisper is allowed to land in (PRD §5.2).
 *
 * This is the component the PRD singles out: nothing in Tier 1 ever plays while a human is
 * speaking. The detector is therefore biased hard toward saying "someone is talking" — a
 * missed gap costs a delayed whisper, a false gap costs the product's core promise.
 *
 * Input is a stream of RMS samples. `SpeechRecognizer.onRmsChanged` is the cheapest source
 * and needs no extra audio capture, but its scale is uncalibrated and varies by device and
 * by room, so the threshold adapts to a running noise floor instead of being fixed.
 *
 * No Android imports and no clock reads: `nowMs` is always supplied by the caller.
 */
class GapDetector(
    /** Silence required before a gap counts as open. PRD §5.2 specifies 400ms. */
    private val requiredSilenceMs: Long = DEFAULT_REQUIRED_SILENCE_MS,
    /** How far above the noise floor a sample must sit to count as speech. */
    private val speechMarginDb: Float = DEFAULT_SPEECH_MARGIN_DB,
    /**
     * How far above the floor a sample must fall back to before speech is considered over.
     * Lower than [speechMarginDb] on purpose — the hysteresis stops the detector flapping
     * between states on the natural amplitude dips inside a single sentence.
     */
    private val releaseMarginDb: Float = DEFAULT_RELEASE_MARGIN_DB,
    /** EMA weight for noise-floor adaptation. Small, so the floor tracks the room, not the talker. */
    private val floorAdaptRate: Float = DEFAULT_FLOOR_ADAPT_RATE,
    /**
     * If no RMS sample arrives within this window, the detector reports "unknown" and refuses
     * to open a gap. Silence in the data is not the same as silence in the room, and guessing
     * wrong means talking over someone.
     */
    private val staleSampleMs: Long = DEFAULT_STALE_SAMPLE_MS,
) {
    enum class Transition {
        /** Nothing changed. */
        NONE,

        /** A human started talking. Any in-flight whisper must be aborted immediately. */
        SPEECH_STARTED,

        /** A human stopped talking. The gap timer starts now; it is not open yet. */
        SPEECH_ENDED,
    }

    private var noiseFloorDb: Float? = null
    private var speaking = false
    private var lastSpeechEndMs: Long? = null
    private var lastSampleMs: Long? = null

    val isSpeaking: Boolean get() = speaking

    /** Current adapted noise floor, exposed for the dev overlay and for tuning. */
    val noiseFloor: Float? get() = noiseFloorDb

    /**
     * Feeds one RMS sample.
     *
     * @param rmsDb amplitude in whatever scale the source uses; only relative values matter.
     * @return the state transition this sample caused, for callers that must react instantly.
     */
    fun onRms(rmsDb: Float, nowMs: Long): Transition {
        lastSampleMs = nowMs
        val floor = noiseFloorDb ?: rmsDb.also { noiseFloorDb = it }

        val transition = if (speaking) {
            if (rmsDb <= floor + releaseMarginDb) {
                speaking = false
                lastSpeechEndMs = nowMs
                Transition.SPEECH_ENDED
            } else {
                Transition.NONE
            }
        } else {
            if (rmsDb >= floor + speechMarginDb) {
                speaking = true
                Transition.SPEECH_STARTED
            } else {
                Transition.NONE
            }
        }

        // Adapt the floor only while nobody is talking, otherwise loud speech drags the
        // threshold up behind it and the detector goes deaf to the conversation.
        if (!speaking) {
            noiseFloorDb = floor + (rmsDb - floor) * floorAdaptRate
        }

        return transition
    }

    /**
     * True when it is safe to speak a Tier 1 whisper.
     *
     * Returns false whenever the answer is not confidently yes: while speech is active,
     * before the silence has lasted long enough, and whenever the sample stream has gone
     * stale. Callers should treat false as "not yet", not as "never".
     */
    fun isGapOpen(nowMs: Long): Boolean {
        if (speaking) return false
        val lastSample = lastSampleMs ?: return false
        if (nowMs - lastSample > staleSampleMs) return false
        val speechEnd = lastSpeechEndMs ?: return true
        return nowMs - speechEnd >= requiredSilenceMs
    }

    /** How long the room has been quiet, or null if someone is talking or state is unknown. */
    fun silenceDurationMs(nowMs: Long): Long? {
        if (speaking) return null
        val speechEnd = lastSpeechEndMs ?: return nowMs - (lastSampleMs ?: return null)
        return nowMs - speechEnd
    }

    /** Clears adaptation and state. Call on session start or when the audio route changes. */
    fun reset() {
        noiseFloorDb = null
        speaking = false
        lastSpeechEndMs = null
        lastSampleMs = null
    }

    companion object {
        const val DEFAULT_REQUIRED_SILENCE_MS = 400L
        const val DEFAULT_SPEECH_MARGIN_DB = 4.0f
        const val DEFAULT_RELEASE_MARGIN_DB = 2.0f
        const val DEFAULT_FLOOR_ADAPT_RATE = 0.05f
        const val DEFAULT_STALE_SAMPLE_MS = 1_500L
    }
}
