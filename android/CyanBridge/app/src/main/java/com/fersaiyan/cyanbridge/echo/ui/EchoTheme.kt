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
 * Ported 1:1 from echo/src/theme/colors.js — keep the two in sync.
 */
object EchoColors {
    val bg = Color(0xFF080808)
    val surface = Color(0xFF111111)
    val surface2 = Color(0xFF181818)
    val captureBorder = Color(0xFF2A2A2A)
    val text = Color(0xFFFFFFFF)
    val text2 = Color(0xFFA0A0A0)
    val border = Color(0xFF333333)
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
    val captureSize = 280.dp
    val screenPadding = 24.dp
    val cardRadius = 20.dp
    val itemRadius = 16.dp
    val buttonRadius = 16.dp
    val stepperSize = 56.dp
    val stepperRadius = 14.dp
    val navRadius = 14.dp
    val bigButtonMinHeight = 64.dp
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
        letterSpacing = 4.sp,
    )
    val badge = TextStyle(
        color = EchoColors.text2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
    val captureLabel = TextStyle(
        color = EchoColors.text,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 4.sp,
    )
    val captureSub = TextStyle(color = EchoColors.text2, fontSize = 14.sp)
    val status = TextStyle(color = EchoColors.text2, fontSize = 16.sp, lineHeight = 24.sp)
    val kicker = TextStyle(
        color = EchoColors.text2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    )
    val big = TextStyle(color = EchoColors.text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
    val listTitle = TextStyle(
        color = EchoColors.text2,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
    )
    val itemTitle = TextStyle(color = EchoColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    val itemDetail = TextStyle(color = EchoColors.text2, fontSize = 14.sp)
    val bigButton = TextStyle(color = EchoColors.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val stepGlyph = TextStyle(color = EchoColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val navTab = TextStyle(color = EchoColors.text2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val empty = TextStyle(color = EchoColors.text2, fontSize = 16.sp)
    val note = TextStyle(color = EchoColors.focus, fontSize = 14.sp, lineHeight = 20.sp)
}

/** Material3 typography is unused — ECHO styles every text node explicitly. */
internal val EchoTypography = Typography()
