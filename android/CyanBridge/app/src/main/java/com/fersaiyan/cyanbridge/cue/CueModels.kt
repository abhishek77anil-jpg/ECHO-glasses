package com.fersaiyan.cyanbridge.cue

/**
 * Core state for Cue (PRD §8).
 *
 * Everything here is plain Kotlin with no Android dependencies so the spine can be
 * unit-tested on the JVM without hardware. Time is always passed in as a parameter
 * rather than read from the clock, for the same reason.
 */

/** A person Cue has heard in this session. */
data class Person(
    /** Diarization label from the STT provider, e.g. "speaker_0". Stable within a session. */
    val speakerLabel: String,
    /** Resolved human name, or null while still "someone new". */
    val name: String? = null,
    val firstHeardMs: Long,
    val lastHeardMs: Long,
    val isPresent: Boolean = true,
) {
    /** What Cue actually says out loud for this person. Never a description (PRD §8). */
    val spokenLabel: String get() = name ?: "someone new"

    /**
     * How this person is written into a model prompt.
     *
     * Deliberately not [spokenLabel]: every unnamed person shares the phrase "someone new",
     * so using it in a transcript would collapse two strangers into one voice and the model
     * would answer about the wrong person. The raw diarization label is ugly but unique,
     * and uniqueness is what the prompt actually needs.
     */
    val promptLabel: String get() = name ?: speakerLabel

    val isNamed: Boolean get() = name != null
}

/** One utterance from one speaker. */
data class Turn(
    val speakerLabel: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/** The most recent photo captured through the AI photo path (PRD §7.4). */
data class PhotoContext(
    val thumbnail: ByteArray,
    val capturedAtMs: Long,
    val caption: String? = null,
) {
    // ByteArray needs explicit equals/hashCode to keep data-class semantics sane.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PhotoContext) return false
        return capturedAtMs == other.capturedAtMs &&
            caption == other.caption &&
            thumbnail.contentEquals(other.thumbnail)
    }

    override fun hashCode(): Int {
        var result = thumbnail.contentHashCode()
        result = 31 * result + capturedAtMs.hashCode()
        result = 31 * result + (caption?.hashCode() ?: 0)
        return result
    }
}

/**
 * Immutable snapshot of the rolling conversation state.
 *
 * Produced by [com.fersaiyan.cyanbridge.cue.context.ConversationContextStore]; consumed by
 * prompt builders and the briefing generator.
 */
data class ConversationContext(
    val sessionStartMs: Long,
    val roster: List<Person> = emptyList(),
    val turns: List<Turn> = emptyList(),
    val lastPhoto: PhotoContext? = null,
    val pendingQuestion: Boolean = false,
    val userLastSpokeMs: Long = 0L,
) {
    val presentPeople: List<Person> get() = roster.filter { it.isPresent }

    val namedPresentPeople: List<Person> get() = presentPeople.filter { it.isNamed }

    fun personFor(speakerLabel: String): Person? = roster.firstOrNull { it.speakerLabel == speakerLabel }

    /** Transcript text for the last [windowMs], oldest first. Used for prompt context (PRD §8). */
    fun transcriptWindow(nowMs: Long, windowMs: Long = TRANSCRIPT_PROMPT_WINDOW_MS): List<Turn> =
        turns.filter { nowMs - it.endMs <= windowMs }

    /** Renders the transcript window as "Name: text" lines for a model prompt. */
    fun transcriptWindowText(nowMs: Long, windowMs: Long = TRANSCRIPT_PROMPT_WINDOW_MS): String =
        transcriptWindow(nowMs, windowMs).joinToString("\n") { turn ->
            val who = personFor(turn.speakerLabel)?.promptLabel ?: turn.speakerLabel
            "$who: ${turn.text}"
        }

    companion object {
        /** PRD §8: photo prompts carry the last 30 seconds of transcript. */
        const val TRANSCRIPT_PROMPT_WINDOW_MS = 30_000L

        /** PRD §8/§10: the rolling buffer keeps 60 seconds and discards continuously. */
        const val TRANSCRIPT_RETENTION_MS = 60_000L
    }
}
