package com.fersaiyan.cyanbridge.echo.model

/**
 * The nav ring. Swiping left/right walks this list; the tab bar renders it.
 *
 * A result is deliberately NOT a view — it is a state of [Home], so there is
 * never a screen the user can land on that the swipe ring cannot reach.
 */
enum class EchoView(val tab: String, val label: String) {
    Home("Home", "Home. Double tap to analyze."),
    Live("Live", "Live awareness. Double tap to start or stop."),
    History("History", "History."),
    Settings("Audio", "Audio and haptics settings."),
    Help("Help", "Help. Long press anywhere also opens help.");

    fun next(): EchoView = EchoView.entries[(ordinal + 1) % EchoView.entries.size]

    fun prev(): EchoView =
        EchoView.entries[(ordinal - 1 + EchoView.entries.size) % EchoView.entries.size]
}

const val HELP_SPEECH =
    "Double tap the center of the screen to analyze. " +
        "Swipe left or right to move between sections. " +
        "Single tap tells you where you are. " +
        "Long press opens help. " +
        "Two finger double tap repeats the last result."

/**
 * What help says when TalkBack is running.
 *
 * The gesture vocabulary above is unreachable in that mode — TalkBack owns the
 * touch screen — so reciting it would be actively misleading. This describes
 * the mechanism that does work: focus for the controls, and TalkBack's own
 * local context menu for the actions that used to be gestures.
 */
const val HELP_SPEECH_SCREEN_READER =
    "TalkBack is running, so use its gestures to move around. " +
        "Swipe right and left to move between controls, and double tap to activate one. " +
        "For repeat last result, where am I, or to change section, " +
        "open TalkBack's actions menu by swiping up then right."

const val GREETING =
    "ECHO ready. Double tap the center of the screen to analyze. Long press for help."

/** The greeting, minus the gestures that TalkBack makes unavailable. */
const val GREETING_SCREEN_READER =
    "ECHO ready. Swipe to find the capture button, then double tap it to analyze."
