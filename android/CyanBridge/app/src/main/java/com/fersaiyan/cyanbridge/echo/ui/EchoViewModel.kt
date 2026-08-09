package com.fersaiyan.cyanbridge.echo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fersaiyan.cyanbridge.echo.audio.EchoHaptics
import com.fersaiyan.cyanbridge.echo.audio.EchoSpeech
import com.fersaiyan.cyanbridge.echo.audio.HapticPattern
import com.fersaiyan.cyanbridge.echo.audio.Priority
import com.fersaiyan.cyanbridge.echo.audio.describeHaptic
import com.fersaiyan.cyanbridge.echo.data.EchoLimits
import com.fersaiyan.cyanbridge.echo.data.EchoSettings
import com.fersaiyan.cyanbridge.echo.data.EchoStore
import com.fersaiyan.cyanbridge.echo.data.clamp
import com.fersaiyan.cyanbridge.echo.model.AnalysisResult
import com.fersaiyan.cyanbridge.echo.model.CaptureStatus
import com.fersaiyan.cyanbridge.echo.model.EchoState
import com.fersaiyan.cyanbridge.echo.model.EchoView
import com.fersaiyan.cyanbridge.echo.model.GREETING
import com.fersaiyan.cyanbridge.echo.model.GREETING_SCREEN_READER
import com.fersaiyan.cyanbridge.echo.model.HELP_SPEECH
import com.fersaiyan.cyanbridge.echo.model.HELP_SPEECH_SCREEN_READER
import com.fersaiyan.cyanbridge.echo.model.HistoryEntry
import com.fersaiyan.cyanbridge.echo.model.LiveEvent
import com.fersaiyan.cyanbridge.echo.model.LiveKind
import com.fersaiyan.cyanbridge.echo.service.AnalysisOutcome
import com.fersaiyan.cyanbridge.echo.service.analyzePerson
import com.fersaiyan.cyanbridge.echo.service.liveSessionFlow
import com.fersaiyan.cyanbridge.echo.service.speakFailure
import com.fersaiyan.cyanbridge.echo.service.speakResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val LIVE_FEED_MAX = 8

/** Design: the toast holds for 1400ms before fading. */
private const val HINT_DURATION_MS = 1400L

/**
 * Orchestration for the ECHO screens: state, flows and the gesture handlers.
 *
 * This is the Kotlin counterpart of `echo/src/EchoShell.js`. The React version
 * needed a wall of refs so gesture callbacks built on an earlier render would
 * not read stale values; a ViewModel holds the live state directly, so those
 * are gone. The behaviour they protected — generation-checked results, guard
 * on the in-flight job rather than the status — is kept exactly.
 */
class EchoViewModel(app: Application) : AndroidViewModel(app) {

    private val store = EchoStore(app)
    val speech = EchoSpeech(app)
    private val haptics = EchoHaptics(app, viewModelScope)

    private val _view = MutableStateFlow(EchoView.Home)
    val view: StateFlow<EchoView> = _view.asStateFlow()

    private val _echo = MutableStateFlow(EchoState())
    val echo: StateFlow<EchoState> = _echo.asStateFlow()

    private val _settings = MutableStateFlow(EchoSettings())
    val settings: StateFlow<EchoSettings> = _settings.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _liveOn = MutableStateFlow(false)
    val liveOn: StateFlow<Boolean> = _liveOn.asStateFlow()

    private val _liveFeed = MutableStateFlow<List<LiveEvent>>(emptyList())
    val liveFeed: StateFlow<List<LiveEvent>> = _liveFeed.asStateFlow()

    /**
     * Transient gesture confirmation shown in the toast pill.
     *
     * Purely visual — the same event is always spoken as well, so this exists
     * for users with some sight who want instant proof the gesture landed
     * rather than waiting out a sentence. Never announced; see [EchoToast].
     */
    private val _hint = MutableStateFlow<String?>(null)
    val hint: StateFlow<String?> = _hint.asStateFlow()

    private var hintJob: Job? = null

    /** What "repeat last result" replays. */
    private var lastSpeech: String? = null

    private var captureJob: Job? = null
    private var liveJob: Job? = null
    private var generation = 0L

