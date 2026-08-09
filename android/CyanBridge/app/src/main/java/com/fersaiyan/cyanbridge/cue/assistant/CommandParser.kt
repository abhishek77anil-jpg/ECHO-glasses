package com.fersaiyan.cyanbridge.cue.assistant

/**
 * Turns a spoken utterance into an [AssistantIntent].
 *
 * Pattern matching rather than a model, for three reasons that all matter to a blind user in
 * a live conversation: it answers inside the PRD §7.8 budget, it works with no network, and
 * it is predictable. An assistant that interprets "stop" differently depending on the phase
 * of the moon is worse than one with a small vocabulary.
 *
 * Anything unrecognised becomes [AssistantIntent.OpenQuestion] and goes to the model, so a
 * miss here costs latency, never capability.
 */
object CommandParser {

    fun parse(utterance: String): AssistantIntent {
        val text = normalise(utterance)
        if (text.isBlank()) return AssistantIntent.OpenQuestion(utterance.trim())

        // Stop is checked first and matched loosely. When someone says stop they mean now,
        // and making them compete with other patterns for it would be a bad joke.
        if (matchesAny(text, STOP)) return AssistantIntent.Stop

        if (matchesAny(text, WHO_IS_HERE)) return AssistantIntent.WhoIsHere
        if (matchesAny(text, WHO_IS_SPEAKING)) return AssistantIntent.WhoIsSpeaking
        if (matchesAny(text, REPEAT)) return AssistantIntent.RepeatLast
        if (matchesAny(text, HELP)) return AssistantIntent.Help

        SPEECH_CHANGES.forEach { (phrases, change) ->
            if (matchesAny(text, phrases)) return AssistantIntent.AdjustSpeech(change)
        }

        whatWasSaid(text)?.let { return it }

        if (matchesAny(text, DESCRIBE_VIEW)) {
            return AssistantIntent.DescribeView(question = visualQuestion(utterance.trim()))
        }

        return AssistantIntent.OpenQuestion(utterance.trim())
    }

    /**
     * "what did she say" / "what did Priya just say".
     *
     * The captured name is passed through unresolved; matching it against the roster is the
     * assistant's job, since only it knows who is actually present.
     */
    private fun whatWasSaid(text: String): AssistantIntent? {
        WHAT_WAS_SAID.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            val who = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val name = who.takeIf { it.isNotBlank() && it !in PRONOUNS }
            return AssistantIntent.WhatWasSaid(
                speakerName = name?.replaceFirstChar { it.uppercaseChar() },
            )
        }
        return null
    }

    /**
     * Keeps the user's actual question for the photo prompt.
     *
     * "what am I looking at" carries no question worth forwarding, but "how much does this
     * cost" does — and stripping it would turn a specific question into a generic
     * description, which is the failure mode PRD §6 calls out for P0-5.
     */
    private fun visualQuestion(original: String): String? {
        val normalised = normalise(original)
        val isGeneric = GENERIC_VIEW_QUERIES.any { normalised == it || normalised.startsWith("$it ") }
        return if (isGeneric) null else original.takeIf { it.isNotBlank() }
    }

    private fun normalise(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9'\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun matchesAny(text: String, phrases: List<String>): Boolean =
        phrases.any { phrase -> text == phrase || text.contains(phrase) }

    private val STOP = listOf(
        "stop", "be quiet", "quiet", "shush", "cancel", "never mind", "nevermind", "shut up",
    )

    private val WHO_IS_HERE = listOf(
        "who is here", "who's here", "whos here", "who is in the room", "who is around",
        "who is with me", "who am i with", "who is present", "list people", "who is nearby",
    )

    private val WHO_IS_SPEAKING = listOf(
        "who is speaking", "who's speaking", "whos speaking", "who is talking",
        "who's talking", "whos talking", "who is that", "who was that", "who said that",
        "who is this",
    )

    private val REPEAT = listOf(
        "repeat", "say that again", "say it again", "again please", "what was that",
        "come again", "one more time",
    )

    private val HELP = listOf(
        "what can you do", "help", "what are my options", "commands", "how do i use this",
        "what can i ask",
    )

    private val DESCRIBE_VIEW = listOf(
        "what am i looking at", "what is in front of me", "what's in front of me",
        "describe this", "describe that", "what is this", "what's this", "what is that",
        "what's that", "look at this", "look at that", "read this", "read that",
        "what does this say", "what does that say", "how much", "what colour", "what color",
    )

    private val GENERIC_VIEW_QUERIES = listOf(
        "what am i looking at", "what is in front of me", "what's in front of me",
        "describe this", "describe that", "look at this", "look at that",
    )

    private val WHAT_WAS_SAID = listOf(
        Regex("""what did (\w+) (?:just )?say"""),
        Regex("""what did (\w+) mean"""),
        Regex("""what was (\w+) saying"""),
    )

    private val PRONOUNS = setOf("he", "she", "they", "it", "you", "we", "i", "that", "this")

    private val SPEECH_CHANGES = listOf(
        listOf("speak faster", "faster", "speed up", "talk faster") to
            AssistantIntent.SpeechChange.FASTER,
        listOf("speak slower", "slower", "slow down", "talk slower") to
            AssistantIntent.SpeechChange.SLOWER,
        listOf("more detail", "tell me more", "be more detailed", "elaborate", "go on") to
            AssistantIntent.SpeechChange.MORE_DETAIL,
        listOf("less detail", "say less", "be brief", "shorter", "keep it short") to
            AssistantIntent.SpeechChange.LESS_DETAIL,
    )
}
