package com.fersaiyan.cyanbridge.echo.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * ECHO haptic language — consistent and learnable.
 *
 * ```
 *   nav     one short buzz      selection / navigation
 *   confirm two short buzzes    action confirmed
 *   error   one long buzz       error / warning
 *   result  three pulses        analysis complete
 *   tick    light tap           setting changed
 * ```
 *
 * On Android these are real millisecond waveforms, which is what makes the
 * four patterns physically distinguishable. The JS build needed a separate
 * Taptic-sequencing path for iOS because iOS collapses any custom pattern
 * array into one generic buzz; that whole branch is gone here.
 *
 * A device with no vibrator fails silently instead of crashing.
 */
enum class HapticPattern(val key: String, val title: String, val desc: String) {
    Nav("nav", "Navigation", "One short buzz"),
    Confirm("confirm", "Confirmed", "Two short buzzes"),
    Error("error", "Error", "One long buzz"),
    Result("result", "Result ready", "Three pulses"),

    /** Not user-facing in the pattern tester — it is feedback, not vocabulary. */
    Tick("tick", "Tick", "Light tap");

    companion object {
        /** The user-facing catalogue. Settings renders this and the tester
         *  speaks it, so a new pattern is described in exactly one place. */
        val catalogue = listOf(Nav, Confirm, Error, Result)
    }
}

fun describeHaptic(p: HapticPattern): String = "${p.title}. ${p.desc}."

/** [waitBeforeStart, vibrate, pause, vibrate, ...] */
private val PATTERNS: Map<HapticPattern, LongArray> = mapOf(
    HapticPattern.Nav to longArrayOf(0, 45),
    HapticPattern.Confirm to longArrayOf(0, 55, 80, 55),
    HapticPattern.Error to longArrayOf(0, 400),
    HapticPattern.Result to longArrayOf(0, 80, 90, 80, 90, 80),
    HapticPattern.Tick to longArrayOf(0, 18),
)

class EchoHaptics(context: Context, private val scope: CoroutineScope) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** 0 off | 0.5 gentle | 1 normal | 1.5 strong */
    var scale: Float = 1f

    private var busyJob: Job? = null

    fun play(pattern: HapticPattern = HapticPattern.Nav) {
        if (scale == 0f) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        // Don't let overlapping patterns smear into one long buzz. Errors always
        // win — a failure the user cannot feel is a failure they will not notice.
        if (busyJob?.isActive == true && pattern != HapticPattern.Error) return

        val base = PATTERNS[pattern] ?: PATTERNS.getValue(HapticPattern.Nav)
        // Scale only the "on" durations (odd indices), keep gaps intact so the
        // rhythm stays recognisable at every intensity.
        val timings = LongArray(base.size) { i ->
            if (i % 2 == 1) max(12L, (base[i] * scale).roundToLong()) else base[i]
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(timings, -1)
            }
        }

        busyJob?.cancel()
        busyJob = scope.launch { delay(timings.sum()) }
    }

    /** Cancel any running pattern (used when live mode stops). */
    fun stop() {
        busyJob?.cancel()
        busyJob = null
        runCatching { vibrator?.cancel() }
    }
}