    init {
        speech.init()
        val saved = store.loadSettings()
        applySettings(saved)
        _history.value = store.loadHistory()

        viewModelScope.launch {
            delay(600)
            val greeting =
                if (speech.screenReaderOn) GREETING_SCREEN_READER else GREETING
            announce(greeting, HapticPattern.Nav, Priority.HIGH)
        }
    }

    private fun applySettings(s: EchoSettings) {
        _settings.value = s
        speech.rate = s.rate
        speech.volume = s.volume
        haptics.scale = s.hapticScale
    }

    private fun announce(
        text: String,
        haptic: HapticPattern? = null,
        priority: Priority = Priority.NORMAL,
    ) {
        speech.say(text, priority)
        haptic?.let { haptics.play(it) }
    }

    /** Shows a toast for [HINT_DURATION_MS], replacing any hint already up. */
    private fun showHint(text: String) {
        hintJob?.cancel()
        _hint.value = text
        hintJob = viewModelScope.launch {
            delay(HINT_DURATION_MS)
            _hint.value = null
        }
    }

    /* ---------- navigation ---------- */

    fun goView(target: EchoView, speak: Boolean = true) {
        _view.value = target
        if (speak) {
            announce("${target.label} Swipe right for ${target.next().name.lowercase()}.", HapticPattern.Nav)
        }
    }

    fun swipe(next: Boolean) {
        val cur = _view.value
        val target = if (next) cur.next() else cur.prev()
        goView(target)
        showHint(target.tab)
    }

    /* ---------- capture ---------- */

    fun startCapture() {
        // Guard on the job, not on echo.status: taps can arrive faster than the
        // state settles, and status would still read Idle for that instant.
        if (captureJob?.isActive == true) return

        val gen = ++generation
        _echo.update { it.captureStart(gen) }
        _view.value = EchoView.Home
        announce("Analyzing.", HapticPattern.Confirm, Priority.HIGH)

        captureJob = viewModelScope.launch {
            val outcome = runCatching { analyzePerson() }.getOrElse { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }

            when (outcome) {
                is AnalysisOutcome.Success -> {
                    val pct = (outcome.confidence * 100).roundToInt()
                    val speechText = speakResult(outcome.expression, pct)
                    lastSpeech = speechText
                    _echo.update {
                        it.captureSuccess(gen, AnalysisResult(outcome.expression, pct))
                    }
                    addHistory(HistoryEntry(outcome.expression, pct, System.currentTimeMillis()))
                    announce(speechText, HapticPattern.Result, Priority.HIGH)
                }

                is AnalysisOutcome.Failure -> {
                    val msg = speakFailure(outcome.error)
                    lastSpeech = msg
                    _echo.update { it.captureFailure(gen, msg) }
                    announce(msg, HapticPattern.Error, Priority.HIGH)
                }

                null -> {
                    val msg = "Something went wrong. Please try again."
                    lastSpeech = msg
                    _echo.update { it.captureFailure(gen, msg) }
                    announce(msg, HapticPattern.Error, Priority.HIGH)
                }
            }
        }
    }

    fun cancelCapture() {
        captureJob?.cancel()
        captureJob = null
        // Bumping the generation is what stops a result already in flight from
        // landing on a screen the user has cancelled out of.
        val gen = ++generation
        _echo.update { it.captureCancel(gen) }
        announce("Analysis cancelled.", HapticPattern.Nav, Priority.HIGH)
    }

    fun repeatLast() {
        val text = lastSpeech
        showHint("Repeat")
        if (text != null) {
            announce(text, HapticPattern.Nav, Priority.HIGH)
        } else {
            announce(
                "No result yet. Double tap the center of the screen to analyze.",
                HapticPattern.Nav,
                Priority.HIGH,
            )
        }
    }

    /* ---------- live awareness ---------- */

    fun toggleLive() {
        if (liveJob?.isActive == true) {
            liveJob?.cancel()
            liveJob = null
            haptics.stop()
            _liveOn.value = false
            announce("Live awareness off.", HapticPattern.Nav, Priority.HIGH)
            return
        }

        _liveOn.value = true
        announce(
            "Live awareness on. I will quietly tell you who is here and what changes. " +
                "Only you can hear this.",
            HapticPattern.Confirm,
            Priority.HIGH,
        )

        liveJob = viewModelScope.launch {
            liveSessionFlow().collect { ev ->
                _liveFeed.update { feed -> (listOf(ev) + feed).take(LIVE_FEED_MAX) }
                // LOW priority: an ambient update must never cut off a result
                // the user explicitly asked for.
                speech.say(ev.text, Priority.LOW)
                haptics.play(
                    if (ev.kind == LiveKind.Leave) HapticPattern.Nav else HapticPattern.Confirm,
                )
            }
        }
    }

