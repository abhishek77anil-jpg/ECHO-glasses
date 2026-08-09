package com.fersaiyan.cyanbridge.cue.output

/**
 * Tier 0 output: non-speech audio, under 200ms, no language processing (PRD §5.1).
 *
 * Each earcon is defined as a short tone sequence rather than an audio asset so the whole
 * vocabulary can be synthesized to PCM once at startup and played from cache afterwards.
 * The PRD's 200ms event-to-earcon budget rules out synthesizing at play time.
 *
 * The shapes are deliberately iconic and mirror each other — ascending means added,
 * descending means removed — because the vocabulary has to be learnable in about ten
 * minutes and then cost no attention at all.
 */
enum class Earcon(
    val tones: List<Tone>,
    val description: String,
) {
    /** Two ascending notes: "something got added". */
    PERSON_ENTERED(
        tones = listOf(Tone(660f, 70), Tone(880f, 70)),
        description = "New person entered",
    ),

    /** Two descending notes: the mirror of entering. */
    PERSON_LEFT(
        tones = listOf(Tone(880f, 70), Tone(660f, 70)),
        description = "Person left",
    ),

    /** Single soft chime, directional metaphor. */
    ADDRESSED_DIRECTLY(
        tones = listOf(Tone(990f, 120)),
        description = "Someone is addressing you directly",
    ),

    /** Slow double pulse. The ambiguity is the point: it means "your turn". */
    AWAITING_RESPONSE(
        tones = listOf(Tone(520f, 90), Tone(0f, 90), Tone(520f, 90)),
        description = "Someone is waiting for you to respond",
    ),

    /** Rising tick, used to cover the photo-path latency. */
    WORKING(
        tones = listOf(Tone(740f, 45), Tone(0f, 30), Tone(920f, 45)),
        description = "Cue is working on your request",
    ),

    /** Low muted thud. Replaces ever saying "sorry, I didn't catch that". */
    FAILED(
        tones = listOf(Tone(220f, 140)),
        description = "Cue failed or is unsure",
    ),

    /** Short flat buzz. Distinct from failure: the command was rejected, not wrong (PRD §6.5). */
    DEVICE_BUSY(
        tones = listOf(Tone(320f, 60), Tone(0f, 25), Tone(320f, 60)),
        description = "Glasses busy, command rejected",
    ),

    /**
     * Three descending notes. The user must know the system has gone blind: silence is
     * indistinguishable from an empty room, and that ambiguity is dangerous (PRD §5.1).
     */
    GLASSES_LOST(
        tones = listOf(Tone(880f, 90), Tone(660f, 90), Tone(440f, 130)),
        description = "Cue lost the glasses",
    );

    /** Total duration. Kept under the 200ms budget for the high-frequency earcons. */
    val durationMs: Int get() = tones.sumOf { it.durationMs }

    /** One tone step. A frequency of zero is a rest, used to shape pulses. */
    data class Tone(val frequencyHz: Float, val durationMs: Int) {
        val isRest: Boolean get() = frequencyHz <= 0f
    }
}
