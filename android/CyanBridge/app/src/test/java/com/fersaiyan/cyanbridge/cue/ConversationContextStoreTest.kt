package com.fersaiyan.cyanbridge.cue

import com.fersaiyan.cyanbridge.cue.context.ConversationContextStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rolling roster and transcript state (PRD §8, P0-2, P0-3). */
class ConversationContextStoreTest {

    private fun store(departureMs: Long = 45_000L) =
        ConversationContextStore(sessionStartMs = 0, departureSilenceMs = departureMs)

    @Test
    fun `a new voice enters the roster`() {
        val store = store()

        val events = store.onTurn("speaker_0", "hello there", 0, 1_000)

        assertEquals(1, events.size)
        val entered = events.first() as ConversationContextStore.Event.PersonEntered
        assertEquals("speaker_0", entered.person.speakerLabel)
        assertEquals("unnamed people are still useful", "someone new", entered.person.spokenLabel)
    }

    @Test
    fun `a known voice speaking again raises no event`() {
        val store = store()
        store.onTurn("speaker_0", "hello", 0, 1_000)

        val events = store.onTurn("speaker_0", "still here", 2_000, 3_000)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a silent voice is marked as left exactly once`() {
        val store = store(departureMs = 10_000)
        store.onTurn("speaker_0", "hello", 0, 1_000)

        assertTrue("too early to call it a departure", store.tick(5_000).isEmpty())

        val left = store.tick(11_000)
        assertEquals(1, left.size)
        assertTrue(left.first() is ConversationContextStore.Event.PersonLeft)

        assertTrue("must not repeat while they stay silent", store.tick(20_000).isEmpty())
    }

    @Test
    fun `someone who left and speaks again re-enters`() {
        val store = store(departureMs = 10_000)
        store.onTurn("speaker_0", "hello", 0, 1_000)
        store.tick(11_000)

        val events = store.onTurn("speaker_0", "back again", 12_000, 13_000)

        assertEquals(1, events.size)
        assertTrue(events.first() is ConversationContextStore.Event.PersonEntered)
        assertTrue(store.snapshot().personFor("speaker_0")!!.isPresent)
    }

    @Test
    fun `binding a name emits an event and is idempotent`() {
        val store = store()
        store.onTurn("speaker_0", "I'm Sarah", 0, 1_000)

        val named = store.bindName("speaker_0", "Sarah")
        assertTrue(named is ConversationContextStore.Event.PersonNamed)
        assertEquals("Sarah", store.snapshot().personFor("speaker_0")?.name)

        assertNull("re-binding the same name is a no-op", store.bindName("speaker_0", "Sarah"))
    }

    @Test
    fun `binding a name to an unknown speaker is ignored`() {
        assertNull(store().bindName("speaker_9", "Ghost"))
    }

    @Test
    fun `transcript is pruned continuously to the retention window`() {
        val store = store()
        store.onTurn("speaker_0", "old news", 0, 1_000)
        store.onTurn("speaker_0", "recent news", 61_000, 62_000)

        val turns = store.snapshot().turns

        assertEquals("the 60s buffer is discarded as it rolls", 1, turns.size)
        assertEquals("recent news", turns.first().text)
    }

    @Test
    fun `prompt window uses names and only the last thirty seconds`() {
        val store = store()
        store.onTurn("speaker_0", "way earlier", 0, 1_000)
        store.onTurn("speaker_0", "I'm Sarah", 40_000, 41_000)
        store.bindName("speaker_0", "Sarah")
        store.onTurn("speaker_1", "hi Sarah", 42_000, 43_000)

        val text = store.snapshot().transcriptWindowText(nowMs = 43_000)

        assertEquals("Sarah: I'm Sarah\nspeaker_1: hi Sarah", text)
    }

    @Test
    fun `present people exclude those who have left`() {
        val store = store(departureMs = 10_000)
        store.onTurn("speaker_0", "hello", 0, 1_000)
        store.onTurn("speaker_1", "hi", 500, 1_500)
        store.bindName("speaker_0", "Sarah")
        store.bindName("speaker_1", "Priya")

        store.onTurn("speaker_1", "still talking", 9_000, 10_000)
        store.tick(12_000)

        val snapshot = store.snapshot()
        assertEquals(listOf("Priya"), snapshot.namedPresentPeople.map { it.name })
        assertFalse(snapshot.personFor("speaker_0")!!.isPresent)
    }

    @Test
    fun `reset clears everything when the glasses come off`() {
        val store = store()
        store.onTurn("speaker_0", "hello", 0, 1_000)
        store.bindName("speaker_0", "Sarah")

        store.reset()

        val snapshot = store.snapshot()
        assertTrue(snapshot.roster.isEmpty())
        assertTrue(snapshot.turns.isEmpty())
    }
}
