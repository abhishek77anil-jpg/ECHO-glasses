package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.assistant.AssistantIntent
import com.fersaiyan.cyanbridge.cue.assistant.AssistantReply
import com.fersaiyan.cyanbridge.cue.assistant.CommandParser
import com.fersaiyan.cyanbridge.cue.assistant.CueAssistant
import com.fersaiyan.cyanbridge.cue.context.ConversationContextStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The conversational half of Cue: command understanding and grounded answers. */
class AssistantTest {

    private fun contextWith(vararg turns: Triple<String, String, Long>): ConversationContext {
        val store = ConversationContextStore(sessionStartMs = 0)
        turns.forEach { (label, text, start) -> store.onTurn(label, text, start, start + 1_000) }
        // Let passive roll call bind any names present in the transcript.
        com.fersaiyan.cyanbridge.cue.context.RollCallExtractor.extract(store.snapshot().turns)
            .forEach { store.bindName(it.speakerLabel, it.name) }
        return store.snapshot()
    }

    // ---- parsing -----------------------------------------------------------------

    @Test
    fun `recognises the core commands in several phrasings`() {
        val cases = mapOf(
            "who's here" to AssistantIntent.WhoIsHere,
            "Who is in the room?" to AssistantIntent.WhoIsHere,
            "hey, who am I with" to AssistantIntent.WhoIsHere,
            "who's talking" to AssistantIntent.WhoIsSpeaking,
            "who said that?" to AssistantIntent.WhoIsSpeaking,
            "say that again" to AssistantIntent.RepeatLast,
            "what was that" to AssistantIntent.RepeatLast,
            "stop" to AssistantIntent.Stop,
            "be quiet" to AssistantIntent.Stop,
            "what can you do" to AssistantIntent.Help,
        )
        cases.forEach { (utterance, expected) ->
            assertEquals("failed on: $utterance", expected, CommandParser.parse(utterance))
        }
    }

    @Test
    fun `stop wins even when the sentence says other things`() {
        // "Stop" must never lose a race with another pattern.
        assertEquals(AssistantIntent.Stop, CommandParser.parse("stop, who's here"))
    }

    @Test
    fun `generic look commands carry no question but specific ones do`() {
        val generic = CommandParser.parse("what am I looking at") as AssistantIntent.DescribeView
        assertNull(generic.question)

        val specific = CommandParser.parse("how much does this cost") as AssistantIntent.DescribeView
        assertEquals("how much does this cost", specific.question)
    }

    @Test
    fun `extracts a name from a what-did-they-say question`() {
        val named = CommandParser.parse("what did Priya just say") as AssistantIntent.WhatWasSaid
        assertEquals("Priya", named.speakerName)

        val pronoun = CommandParser.parse("what did she say") as AssistantIntent.WhatWasSaid
        assertNull("pronouns are not names", pronoun.speakerName)
    }

    @Test
    fun `speech adjustments are understood`() {
        assertEquals(
            AssistantIntent.AdjustSpeech(AssistantIntent.SpeechChange.SLOWER),
            CommandParser.parse("slow down"),
        )
        assertEquals(
            AssistantIntent.AdjustSpeech(AssistantIntent.SpeechChange.LESS_DETAIL),
            CommandParser.parse("keep it short"),
        )
    }

    @Test
    fun `unknown requests become open questions rather than failures`() {
        val intent = CommandParser.parse("what time is my flight tomorrow")
        assertTrue(intent is AssistantIntent.OpenQuestion)
        assertEquals("what time is my flight tomorrow", (intent as AssistantIntent.OpenQuestion).text)
    }

    // ---- answering ---------------------------------------------------------------

