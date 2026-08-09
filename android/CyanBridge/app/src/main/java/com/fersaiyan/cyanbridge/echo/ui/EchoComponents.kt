package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.echo.model.EchoView
import androidx.compose.material3.Text

/* ------------------------------------------------------------------ header */

@Composable
fun EchoHeader(status: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = EchoDimens.screenPadding)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "E C H O",
            style = EchoText.logo,
            modifier = Modifier.semantics { heading() },
        )
        Box(
            modifier = Modifier
                .clip(EchoShapes.pill)
                .border(1.dp, EchoColors.border, EchoShapes.pill)
                .padding(horizontal = 12.dp, vertical = 4.dp)
                // Deliberately NOT a live region. Every status change is
                // already announced through EchoSpeech, which is where the
                // priority rules live; a live region would announce it a
                // second time and would bypass those rules entirely. See the
                // "never speak twice" note in EchoSpeech.
                .semantics {
                    contentDescription = "ECHO status"
                    stateDescription = status.lowercase()
                },
        ) {
            Text(text = status, style = EchoText.badge)
        }
    }
}

/* ------------------------------------------------------------------ button */

/**
 * The standard ECHO action button. Minimum height is 64dp — comfortably past
 * the 56dp floor every touch target in this app has to clear.
 */
@Composable
fun BigBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    contentDesc: String? = null,
) {
    val background = if (primary) EchoColors.text else EchoColors.surface
    val borderColor = when {
        primary -> EchoColors.text
        danger -> EchoColors.danger
        else -> EchoColors.border
    }
    val labelColor = when {
        primary -> EchoColors.bg
        danger -> EchoColors.danger
        else -> EchoColors.text
    }
    val subColor = if (primary) EchoColors.onPrimarySub else EchoColors.text2

    // A destructive action must not be signalled by colour alone (a red border reads as an
    // ordinary border to a red-green colour-blind user, and to anyone in bright sun). The
    // border thickens and the label carries a warning glyph, so the meaning survives with
    // colour removed entirely.
    val borderWidth = if (danger) 3.dp else 1.5.dp
    val displayLabel = if (danger) "⚠  $label" else label

    val interactionSource = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interactionSource)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = EchoDimens.bigButtonMinHeight)
            .padding(bottom = 14.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(EchoShapes.button)
            .background(background)
            .border(borderWidth, borderColor, EchoShapes.button)
            .echoFocusRing(interactionSource, EchoShapes.button)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = contentDesc ?: label }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = displayLabel,
            style = EchoText.bigButton.copy(color = labelColor),
            textAlign = TextAlign.Center,
        )
        if (sub != null) {
            Text(
                text = sub,
                style = EchoText.bigButtonSub.copy(color = subColor),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ----------------------------------------------------------------- stepper */

@Composable
fun Stepper(
    label: String,
    value: String,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(EchoShapes.item)
            .background(EchoColors.surface)
            .border(1.dp, EchoColors.border, EchoShapes.item)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = EchoText.itemTitle)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(glyph = "−", desc = "Decrease $label, currently $value") { onStep(-1) }
            Text(
                text = value,
                style = EchoText.stepValue,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(72.dp),
            )
            StepButton(glyph = "+", desc = "Increase $label, currently $value") { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(glyph: String, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(EchoDimens.stepperSize)
            .clip(EchoShapes.stepper)
            .background(EchoColors.surface2)
            .border(1.5.dp, EchoColors.border, EchoShapes.stepper)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = EchoText.stepGlyph)
    }
}

/* ------------------------------------------------------------------ navbar */

@Composable
fun EchoNavBar(
    current: EchoView,
    onSelect: (EchoView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EchoView.entries.forEach { v ->
            val active = v == current
            val tint = if (active) EchoColors.text else EchoColors.text2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = EchoDimens.navMinHeight)
                    .clip(EchoShapes.nav)
                    .background(if (active) EchoColors.surface else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (active) EchoColors.border else Color.Transparent,
                        EchoShapes.nav,
                    )
                    .clickable(role = Role.Tab) { onSelect(v) }
                    // mergeDescendants collapses the glyph and the label into a
                    // single focus stop. Without it TalkBack lands on the glyph
                    // first and reads a decorative symbol as if it were content.
                    .semantics(mergeDescendants = true) {
                        contentDescription = v.label
                        selected = active
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = v.glyph,
                    style = EchoText.navGlyph.copy(color = tint),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = v.tab,
                    style = EchoText.navTab.copy(color = tint),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/* -------------------------------------------------------------------- item */

/** The standard list row: a title, a detail line, optional coloured left rule. */
@Composable
fun EchoItem(
    title: String,
    detail: String? = null,
    extra: String? = null,
    ruleColor: Color? = null,
    contentDesc: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(EchoShapes.item)
            .background(EchoColors.surface)
            .border(1.dp, EchoColors.border, EchoShapes.item)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = contentDesc
                    ?: listOfNotNull(title, detail).joinToString(". ")
            },
    ) {
        if (ruleColor != null) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .defaultMinSize(minHeight = EchoDimens.minTouch)
                    .background(ruleColor),
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = EchoText.itemTitle)
            if (detail != null) Text(text = detail, style = EchoText.itemDetail)
            if (extra != null) Text(text = extra, style = EchoText.itemDetail)
        }
    }
}
