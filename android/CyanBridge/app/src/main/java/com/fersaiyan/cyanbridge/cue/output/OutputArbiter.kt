package com.fersaiyan.cyanbridge.cue.output

import com.fersaiyan.cyanbridge.cue.audio.GapDetector

/** Speech output sink. Injected so the arbiter stays unit-testable off-device. */
interface CueSpeaker {
    /** Speaks [text], flushing anything already playing. [onDone] fires on completion or stop. */
    fun speak(text: String, rate: Float, utteranceId: String, onDone: () -> Unit)

    /** Stops immediately. Must trigger the pending [speak] callback. */
    fun stop()
}

/** Tier 0 sink. */
interface EarconSink {
    fun play(earcon: Earcon)
}

/**
 * The interruption budget, enforced (PRD §5.1 and §5.2).
 *
 * Cue is defined by what it refuses to say, and this class is where the refusing happens.
 * The rules it implements:
 *
 * - **Tier 0 earcons** play immediately and are never gated. They are non-verbal and under
 *   200ms, so the cost of one landing on top of speech is far lower than the cost of
 *   delaying the event it signals.
 * - **Tier 1 whispers** play only inside a confirmed gap, are abandoned the instant a human
 *   starts talking, and expire rather than queue. A whisper that arrives after the
 *   conversation has moved on is noise, so a stale one is dropped, not deferred.
 * - **Tier 2 briefings** are user-initiated, so they start immediately and outrank whispers.
 *
 * Drive it by calling [pump] on a timer and forwarding every [GapDetector.Transition].
 */
