package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.ambient.AmbientScanPolicy
import com.fersaiyan.cyanbridge.cue.assistant.AssistantIntent
import com.fersaiyan.cyanbridge.cue.assistant.CommandParser
import com.fersaiyan.cyanbridge.cue.onboarding.VoiceOnboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ambient surroundings scanning and the voice-only setup flow. */
class AmbientAndOnboardingTest {

    // ---- ambient scan policy -----------------------------------------------------

    private fun hazard(
        label: String,
        severity: AmbientScanPolicy.Severity,
        bearing: String? = "ahead",
    ) = AmbientScanPolicy.Hazard(label, severity, bearing)

    @Test
    fun `urgent hazards are announced immediately`() {
        val policy = AmbientScanPolicy()

        val result = policy.consider(
            AmbientScanPolicy.Observation(
                summary = "a corridor",
                hazards = listOf(hazard("step down", AmbientScanPolicy.Severity.URGENT)),
                atMs = 1_000,
            ),
        )

        assertNotNull(result)
        assertEquals("step down, ahead", result!!.speech)
        assertEquals(AmbientScanPolicy.Severity.URGENT, result.severity)
        assertTrue("urgent deserves an alert sound first", result.leadWithAlert)
    }

    @Test
    fun `the same urgent hazard is not repeated every frame`() {
        val policy = AmbientScanPolicy()
        val observation = { at: Long ->
            AmbientScanPolicy.Observation(
                summary = null,
                hazards = listOf(hazard("step down", AmbientScanPolicy.Severity.URGENT)),
                atMs = at,
            )
        }

        assertNotNull(policy.consider(observation(1_000)))
        assertNull("a 2-second-old warning is not news", policy.consider(observation(3_000)))
        assertNotNull("but it is again after the window", policy.consider(observation(6_000)))
    }

    @Test
    fun `routine scene updates stay quiet until the cooldown passes`() {
        val policy = AmbientScanPolicy()

        val first = policy.consider(
            AmbientScanPolicy.Observation("an office with desks", emptyList(), 0),
        )
        assertNotNull(first)

        val soon = policy.consider(
            AmbientScanPolicy.Observation("a kitchen with a sink", emptyList(), 5_000),
        )
        assertNull("must not narrate continuously", soon)
    }

    @Test
    fun `a rephrased description of the same scene is not repeated`() {
        val policy = AmbientScanPolicy()
        policy.consider(AmbientScanPolicy.Observation("a hallway with a door", emptyList(), 0))

        val rephrased = policy.consider(
            AmbientScanPolicy.Observation("a door in a hallway", emptyList(), 90_000),
        )

        assertNull("same news, different words", rephrased)
    }

    @Test
    fun `a genuinely different scene is announced after the cooldown`() {
        val policy = AmbientScanPolicy()
        policy.consider(AmbientScanPolicy.Observation("a hallway with a door", emptyList(), 0))

        val changed = policy.consider(
            AmbientScanPolicy.Observation("a busy street with parked cars", emptyList(), 90_000),
        )

        assertNotNull(changed)
        assertEquals("a busy street with parked cars", changed!!.speech)
    }

    @Test
    fun `nothing is said when there is nothing to say`() {
        val policy = AmbientScanPolicy()
        assertNull(policy.consider(AmbientScanPolicy.Observation(null, emptyList(), 1_000)))
    }

    @Test
    fun `hazards are phrased as observations, never instructions`() {
        val policy = AmbientScanPolicy()
        val result = policy.consider(
            AmbientScanPolicy.Observation(
                summary = null,
                hazards = listOf(hazard("a bollard", AmbientScanPolicy.Severity.URGENT, "on your left")),
                atMs = 0,
            ),
        )!!

        assertEquals("a bollard, on your left", result.speech)
        listOf("stop", "turn", "go ", "move").forEach {
            assertFalse(
                "must not issue mobility instructions: ${result.speech}",
                result.speech.lowercase().contains(it),
            )
        }
    }

    @Test
    fun `asking on demand bypasses every cooldown`() {
        val policy = AmbientScanPolicy()
        policy.consider(AmbientScanPolicy.Observation("an office", emptyList(), 0))

        val answer = policy.describeOnDemand(
            AmbientScanPolicy.Observation(
                summary = "an office",
                hazards = listOf(hazard("a chair", AmbientScanPolicy.Severity.CAUTION)),
                atMs = 1_000,
            ),
        )

        assertTrue(answer.contains("an office"))
        assertTrue(answer.contains("a chair"))
    }

    @Test
    fun `on demand says so plainly when it cannot see`() {
        val answer = AmbientScanPolicy()
            .describeOnDemand(AmbientScanPolicy.Observation(null, emptyList(), 0))
        assertEquals("I can't see anything clearly right now", answer)
    }

    // ---- surroundings commands ---------------------------------------------------

