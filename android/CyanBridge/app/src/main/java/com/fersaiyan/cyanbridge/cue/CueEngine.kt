package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.audio.GapDetector
import com.fersaiyan.cyanbridge.cue.context.ConversationContextStore
import com.fersaiyan.cyanbridge.cue.context.RollCallExtractor
import com.fersaiyan.cyanbridge.cue.output.Earcon
import com.fersaiyan.cyanbridge.cue.output.OutputArbiter

/**
 * The decision layer: turns speech events into the cheapest output that carries them.
 *
 * Everything above this (speech-to-text, Bluetooth notify frames, the TTS engine) is
 * platform glue. Everything below it (context store, gap detector, arbiter) is mechanism.
 * This class holds the product behaviour, which is why it is deliberately free of Android
 * imports and takes `nowMs` everywhere — all of it is testable off-device.
 */
class CueEngine(
    private val store: ConversationContextStore,
    private val arbiter: OutputArbiter,
    private val gapDetector: GapDetector,
    private val photoQuestion: PhotoQuestionRunner? = null,
    private val config: Config = Config(),
) {
    data class Config(
        /** Whisper the name of someone who just arrived, on top of the Tier 0 earcon. */
        val whisperNameOnEntry: Boolean = true,
        /**
         * Announce a name the moment roll call resolves it.
         *
         * On, because a self-introduction resolves the name *after* the entry earcon has
         * already fired. Without this the user hears "someone arrived" and then never learns
         * who, until that person happens to speak a second time — which is precisely the
         * entrance moment in the demo script. Costs exactly one whisper per person per
         * session, which is a good use of the interruption budget.
         */
        val announceNewlyResolvedNames: Boolean = true,
    )

    /** The photo path (PRD P0-5). Implemented on the Android side; injected so this stays pure. */
    interface PhotoQuestionRunner {
        /**
         * Captures a thumbnail and answers a question about it using [context].
         *
         * @return false when the glasses rejected the command because they were busy, so the
         *   engine can play the busy earcon. Never queue — see PRD §7.5.
         */
        fun requestPhotoAnswer(context: ConversationContext, nowMs: Long): Boolean
    }

    /**
     * The diarization label belonging to the wearer, when known.
     *
     * The wearer's own voice is diarized like anybody else's, so without this Cue whispers
     * the user's name back at them every time they speak. Identifying the label is a
     * hardware question (the head-mounted mic makes the wearer consistently the loudest
     * speaker), which is why it is set from outside rather than guessed here.
     */
    @Volatile
    var wearerLabel: String? = null

    private var lastAttributedLabel: String? = null

    /**
     * Feeds one RMS sample from the live mic.
     *
     * Samples are dropped while Cue is talking: the open-ear speaker sits beside the mic, so
     * without this the detector hears Cue's own whisper and concludes a human is speaking,
     * which would wedge the gap closed for as long as Cue keeps talking.
     */
    fun onRms(rmsDb: Float, nowMs: Long) {
        if (arbiter.isSelfSpeaking(nowMs)) return
        val transition = gapDetector.onRms(rmsDb, nowMs)
        arbiter.onSpeechTransition(transition, nowMs)
    }

    /**
     * A new speaker turn began (PRD P0-1).
     *
     * Called from the interim streaming result rather than the final one, because the budget
     * is 800ms from turn start and waiting for a finalized transcript blows it.
     */
    fun onSpeakerTurnStarted(speakerLabel: String, nowMs: Long) {
        if (speakerLabel == wearerLabel) {
            store.markUserSpoke(nowMs)
            lastAttributedLabel = speakerLabel
            return
        }
        if (speakerLabel == lastAttributedLabel) return
        lastAttributedLabel = speakerLabel

        val person = store.snapshot().personFor(speakerLabel)
        // An unknown speaker is announced by the presence path instead, once their first
        // transcript arrives and the roster learns about them.
        if (person != null) {
            arbiter.offerWhisper(person.spokenLabel, nowMs, dedupeKey = speakerLabel)
        }
    }

    /**
     * A finalized transcript segment. Updates the roster, runs passive roll call, and emits
     * presence output.
     */
    fun onTranscript(speakerLabel: String, text: String, startMs: Long, endMs: Long) {
        val events = store.onTurn(speakerLabel, text, startMs, endMs)
        events.forEach { handleRosterEvent(it, endMs) }

        if (speakerLabel == wearerLabel) {
            store.markUserSpoke(endMs)
            return
        }

        runRollCall(endMs)
    }

    /** Departure sweep plus the arbiter's whisper queue. Call roughly every 100ms. */
    fun pump(nowMs: Long) {
        store.tick(nowMs).forEach { handleRosterEvent(it, nowMs) }
        arbiter.pump(nowMs)
    }

    /**
     * Pause button (PRD §5.3).
     *
     * One button, two jobs, resolved by state: if Cue is mid-sentence the press means stop,
     * otherwise it means tell me who is here. A user who has heard enough has no other way
     * to cut it off, so interrupting has to win when both readings apply.
     */
    fun onPausePressed(nowMs: Long) {
        if (arbiter.isSelfSpeaking(nowMs)) {
            arbiter.interrupt()
            return
        }
        arbiter.speakBriefing(RosterBriefing.format(store.snapshot()), nowMs)
    }

    /** Volume button: repeat the last thing said, from cache, never regenerated (PRD P0-6). */
    fun onRepeatPressed(nowMs: Long) {
        if (!arbiter.repeatLast(nowMs)) {
            arbiter.emitEarcon(Earcon.FAILED)
        }
    }

    /**
     * AI photo button (PRD P0-5).
     *
     * The working earcon covers the thumbnail latency, which is unmeasured until Spike A.
     * A rejection plays the busy earcon and is dropped rather than queued.
     */
    fun onPhotoQuestionPressed(nowMs: Long) {
        val runner = photoQuestion ?: run {
            arbiter.emitEarcon(Earcon.FAILED)
            return
        }
        arbiter.emitEarcon(Earcon.WORKING)
        val accepted = runner.requestPhotoAnswer(store.snapshot(), nowMs)
        if (!accepted) {
            arbiter.emitEarcon(Earcon.DEVICE_BUSY)
        }
    }

    /** Speaks a model answer. Tier 2, because the user asked for it. */
    fun onPhotoAnswer(text: String, nowMs: Long) {
        arbiter.speakBriefing(text, nowMs)
    }

    fun onPhotoFailed(nowMs: Long) {
        arbiter.emitEarcon(Earcon.FAILED)
    }

    fun onDeviceBusy() {
        arbiter.emitEarcon(Earcon.DEVICE_BUSY)
    }

    /**
     * The glasses disconnected.
     *
     * The user must be told. Silence is indistinguishable from an empty room, and a blind
     * user acting on that ambiguity is the most dangerous failure this product has.
     */
    fun onGlassesLost() {
        arbiter.emitEarcon(Earcon.GLASSES_LOST)
    }

    /** Session end: glasses came off. Clears the roster and every cached utterance. */
    fun endSession() {
        store.reset()
        arbiter.reset()
        gapDetector.reset()
        lastAttributedLabel = null
        wearerLabel = null
    }

    fun snapshot(): ConversationContext = store.snapshot()

    private fun handleRosterEvent(event: ConversationContextStore.Event, nowMs: Long) {
        if (event.person.speakerLabel == wearerLabel) return

        when (event) {
            is ConversationContextStore.Event.PersonEntered -> {
                arbiter.emitEarcon(Earcon.PERSON_ENTERED)
                if (config.whisperNameOnEntry && event.person.isNamed) {
                    arbiter.offerWhisper(
                        event.person.spokenLabel,
                        nowMs,
                        dedupeKey = event.person.speakerLabel,
                    )
                }
            }

            is ConversationContextStore.Event.PersonLeft -> {
                arbiter.emitEarcon(Earcon.PERSON_LEFT)
            }

            is ConversationContextStore.Event.PersonNamed -> {
                if (config.announceNewlyResolvedNames) {
                    arbiter.offerWhisper(
                        event.person.spokenLabel,
                        nowMs,
                        dedupeKey = "named-${event.person.speakerLabel}",
                    )
                }
            }
        }
    }

    /**
     * Passive enrollment over the current transcript window (PRD P0-2).
     *
     * Only unnamed speakers are considered, so a resolved name is never overwritten by a
     * later sentence that happens to contain a different one — "have you met Priya?" must
     * not rename the person saying it.
     */
    private fun runRollCall(nowMs: Long) {
        val snapshot = store.snapshot()
        val unnamed = snapshot.roster.filterNot { it.isNamed }.map { it.speakerLabel }.toSet()
        if (unnamed.isEmpty()) return

        RollCallExtractor.extract(snapshot.turns)
            .filter { it.speakerLabel in unnamed }
            .forEach { binding ->
                store.bindName(binding.speakerLabel, binding.name)
                    ?.let { handleRosterEvent(it, nowMs) }
            }
    }
}