class OutputArbiter(
    private val speaker: CueSpeaker,
    private val earcons: EarconSink,
    private val gapDetector: GapDetector,
    /**
     * Clock, used only where a timestamp cannot be passed in — specifically the speech
     * completion callback, which arrives asynchronously from the TTS engine. Injected so
     * tests can drive the self-speech tail deterministically.
     */
    private val clock: () -> Long = System::currentTimeMillis,
    /** PRD §5.1: whispers are spoken fast. The user is a TTS power user (PRD §3). */
    private val whisperRate: Float = DEFAULT_WHISPER_RATE,
    private val briefingRate: Float = DEFAULT_BRIEFING_RATE,
    /** A whisper older than this is dropped instead of waiting for a gap. */
    private val whisperTtlMs: Long = DEFAULT_WHISPER_TTL_MS,
    /** Floor on the interval between whispers, to protect the interruption budget. */
    private val minWhisperIntervalMs: Long = DEFAULT_MIN_WHISPER_INTERVAL_MS,
    /**
     * How long after Cue's own speech ends before the mic is trusted again. The open-ear
     * speaker sits next to the mic, so Cue hears itself; without this the gap detector reads
     * Cue's own whisper as a human talking.
     */
    private val selfSpeechTailMs: Long = DEFAULT_SELF_SPEECH_TAIL_MS,
) {
    /** Live counters for the dev overlay and the PRD §15 metrics. */
    data class Stats(
        val whispersSpoken: Int = 0,
        val whispersDroppedStale: Int = 0,
        val whispersDroppedDuplicate: Int = 0,
        val whispersDroppedRateLimited: Int = 0,
        val whispersAborted: Int = 0,
        val briefingsSpoken: Int = 0,
        val earconsPlayed: Int = 0,
        /**
         * PRD §15 headline metric: whispers that began while a human was speaking. This
         * should be structurally impossible, since a whisper only starts inside an open gap
         * and a gap requires silence. It is counted anyway, as an assertion against the
         * design rather than a statistic — anything above zero is a bug, not a tuning issue.
         */
        val interruptions: Int = 0,
        /**
         * Briefings that began over speech. Tracked separately because these are legitimate:
         * the user pressed a button and asked for it. Not part of the headline metric.
         */
        val briefingsOverSpeech: Int = 0,
    )

    private data class PendingWhisper(
        val text: String,
        val createdAtMs: Long,
        val dedupeKey: String?,
    )

    private val lock = Any()

    private var pending: PendingWhisper? = null
    private var lastWhisperKey: String? = null

    /**
     * Null until the first whisper of the session.
     *
     * Deliberately nullable rather than 0: with a zero sentinel the rate limiter measures
     * against the epoch and silently swallows the first whisper of every session, which is
     * the one most likely to be on stage.
     */
    private var lastWhisperAtMs: Long? = null
    private var speakingTier: Int? = null
    private var selfSpeechEndedAtMs = 0L
    private var utteranceCounter = 0L

    /** Last thing Cue actually said, for repeat-last (PRD P0-6). Never regenerated. */
    private var lastSpokenText: String? = null
    private var lastSpokenRate: Float = DEFAULT_WHISPER_RATE

    @Volatile
    var stats = Stats()
        private set

    /** True while Cue's own audio is playing, plus a short tail. Gate mic input on this. */
    fun isSelfSpeaking(nowMs: Long): Boolean = synchronized(lock) {
        speakingTier != null || nowMs - selfSpeechEndedAtMs < selfSpeechTailMs
    }

    /** Tier 0. Immediate and unconditional. */
    fun emitEarcon(earcon: Earcon) {
        earcons.play(earcon)
        synchronized(lock) { stats = stats.copy(earconsPlayed = stats.earconsPlayed + 1) }
    }

    /**
     * Tier 1. Offers a whisper for the next gap.
     *
     * Only one whisper is ever pending: a newer offer replaces an older one, because the
     * newer fact is the more relevant one by definition.
     *
     * @param dedupeKey suppresses a consecutive repeat, e.g. the same speaker name twice.
     */
    fun offerWhisper(text: String, nowMs: Long, dedupeKey: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            if (dedupeKey != null && dedupeKey == lastWhisperKey) {
                stats = stats.copy(whispersDroppedDuplicate = stats.whispersDroppedDuplicate + 1)
                return
            }
            pending = PendingWhisper(trimmed, nowMs, dedupeKey)
        }
    }

    /**
     * Tier 2. User-initiated, so it starts now and outranks any pending or playing whisper.
     */
    fun speakBriefing(text: String, nowMs: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            pending = null
            val overSpeech = gapDetector.isSpeaking
            if (speakingTier != null) speaker.stop()
            speakingTier = TIER_BRIEFING
            lastSpokenText = trimmed
            lastSpokenRate = briefingRate
            stats = stats.copy(
                briefingsSpoken = stats.briefingsSpoken + 1,
                briefingsOverSpeech = stats.briefingsOverSpeech + if (overSpeech) 1 else 0,
            )
        }
        startSpeaking(trimmed, briefingRate, nowMs)
    }

    /**
     * Drives the whisper queue. Call on a timer (roughly every 100ms).
     *
     * Everything that decides whether a whisper is allowed to happen lives here.
     */
    fun pump(nowMs: Long) {
        val toSpeak: PendingWhisper = synchronized(lock) {
            val candidate = pending ?: return
            if (speakingTier != null) return

            if (nowMs - candidate.createdAtMs > whisperTtlMs) {
                pending = null
                stats = stats.copy(whispersDroppedStale = stats.whispersDroppedStale + 1)
                return
            }
            if (!gapDetector.isGapOpen(nowMs)) return
            val since = lastWhisperAtMs
            if (since != null && nowMs - since < minWhisperIntervalMs) {
                // Not dropped yet — it may still fit inside its TTL once the floor clears.
                return
            }

            pending = null
            lastWhisperKey = candidate.dedupeKey
            lastWhisperAtMs = nowMs
            speakingTier = TIER_WHISPER
            lastSpokenText = candidate.text
            lastSpokenRate = whisperRate
            stats = stats.copy(
                whispersSpoken = stats.whispersSpoken + 1,
                // Assertion, not a statistic: an open gap implies silence, so this branch
                // is unreachable unless the gap contract has been broken somewhere.
                interruptions = stats.interruptions + if (gapDetector.isSpeaking) 1 else 0,
            )
            candidate
        }
        startSpeaking(toSpeak.text, whisperRate, nowMs)
    }

    /**
     * Forward every transition from the gap detector.
     *
     * On [GapDetector.Transition.SPEECH_STARTED] an in-flight whisper is cut mid-word. That
     * is the correct behaviour: a human started talking, and finishing the word costs more
     * than losing it. Briefings survive, because the user asked for them.
     */
    fun onSpeechTransition(transition: GapDetector.Transition, nowMs: Long) {
        if (transition != GapDetector.Transition.SPEECH_STARTED) return
        val abort = synchronized(lock) {
            if (speakingTier == TIER_WHISPER) {
                stats = stats.copy(whispersAborted = stats.whispersAborted + 1)
                true
            } else {
                // Also drop anything queued: the gap it was waiting for has closed.
                if (pending != null) {
                    pending = null
                    stats = stats.copy(whispersDroppedStale = stats.whispersDroppedStale + 1)
                }
                false
            }
        }
        if (abort) speaker.stop()
    }

    /**
     * Pause button: stop talking right now (PRD §5.3).
     *
     * A blind user who has heard enough of a briefing has no other way to stop it, so this
     * is the difference between a tool that respects their time and one that lectures them.
     */
    fun interrupt() {
        val wasSpeaking = synchronized(lock) {
            pending = null
            speakingTier != null
        }
        if (wasSpeaking) speaker.stop()
    }

    /**
     * P0-6 repeat-last. Replays from cache and never regenerates.
     *
     * @return false when there is nothing to repeat, so the caller can play [Earcon.FAILED].
     */
    fun repeatLast(nowMs: Long): Boolean {
        val text: String
        val rate: Float
        synchronized(lock) {
            text = lastSpokenText ?: return false
            rate = lastSpokenRate
            if (speakingTier != null) speaker.stop()
            speakingTier = TIER_BRIEFING
        }
        startSpeaking(text, rate, nowMs)
        return true
    }

    /** Session end. Clears cached speech so nothing survives the glasses coming off. */
    fun reset() {
        val wasSpeaking = synchronized(lock) {
            val speaking = speakingTier != null
            pending = null
            lastWhisperKey = null
            lastWhisperAtMs = null
            lastSpokenText = null
            speakingTier = null
            speaking
        }
        if (wasSpeaking) speaker.stop()
    }

    private fun startSpeaking(text: String, rate: Float, nowMs: Long) {
        val id = synchronized(lock) { "cue-${++utteranceCounter}" }
        speaker.speak(text, rate, id) {
            // Completion arrives asynchronously, so the finish time comes from the clock
            // rather than from `nowMs`, which is when the utterance *started*.
            val finishedAt = clock()
            synchronized(lock) {
                speakingTier = null
                selfSpeechEndedAtMs = maxOf(selfSpeechEndedAtMs, finishedAt)
            }
        }
    }

    /** Records when Cue's own audio finished, so the mic can be trusted again. */
    fun onSelfSpeechFinished(nowMs: Long) {
        synchronized(lock) { selfSpeechEndedAtMs = nowMs }
    }

    companion object {
        private const val TIER_WHISPER = 1
        private const val TIER_BRIEFING = 2

        const val DEFAULT_WHISPER_RATE = 1.5f
        const val DEFAULT_BRIEFING_RATE = 1.35f
        const val DEFAULT_WHISPER_TTL_MS = 3_000L
        const val DEFAULT_MIN_WHISPER_INTERVAL_MS = 1_500L
        const val DEFAULT_SELF_SPEECH_TAIL_MS = 250L
    }
}
