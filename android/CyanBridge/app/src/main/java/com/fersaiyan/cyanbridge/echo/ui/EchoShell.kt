package com.fersaiyan.cyanbridge.echo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.fersaiyan.cyanbridge.echo.model.CaptureStatus
import com.fersaiyan.cyanbridge.echo.model.EchoView

/**
 * Root of the ECHO experience — the Kotlin counterpart of
 * `echo/src/EchoShell.js`.
 *
 * Layout is fixed: a header that never scrolls, one full-bleed gesture surface
 * in the middle, and a tab bar pinned to the bottom. The gesture surface is
 * the whole middle region on purpose — a user who cannot see the screen must
 * never have to find a control.
 */
@Composable
fun EchoShell(vm: EchoViewModel) {
    val view by vm.view.collectAsState()
    val echo by vm.echo.collectAsState()
    val settings by vm.settings.collectAsState()
    val history by vm.history.collectAsState()
    val liveOn by vm.liveOn.collectAsState()
    val liveFeed by vm.liveFeed.collectAsState()

    val androidView = LocalView.current

    // Hand the speech service a real view to post announcements through. When
    // TalkBack is running this is the ONLY path that produces sound — see the
    // "never speak twice" rule in EchoSpeech.
    DisposableEffect(androidView) {
        vm.speech.announceForAccessibility = { text ->
            androidView.announceForAccessibility(text)
        }
        onDispose { vm.speech.announceForAccessibility = null }
    }

    // The user can toggle TalkBack while ECHO is foregrounded. This does more
    // than change what Settings and Help say — it switches the entire input
    // model, because TalkBack consumes the touch gestures ECHO is built on.
    val screenReaderOn by rememberScreenReaderState()

    val hint by vm.hint.collectAsState()

    // Design: the app lives in a 520px column, centred. On a tablet a
    // full-bleed row of five nav tabs spreads the targets so far apart that
    // finding them by feel stops working — which is the point of a layout that
    // never moves.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoColors.bg),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = EchoDimens.contentMaxWidth)
            .systemBarsPadding(),
    ) {
        EchoHeader(status = echo.status.badge)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                // Two input models, never both. With TalkBack off the whole
                // middle region is the gesture surface; with TalkBack on the
                // same vocabulary is published as custom actions, reachable
                // from its local context menu.
                .echoGestures(
                    onPrimary = vm::onPrimary,
                    onWhereAmI = vm::onWhereAmI,
                    onLongPress = vm::onHelp,
                    onRepeat = vm::repeatLast,
                    onSwipe = vm::swipe,
                    enabled = !screenReaderOn,
                )
                .echoCustomActions(
                    onRepeat = vm::repeatLast,
                    onWhereAmI = vm::onWhereAmI,
                    onHelp = vm::onHelp,
                    onNextSection = { vm.swipe(next = true) },
                    onPreviousSection = { vm.swipe(next = false) },
                ),
        ) {
            when (view) {
                EchoView.Home -> {
                    val result = echo.result
                    // A result is a state of Home, not a view of its own.
                    if (echo.status == CaptureStatus.Result && result != null) {
                        ResultScreen(
                            result = result,
                            onRepeat = vm::repeatLast,
                            onAgain = vm::startCapture,
                        )
                    } else {
                        HomeScreen(
                            status = echo.status,
                            error = echo.error,
                            onCapture = vm::startCapture,
                            onCancel = vm::cancelCapture,
                        )
                    }
                }

                EchoView.Live -> LiveScreen(
                    on = liveOn,
                    feed = liveFeed,
                    onToggle = vm::toggleLive,
                )

                EchoView.History -> HistoryScreen(
                    items = history,
                    onClear = vm::clearHistory,
                )

                EchoView.Settings -> SettingsScreen(
                    settings = settings,
                    onStepRate = vm::stepRate,
                    onStepVolume = vm::stepVolume,
                    onStepHaptics = vm::stepHaptics,
                    onTestHaptic = vm::testHaptic,
                    onReset = vm::resetSettings,
                    screenReaderOn = screenReaderOn,
                    screenReaderName = vm.speech.screenReaderName,
                )

                EchoView.Help -> HelpScreen(
                    onSpeak = vm::speakHelp,
                    screenReaderOn = screenReaderOn,
                    screenReaderName = vm.speech.screenReaderName,
                )
            }
        }

        EchoNavBar(current = view, onSelect = { vm.goView(it) })
    }

        // Sits above everything, including the nav bar. Purely visual — it is
        // removed from the accessibility tree because the same words are always
        // spoken. See EchoToast.
        EchoToast(message = hint, reduceMotion = screenReaderOn)
    }
}
