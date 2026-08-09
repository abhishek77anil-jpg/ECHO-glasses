package com.fersaiyan.cyanbridge.cue.context

import com.fersaiyan.cyanbridge.cue.ConversationContext
import com.fersaiyan.cyanbridge.cue.PhotoContext
import com.fersaiyan.cyanbridge.cue.Person
import com.fersaiyan.cyanbridge.cue.Turn

/**
 * Rolling conversation state (PRD §8).
 *
 * Single source of truth for the roster and the transcript window. Deliberately free of
 * Android and coroutine dependencies: every method takes `nowMs`, so the whole thing is
 * unit-testable with a fake clock.
 *
 * Thread confinement: all mutating calls are synchronized. Callers arrive from the STT
 * callback thread and the UI thread, so this is not optional.
 */
class ConversationContextStore(
    private val sessionStartMs: Long,
    /**
     * How long a known voice must stay silent before Cue treats them as gone (PRD P0-3).
     *
     * This is a genuine false-positive/latency tradeoff and there is no correct value: too
     * short and a quiet listener is announced as having left, too long and the departure
     * earcon lands after it stopped being useful. 45s is a demo-friendly starting point.
     * Tune it against a real conversation (PRD Hours 10-16).
     */
    private val departureSilenceMs: Long = DEFAULT_DEPARTURE_SILENCE_MS,
    private val retentionMs: Long = ConversationContext.TRANSCRIPT_RETENTION_MS,
) {
    /** Emitted as the roster changes. Consumed by the arbiter to pick an earcon plus whisper. */
    sealed interface Event {
        val person: Person

        data class PersonEntered(override val person: Person) : Event
        data class PersonLeft(override val person: Person) : Event
        data class PersonNamed(override val person: Person) : Event
    }

    private val lock = Any()
    private val roster = LinkedHashMap<String, Person>()
    private val turns = ArrayDeque<Turn>()
    private var lastPhoto: PhotoContext? = null
    private var pendingQuestion = false
    private var userLastSpokeMs = 0L

    /**
     * Records a completed utterance.
     *
     * Returns any roster events it caused. Returning rather than emitting keeps the store
     * free of callback plumbing and lets the caller decide what to do on the right thread.
     */
    fun onTurn(speakerLabel: String, text: String, startMs: Long, endMs: Long): List<Event> =
        synchronized(lock) {
            val events = mutableListOf<Event>()
            val existing = roster[speakerLabel]

            if (existing == null) {
                val person = Person(
                    speakerLabel = speakerLabel,
                    name = null,
                    firstHeardMs = startMs,
                    lastHeardMs = endMs,
                )
                roster[speakerLabel] = person
                events += Event.PersonEntered(person)
            } else {
                val returning = !existing.isPresent
                val updated = existing.copy(lastHeardMs = endMs, isPresent = true)
                roster[speakerLabel] = updated
                if (returning) events += Event.PersonEntered(updated)
            }

            turns.addLast(Turn(speakerLabel, text, startMs, endMs))
            pruneTurns(endMs)
            events
        }

    /**
     * Departure sweep. Call on a timer; the store has no clock of its own.
     *
     * Only people who have been heard at least once can leave, and a person is only marked
     * gone once — the event does not repeat while they stay silent.
     */
    fun tick(nowMs: Long): List<Event> = synchronized(lock) {
        val events = mutableListOf<Event>()
        roster.entries.forEach { entry ->
            val person = entry.value
            if (person.isPresent && nowMs - person.lastHeardMs >= departureSilenceMs) {
                val updated = person.copy(isPresent = false)
                entry.setValue(updated)
                events += Event.PersonLeft(updated)
            }
        }
        pruneTurns(nowMs)
        events
    }

    /**
     * Binds a diarization label to a real name (PRD P0-2).
     *
     * No-op when the label is unknown or already carries that name, so repeated roll-call
     * passes over an overlapping transcript window stay idempotent.
     */
    fun bindName(speakerLabel: String, name: String): Event? = synchronized(lock) {
        val existing = roster[speakerLabel] ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty() || existing.name == trimmed) return null
        val updated = existing.copy(name = trimmed)
        roster[speakerLabel] = updated
        Event.PersonNamed(updated)
    }

    fun setPhoto(photo: PhotoContext) = synchronized(lock) { lastPhoto = photo }

    fun setPendingQuestion(pending: Boolean) = synchronized(lock) { pendingQuestion = pending }

    fun markUserSpoke(nowMs: Long) = synchronized(lock) { userLastSpokeMs = nowMs }

    fun snapshot(): ConversationContext = synchronized(lock) {
        ConversationContext(
            sessionStartMs = sessionStartMs,
            roster = roster.values.toList(),
            turns = turns.toList(),
            lastPhoto = lastPhoto,
            pendingQuestion = pendingQuestion,
            userLastSpokeMs = userLastSpokeMs,
        )
    }

    /** Session end: glasses came off. Clears everything (PRD §5.4 and §10). */
    fun reset() = synchronized(lock) {
        roster.clear()
        turns.clear()
        lastPhoto = null
        pendingQuestion = false
        userLastSpokeMs = 0L
    }

    /**
     * Drops transcript older than the retention window.
     *
     * This is the privacy guarantee in PRD §10 expressed as code — the rolling buffer is
     * discarded continuously rather than swept later, so there is no window in which more
     * than [retentionMs] of conversation exists in memory.
     */
    private fun pruneTurns(nowMs: Long) {
        while (turns.isNotEmpty() && nowMs - turns.first().endMs > retentionMs) {
            turns.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_DEPARTURE_SILENCE_MS = 45_000L
    }
}
