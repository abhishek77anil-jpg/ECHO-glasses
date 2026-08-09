package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.audio.GapDetector
import com.fersaiyan.cyanbridge.cue.output.CueSpeaker
import com.fersaiyan.cyanbridge.cue.output.Earcon
import com.fersaiyan.cyanbridge.cue.output.EarconSink
import com.fersaiyan.cyanbridge.cue.output.OutputArbiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interruption budget (PRD §5.1, §5.2, §15).
 *
 * The single assertion that matters across this whole file: Cue never speaks a whisper
 * while a human is speaking.
 */
class OutputArbiterTest {

    private class FakeSpeaker : CueSpeaker {
        val spoken = mutableListOf<String>()
        val rates = mutableListOf<Float>()
        var stopCount = 0
        private var pendingDone: (() -> Unit)? = null

        override fun speak(text: String, rate: Float, utteranceId: String, onDone: () -> Unit) {
            spoken += text
            rates += rate
            pendingDone = onDone
        }

        override fun stop() {
            stopCount++
            pendingDone?.invoke()
            pendingDone = null
        }

        /** Simulates the TTS engine finishing an utterance normally. */
        fun finish() {
            pendingDone?.invoke()
            pendingDone = null
        }

        val last: String? get() = spoken.lastOrNull()
    }

    private class FakeEarcons : EarconSink {
        val played = mutableListOf<Earcon>()
        override fun play(earcon: Earcon) {
            played += earcon
        }
    }

    private class Fixture {
        val speaker = FakeSpeaker()
        val earcons = FakeEarcons()
        val gap = GapDetector()
        var now = 0L
        val arbiter = OutputArbiter(
            speaker = speaker,
            earcons = earcons,
            gapDetector = gap,
            clock = { now },
        )

        /** Drives the detector into "someone is talking". */
        fun speechStarts(at: Long) {
            now = at
            gap.onRms(0f, at - 1)
            val transition = gap.onRms(10f, at)
            arbiter.onSpeechTransition(transition, at)
        }

        /** Drives the detector into silence. */
        fun speechEnds(at: Long) {
            now = at
            val transition = gap.onRms(0f, at)
            arbiter.onSpeechTransition(transition, at)
        }

        /** Keeps the RMS stream alive so the staleness guard does not close the gap. */
        fun idleUntil(at: Long) {
            var t = now
            while (t < at) {
                t = minOf(at, t + 200)
                gap.onRms(0f, t)
            }
            now = at
            arbiter.pump(at)
        }
    }

    @Test
    fun `whisper waits for a gap and never lands on speech`() {
        val f = Fixture()
        f.speechStarts(1_000)

        f.arbiter.offerWhisper("Sarah", nowMs = 1_050, dedupeKey = "sarah")
        f.arbiter.pump(1_100)
        assertNull("must not speak while a human is talking", f.speaker.last)

        f.speechEnds(1_200)
        f.arbiter.pump(1_300)
        assertNull("400ms of silence has not elapsed yet", f.speaker.last)

        f.idleUntil(1_650)
        assertEquals("Sarah", f.speaker.last)
        assertEquals(0, f.arbiter.stats.interruptions)
    }

    @Test
    fun `whisper is aborted mid-word when speech resumes`() {
        val f = Fixture()
        f.speechEnds(100)
        f.idleUntil(600)
        f.arbiter.offerWhisper("Priya", nowMs = 600, dedupeKey = "priya")
        f.arbiter.pump(600)
        assertEquals("Priya", f.speaker.last)

        f.speechStarts(700)

        assertEquals("in-flight whisper must be cut", 1, f.speaker.stopCount)
        assertEquals(1, f.arbiter.stats.whispersAborted)
    }

    @Test
    fun `stale whisper is dropped rather than queued`() {
        val f = Fixture()
        f.speechStarts(0)
        f.arbiter.offerWhisper("Sarah", nowMs = 100, dedupeKey = "sarah")

        // The gap never comes in time.
        f.arbiter.pump(100 + OutputArbiter.DEFAULT_WHISPER_TTL_MS + 1)

        assertNull(f.speaker.last)
        assertEquals(1, f.arbiter.stats.whispersDroppedStale)
    }

    @Test
    fun `consecutive duplicate whispers are suppressed`() {
        val f = Fixture()
        f.speechEnds(100)
        f.idleUntil(600)
        f.arbiter.offerWhisper("Sarah", nowMs = 600, dedupeKey = "sarah")
        f.arbiter.pump(600)
        assertEquals(1, f.speaker.spoken.size)
        f.speaker.finish()

        f.arbiter.offerWhisper("Sarah", nowMs = 700, dedupeKey = "sarah")
        f.idleUntil(3_000)

        assertEquals("same speaker twice running should stay quiet", 1, f.speaker.spoken.size)
        assertEquals(1, f.arbiter.stats.whispersDroppedDuplicate)
    }

