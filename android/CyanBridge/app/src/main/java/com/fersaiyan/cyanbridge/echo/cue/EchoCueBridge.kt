package com.fersaiyan.cyanbridge.echo.cue

import com.fersaiyan.cyanbridge.cue.ConversationContext
import com.fersaiyan.cyanbridge.cue.Person
import com.fersaiyan.cyanbridge.cue.RosterBriefing
import com.fersaiyan.cyanbridge.cue.output.Earcon
import com.fersaiyan.cyanbridge.echo.audio.HapticPattern
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter between ECHO's UI and the Cue decision layer.
 *
 * ECHO and Cue were designed independently and arrived at the same product:
 * ambient awareness for someone who cannot see the room. Rather than let them
 * drift into two vocabularies for one idea, the UI is rebuilt on Cue's model —
 * [Person], [ConversationContext] and [Earcon] are the source of truth, and
 * ECHO renders them.
 *
 * Two rules Cue establishes that the UI must not violate:
 *
 *  - **A person is named or "someone new" — never described.** Cue is explicit
 *    that it does not say "a tall man in a blue shirt". The UI must not
 *    reintroduce descriptions through a label, a subtitle or an avatar.
 *  - **Silence is indistinguishable from an empty room.** A disconnected state
 *    has to be shown *and* sounded. Rendering nothing is a bug, not a neutral
 *    default.
 */

/**
 * Maps Cue's earcon vocabulary onto ECHO's haptic vocabulary.
 *
 * The two channels have to agree: a user who has learned that two ascending
 * notes mean "someone arrived" must not feel the error buzz at the same moment.
 * Cue owns the meaning; ECHO's haptics follow it rather than defining a second,
 * conflicting set.
 *
 * `null` means "no haptic" — [Earcon.WORKING] is deliberately silent to the
 * skin because it fires during latency the user is already waiting through, and
 * a buzz there reads as a result arriving.
 */
fun Earcon.toHaptic(): HapticPattern? = when (this) {
    Earcon.PERSON_ENTERED -> HapticPattern.Confirm
    Earcon.PERSON_LEFT -> HapticPattern.Nav
    Earcon.ADDRESSED_DIRECTLY -> HapticPattern.Result
    Earcon.AWAITING_RESPONSE -> HapticPattern.Result
    Earcon.WORKING -> null
    Earcon.FAILED -> HapticPattern.Error
    Earcon.DEVICE_BUSY -> HapticPattern.Error
    Earcon.GLASSES_LOST -> HapticPattern.Error
}

/**
 * The seam between ECHO's UI and a live conversation.
 *
 * Today this is satisfied by the scripted demo source. The real implementation
 * wraps `CueEngine` — its `snapshot()` already returns exactly the
 * [ConversationContext] this exposes, so swapping them changes no UI code.
 *
 * What the real one additionally needs, and the demo does not: microphone
 * permission, a transcription provider feeding `onTranscript`, and a ~100ms
 * `pump()` tick. Those are the gap between this screen and a working Cue.
 */
interface EchoCueSource {
    val context: StateFlow<ConversationContext>
    val isRunning: StateFlow<Boolean>

    fun start()
    fun stop()
}

/** The spoken "who's here" answer, straight from Cue. Never regenerated locally. */
fun ConversationContext.briefing(): String = RosterBriefing.format(this)

/**
 * How a roster row reads to a screen reader.
 *
 * Ordering is deliberate: the name comes first because it is the only part a
 * user is listening for, and presence is stated as a state rather than folded
 * into the label so TalkBack reports it as a changing property.
 */
fun Person.rosterContentDescription(): String = buildString {
    append(spokenLabel)
    append(if (isPresent) ", here" else ", left")
}

/** Row title. [Person.spokenLabel] is already "someone new" for the unnamed. */
fun Person.rosterTitle(): String = spokenLabel

/** Row detail. Kept factual — never a physical description. */
fun Person.rosterDetail(): String =
    if (isPresent) "In the room" else "Left the room"
