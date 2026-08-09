package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.context.RollCallExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Passive roll call (PRD P0-2).
 *
 * The false-positive tests carry more weight than the positive ones. A missed name leaves
 * someone as "someone new", which is still useful; a wrong name is spoken confidently into
 * a blind user's ear and acted on.
 */
class RollCallExtractorTest {

    private fun turn(label: String, text: String, start: Long = 0) =
        Turn(speakerLabel = label, text = text, startMs = start, endMs = start + 1_000)

    @Test
    fun `catches the common self-introduction forms`() {
        val cases = mapOf(
            "Hi, I'm Sarah" to "Sarah",
            "hi there, my name is Priya" to "Priya",
            "My name's Grant" to "Grant",
            "I am Amogh" to "Amogh",
            "you can call me Dev" to "Dev",
            "I'm called Rin" to "Rin",
        )
        cases.forEach { (text, expected) ->
            val binding = RollCallExtractor.extractFromText("speaker_0", text)
            assertEquals("failed on: $text", expected, binding?.name)
        }
    }

    @Test
    fun `captures a two word name`() {
        val binding = RollCallExtractor.extractFromText("speaker_0", "I'm Sarah Chen, good to meet you")
        assertEquals("Sarah Chen", binding?.name)
    }

    @Test
    fun `normalises capitalisation from lowercase transcripts`() {
        // Speech-to-text does not reliably capitalise names.
        val binding = RollCallExtractor.extractFromText("speaker_1", "my name is priya")
        assertEquals("Priya", binding?.name)
    }

    @Test
    fun `rejects the common non-name completions`() {
        val notIntroductions = listOf(
            "I'm tired",
            "I'm sorry",
            "I'm going to grab a coffee",
            "I'm not sure about that",
            "I am here",
            "I'm just saying",
            "I'm ready",
            "I'm good thanks",
            "I'm looking at the chart",
            "I'm working on it",
        )
        notIntroductions.forEach { text ->
            assertNull("should not have matched: $text", RollCallExtractor.extractFromText("speaker_0", text))
        }
    }

    @Test
    fun `ignores utterances with no introduction at all`() {
        assertNull(RollCallExtractor.extractFromText("speaker_0", "so what did you think of the numbers"))
    }

    @Test
    fun `assigns each name to the speaker who said it`() {
        val turns = listOf(
            turn("speaker_0", "Hi, I'm Sarah", 0),
            turn("speaker_1", "Nice to meet you, my name is Priya", 2_000),
            turn("speaker_0", "Good to meet you too", 4_000),
        )

        val bindings = RollCallExtractor.extract(turns).associate { it.speakerLabel to it.name }

        assertEquals(mapOf("speaker_0" to "Sarah", "speaker_1" to "Priya"), bindings)
    }

    @Test
    fun `a later correction wins over an earlier mishearing`() {
        val turns = listOf(
            turn("speaker_0", "I'm Sara", 0),
            turn("speaker_0", "actually my name is Sarah", 3_000),
        )

        val bindings = RollCallExtractor.extract(turns)

        assertEquals(1, bindings.size)
        assertEquals("Sarah", bindings.first().name)
    }

    @Test
    fun `weaker phrasings are marked medium confidence`() {
        val binding = RollCallExtractor.extractFromText("speaker_2", "this is Grant")
        assertEquals("Grant", binding?.name)
        assertEquals(RollCallExtractor.Confidence.MEDIUM, binding?.confidence)
    }

    @Test
    fun `explicit introductions are marked high confidence`() {
        val binding = RollCallExtractor.extractFromText("speaker_2", "my name is Grant")
        assertEquals(RollCallExtractor.Confidence.HIGH, binding?.confidence)
    }

    @Test
    fun `three people enrol from an ordinary exchange`() {
        // PRD P0-2 acceptance: three enrolled from natural conversation, zero taps.
        val turns = listOf(
            turn("speaker_0", "Hey, I'm Sarah", 0),
            turn("speaker_1", "Priya, nice to meet you", 2_000),
            turn("speaker_1", "sorry, my name is Priya", 4_000),
            turn("speaker_2", "and I'm Grant", 6_000),
        )

        val bindings = RollCallExtractor.extract(turns).associate { it.speakerLabel to it.name }

        assertEquals(
            mapOf("speaker_0" to "Sarah", "speaker_1" to "Priya", "speaker_2" to "Grant"),
            bindings,
        )
    }
}