    @Test
    fun `a newer whisper replaces an older pending one`() {
        val f = Fixture()
        f.speechStarts(0)
        f.arbiter.offerWhisper("Sarah", nowMs = 100, dedupeKey = "sarah")
        f.arbiter.offerWhisper("Priya", nowMs = 200, dedupeKey = "priya")

        f.speechEnds(300)
        f.idleUntil(800)

        assertEquals("the newer fact wins", "Priya", f.speaker.last)
        assertEquals(1, f.speaker.spoken.size)
    }

    @Test
    fun `briefing starts immediately and outranks a pending whisper`() {
        val f = Fixture()
        f.speechStarts(1_000)
        f.arbiter.offerWhisper("Sarah", nowMs = 1_050, dedupeKey = "sarah")

        f.arbiter.speakBriefing("Sarah and Priya are here", nowMs = 1_100)

        assertEquals("Sarah and Priya are here", f.speaker.last)
        assertEquals(1, f.arbiter.stats.briefingsSpoken)
        assertEquals("user-initiated, so tracked separately", 1, f.arbiter.stats.briefingsOverSpeech)
        assertEquals("briefings are not whisper interruptions", 0, f.arbiter.stats.interruptions)

        // The pending whisper was discarded, not deferred.
        f.speechEnds(1_200)
        f.speaker.finish()
        f.idleUntil(2_000)
        assertEquals("Sarah and Priya are here", f.speaker.last)
    }

    @Test
    fun `interrupt stops speech immediately`() {
        val f = Fixture()
        f.arbiter.speakBriefing("A long briefing about the room", nowMs = 100)
        assertEquals(0, f.speaker.stopCount)

        f.arbiter.interrupt()

        assertEquals(1, f.speaker.stopCount)
    }

    @Test
    fun `repeat last replays from cache without regenerating`() {
        val f = Fixture()
        f.speechEnds(100)
        f.idleUntil(600)
        f.arbiter.offerWhisper("Sarah", nowMs = 600, dedupeKey = "sarah")
        f.arbiter.pump(600)
        f.speaker.finish()

        assertTrue(f.arbiter.repeatLast(nowMs = 5_000))

        assertEquals(2, f.speaker.spoken.size)
        assertEquals("Sarah", f.speaker.last)
    }

    @Test
    fun `repeat last reports failure when there is nothing to repeat`() {
        val f = Fixture()
        assertFalse(f.arbiter.repeatLast(nowMs = 1_000))
        assertTrue(f.speaker.spoken.isEmpty())
    }

    @Test
    fun `earcons are never gated by speech`() {
        val f = Fixture()
        f.speechStarts(1_000)

        f.arbiter.emitEarcon(Earcon.PERSON_ENTERED)

        assertEquals(listOf(Earcon.PERSON_ENTERED), f.earcons.played)
        assertEquals(1, f.arbiter.stats.earconsPlayed)
    }

    @Test
    fun `whispers respect the minimum interval`() {
        val f = Fixture()
        f.speechEnds(100)
        f.idleUntil(600)
        f.arbiter.offerWhisper("Sarah", nowMs = 600, dedupeKey = "sarah")
        f.arbiter.pump(600)
        f.speaker.finish()
        assertEquals(1, f.speaker.spoken.size)

        // A different person, well inside the rate-limit floor.
        f.arbiter.offerWhisper("Priya", nowMs = 700, dedupeKey = "priya")
        f.idleUntil(900)
        assertEquals("rate limit should hold it back", 1, f.speaker.spoken.size)
    }

    @Test
    fun `self speech window suppresses the mic while cue is talking`() {
        val f = Fixture()
        f.arbiter.speakBriefing("Sarah is here", nowMs = 100)
        assertTrue("mic must be distrusted while Cue talks", f.arbiter.isSelfSpeaking(150))

        f.now = 200
        f.speaker.finish()

        assertTrue("tail still active", f.arbiter.isSelfSpeaking(300))
        assertFalse(
            "tail expires",
            f.arbiter.isSelfSpeaking(200 + OutputArbiter.DEFAULT_SELF_SPEECH_TAIL_MS + 1),
        )
    }

    @Test
    fun `reset clears cached speech so nothing survives the session`() {
        val f = Fixture()
        f.speechEnds(100)
        f.idleUntil(600)
        f.arbiter.offerWhisper("Sarah", nowMs = 600, dedupeKey = "sarah")
        f.arbiter.pump(600)
        f.speaker.finish()

        f.arbiter.reset()

        assertFalse("repeat-last must not leak across sessions", f.arbiter.repeatLast(nowMs = 700))
    }
}
