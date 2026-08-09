package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Transient confirmation that a gesture was recognised.
 *
 * From the design's `#toast`: a pill above the nav bar that fades in, holds
 * briefly, and fades out. It names what just happened — "Help", "Repeat",
 * "History" — so a sighted or partially-sighted user gets immediate proof the
 * gesture landed, instead of waiting for a sentence of speech to finish.
 *
 * ## Why it is hidden from screen readers
 *
 * The design marks this `aria-hidden="true"`, and that is not an oversight —
 * it is the same rule the rest of the app follows. Every gesture that raises a
 * toast *also* speaks through `EchoSpeech`. If the toast were exposed to
 * TalkBack it would announce the identical words a second time, which is the
 * exact double-speaking failure this codebase is built to avoid.
 *
 * [clearAndSetSemantics] with an empty block removes it from the accessibility
 * tree entirely — stronger than `contentDescription = null`, which would still
 * leave a focusable, silent node in the traversal order for a user to land on
 * and hear nothing.
 *
 * So: the toast is redundant *by design*. It is the visual channel of a message
 * that is already carried by audio and haptics. Removing it costs a blind user
 * nothing and costs a low-vision user real feedback.
 */
@Composable
fun BoxScope.EchoToast(
    message: String?,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val visible = message != null
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // Design: `transition: opacity .2s`, disabled under reduced motion.
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else 200,
            easing = LinearEasing,
        ),
        label = "toastAlpha",
    )

    // Keep the last message while fading out so the text does not vanish a
    // frame before the pill does.
    if (alpha <= 0f && message == null) return

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .alpha(alpha)
            .clip(EchoShapes.pill)
            .background(EchoColors.surface2)
            .border(1.dp, EchoColors.border, EchoShapes.pill)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            // Not merely undescribed — removed from the tree. See the note above.
            .clearAndSetSemantics { },
    ) {
        Text(
            text = message.orEmpty(),
            style = EchoText.toast,
            textAlign = TextAlign.Center,
        )
    }
}
