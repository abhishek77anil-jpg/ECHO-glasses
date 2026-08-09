package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ECHO palette. Deliberately high-contrast on near-black: the app is used by
 * people with low vision as often as by people with none, and every surface
 * here clears WCAG AA against [EchoColors.bg].
 *
 * Originally ported 1:1 from echo/src/theme/colors.js. It has since diverged: three tokens
 * were measured against WCAG and corrected (see each below). **Port these values back to the
 * JS before syncing in the other direction**, or the fixes will be silently reverted.
 */
object EchoColors {
    val bg = Color(0xFF080808)
    val surface = Color(0xFF111111)
    val surface2 = Color(0xFF181818)

    /**
     * Outline of the capture control. Was #2A2A2A, which measured **1.32:1** against
     * [surface] — WCAG 1.4.11 requires 3:1 for a control's boundary, so the edge of the
     * app's single most important button was effectively invisible to anyone with reduced
     * contrast sensitivity. Now 3.5:1.
     */
    val captureBorder = Color(0xFF6B6B6B)

    val text = Color(0xFFFFFFFF)

    /**
     * Secondary text. Was #A0A0A0, which cleared AA but landed at 6.7:1 on [surface2] —
     * short of the 7:1 AAA bar for body text. Raised to 8.4:1 there and 9.6:1 on [bg].
     * AA is the floor for a general app; this one's users are the reason AAA exists.
     */
    val text2 = Color(0xFFB4B4B4)

    /**
     * Component borders. Was #333333, measuring **1.50:1** against [surface] and failing
     * the same non-text contrast rule as [captureBorder]. Now 3.3:1.
     */
    val border = Color(0xFF666666)
    val focus = Color(0xFF7DD3FC)
    val ok = Color(0xFF4ADE80)
    val warn = Color(0xFFFBBF24)
    val danger = Color(0xFFF87171)

    /** Subtitle colour on a primary (inverted) button. */
    val onPrimarySub = Color(0xFF333333)
}

/**
 * Every touch target here is >= 56dp on its short edge. That is the single
 * most important visual rule in this app — do not shrink them.
 */
object EchoDimens {
    val minTouch = 56.dp

    /**
     * Capture target. The design specifies `min(72vw, 300px)` — it scales with
     * the screen instead of being pinned, so it stays thumb-findable on a small
     * phone and does not become absurd on a tablet. Applied as a fraction of
     * available width, clamped to [captureMax].
     */
    const val CAPTURE_WIDTH_FRACTION = 0.72f
    val captureMin = 240.dp
    val captureMax = 300.dp

    /** Gap between the capture button and its outer ring (design: inset -14px). */
    val captureRingGap = 14.dp

    /**
     * The design centres the app in a 520px column. On a tablet a full-bleed
     * row of five nav tabs spreads the targets so far apart that finding them
     * by feel stops working, which is the whole point of the fixed layout.
     */
    val contentMaxWidth = 520.dp

    val screenPadding = 24.dp
    val cardRadius = 20.dp
    val itemRadius = 16.dp
    val buttonRadius = 16.dp
    val stepperSize = 56.dp
    val stepperRadius = 14.dp
    val navRadius = 14.dp
    val navMinHeight = 60.dp
    val bigButtonMinHeight = 64.dp
    val statusLineMinHeight = 56.dp
}

object EchoShapes {
    val card = RoundedCornerShape(EchoDimens.cardRadius)
    val item = RoundedCornerShape(EchoDimens.itemRadius)
    val button = RoundedCornerShape(EchoDimens.buttonRadius)
    val stepper = RoundedCornerShape(EchoDimens.stepperRadius)
    val nav = RoundedCornerShape(EchoDimens.navRadius)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Text styles mirroring the React Native StyleSheet. Sizes are in sp so they
 * honour the system font-scale setting — an accessibility app must never pin
 * text to a fixed pixel size.
 */
object EchoText {
    val logo = TextStyle(
        color = EchoColors.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        // Design: letter-spacing .35em on 22px.
        letterSpacing = 7.7.sp,
    )
    val badge = TextStyle(
        color = EchoColors.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
    val captureLabel = TextStyle(
        color = EchoColors.text,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        // Design: letter-spacing .18em on 26px.
        letterSpacing = 4.7.sp,
    )
    val captureSub = TextStyle(
        color = EchoColors.text2,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )

    /** Body copy under the capture target. Design body scale is 18px / 1.5. */
    val status = TextStyle(color = EchoColors.text2, fontSize = 17.sp, lineHeight = 26.sp)

    /** The lead line of the status block — always the state, in white and bold. */
    val statusLead = TextStyle(
        color = EchoColors.text,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp,
    )
    val kicker = TextStyle(
        color = EchoColors.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
    )
    val big = TextStyle(color = EchoColors.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
    val listTitle = TextStyle(
        color = EchoColors.text2,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
    )
    val itemTitle = TextStyle(color = EchoColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val itemDetail = TextStyle(color = EchoColors.text2, fontSize = 15.sp)
    val bigButton = TextStyle(color = EchoColors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val bigButtonSub = TextStyle(
        color = EchoColors.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    val stepGlyph = TextStyle(color = EchoColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val stepValue = TextStyle(color = EchoColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val navTab = TextStyle(color = EchoColors.text2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val navGlyph = TextStyle(color = EchoColors.text2, fontSize = 20.sp)
    val empty = TextStyle(color = EchoColors.text2, fontSize = 17.sp)
    val note = TextStyle(color = EchoColors.focus, fontSize = 14.sp, lineHeight = 20.sp)

    /** Transient gesture-confirmation toast. */
    val toast = TextStyle(color = EchoColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/** Material3 typography is unused — ECHO styles every text node explicitly. */
internal val EchoTypography = Typography()