    @Test
    fun `answers who is here from the roster with no model call`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_1", "my name is Priya", 2_000L),
        )

        val reply = CueAssistant().respond(AssistantIntent.WhoIsHere, context, 3_000)

        assertEquals("Sarah and Priya", reply.speech)
        assertEquals(AssistantReply.Action.None, reply.action)
        assertFalse("must not need the network", reply.isPending)
    }

    @Test
    fun `answers who is speaking using the most recent turn`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_1", "my name is Priya", 2_000L),
        )

        val reply = CueAssistant().respond(AssistantIntent.WhoIsSpeaking, context, 3_000)

        assertEquals("Priya", reply.speech)
    }

    @Test
    fun `replays what was said verbatim rather than summarising`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_1", "my name is Priya", 2_000L),
            Triple("speaker_1", "the deadline moved to Friday", 4_000L),
        )

        val reply = CueAssistant().respond(AssistantIntent.WhatWasSaid(null), context, 5_000)

        assertTrue(
            "the user wants the words, not a paraphrase: ${reply.speech}",
            reply.speech!!.contains("the deadline moved to Friday"),
        )
        assertTrue(reply.speech!!.startsWith("Priya said:"))
    }

    @Test
    fun `replays a named person's words`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_0", "I'll send the invoice", 2_000L),
            Triple("speaker_1", "my name is Priya", 4_000L),
        )

        val reply = CueAssistant().respond(AssistantIntent.WhatWasSaid("Sarah"), context, 5_000)

        assertTrue(reply.speech!!.contains("I'll send the invoice"))
    }

    @Test
    fun `says so when asked about someone it does not know`() {
        val context = contextWith(Triple("speaker_0", "hi I'm Sarah", 0L))

        val reply = CueAssistant().respond(AssistantIntent.WhatWasSaid("Grant"), context, 2_000)

        assertEquals("I don't have anyone called Grant", reply.speech)
    }

    @Test
    fun `a visual question becomes a photo action carrying the question`() {
        val context = contextWith(Triple("speaker_0", "look at this chart", 0L))

        val reply = CueAssistant().respond(
            AssistantIntent.DescribeView("how much does this cost"),
            context,
            1_000,
        )

        assertEquals(
            AssistantReply.Action.CapturePhoto("how much does this cost"),
            reply.action,
        )
        assertTrue("the working earcon covers this", reply.isPending)
        assertNull("nothing to say until the answer arrives", reply.speech)
    }

    @Test
    fun `an open question is handed to the model, not answered blind`() {
        val reply = CueAssistant().respond(
            AssistantIntent.OpenQuestion("what's the capital of Peru"),
            contextWith(),
            1_000,
        )

        assertEquals(AssistantReply.Action.AskModel("what's the capital of Peru"), reply.action)
    }

    @Test
    fun `stop produces an action and no speech`() {
        val reply = CueAssistant().respond(AssistantIntent.Stop, contextWith(), 0)

        assertEquals(AssistantReply.Action.StopSpeaking, reply.action)
        assertNull("stop must not talk back", reply.speech)
    }

    @Test
    fun `help is spoken and lists the commands`() {
        val speech = CueAssistant().respond(AssistantIntent.Help, contextWith(), 0).speech!!

        listOf("who's here", "who's talking", "looking at", "stop").forEach {
            assertTrue("help should mention '$it': $speech", speech.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `verbosity changes how much is replayed`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_0", "first point", 2_000L),
            Triple("speaker_0", "second point", 4_000L),
        )

        val terse = CueAssistant(CueAssistant.Verbosity.TERSE)
            .respond(AssistantIntent.WhatWasSaid(null), context, 5_000).speech!!
        val detailed = CueAssistant(CueAssistant.Verbosity.DETAILED)
            .respond(AssistantIntent.WhatWasSaid(null), context, 5_000).speech!!

        assertTrue("detailed should carry more", detailed.length > terse.length)
    }

    @Test
    fun `less detail then more detail returns to normal`() {
        val assistant = CueAssistant()
        assertEquals(
            CueAssistant.Verbosity.TERSE,
            assistant.applySpeechChange(AssistantIntent.SpeechChange.LESS_DETAIL),
        )
        assertEquals(
            CueAssistant.Verbosity.NORMAL,
            assistant.applySpeechChange(AssistantIntent.SpeechChange.MORE_DETAIL),
        )
    }

    @Test
    fun `system prompt grounds the model in the roster and transcript`() {
        val context = contextWith(
            Triple("speaker_0", "hi I'm Sarah", 0L),
            Triple("speaker_0", "revenue is down twelve percent", 2_000L),
        )

        val prompt = CueAssistant().systemPrompt(context, nowMs = 3_000)

        assertTrue(prompt.contains("Sarah"))
        assertTrue(prompt.contains("revenue is down twelve percent"))
        assertTrue("must forbid the description failure mode", prompt.contains("never by "))
        assertTrue(prompt.contains("blind"))
    }
}
