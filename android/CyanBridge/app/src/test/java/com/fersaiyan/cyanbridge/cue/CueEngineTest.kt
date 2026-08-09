package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.audio.GapDetector
import com.fersaiyan.cyanbridge.cue.context.ConversationContextStore
import com.fersaiyan.cyanbridge.cue.output.CueSpeaker
import com.fersaiyan.cyanbridge.cue.output.Earcon
import com.fersaiyan.cyanbridge.cue.output.EarconSink
import com.fersaiyan.cyanbridge.cue.output.OutputArbiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end behaviour of the decision layer (PRD P0-1 through P0-6). */
class CueEngineTest {

    private class FakeSpeaker : CueSpeaker {
        val spoken = mutableListOf<String>()
        private var pendingDone: (() -> Unit)? = null
        override fun speak(text: String, rate: Float, utteranceId: String, onDone: () -> Unit) {
            spoken += text
            pendingDone = onDone
        }
        override fun stop() {
            pendingDone?.invoke()
            pendingDone = null
        }
        fun finish() {
            pendingDone?.invoke()
            pendingDone = null
        }
        val last: String? get() = spoken.lastOrNull()
    }

    private class FakeEarcons : EarconSink {
        val played = mutableListOf<Earcon>()
        override fun play(earcon: Earcon) { played += earcon }
    }

    private class Fixture(wearer: String? = null) {
        val speaker = FakeSpeaker()
        val earcons = FakeEarcons()
        val gap = GapDetector()
        val store = ConversationContextStore(sessionStartMs = 0, departureSilenceMs = 10_000)
        var now = 0L
        val arbiter = OutputArbiter(speaker, earcons, gap, clock = { now })
        val engine = CueEngine(store, arbiter, gap).apply { wearerLabel = wearer }

        /**
         * Advances time with a quiet mic so gaps can open, pumps, and lets any resulting
         * utterance finish — a real TTS engine completes a one-word whisper long before the
         * next pump, and leaving it hanging would misrepresent the arbiter's state.
         */
        fun quietUntil(at: Long) {
            var t = now
            while (t < at) {
                t = minOf(at, t + 100)
                engine.onRms(0f, t)
            }
            now = at
            engine.pump(at)
            speaker.finish()
        }

        fun someoneTalks(at: Long) {
            now = at
            engine.onRms(0f, at - 1)
            engine.onRms(10f, at)
        }
    }

