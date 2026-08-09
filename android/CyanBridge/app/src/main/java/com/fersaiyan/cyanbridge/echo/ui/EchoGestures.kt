package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * The whole screen is one gesture surface. Five gestures:
 *
 * ```
 *   single tap              where am I
 *   double tap              primary action
 *   long press              help
 *   fling left / right      next / previous section
 *   two-finger double tap   repeat last result
 * ```
 *
 * The React Native build composed these with `Gesture.Race(...)` and
 * `Gesture.Exclusive(doubleTap, singleTap)`. Compose has no Race/Exclusive
 * combinator, so the arbitration is rebuilt here:
 *
 *  - `detectTapGestures` already gives single/double exclusivity for free —
 *    `onTap` only fires once the double-tap window has lapsed, which is
 *    exactly what `Gesture.Exclusive` bought us.
 *  - The two-finger detector runs on [PointerEventPass.Initial] so it sees
 *    events *before* the tap detector, and consumes them once a second
 *    pointer appears. Without that consumption a two-finger double tap also
 *    registers as a one-finger double tap and fires the primary action —
 *    i.e. it would start a capture every time the user asked to repeat one.
 *  - Drag needs to clear touch slop before it reports, so a tap can never be
 *    mistaken for a fling.
 *
 * Handlers are passed as lambdas reading straight off the ViewModel, so there
 * is no stale-closure problem of the kind the JS version needed refs for.
 */
private const val TWO_FINGER_DOUBLE_TAP_WINDOW_MS = 350L
private val SWIPE_THRESHOLD = 60.dp

fun Modifier.echoGestures(
    onPrimary: () -> Unit,
    onWhereAmI: () -> Unit,
    onLongPress: () -> Unit,
    onRepeat: () -> Unit,
    onSwipe: (next: Boolean) -> Unit,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    // TalkBack is running and owns the touch screen: it consumes exploration
    // touches and re-issues only a synthesized click on the focused node, so
    // none of these detectors would ever fire. Attaching them anyway is not
    // merely useless — the Initial-pass consumption in the two-finger detector
    // interferes with TalkBack's own gesture handling. The vocabulary is
    // published as custom accessibility actions instead; see EchoAccessibility.
    this
} else {
    this
    // Initial pass — must be declared first so it wins arbitration.
    .pointerInput(Unit) { detectTwoFingerDoubleTap(onRepeat) }
    .pointerInput(Unit) {
        val threshold = SWIPE_THRESHOLD.toPx()
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) >= threshold) {
                    // Fling left = advance, matching Directions.LEFT -> "next".
                    onSwipe(total < 0)
                }
                total = 0f
            },
            onDragCancel = { total = 0f },
        ) { _, dragAmount -> total += dragAmount }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onWhereAmI() },
            onDoubleTap = { onPrimary() },
            onLongPress = { onLongPress() },
        )
    }
}

/**
 * Fires when two taps, each made with two or more fingers, land within
 * [TWO_FINGER_DOUBLE_TAP_WINDOW_MS] of each other.
 *
 * Runs on the Initial pass and consumes every change once a gesture has been
 * claimed as multi-touch, so the single-finger tap detector downstream never
 * sees it.
 */
private suspend fun PointerInputScope.detectTwoFingerDoubleTap(onDetected: () -> Unit) {
    awaitPointerEventScope {
        var multiTouch = false
        var lastTapEndedAt = 0L

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }

            if (pressed >= 2) multiTouch = true
            if (multiTouch) event.changes.forEach { it.consume() }

            if (pressed == 0) {
                if (multiTouch) {
                    val now = System.currentTimeMillis()
                    if (lastTapEndedAt != 0L &&
                        now - lastTapEndedAt <= TWO_FINGER_DOUBLE_TAP_WINDOW_MS
                    ) {
                        onDetected()
                        lastTapEndedAt = 0L
                    } else {
                        lastTapEndedAt = now
                    }
                }
                multiTouch = false
            }
        }
    }
}
