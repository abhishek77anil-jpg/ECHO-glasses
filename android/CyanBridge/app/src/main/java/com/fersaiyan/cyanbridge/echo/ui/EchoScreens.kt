package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.echo.audio.HapticPattern
import com.fersaiyan.cyanbridge.echo.data.EchoLimits
import com.fersaiyan.cyanbridge.echo.data.EchoSettings
import com.fersaiyan.cyanbridge.echo.model.AnalysisResult
import com.fersaiyan.cyanbridge.echo.model.CaptureStatus
import com.fersaiyan.cyanbridge.echo.model.HistoryEntry
import com.fersaiyan.cyanbridge.echo.model.LiveEvent
import com.fersaiyan.cyanbridge.echo.model.LiveKind
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Timestamps travel through the app as epoch milliseconds so they survive
 * persistence; they only become dates at the moment they are rendered.
 */
private fun formatTime(ms: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

private val ruleColor = mapOf(
    LiveKind.Arrive to EchoColors.ok,
    LiveKind.Leave to EchoColors.warn,
    LiveKind.Attn to EchoColors.focus,
)

/* -------------------------------------------------------------------- home */

@Composable
fun HomeScreen(
    status: CaptureStatus,
    error: String?,
    onCapture: () -> Unit,
    onCancel: () -> Unit,
) {
    val analyzing = status == CaptureStatus.Analyzing

    val captureInteraction = remember { MutableInteractionSource() }
    // Three redundant signals for "working": the word ANALYZING, the spoken status, and this
    // slow breath. Any one of them alone fails somebody.
    val pulse = rememberWorkingPulse(analyzing)
    val press = rememberPressScale(captureInteraction)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(EchoDimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        Column(
            modifier = Modifier
                // sizeIn rather than a fixed size: at a 200% font scale the label inside a
                // hard 280dp circle clips, and the people most likely to be running a large
                // font scale are exactly this app's users.
                .sizeIn(
                    minWidth = EchoDimens.captureSize,
                    minHeight = EchoDimens.captureSize,
                )
                .graphicsLayer {
                    val scale = pulse * press
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(EchoColors.surface)
                .border(
                    3.dp,
                    if (analyzing) EchoColors.focus else EchoColors.captureBorder,
                    CircleShape,
                )
                .echoFocusRing(captureInteraction, CircleShape, width = 4.dp)
                .clickable(
                    interactionSource = captureInteraction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                ) { if (analyzing) onCancel() else onCapture() }
                // The capture target is the first thing a user needs on Home
                // even though it sits in the visual centre, so reading order is
                // stated rather than inferred from geometry.
                .echoTraversalOrder(0f)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (analyzing) {
                        "Cancel analysis"
                    } else {
                        "Capture and analyze the person in front of you"
                    }
                    stateDescription = if (analyzing) "Analyzing" else "Ready"
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = if (analyzing) "ANALYZING" else "CAPTURE",
                style = EchoText.captureLabel,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (analyzing) "Double tap to cancel" else "Double tap to analyze",
                style = EchoText.captureSub,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                // Not a live region: the same words are spoken through
                // EchoSpeech at HIGH priority the moment the state changes.
                // Marking it live would say everything twice under TalkBack.
                .semantics(mergeDescendants = true) { },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                analyzing -> Text(
                    text = "Analyzing…",
                    style = EchoText.status.copy(
                        color = EchoColors.text,
                        fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                )

                status == CaptureStatus.Error -> {
                    Text(
                        text = error.orEmpty(),
                        style = EchoText.status.copy(
                            color = EchoColors.danger,
                            fontWeight = FontWeight.Bold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Double tap the center button to try again.",
                        style = EchoText.status,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    Text(
                        text = "Ready",
                        style = EchoText.status.copy(
                            color = EchoColors.text,
                            fontWeight = FontWeight.Bold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Double tap the center button to begin. Long press for help.",
                        style = EchoText.status,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ result */

@Composable
fun ResultScreen(
    result: AnalysisResult,
    onRepeat: () -> Unit,
    onAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = EchoDimens.screenPadding)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clip(EchoShapes.card)
                .background(EchoColors.surface)
                .border(1.dp, EchoColors.border, EchoShapes.card)
                .padding(EchoDimens.screenPadding),
        ) {
            Text(text = "EXPRESSION", style = EchoText.kicker)
            Text(
                text = result.expression,
                style = EchoText.big,
                modifier = Modifier.padding(top = 4.dp),
            )

            Text(
                text = "CONFIDENCE",
                style = EchoText.kicker,
                modifier = Modifier.padding(top = 18.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(10.dp)
                    .clip(EchoShapes.pill)
                    .background(EchoColors.surface2)
                    .border(1.dp, EchoColors.border, EchoShapes.pill)
                    .semantics {
                        contentDescription = "Confidence ${result.pct} percent"
                        progressBarRangeInfo =
                            ProgressBarRangeInfo(result.pct / 100f, 0f..1f, 100)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(result.pct / 100f)
                        .fillMaxHeight()
                        .clip(EchoShapes.pill)
                        .background(EchoColors.text),
                )
            }
            Text(
                text = "${result.pct}%",
                style = EchoText.itemDetail,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        BigBtn(
            label = "Repeat result",
            onClick = onRepeat,
            contentDesc = "Repeat the last result",
        )
        BigBtn(
            label = "Analyze again",
            onClick = onAgain,
            primary = true,
            contentDesc = "Analyze again",
        )
    }
}

/* -------------------------------------------------------------------- live */

@Composable
fun LiveScreen(on: Boolean, feed: List<LiveEvent>, onToggle: () -> Unit) {
    ScrollColumn {
        Text(
            text = "LIVE AWARENESS",
            style = EchoText.listTitle,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        BigBtn(
            label = if (on) "Stop live awareness" else "Start live awareness",
            sub = "Who's here · who's speaking · arrivals & exits",
            onClick = onToggle,
            primary = !on,
            contentDesc = if (on) {
                "Stop live awareness"
            } else {
                "Start live awareness. ECHO will quietly announce who is here, " +
                    "who is speaking, and when people arrive or leave."
            },
        )

        if (on && feed.isEmpty()) {
            Text(
                text = "Listening… the first update arrives in a moment.",
                style = EchoText.itemDetail,
            )
        }

        // Emphatically NOT a live region. These events are ambient and arrive
        // on a timer; EchoSpeech already speaks them at LOW priority so they
        // yield to anything the user actually asked for. A live region here
        // would announce every arrival over the top of a result mid-sentence,
        // which is the single behaviour the priority system exists to stop.
        Column {
            feed.forEach { ev ->
                EchoItem(
                    title = ev.label,
                    detail = ev.text,
                    extra = formatTime(ev.time),
                    ruleColor = ruleColor[ev.kind] ?: EchoColors.focus,
                    contentDesc = "${ev.label}. ${ev.text}, at ${formatTime(ev.time)}",
                )
            }
        }
    }
}

/* ----------------------------------------------------------------- history */

@Composable
fun HistoryScreen(items: List<HistoryEntry>, onClear: () -> Unit) {
    ScrollColumn {
        Text(
            text = "HISTORY",
            style = EchoText.listTitle,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        if (items.isEmpty()) {
            Text(
                text = "History is currently empty.",
                style = EchoText.empty,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
            )
        }

        items.forEach { h ->
            EchoItem(
                title = h.expression,
                detail = "Confidence ${h.pct}% · ${formatTime(h.time)}",
                contentDesc = "${h.expression}, confidence ${h.pct} percent, " +
                    "at ${formatTime(h.time)}",
            )
        }

        if (items.isNotEmpty()) {
            BigBtn(
                label = "Clear history",
                onClick = onClear,
                danger = true,
                contentDesc = "Clear history. This deletes all ${items.size} saved results.",
            )
        }
    }
}

/* ---------------------------------------------------------------- settings */

@Composable
fun SettingsScreen(
    settings: EchoSettings,
    onStepRate: (Int) -> Unit,
    onStepVolume: (Int) -> Unit,
    onStepHaptics: (Int) -> Unit,
    onTestHaptic: (HapticPattern) -> Unit,
    onReset: () -> Unit,
    screenReaderOn: Boolean,
    screenReaderName: String,
) {
    ScrollColumn {
        Text(
            text = "AUDIO",
            style = EchoText.listTitle,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        if (screenReaderOn) {
            Text(
                text = "$screenReaderName is running, so it speaks for ECHO using your own " +
                    "voice settings. Speed and volume below apply when $screenReaderName is off.",
                style = EchoText.note,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Stepper(
            label = "Voice speed",
            value = "${format1dp(settings.rate)}×",
            onStep = onStepRate,
        )
        Stepper(
            label = "Voice volume",
            value = "${(settings.volume * 100).roundToInt()}%",
            onStep = onStepVolume,
        )

        Text(
            text = "HAPTICS",
            style = EchoText.listTitle,
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
        )
        Stepper(
            label = "Feedback intensity",
            value = EchoLimits.hapticLabel(settings.hapticScale),
            onStep = onStepHaptics,
        )

        Text(
            text = "FEEL THE PATTERNS",
            style = EchoText.listTitle,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        Text(
            text = "Each buzz means something specific. Tap one to hear its name and feel it.",
            style = EchoText.itemDetail,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        HapticPattern.catalogue.forEach { p ->
            EchoItem(
                title = p.title,
                detail = p.desc,
                contentDesc = "Test ${p.title} pattern. ${p.desc}.",
                onClick = { onTestHaptic(p) },
            )
        }

        BigBtn(
            label = "Reset to defaults",
            onClick = onReset,
            contentDesc = "Reset voice speed, volume and haptic intensity to their " +
                "default values",
        )
    }
}

/* -------------------------------------------------------------------- help */

private val HELP_ROWS = listOf(
    "Single tap" to "Hear the current option",
    "Double tap" to "Activate",
    "Swipe left / right" to "Previous / next section",
    "Long press" to "Speak this help",
    "Two-finger double tap" to "Repeat last result",
)

/**
 * What help shows when TalkBack is running.
 *
 * None of the rows above apply in that mode — TalkBack consumes the touches
 * before ECHO sees them. Showing them anyway teaches a user gestures that will
 * appear broken, so the screen swaps to the mechanism that does work.
 */
private val HELP_ROWS_SCREEN_READER = listOf(
    "Swipe right / left" to "Move to the next or previous control",
    "Double tap" to "Activate the focused control",
    "Swipe up then right" to "Open the actions menu",
    "Actions menu" to "Repeat last result, where am I, change section, speak help",
)

@Composable
fun HelpScreen(
    onSpeak: () -> Unit,
    screenReaderOn: Boolean,
    screenReaderName: String,
) {
    ScrollColumn {
        Text(
            text = "GESTURE GUIDE",
            style = EchoText.listTitle,
            modifier = Modifier.padding(bottom = 14.dp),
        )

        if (screenReaderOn) {
            Text(
                text = "$screenReaderName is on, so it handles the gestures and speaks for " +
                    "ECHO — nothing is said twice. ECHO's own actions live in " +
                    "$screenReaderName's actions menu.",
                style = EchoText.note,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        val rows = if (screenReaderOn) HELP_ROWS_SCREEN_READER else HELP_ROWS
        rows.forEach { (gesture, meaning) ->
            EchoItem(
                title = gesture,
                detail = meaning,
                contentDesc = "$gesture. $meaning",
            )
        }

        BigBtn(
            label = "Speak this guide",
            onClick = onSpeak,
            contentDesc = "Speak the gesture guide aloud",
        )
    }
}

/* ------------------------------------------------------------------ shared */

@Composable
private fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = EchoDimens.screenPadding)
            .padding(top = 12.dp, bottom = EchoDimens.screenPadding),
    ) {
        content()
    }
}
