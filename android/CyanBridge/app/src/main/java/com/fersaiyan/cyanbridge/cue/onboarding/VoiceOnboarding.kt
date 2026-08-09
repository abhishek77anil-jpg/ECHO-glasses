package com.fersaiyan.cyanbridge.cue.onboarding

/**
 * Setup, entirely by voice.
 *
 * A sighted user is walked through setup by a screen. A blind user cannot be, and handing
 * them a TalkBack tree on first launch — before they have any idea what the app does — is
 * how assistive apps get abandoned on day one. So onboarding is a conversation: Cue asks,
 * the user answers out loud, and nothing requires finding a control.
 *
 * Pure state machine with no Android and no clock, so the whole script is unit-testable and
 * the wording can be reviewed without a device.
 *
 * Three affordances exist at every single step, because a listener has no scrollback:
 * "repeat" replays the prompt, "skip" moves on, and "stop" leaves. A user who mishears step
 * three must never be stuck there.
 */
class VoiceOnboarding(
    private val steps: List<Step> = defaultSteps(),
) {
    enum class Step {
        WELCOME,
        SAFETY,
        MICROPHONE,
        GLASSES,
        SPEECH_RATE,
        SURROUNDINGS,
        COMMANDS,
        DONE,
    }

    /** What the caller should do after handing an utterance to [onReply]. */
    sealed interface Outcome {
        /** Say this, then keep listening. */
        data class Say(val speech: String, val listenAgain: Boolean = true) : Outcome

        /** Onboarding finished. */
        data class Finished(val speech: String, val settings: Settings) : Outcome

        /** The user asked to leave. Honour it immediately. */
        data class Aborted(val speech: String) : Outcome

        /** A permission or connection is needed before the flow can continue. */
        data class NeedsAction(val speech: String, val action: RequiredAction) : Outcome
    }

    enum class RequiredAction { GRANT_MICROPHONE, CONNECT_GLASSES }

    /** What onboarding collected. Everything has a working default, so skipping is safe. */
    data class Settings(
        val speechRate: SpeechRate = SpeechRate.NORMAL,
        val surroundingsEnabled: Boolean = false,
        val microphoneGranted: Boolean = false,
        val glassesConnected: Boolean = false,
    )

    enum class SpeechRate(val multiplier: Float) {
        SLOW(0.9f), NORMAL(1.35f), FAST(1.8f)
    }

    private var index = 0
    private var settings = Settings()

    val currentStep: Step get() = steps.getOrElse(index) { Step.DONE }

    val isFinished: Boolean get() = currentStep == Step.DONE

    fun currentSettings(): Settings = settings

    /** The prompt for the current step. Say this, then listen. */
    fun currentPrompt(): String = promptFor(currentStep)

    /**
     * Handles one spoken reply.
     *
     * Global commands are checked before step-specific ones so that "stop" and "repeat"
     * always work, even when a step is expecting a yes or no.
     */
    fun onReply(utterance: String): Outcome {
        val text = normalise(utterance)

        if (matches(text, ABORT)) {
            return Outcome.Aborted("Stopping setup. You can say set up again any time.")
        }
        if (matches(text, REPEAT)) {
            return Outcome.Say(currentPrompt())
        }
        if (matches(text, HELP)) {
            return Outcome.Say(
                "At any point you can say repeat to hear that again, " +
                    "skip to move on, or stop to leave setup. " + currentPrompt(),
            )
        }
        if (matches(text, SKIP)) {
            return advance()
        }

        return when (currentStep) {
            Step.WELCOME, Step.SAFETY, Step.COMMANDS -> advance()

            Step.MICROPHONE ->
                if (settings.microphoneGranted) {
                    advance()
                } else {
                    Outcome.NeedsAction(
                        "I'll ask for microphone access now. Please allow it.",
                        RequiredAction.GRANT_MICROPHONE,
                    )
                }

            Step.GLASSES ->
                if (settings.glassesConnected) {
                    advance()
                } else {
                    Outcome.NeedsAction(
                        "Let's connect your glasses. Make sure they're switched on.",
                        RequiredAction.CONNECT_GLASSES,
                    )
                }

            Step.SPEECH_RATE -> {
                val rate = parseRate(text)
                if (rate == null) {
                    Outcome.Say(
                        "I didn't catch that. Say slower, normal, or faster.",
                    )
                } else {
                    settings = settings.copy(speechRate = rate)
                    advanceWithPrefix(confirmRate(rate))
                }
            }

            Step.SURROUNDINGS -> {
                val yes = parseYesNo(text)
                if (yes == null) {
                    Outcome.Say("Sorry, was that a yes or a no?")
                } else {
                    settings = settings.copy(surroundingsEnabled = yes)
                    advanceWithPrefix(
                        if (yes) {
                            "Surroundings updates are on. Remember it's extra information, " +
                                "not a replacement for your cane or guide dog."
                        } else {
                            "Left off. You can turn it on later by saying, start surroundings."
                        },
                    )
                }
            }

            Step.DONE -> Outcome.Finished(promptFor(Step.DONE), settings)
        }
    }

    /** Records that the microphone was granted, so the flow can move past that step. */
    fun onMicrophoneGranted(granted: Boolean): Outcome {
        settings = settings.copy(microphoneGranted = granted)
        return if (granted) {
            advanceWithPrefix("Thanks, I can hear you.")
        } else {
            // Not fatal: the buttons still work. Say so rather than dead-ending.
            Outcome.Say(
                "Without the microphone I can't hear questions, but the glasses buttons " +
                    "still work. You can grant it later in settings.",
            ).also { index = stepIndex(Step.GLASSES) }
        }
    }

    fun onGlassesConnected(connected: Boolean): Outcome {
        settings = settings.copy(glassesConnected = connected)
        return if (connected) {
            advanceWithPrefix("Glasses connected.")
        } else {
            Outcome.Say(
                "I couldn't find your glasses. We can carry on and connect them later.",
            ).also { index = stepIndex(Step.SPEECH_RATE) }
        }
    }

    private fun advance(): Outcome {
        index = (index + 1).coerceAtMost(steps.lastIndex)
        return if (currentStep == Step.DONE) {
            Outcome.Finished(promptFor(Step.DONE), settings)
        } else {
            Outcome.Say(currentPrompt())
        }
    }

    private fun advanceWithPrefix(prefix: String): Outcome =
        when (val next = advance()) {
            is Outcome.Say -> Outcome.Say("$prefix ${next.speech}")
            is Outcome.Finished -> Outcome.Finished("$prefix ${next.speech}", next.settings)
            else -> next
        }

    private fun stepIndex(step: Step): Int = steps.indexOf(step).coerceAtLeast(0)

    /**
     * The script.
     *
     * Written to be heard once. Short sentences, one idea each, and every question states its
     * accepted answers — a listener cannot scan for the options the way a reader can.
     */
    private fun promptFor(step: Step): String = when (step) {
        Step.WELCOME ->
            "Welcome to Cue. I'll tell you who's around you and what's being said, " +
                "and answer questions out loud. Setup takes about a minute, all by voice. " +
                "Say next when you're ready."

        Step.SAFETY ->
            "One important thing first. Cue adds information. It does not replace your cane, " +
                "your guide dog, or your own judgement. Never rely on it to cross a road or " +
                "avoid a drop. Say next to continue."

        Step.MICROPHONE ->
            "I need your microphone so I can hear your questions and who's speaking. " +
                "Say next to allow it."

        Step.GLASSES ->
            "Now let's connect your glasses. Switch them on and say next."

        Step.SPEECH_RATE ->
            "How fast should I speak? Say slower, normal, or faster. " +
                "You can change this any time by saying speak faster or speak slower."

        Step.SURROUNDINGS ->
            "Would you like me to describe your surroundings now and then? " +
                "I take a quick photo, tell you what's there, and delete it straight away. " +
                "Nothing is stored or uploaded. Say yes or no."

        Step.COMMANDS ->
            "Almost done. You can ask me: who's here. Who's talking. " +
                "What did they just say. And, what's around me. " +
                "Say stop any time to interrupt me. Say next to finish."

        Step.DONE ->
            "You're all set. Tap anywhere and ask me something whenever you like."
    }

    private fun confirmRate(rate: SpeechRate): String = when (rate) {
        SpeechRate.SLOW -> "Speaking slower."
        SpeechRate.NORMAL -> "Normal speed."
        SpeechRate.FAST -> "Speaking faster."
    }

    private fun parseRate(text: String): SpeechRate? = when {
        matches(text, listOf("slower", "slow", "slow down")) -> SpeechRate.SLOW
        matches(text, listOf("faster", "fast", "quick", "speed up")) -> SpeechRate.FAST
        matches(text, listOf("normal", "medium", "default", "same", "fine")) -> SpeechRate.NORMAL
        else -> null
    }

    private fun parseYesNo(text: String): Boolean? = when {
        matches(text, listOf("yes", "yeah", "yep", "sure", "ok", "okay", "please do", "go on")) -> true
        matches(text, listOf("no", "nope", "not now", "later", "don't", "do not")) -> false
        else -> null
    }

    private fun normalise(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9'\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun matches(text: String, phrases: List<String>): Boolean =
        phrases.any { text == it || text.startsWith("$it ") || text.contains(" $it ") || text.endsWith(" $it") }

    private companion object {
        val ABORT = listOf("stop", "quit", "exit", "cancel", "leave setup", "not now")
        val REPEAT = listOf("repeat", "again", "say that again", "pardon", "what")
        val SKIP = listOf("skip", "next", "continue", "go on", "move on", "done")
        val HELP = listOf("help", "what can i say", "options")

        fun defaultSteps(): List<Step> = Step.entries.toList()
    }
}
