package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.audio.GapDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gap detector is the component PRD §5.2 says to build best, so these tests pin the
 * behaviour that matters: silence must be confirmed, never assumed.
 */
class GapDetectorTest {

    private fun detector() = GapDetector()

    @Test
    fun `reports no gap before any audio has been seen`() {
        // Never having heard anything is not the same as silence.
        assertFalse(detector().isGapOpen(nowMs = 10_000))
    }

    @Test
    fun `quiet room with no speech yet counts as a gap`() {
        val gap = detector()
        gap.onRms(0f, nowMs = 0)
        assertTrue(gap.isGapOpen(nowMs = 100))
    }

    @Test
    fun `detects speech starting and ending`() {
        val gap = detector()
        assertEquals(GapDetector.Transition.NONE, gap.onRms(0f, 0))
        assertEquals(GapDetector.Transition.SPEECH_STARTED, gap.onRms(10f, 100))
        assertEquals(GapDetector.Transition.NONE, gap.onRms(9f, 200))
        assertEquals(GapDetector.Transition.SPEECH_ENDED, gap.onRms(0f, 300))
    }

    @Test
    fun `gap stays closed until the required silence has elapsed`() {
        val gap = detector()
        gap.onRms(0f, 0)
        gap.onRms(10f, 100)
        gap.onRms(0f, 300)

        assertFalse("gap must not open immediately after speech", gap.isGapOpen(400))
        assertFalse(gap.isGapOpen(699))
        assertTrue("gap opens at 400ms of silence", gap.isGapOpen(700))
    }

    @Test
    fun `no gap while speech is active`() {
        val gap = detector()
        gap.onRms(0f, 0)
        gap.onRms(10f, 100)
        assertFalse(gap.isGapOpen(5_000))
    }

    @Test
    fun `hysteresis keeps a dip inside one sentence from ending speech`() {
        val gap = detector()
        gap.onRms(0f, 0)
        gap.onRms(10f, 100)
        // Above the release margin but below the speech margin: still talking.
        assertEquals(GapDetector.Transition.NONE, gap.onRms(3f, 150))
        assertTrue(gap.isSpeaking)
    }

    @Test
    fun `stale samples close the gap rather than guessing`() {
        val gap = detector()
        gap.onRms(0f, 0)
        gap.onRms(10f, 100)
        gap.onRms(0f, 300)
        assertTrue(gap.isGapOpen(700))

        // The RMS stream died. Silence in the data is not silence in the room.
        assertFalse(gap.isGapOpen(300 + GapDetector.DEFAULT_STALE_SAMPLE_MS + 1))
    }

    @Test
    fun `noise floor adapts to a loud room so speech is still detectable`() {
        val gap = detector()
        // Room noise sits at 20, well above the default margins.
        repeat(60) { i -> gap.onRms(20f, i * 10L) }
        assertFalse("steady room noise must not read as speech", gap.isSpeaking)

        val floor = gap.noiseFloor ?: error("floor should be seeded")
        assertTrue("floor should have risen toward room level, was $floor", floor > 15f)

        // Speech still stands out above the adapted floor.
        assertEquals(GapDetector.Transition.SPEECH_STARTED, gap.onRms(30f, 1_000))
    }

    @Test
    fun `reset clears adaptation and state`() {
        val gap = detector()
        gap.onRms(0f, 0)
        gap.onRms(10f, 100)
        assertTrue(gap.isSpeaking)

        gap.reset()

        assertFalse(gap.isSpeaking)
        assertFalse(gap.isGapOpen(10_000))
    }
}
