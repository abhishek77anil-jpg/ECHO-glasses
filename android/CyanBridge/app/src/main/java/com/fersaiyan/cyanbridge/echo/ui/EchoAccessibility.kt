package com.fersaiyan.cyanbridge.echo.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

/**
 * Screen-reader support for ECHO.
 *
 * ## The problem this file exists to solve
 *
 * ECHO's whole interaction model is a full-screen gesture surface: double tap
 * anywhere to analyze, swipe to move between sections, two-finger double tap
 * to repeat. That model is excellent for a blind user who is *not* running a
 * screen reader, and completely dead for one who is.
 *
 * When TalkBack is active it owns the touch screen. It consumes exploration
 * touches and re-issues only a synthesized click on the focused node, so a
 * `pointerInput` block never sees a double tap, a fling, or a second finger.
 * Every custom gesture in [echoGestures] silently stops firing — for exactly
 * the users the app was built for. Testing on a sighted developer's phone with
 * TalkBack off hides this completely.
 *
 * ## The fix
 *
 * Two parallel input models, chosen by [rememberScreenReaderState]:
 *
 *  - **TalkBack off** — the gesture surface, as designed.
 *  - **TalkBack on** — every action is reachable as a real focusable control,
 *    and the gesture vocabulary is additionally published as *custom
 *    accessibility actions* on the root. TalkBack surfaces those in its local
 *    context menu (swipe up-then-right), so "repeat last result" and "where am
 *    I" stay available without competing with TalkBack's own gestures.
 *
 * The rule for the whole package: never build an interaction that only exists
 * as a raw touch gesture. If it cannot be reached by focus or by a custom
 * action, a TalkBack user cannot reach it at all.
 */

/**
 * Live TalkBack state. Recomposes when the user toggles it while ECHO is
 * foregrounded — Settings and Help both change what they say, and the input
 * model itself switches, so this cannot be read once at startup.
 */
@Composable
fun rememberScreenReaderState(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isScreenReaderOn(context)) }

    DisposableEffect(context) {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val touchExploration =
            AccessibilityManager.TouchExplorationStateChangeListener {
                state.value = isScreenReaderOn(context)
            }
        // Touch exploration alone is not enough: turning the whole service off
        // does not always fire a touch-exploration change on every OEM build.
        val accessibilityEnabled =
            AccessibilityManager.AccessibilityStateChangeListener {
                state.value = isScreenReaderOn(context)
            }

        manager.addTouchExplorationStateChangeListener(touchExploration)
        manager.addAccessibilityStateChangeListener(accessibilityEnabled)
        state.value = isScreenReaderOn(context)

        onDispose {
            manager.removeTouchExplorationStateChangeListener(touchExploration)
            manager.removeAccessibilityStateChangeListener(accessibilityEnabled)
        }
    }
    return state
}

fun isScreenReaderOn(context: Context): Boolean {
    val manager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
    return manager.isEnabled && manager.isTouchExplorationEnabled
}

/**
 * Publishes ECHO's gesture vocabulary as custom accessibility actions.
 *
 * Applied to the root so the actions are available from anywhere in the app,
 * which is what makes them a real replacement for "this gesture works
 * anywhere on the screen" rather than a per-screen approximation.
 *
 * Labels are written as commands ("Repeat last result"), not descriptions,
 * because TalkBack reads them aloud as a menu the user is choosing from.
 */
fun Modifier.echoCustomActions(
    onRepeat: () -> Unit,
    onWhereAmI: () -> Unit,
    onHelp: () -> Unit,
    onNextSection: () -> Unit,
    onPreviousSection: () -> Unit,
): Modifier = this.semantics {
    isTraversalGroup = true
    customActions = listOf(
        CustomAccessibilityAction("Repeat last result") { onRepeat(); true },
        CustomAccessibilityAction("Where am I") { onWhereAmI(); true },
        CustomAccessibilityAction("Next section") { onNextSection(); true },
        CustomAccessibilityAction("Previous section") { onPreviousSection(); true },
        CustomAccessibilityAction("Speak help") { onHelp(); true },
    )
}

/**
 * Pins reading order. Compose infers traversal order from layout position,
 * which is usually right, but the capture target sits in the visual centre of
 * Home while being the *first* thing a user needs — so it is stated
 * explicitly rather than left to geometry.
 */
fun Modifier.echoTraversalOrder(index: Float): Modifier =
    this.semantics { traversalIndex = index }
