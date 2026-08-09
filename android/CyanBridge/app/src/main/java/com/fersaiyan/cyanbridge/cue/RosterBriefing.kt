package com.fersaiyan.cyanbridge.cue

/**
 * Builds the spoken "who's here" answer (PRD P0-4).
 *
 * Answered entirely from the in-memory roster, so it needs no network and still works during
 * a Wi-Fi transfer or in a dead zone. It is also the one Tier 2 output that must never take
 * more than a breath to say, so the phrasing stays flat: names, a count of strangers, done.
 */
object RosterBriefing {

    fun format(context: ConversationContext): String {
        val present = context.presentPeople
        if (present.isEmpty()) return "No one yet"

        val names = present.mapNotNull { it.name }
        val unnamedCount = present.size - names.size

        val unnamedPhrase = when (unnamedCount) {
            0 -> null
            1 -> "one other"
            else -> "$unnamedCount others"
        }

        if (names.isEmpty()) {
            // Everyone present is still a stranger.
            return when (unnamedCount) {
                1 -> "One person, no name yet"
                else -> "$unnamedCount people, no names yet"
            }
        }

        val parts = names + listOfNotNull(unnamedPhrase)
        return joinNaturally(parts)
    }

    /** "Sarah" / "Sarah and Priya" / "Sarah, Priya, and Grant". */
    private fun joinNaturally(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }
}