    @Test
    fun `surroundings commands are understood`() {
        assertEquals(
            AssistantIntent.DescribeSurroundings,
            CommandParser.parse("what's around me"),
        )
        assertEquals(
            AssistantIntent.DescribeSurroundings,
            CommandParser.parse("is anything in my way"),
        )
        assertEquals(
            AssistantIntent.SetSurroundings(true),
            CommandParser.parse("start surroundings"),
        )
        assertEquals(
            AssistantIntent.SetSurroundings(false),
            CommandParser.parse("stop scanning"),
        )
    }

    @Test
    fun `asking about the room is not confused with asking about a held object`() {
        assertEquals(AssistantIntent.DescribeSurroundings, CommandParser.parse("what is around me"))
        assertTrue(CommandParser.parse("what is this") is AssistantIntent.DescribeView)
    }

    // ---- voice onboarding --------------------------------------------------------

    @Test
    fun `onboarding opens with what cue does and how long it takes`() {
        val prompt = VoiceOnboarding().currentPrompt()
        assertTrue(prompt.contains("Cue"))
        assertTrue("must set expectations up front", prompt.contains("minute"))
        assertTrue("must state how to proceed", prompt.contains("next"))
    }

    @Test
    fun `the safety limitation is stated before anything is configured`() {
        val flow = VoiceOnboarding()
        flow.onReply("next")

        val safety = flow.currentPrompt().lowercase()
        assertEquals(VoiceOnboarding.Step.SAFETY, flow.currentStep)
        assertTrue(safety.contains("cane"))
        assertTrue(safety.contains("does not replace"))
    }

    @Test
    fun `repeat replays the current prompt without advancing`() {
        val flow = VoiceOnboarding()
        val before = flow.currentStep

        val outcome = flow.onReply("repeat") as VoiceOnboarding.Outcome.Say

        assertEquals(before, flow.currentStep)
        assertEquals(flow.currentPrompt(), outcome.speech)
    }

    @Test
    fun `stop leaves setup at any point`() {
        val flow = VoiceOnboarding()
        flow.onReply("next")

        val outcome = flow.onReply("stop")

        assertTrue(outcome is VoiceOnboarding.Outcome.Aborted)
    }

    @Test
    fun `help explains the escape hatches and repeats the prompt`() {
        val outcome = VoiceOnboarding().onReply("help") as VoiceOnboarding.Outcome.Say
        listOf("repeat", "skip", "stop").forEach {
            assertTrue("help should mention '$it'", outcome.speech.contains(it))
        }
    }

    @Test
    fun `speech rate is captured from a spoken answer`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.SPEECH_RATE) flow.onReply("skip")

        flow.onReply("faster")

        assertEquals(VoiceOnboarding.SpeechRate.FAST, flow.currentSettings().speechRate)
    }

    @Test
    fun `an unclear speech rate answer re-asks instead of guessing`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.SPEECH_RATE) flow.onReply("skip")

        val outcome = flow.onReply("purple") as VoiceOnboarding.Outcome.Say

        assertEquals(VoiceOnboarding.Step.SPEECH_RATE, flow.currentStep)
        assertTrue(outcome.speech.contains("slower"))
    }

    @Test
    fun `surroundings step explains the privacy behaviour before asking`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.SURROUNDINGS) flow.onReply("skip")

        val prompt = flow.currentPrompt().lowercase()

        assertTrue("must say the photo is deleted", prompt.contains("delete"))
        assertTrue("must say nothing is uploaded", prompt.contains("uploaded"))
    }

    @Test
    fun `enabling surroundings repeats the safety caveat`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.SURROUNDINGS) flow.onReply("skip")

        val outcome = flow.onReply("yes") as VoiceOnboarding.Outcome.Say

        assertTrue(flow.currentSettings().surroundingsEnabled)
        assertTrue(outcome.speech.lowercase().contains("cane"))
    }

    @Test
    fun `a denied microphone does not dead-end the flow`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.MICROPHONE) flow.onReply("skip")

        val outcome = flow.onMicrophoneGranted(false) as VoiceOnboarding.Outcome.Say

        assertTrue("must offer a way to keep going", outcome.speech.contains("buttons"))
        assertEquals(VoiceOnboarding.Step.GLASSES, flow.currentStep)
    }

    @Test
    fun `skipping everything still yields usable defaults`() {
        val flow = VoiceOnboarding()
        var guard = 0
        while (!flow.isFinished && guard++ < 20) flow.onReply("skip")

        assertTrue(flow.isFinished)
        val settings = flow.currentSettings()
        assertEquals(VoiceOnboarding.SpeechRate.NORMAL, settings.speechRate)
        assertFalse("surroundings must be opt-in", settings.surroundingsEnabled)
    }

    @Test
    fun `the final step teaches the commands`() {
        val flow = VoiceOnboarding()
        while (flow.currentStep != VoiceOnboarding.Step.COMMANDS) flow.onReply("skip")

        val prompt = flow.currentPrompt().lowercase()

        listOf("who's here", "who's talking", "around me", "stop").forEach {
            assertTrue("commands step should teach '$it'", prompt.contains(it))
        }
    }
}
