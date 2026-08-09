package com.fersaiyan.cyanbridge.cue.context

import com.fersaiyan.cyanbridge.cue.Turn

/**
 * Passive roll call (PRD P0-2): binds diarization labels to names by noticing the ordinary
 * human ritual of introducing yourself. No setup screen, no training step, no trigger.
 *
 * Two layers, and the local one exists on purpose:
 *
 * 1. **This class.** Pattern matching over the transcript. Instant, offline, free, and it
 *    catches the overwhelming majority of real self-introductions. It is what keeps the
 *    demo working when the venue Wi-Fi does not.
 * 2. **[RollCallResolver].** A model pass over the same window for everything phrasing
 *    tricks can hide. Slower and needs the network.
 *
 * The failure mode is deliberately asymmetric. A missed name leaves someone as "someone
 * new", which is still useful. A wrong name is spoken confidently into the user's ear and
 * they act on it. So the matcher is strict, and when it is unsure it declines.
 */
object RollCallExtractor {

    data class Binding(
        val speakerLabel: String,
        val name: String,
        val confidence: Confidence,
    )

    enum class Confidence {
        /** An explicit self-introduction: "my name is Sarah". */
        HIGH,

        /** A weaker form that is usually but not always self-referential: "this is Sarah". */
        MEDIUM,
    }

    /**
     * Words that follow "I'm" far more often than any name does.
     *
     * Speech-to-text capitalization is unreliable, so capitalization alone cannot carry the
     * decision. This list is the second gate. It is not exhaustive and does not need to be:
     * anything that slips past still has to look like a name, and anything rejected here can
     * still be recovered by the model pass.
     */
    private val NOT_NAMES = setOf(
        "a", "about", "afraid", "after", "all", "almost", "already", "alright", "always",
        "an", "and", "annoyed", "any", "anyway", "asking", "at", "available", "away",
        "back", "bad", "been", "before", "behind", "being", "better", "busy", "but",
        "certain", "clear", "close", "cold", "coming", "confused", "curious",
        "different", "doing", "done", "down", "excited", "familiar", "fine", "finished",
        "first", "free", "from", "full", "getting", "glad", "going", "gonna", "good",
        "grateful", "great", "guessing", "guilty", "happy", "hard", "having", "he",
        "hearing", "here", "home", "hoping", "hot", "hungry", "in", "interested", "into",
        "it", "just", "kidding", "kind", "last", "late", "leaving", "listening", "looking",
        "lost", "making", "maybe", "more", "most", "moving", "much", "my", "near", "new",
        "next", "nervous", "no", "not", "now", "of", "off", "okay", "on", "one", "only",
        "open", "or", "other", "out", "over", "playing", "pretty", "quite", "read", "ready",
        "really", "right", "running", "sad", "same", "saying", "scared", "seeing", "she",
        "sitting", "so", "some", "sorry", "speaking", "standing", "starting", "still",
        "stuck", "supposed", "sure", "surprised", "taking", "talking", "telling", "that",
        "the", "there", "they", "thinking", "this", "thrilled", "tired", "to", "together",
        "too", "trying", "under", "until", "up", "used", "using", "very", "waiting",
        "walking", "wanting", "watching", "we", "well", "what", "when", "where", "which",
        "who", "why", "with", "wondering", "working", "worried", "wrong", "yes", "you",
        "your",
    )

    /**
     * Each pattern captures one or two trailing words. Two allows "Sarah Chen"; more would
     * start swallowing sentences.
     */
    private val NAME_PATTERN = "([A-Za-z][A-Za-z'\\-]{1,20}(?:\\s+[A-Z][A-Za-z'\\-]{1,20})?)"

    private val HIGH_CONFIDENCE_PATTERNS = listOf(
        Regex("""\bmy name(?:'s| is)\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
        Regex("""\bi'?m\s+called\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
        Regex("""\bcall me\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
        Regex("""\bi'?m\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
        Regex("""\bi am\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
    )

    private val MEDIUM_CONFIDENCE_PATTERNS = listOf(
        Regex("""\bthis is\s+$NAME_PATTERN""", RegexOption.IGNORE_CASE),
        Regex("""^\s*$NAME_PATTERN\s+here\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * Scans a transcript window for self-introductions.
     *
     * Returns at most one binding per speaker — the highest-confidence, most recent one, so
     * a later correction ("actually it's Sara, no H") wins over an earlier mishearing.
     */
    fun extract(turns: List<Turn>): List<Binding> {
        val best = LinkedHashMap<String, Binding>()

        turns.forEach { turn ->
            val binding = extractFromText(turn.speakerLabel, turn.text) ?: return@forEach
            val existing = best[turn.speakerLabel]
            // Later turns overwrite earlier ones at equal-or-better confidence.
            if (existing == null || binding.confidence.ordinal <= existing.confidence.ordinal) {
                best[turn.speakerLabel] = binding
            }
        }

        return best.values.toList()
    }

    /** Extracts a binding from a single utterance, or null when nothing looks like a name. */
    fun extractFromText(speakerLabel: String, text: String): Binding? {
        HIGH_CONFIDENCE_PATTERNS.forEach { pattern ->
            matchName(pattern, text)?.let {
                return Binding(speakerLabel, it, Confidence.HIGH)
            }
        }
        MEDIUM_CONFIDENCE_PATTERNS.forEach { pattern ->
            matchName(pattern, text)?.let {
                return Binding(speakerLabel, it, Confidence.MEDIUM)
            }
        }
        return null
    }

    private fun matchName(pattern: Regex, text: String): String? {
        val raw = pattern.find(text)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val words = raw.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        // The first word carries the decision; a second word is only ever a surname.
        val first = words.first()
        if (first.lowercase() in NOT_NAMES) return null
        if (first.length < 2) return null

        val kept = if (words.size > 1 && words[1].lowercase() !in NOT_NAMES) {
            words.take(2)
        } else {
            words.take(1)
        }

        return kept.joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }
}

/**
 * The model-backed second pass (PRD §7.7).
 *
 * Implementations send the transcript window to a fast model and ask it to map speaker
 * labels to names. Kept as an interface so the spine has no network dependency and the
 * whole roll-call path can be tested offline.
 */
interface RollCallResolver {
    suspend fun resolve(turns: List<Turn>, unresolvedLabels: Set<String>): List<RollCallExtractor.Binding>
}
