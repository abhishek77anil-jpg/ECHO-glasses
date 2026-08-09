package com.fersaiyan.cyanbridge.echo.service

import com.fersaiyan.cyanbridge.echo.model.LiveEvent
import com.fersaiyan.cyanbridge.echo.model.LiveKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Live awareness — the ambient channel. Scripted for the demo, but shaped
 * exactly like the real thing will be: a stream you collect and cancel, that
 * pushes events at you as they happen.
 *
 * HARDWARE SEAM: replace the scripted timer with the glasses' continuous
 * scene stream. Keep the [LiveEvent] shape and the UI needs no changes.
 *
 * Modelled as a cold [Flow] rather than the JS start/stop handle — collecting
 * is "start", cancelling the collection is "stop", so the session cannot leak
 * a running timer the way a forgotten stop() could.
 */
private val PEOPLE = listOf("Aarav", "Meera", "Rohan", "Sana", "Vikram")

private val LIVE_SCRIPT = listOf(
    Triple(
        "Room scan",
        LiveKind.Attn,
        "In this room: ${PEOPLE.take(3).joinToString(", ")}. ${PEOPLE[0]} is closest, on your left.",
    ),
    Triple("Speaker", LiveKind.Attn, "${PEOPLE[1]} is speaking."),
    Triple("Arrived", LiveKind.Arrive, "${PEOPLE[3]} just arrived, near the door."),
    Triple("Speaker", LiveKind.Attn, "${PEOPLE[3]} is speaking now."),
    Triple(
        "Waiting on you",
        LiveKind.Attn,
        "${PEOPLE[0]} is facing you. They seem to be waiting for you to respond.",
    ),
    Triple("Left", LiveKind.Leave, "${PEOPLE[2]} left the room."),
)

fun liveSessionFlow(
    firstDelayMs: Long = 1200L,
    minGapMs: Long = 6000L,
    jitterMs: Long = 3000L,
): Flow<LiveEvent> = flow {
    delay(firstDelayMs)
    var idx = 0
    while (true) {
        val (label, kind, text) = LIVE_SCRIPT[idx % LIVE_SCRIPT.size]
        idx += 1
        emit(LiveEvent(label = label, kind = kind, text = text, time = System.currentTimeMillis()))
        delay(minGapMs + Random.nextLong(jitterMs))
    }
}
