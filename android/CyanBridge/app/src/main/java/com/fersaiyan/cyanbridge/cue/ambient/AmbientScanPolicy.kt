package com.fersaiyan.cyanbridge.cue.ambient

/**
 * Decides when a surroundings update is worth saying out loud.
 *
 * The glasses can look around every few seconds, but a device that narrates the room on a
 * timer is the failure PRD §4 names outright: "If it describes the room continuously, it has
 * failed." The camera loop is cheap; the user's attention is not. This class is the valve
 * between them.
 *
 * The rule it enforces is that **novelty earns speech, repetition earns silence**. A doorway
 * that was there four seconds ago is not news. Something new and close is.
 *
 * A safety note that shapes every phrase here: this is supplementary awareness, not mobility
 * guidance. The PRD lists navigation as a non-goal for good reason — a cane reports the
 * ground truthfully and instantly, and a camera on a two-second loop does not. So the policy
 * never emits an instruction ("stop", "turn left"); it reports what it saw and lets the
 * person decide. Being late or wrong is survivable when the output is an observation and
 * dangerous when it is a command.
 */
class AmbientScanPolicy(
    /** Nothing routine is spoken more often than this. */
    private val routineCooldownMs: Long = DEFAULT_ROUTINE_COOLDOWN_MS,
    /** Cautions may interrupt more often than routine updates, but not endlessly. */
    private val cautionCooldownMs: Long = DEFAULT_CAUTION_COOLDOWN_MS,
    /** Even an urgent hazard is not repeated inside this window. */
    private val urgentRepeatMs: Long = DEFAULT_URGENT_REPEAT_MS,
) {
    enum class Severity {
        /** Worth knowing eventually. A bench, a doorway. */
        INFO,

        /** Worth knowing soon. Something in the path. */
        CAUTION,

        /** Worth knowing now. Close and in the way. */
        URGENT,
    }

    data class Hazard(
        val label: String,
        val severity: Severity,
        /** "ahead", "on your left". Null when the vision backend cannot place it. */
        val bearing: String? = null,
    ) {
        /** Identity for repeat-suppression: the same thing in the same place is the same news. */
        val key: String get() = "${label.lowercase()}|${bearing?.lowercase().orEmpty()}"
    }

    data class Observation(
        val summary: String?,
        val hazards: List<Hazard> = emptyList(),
        val atMs: Long,
    )

    data class Announcement(
        val speech: String,
        val severity: Severity,
        /** Urgent items are worth an earcon before the words. */
        val leadWithAlert: Boolean,
    )

    private var lastSpokenAtMs: Long? = null
    private var lastSummary: String? = null
    private val lastHazardAtMs = mutableMapOf<String, Long>()

    /**
     * @return what to say, or null to stay quiet — which is the common and correct outcome.
     */
    fun consider(observation: Observation): Announcement? {
        val now = observation.atMs

        // Urgent first, and on its own. Burying "step down, ahead" inside a sentence about
        // the room is how a useful warning becomes noise.
        observation.hazards
            .filter { it.severity == Severity.URGENT }
            .firstOrNull { isFresh(it, now, urgentRepeatMs) }
            ?.let { hazard ->
                record(hazard, now)
                return Announcement(
                    speech = phrase(hazard),
                    severity = Severity.URGENT,
                    leadWithAlert = true,
                )
            }

        observation.hazards
            .filter { it.severity == Severity.CAUTION }
            .firstOrNull { isFresh(it, now, cautionCooldownMs) }
            ?.let { hazard ->
                if (!cooldownElapsed(now, cautionCooldownMs)) return null
                record(hazard, now)
                return Announcement(
                    speech = phrase(hazard),
                    severity = Severity.CAUTION,
                    leadWithAlert = false,
                )
            }

        // Routine scene updates are the chattiest category and the least urgent, so they
        // have to clear both the clock and the novelty test.
        val summary = observation.summary?.trim().orEmpty()
        if (summary.isEmpty()) return null
        if (!cooldownElapsed(now, routineCooldownMs)) return null
        if (!isMateriallyDifferent(summary)) return null

        lastSummary = summary
        lastSpokenAtMs = now
        return Announcement(summary, Severity.INFO, leadWithAlert = false)
    }

    /**
     * Answers "what's around me" on demand.
     *
     * Bypasses every cooldown: the user asked, so the interruption budget does not apply, and
     * it does not update the ambient timers — an explicit question should not make the
     * background quieter afterwards.
     */
    fun describeOnDemand(observation: Observation): String {
        val hazards = observation.hazards
            .sortedByDescending { it.severity.ordinal }
            .take(MAX_ON_DEMAND_HAZARDS)
        val summary = observation.summary?.trim().orEmpty()

        return when {
            hazards.isEmpty() && summary.isEmpty() -> "I can't see anything clearly right now"
            hazards.isEmpty() -> summary
            summary.isEmpty() -> hazards.joinToString(". ") { phrase(it) }
            else -> summary + ". " + hazards.joinToString(". ") { phrase(it) }
        }
    }

    fun reset() {
        lastSpokenAtMs = null
        lastSummary = null
        lastHazardAtMs.clear()
    }

    private fun isFresh(hazard: Hazard, now: Long, window: Long): Boolean {
        val last = lastHazardAtMs[hazard.key] ?: return true
        return now - last >= window
    }

    private fun record(hazard: Hazard, now: Long) {
        lastHazardAtMs[hazard.key] = now
        lastSpokenAtMs = now
    }

    private fun cooldownElapsed(now: Long, window: Long): Boolean {
        val last = lastSpokenAtMs ?: return true
        return now - last >= window
    }

    /**
     * Cheap novelty test.
     *
     * Word overlap rather than string equality, because a vision model rephrases the same
     * scene constantly — "a hallway with a door" and "a door in a hallway" are the same
     * news, and reading both aloud is exactly the narration the PRD forbids.
     */
    private fun isMateriallyDifferent(summary: String): Boolean {
        val previous = lastSummary ?: return true
        val a = tokens(previous)
        val b = tokens(summary)
        if (a.isEmpty() || b.isEmpty()) return true
        val shared = a.intersect(b).size.toFloat()
        val overlap = shared / maxOf(a.size, b.size).toFloat()
        return overlap < NOVELTY_THRESHOLD
    }

    private fun tokens(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()

    /**
     * Phrases a hazard as an observation, never an instruction.
     *
     * "Step down, ahead" tells the person what is there and leaves the decision with them.
     * "Stop" would be an instruction issued by a device that may be two seconds stale, and a
     * blind pedestrian who learns to obey it has been handed a liability, not an aid.
     */
    private fun phrase(hazard: Hazard): String {
        val where = hazard.bearing?.takeIf { it.isNotBlank() }
        return if (where != null) "${hazard.label}, $where" else hazard.label
    }

    private companion object {
        const val DEFAULT_ROUTINE_COOLDOWN_MS = 45_000L
        const val DEFAULT_CAUTION_COOLDOWN_MS = 12_000L
        const val DEFAULT_URGENT_REPEAT_MS = 4_000L
        const val NOVELTY_THRESHOLD = 0.6f
        const val MAX_ON_DEMAND_HAZARDS = 3

        val STOP_WORDS = setOf(
            "the", "and", "with", "there", "that", "this", "from", "into", "near", "some",
            "your", "you", "for", "are", "was", "has", "have", "its", "it's",
        )
    }
}
