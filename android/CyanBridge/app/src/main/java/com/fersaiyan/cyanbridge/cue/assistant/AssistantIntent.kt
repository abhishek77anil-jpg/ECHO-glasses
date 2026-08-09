package com.fersaiyan.cyanbridge.cue.assistant

/**
 * What the user asked Cue to do.
 *
 * Cue answers most requests from state it already holds — the roster and the rolling
 * transcript — so the common ones resolve locally with no network and no model call. That
 * is not an optimisation: it is what lets the assistant keep working in a dead zone, during
 * a media transfer, and inside the latency budget in PRD §7.8.
 *
 * [OpenQuestion] is the escape hatch for everything else and is the only intent that needs
 * a model.
 */
sealed interface AssistantIntent {

    /** "who's here", "who is in the room" — answered from the roster. */
    data object WhoIsHere : AssistantIntent

    /** "who's talking", "who said that" — answered from the last turn. */
    data object WhoIsSpeaking : AssistantIntent

    /** "what did they just say" — replays recent transcript, not a summary. */
    data class WhatWasSaid(val speakerName: String? = null) : AssistantIntent

    /** "say that again" — replays Cue's own last utterance from cache. */
    data object RepeatLast : AssistantIntent

    /** "what am I looking at" — the only intent that needs the camera. */
    data class DescribeView(val question: String? = null) : AssistantIntent

    /** "what's around me" — answered from the most recent ambient scan, on demand. */
    data object DescribeSurroundings : AssistantIntent

    /** "start surroundings" / "stop surroundings" — toggles the periodic scan. */
    data class SetSurroundings(val enabled: Boolean) : AssistantIntent

    /** "stop", "quiet" — always available, always immediate. */
    data object Stop : AssistantIntent

    /** "slow down", "faster", "say less" — adjusts how Cue speaks. */
    data class AdjustSpeech(val change: SpeechChange) : AssistantIntent

    /** "what can you do" — spoken capability list, the discoverability path for a blind user. */
    data object Help : AssistantIntent

    /** Anything else. Needs the model, and carries the conversation as grounding. */
    data class OpenQuestion(val text: String) : AssistantIntent

    enum class SpeechChange { FASTER, SLOWER, MORE_DETAIL, LESS_DETAIL }
}

/**
 * What Cue says back, and what it should do besides speaking.
 *
 * Separating the two matters: some replies are purely an action ("stop"), some are purely
 * speech, and some need a sound before a slow answer arrives.
 */
data class AssistantReply(
    val speech: String?,
    val action: Action = Action.None,
    /** True when the answer needs the camera or the network and will not be instant. */
    val isPending: Boolean = false,
) {
    sealed interface Action {
        data object None : Action
        data object StopSpeaking : Action
        data object RepeatLast : Action
        data class CapturePhoto(val question: String?) : Action

        /** Read out the latest ambient scan, bypassing its usual cooldowns. */
        data object DescribeSurroundings : Action

        /** Turn the periodic surroundings scan on or off. */
        data class SetAmbientScanning(val enabled: Boolean) : Action

        /**
         * Hand off to the model. Kept as an action rather than a call inside the assistant
         * so the whole decision layer stays synchronous and testable without a network.
         */
        data class AskModel(val question: String) : Action
        data class ChangeSpeech(val change: AssistantIntent.SpeechChange) : Action
    }
}
