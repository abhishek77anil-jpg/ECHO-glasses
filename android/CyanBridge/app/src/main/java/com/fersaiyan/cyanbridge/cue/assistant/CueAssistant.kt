package com.fersaiyan.cyanbridge.cue.assistant

import com.fersaiyan.cyanbridge.cue.ConversationContext
import com.fersaiyan.cyanbridge.cue.RosterBriefing
import com.fersaiyan.cyanbridge.cue.Turn

/**
 * Answers what the user asked, out loud.
 *
 * This is the conversational half of Cue, and it plays by different rules from the ambient
 * half. Unprompted output is rationed to the point of near-silence (PRD §5); a direct
 * question earns a real answer. The user spent a button press and a sentence to ask, so
 * replying with four clipped words would be its own kind of failure.
 *
 * Still bounded, though. The user is in a live conversation with their attention on a human,
 * so answers lead with the fact and stop. No preamble, no "I think", no restating the
 * question back at them.
 *
 * Synchronous and Android-free: model calls leave as [AssistantReply.Action.AskModel] rather
 * than being awaited here, so every branch is unit-testable without a network.
 */
class CueAssistant(
    /** PRD §3: the user is a TTS power user. Terse is the respectful default, not the lazy one. */
    var verbosity: Verbosity = Verbosity.NORMAL,
) {
    enum class Verbosity { TERSE, NORMAL, DETAILED }

    fun respond(intent: AssistantIntent, context: ConversationContext, nowMs: Long): AssistantReply =
        when (intent) {
            is AssistantIntent.Stop ->
                AssistantReply(speech = null, action = AssistantReply.Action.StopSpeaking)

            is AssistantIntent.RepeatLast ->
                AssistantReply(speech = null, action = AssistantReply.Action.RepeatLast)

            is AssistantIntent.WhoIsHere ->
                AssistantReply(speech = RosterBriefing.format(context))

            is AssistantIntent.WhoIsSpeaking -> AssistantReply(speech = whoIsSpeaking(context))

            is AssistantIntent.WhatWasSaid ->
                AssistantReply(speech = whatWasSaid(context, intent.speakerName))

            is AssistantIntent.DescribeView -> AssistantReply(
                speech = null,
                action = AssistantReply.Action.CapturePhoto(intent.question),
                isPending = true,
            )

            is AssistantIntent.AdjustSpeech -> AssistantReply(
                speech = confirmSpeechChange(intent.change),
                action = AssistantReply.Action.ChangeSpeech(intent.change),
            )

            is AssistantIntent.Help -> AssistantReply(speech = helpText())

            is AssistantIntent.OpenQuestion -> AssistantReply(
                speech = null,
                action = AssistantReply.Action.AskModel(intent.text),
                isPending = true,
            )
        }

    /**
     * Who has the floor.
     *
     * Uses the most recent turn rather than live voice activity: by the time the user has
     * finished asking, whoever prompted the question has usually stopped talking, and the
     * useful answer is who just spoke.
     */
    private fun whoIsSpeaking(context: ConversationContext): String {
        val lastTurn = context.turns.lastOrNull() ?: return "Nobody has spoken yet"
        val person = context.personFor(lastTurn.speakerLabel)
        return person?.name ?: "Someone I don't have a name for yet"
    }

    /**
     * Replays what was said, verbatim.
     *
     * Deliberately not summarised. The user missed words and wants the words; a paraphrase
     * would answer a question they did not ask, and would quietly hide the thing they were
     * straining to catch.
     */
    private fun whatWasSaid(context: ConversationContext, speakerName: String?): String {
        val turns = context.turns.filter { it.text.isNotBlank() }
        if (turns.isEmpty()) return "Nothing yet"

        if (speakerName != null) {
            val match = context.roster.firstOrNull { it.name.equals(speakerName, ignoreCase = true) }
                ?: return "I don't have anyone called $speakerName"
            val theirTurns = turns.filter { it.speakerLabel == match.speakerLabel }
            if (theirTurns.isEmpty()) return "$speakerName hasn't said anything yet"
            return quote(match.name, theirTurns.takeLast(turnsToReplay()))
        }

        val recent = turns.takeLast(turnsToReplay())
        val lastLabel = recent.last().speakerLabel
        val name = context.personFor(lastLabel)?.name
        return quote(name, recent.filter { it.speakerLabel == lastLabel })
    }

    private fun quote(name: String?, turns: List<Turn>): String {
        val words = turns.joinToString(" ") { it.text.trim() }.trim()
        if (words.isEmpty()) return "Nothing yet"
        return if (name != null) "$name said: $words" else words
    }

    private fun turnsToReplay(): Int = when (verbosity) {
        Verbosity.TERSE -> 1
        Verbosity.NORMAL -> 2
        Verbosity.DETAILED -> 4
    }

    private fun confirmSpeechChange(change: AssistantIntent.SpeechChange): String = when (change) {
        // Confirmations are one word on purpose. The change itself is audible in the very
        // next thing Cue says, so describing it would be redundant noise.
        AssistantIntent.SpeechChange.FASTER -> "Faster"
        AssistantIntent.SpeechChange.SLOWER -> "Slower"
        AssistantIntent.SpeechChange.MORE_DETAIL -> "More detail"
        AssistantIntent.SpeechChange.LESS_DETAIL -> "Shorter"
    }

    /**
     * The discoverability path.
     *
     * A blind user cannot scan a screen for available commands, so this list is the only way
     * they find out what Cue can do. It is grouped and paced for listening rather than
     * reading, and it names the buttons because those are what the hand can find.
     */
    private fun helpText(): String = when (verbosity) {
        Verbosity.TERSE ->
            "Ask who's here, who's talking, what was said, or what you're looking at. Say stop to interrupt."

        else -> buildString {
            append("You can ask me: who's here. ")
            append("Who's talking. ")
            append("What did they just say. ")
            append("What am I looking at, and I'll use the camera. ")
            append("Say again, to repeat my last answer. ")
            append("Say stop any time to interrupt me. ")
            append("You can also say speak faster, speak slower, or keep it short. ")
            append("Anything else, just ask and I'll answer.")
        }
    }

    /** Applies a speech change. Returns the new verbosity so callers can persist it. */
    fun applySpeechChange(change: AssistantIntent.SpeechChange): Verbosity {
        when (change) {
            AssistantIntent.SpeechChange.MORE_DETAIL -> verbosity = when (verbosity) {
                Verbosity.TERSE -> Verbosity.NORMAL
                else -> Verbosity.DETAILED
            }

            AssistantIntent.SpeechChange.LESS_DETAIL -> verbosity = when (verbosity) {
                Verbosity.DETAILED -> Verbosity.NORMAL
                else -> Verbosity.TERSE
            }

            // Rate changes are handled by the speaker, not by verbosity.
            AssistantIntent.SpeechChange.FASTER, AssistantIntent.SpeechChange.SLOWER -> Unit
        }
        return verbosity
    }

    /**
     * System prompt for the model path.
     *
     * Every rule here exists because its opposite is a real failure mode for a blind
     * listener: hedging wastes the interruption budget, "the image shows" states the obvious,
     * and descriptions instead of names discard the roster the user already knows.
     */
    fun systemPrompt(context: ConversationContext, nowMs: Long): String = buildString {
        append("You are Cue, speaking aloud into the ear of a blind user who is in a live ")
        append("conversation right now. Answer in under 25 words unless asked to elaborate. ")
        append("Lead with the answer. Never open with \"I see\", \"the image shows\", or ")
        append("\"it looks like\". Never restate the question. If unsure, say the short ")
        append("uncertain thing once; never hedge across two sentences. ")
        append("Refer to people by name when they are on the roster below, never by ")
        append("description such as \"the man in the blue shirt\".\n\n")

        val present = context.presentPeople
        if (present.isNotEmpty()) {
            append("People present: ")
            append(present.joinToString(", ") { it.spokenLabel })
            append("\n")
        }

        val transcript = context.transcriptWindowText(nowMs)
        if (transcript.isNotBlank()) {
            append("Recent conversation:\n")
            append(transcript)
        }
    }
}