    @Test
    fun `names the speaker in the next gap`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "hi, I'm Sarah", 0, 1_000)
        f.quietUntil(2_000)

        f.engine.onSpeakerTurnStarted("speaker_1", 2_100)
        f.engine.onTranscript("speaker_1", "good to meet you", 2_100, 3_000)
        f.engine.onSpeakerTurnStarted("speaker_0", 3_100)
        f.quietUntil(4_000)

        assertTrue("Sarah should have been named", f.speaker.spoken.contains("Sarah"))
        assertEquals("no whisper may land on speech", 0, f.arbiter.stats.interruptions)
    }

    @Test
    fun `passive roll call enrols three people with no commands`() {
        // PRD P0-2 acceptance.
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "Hey, I'm Sarah", 0, 2_000)
        f.engine.onTranscript("speaker_1", "my name is Priya", 3_000, 5_000)
        f.engine.onTranscript("speaker_2", "and I'm Grant", 6_000, 8_000)

        val roster = f.engine.snapshot().roster.mapNotNull { it.name }.toSet()

        assertEquals(setOf("Sarah", "Priya", "Grant"), roster)
    }

    @Test
    fun `a new voice fires the entry earcon`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "hello", 0, 1_000)

        assertEquals(listOf(Earcon.PERSON_ENTERED), f.earcons.played)
    }

    @Test
    fun `a departed voice fires the exit earcon exactly once`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "hello", 0, 1_000)
        f.earcons.played.clear()

        f.quietUntil(12_000)
        assertEquals(listOf(Earcon.PERSON_LEFT), f.earcons.played)

        f.quietUntil(20_000)
        assertEquals("must not repeat", 1, f.earcons.played.size)
    }

    @Test
    fun `the wearer is never announced to themselves`() {
        val f = Fixture(wearer = "speaker_0")

        f.engine.onSpeakerTurnStarted("speaker_0", 100)
        f.engine.onTranscript("speaker_0", "I'm Amogh", 100, 1_000)
        f.quietUntil(2_000)

        assertTrue("no earcon for the wearer", f.earcons.played.isEmpty())
        assertTrue("no whisper for the wearer", f.speaker.spoken.isEmpty())
    }

    @Test
    fun `pause button briefs the room when cue is silent`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.engine.onTranscript("speaker_1", "I'm Priya", 2_000, 3_000)
        f.quietUntil(4_000)
        f.speaker.spoken.clear()

        // Well clear of the self-speech tail, so Cue is genuinely idle.
        f.engine.onPausePressed(4_500)

        assertEquals("Sarah and Priya", f.speaker.last)
    }

    @Test
    fun `pause button interrupts when cue is mid sentence`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.engine.onTranscript("speaker_1", "I'm Priya", 2_000, 3_000)
        f.quietUntil(4_000)

        // First press briefs, and the briefing is still playing.
        f.engine.onPausePressed(4_500)
        val spokenCount = f.speaker.spoken.size
        assertEquals("Sarah and Priya", f.speaker.last)

        // Second press lands mid-sentence, so it means stop, not brief again.
        f.engine.onPausePressed(4_600)

        assertEquals("must not start a new briefing", spokenCount, f.speaker.spoken.size)
    }

    @Test
    fun `who is here handles an empty room and anonymous strangers`() {
        val f = Fixture()
        assertEquals("No one yet", RosterBriefing.format(f.engine.snapshot()))

        f.engine.onTranscript("speaker_0", "mumble", 0, 1_000)
        assertEquals("One person, no name yet", RosterBriefing.format(f.engine.snapshot()))

        f.engine.onTranscript("speaker_1", "I'm Sarah", 2_000, 3_000)
        assertEquals("Sarah and one other", RosterBriefing.format(f.engine.snapshot()))
    }

    @Test
    fun `repeat last replays and reports failure when empty`() {
        val f = Fixture()
        f.engine.onRepeatPressed(100)
        assertEquals(listOf(Earcon.FAILED), f.earcons.played)

        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.quietUntil(2_000)
        val before = f.speaker.spoken.size
        f.speaker.finish()

        f.engine.onRepeatPressed(2_500)
        assertEquals(before + 1, f.speaker.spoken.size)
    }

    @Test
    fun `photo question plays working then busy when the glasses reject it`() {
        val rejecting = object : CueEngine.PhotoQuestionRunner {
            override fun requestPhotoAnswer(context: ConversationContext, nowMs: Long) = false
        }
        val speaker = FakeSpeaker()
        val earcons = FakeEarcons()
        val gap = GapDetector()
        val store = ConversationContextStore(sessionStartMs = 0)
        val arbiter = OutputArbiter(speaker, earcons, gap, clock = { 0L })
        val engine = CueEngine(store, arbiter, gap, photoQuestion = rejecting)

        engine.onPhotoQuestionPressed(100)

        assertEquals(listOf(Earcon.WORKING, Earcon.DEVICE_BUSY), earcons.played)
    }

    @Test
    fun `photo question receives the transcript context not just the image`() {
        var seen: ConversationContext? = null
        val runner = object : CueEngine.PhotoQuestionRunner {
            override fun requestPhotoAnswer(context: ConversationContext, nowMs: Long): Boolean {
                seen = context
                return true
            }
        }
        val speaker = FakeSpeaker()
        val earcons = FakeEarcons()
        val gap = GapDetector()
        val store = ConversationContextStore(sessionStartMs = 0)
        val arbiter = OutputArbiter(speaker, earcons, gap, clock = { 0L })
        val engine = CueEngine(store, arbiter, gap, photoQuestion = runner)

        engine.onTranscript("speaker_0", "I'm Grant", 0, 1_000)
        engine.onTranscript("speaker_0", "what do you think of this number", 2_000, 4_000)
        engine.onPhotoQuestionPressed(4_100)

        val text = seen!!.transcriptWindowText(nowMs = 4_100)
        assertTrue("prompt must carry what was just said", text.contains("what do you think"))
        assertTrue("and who said it", text.contains("Grant"))
    }

    @Test
    fun `cue does not hear itself talking`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.quietUntil(2_000)
        assertEquals("Sarah", f.speaker.last)

        // Cue's own audio leaking into the mic must not register as human speech.
        f.engine.onRms(10f, 2_050)

        assertFalse("self-speech must not wedge the gap closed", f.gap.isSpeaking)
    }

    @Test
    fun `ending the session clears the roster and cached speech`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.quietUntil(2_000)

        f.engine.endSession()

        assertTrue(f.engine.snapshot().roster.isEmpty())
        f.earcons.played.clear()
        f.engine.onRepeatPressed(3_000)
        assertEquals("nothing may survive the glasses coming off", listOf(Earcon.FAILED), f.earcons.played)
    }

    @Test
    fun `losing the glasses is announced`() {
        val f = Fixture()
        f.engine.onGlassesLost()
        assertEquals(listOf(Earcon.GLASSES_LOST), f.earcons.played)
    }

    @Test
    fun `a mentioned name does not rename the person saying it`() {
        val f = Fixture()
        f.engine.onTranscript("speaker_0", "I'm Sarah", 0, 1_000)
        f.engine.onTranscript("speaker_0", "have you met Priya", 2_000, 3_000)

        assertEquals("Sarah", f.engine.snapshot().personFor("speaker_0")?.name)
    }
}