    /* ---------- settings ---------- */

    fun stepRate(dir: Int) {
        val next = clamp(
            roundTo1dp(_settings.value.rate + dir * EchoLimits.RATE_STEP),
            EchoLimits.RATE_MIN,
            EchoLimits.RATE_MAX,
        )
        persist(_settings.value.copy(rate = next))
        // Spoken at the new rate, so the user hears the change itself.
        announce("Voice speed ${format1dp(next)}", HapticPattern.Tick)
    }

    fun stepVolume(dir: Int) {
        val next = clamp(
            roundTo1dp(_settings.value.volume + dir * EchoLimits.VOLUME_STEP),
            EchoLimits.VOLUME_MIN,
            EchoLimits.VOLUME_MAX,
        )
        persist(_settings.value.copy(volume = next))
        announce("Volume ${(next * 100).roundToInt()} percent", HapticPattern.Tick)
    }

    fun stepHaptics(dir: Int) {
        val steps = EchoLimits.hapticSteps
        val current = steps.indexOfFirst { it == _settings.value.hapticScale }.coerceAtLeast(0)
        val i = (current + dir).coerceIn(0, steps.size - 1)
        persist(_settings.value.copy(hapticScale = steps[i]))
        haptics.stop()
        announce("Haptics ${EchoLimits.hapticLabels[i]}", HapticPattern.Confirm)
    }

    private fun persist(s: EchoSettings) {
        applySettings(s)
        store.saveSettings(s)
    }

    fun resetSettings() {
        persist(EchoSettings())
        haptics.stop()
        announce("Settings reset to defaults.", HapticPattern.Confirm, Priority.HIGH)
    }

    /**
     * Speak the pattern's name first, then let the user feel it cleanly — a
     * buzz underneath the words is much harder to learn.
     */
    fun testHaptic(p: HapticPattern) {
        speech.say(describeHaptic(p), Priority.HIGH)
        viewModelScope.launch {
            delay(900)
            haptics.play(p)
        }
    }

    /* ---------- history ---------- */

    private fun addHistory(entry: HistoryEntry) {
        val next = (listOf(entry) + _history.value).take(EchoStore.MAX_HISTORY)
        _history.value = next
        store.saveHistory(next)
    }

    fun clearHistory() {
        _history.value = emptyList()
        store.clearHistory()
        announce("History cleared.", HapticPattern.Confirm, Priority.HIGH)
    }

    /* ---------- global gesture handlers ---------- */

    fun onPrimary() {
        when (_view.value) {
            EchoView.Home -> if (captureJob?.isActive == true) cancelCapture() else startCapture()
            EchoView.Live -> toggleLive()
            else -> announce(_view.value.label, HapticPattern.Nav)
        }
    }

    fun onWhereAmI() {
        val v = _view.value
        val extra =
            if (v == EchoView.Home && _echo.value.status == CaptureStatus.Result) {
                " Result ready. Two finger double tap to repeat it."
            } else {
                ""
            }
        announce(v.label + extra, HapticPattern.Tick)
    }

    fun onHelp() {
        _view.value = EchoView.Help
        showHint("Help")
        announce(helpText(), HapticPattern.Confirm, Priority.HIGH)
    }

    fun speakHelp() = announce(helpText(), HapticPattern.Nav, Priority.HIGH)

    /**
     * Reciting the gesture vocabulary to a TalkBack user describes controls
     * they cannot reach — TalkBack consumes those touches. Help has to teach
     * whichever input model is actually live.
     */
    private fun helpText(): String =
        if (speech.screenReaderOn) HELP_SPEECH_SCREEN_READER else HELP_SPEECH

    override fun onCleared() {
        liveJob?.cancel()
        captureJob?.cancel()
        haptics.stop()
        speech.release()
        super.onCleared()
    }

    private fun roundTo1dp(v: Float): Float = (v * 10).roundToInt() / 10f
}

internal fun format1dp(v: Float): String {
    val scaled = (v * 10).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}
