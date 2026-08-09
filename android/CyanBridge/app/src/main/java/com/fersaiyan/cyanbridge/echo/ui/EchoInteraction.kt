package com.fersaiyan.cyanbridge.echo.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Interaction primitives shared across ECHO.
 *
 * Three things were missing from the visual layer, and each is an accessibility gap rather
 * than a polish item:
 *
 * 1. **Nothing showed keyboard or switch focus.** `EchoColors.focus` existed but was only
 *    ever used decoratively. Switch Access and keyboard navigation are heavily used by
 *    people with combined motor and vision impairments, and without a focus ring they are
 *    driving blind in the literal sense.
 * 2. **Nothing moved.** Motion is a strong, pre-attentive state signal for someone with
 *    usable but limited vision — far easier to catch than a word change. It has to be
 *    opt-out, though, because motion is also a migraine and vestibular trigger.
 * 3. **Pressed state relied on the default ripple**, which is nearly invisible on a
 *    near-black surface.
 */

/**
 * True when the user has switched animations off system-wide.
 *
 * Honouring this is not optional politeness: for people with vestibular disorders, motion
 * sickness and migraine, animation is a symptom trigger. Android exposes the preference as
 * an animator duration scale of zero.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * A visible focus ring.
 *
 * Drawn outside the component's own border so it reads as a distinct ring rather than a
 * colour change, which matters when the underlying border is already coloured to mean
 * something else — a focused danger button must still look dangerous.
 */
@Composable
fun Modifier.echoFocusRing(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    width: Dp = 3.dp,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return this.border(
        width = if (focused) width else 0.dp,
        color = if (focused) EchoColors.focus else Color.Transparent,
        shape = shape,
    )
}

/**
 * Pressed-state scale.
 *
 * A ripple on a near-black surface is almost invisible, so the confirmation that a tap
 * registered is carried by geometry instead of colour. Small on purpose — this is feedback,
 * not decoration — and it collapses to no movement when the user has asked for that.
 */
@Composable
fun rememberPressScale(interactionSource: MutableInteractionSource): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    val target = if (pressed && !reduceMotion) PRESSED_SCALE else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 90),
        label = "echo-press-scale",
    )
    return scale
}

/**
 * A slow breathing scale for a control that is actively working.
 *
 * Paired with, never a replacement for, the spoken status and the text label — it is a third
 * redundant channel for the same fact, which is what lets it be safely dropped when the user
 * has motion disabled.
 *
 * The hooks run unconditionally and the result is chosen afterwards, because a composable
 * cannot skip a `remember` on some paths and not others.
 */
@Composable
fun rememberWorkingPulse(active: Boolean): Float {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "echo-working")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_SCALE,
        animationSpec = infiniteRepeatable(
            // Slow and shallow. A fast pulse reads as an alarm, and this state is
            // "thinking", not "danger".
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "echo-working-scale",
    )
    return if (active && !reduceMotion) pulse else 1f
}

private const val PRESSED_SCALE = 0.97f
private const val PULSE_SCALE = 1.035f
