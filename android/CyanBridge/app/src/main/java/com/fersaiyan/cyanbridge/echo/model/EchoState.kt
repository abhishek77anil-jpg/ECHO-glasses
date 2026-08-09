package com.fersaiyan.cyanbridge.echo.model

/**
 * Capture state machine.
 *
 * ```
 *   Idle ──start──> Analyzing ──success──> Result
 *     ^                │  │
 *     │                │  └────failure───> Error
 *     └────cancel──────┘
 * ```
 *
 * [Result] and [Error] are terminal until the next capture starts — a result
 * stays on screen so the user can come back to it and replay it.
 *
 * Every transition out of [Analyzing] carries the `gen` of the run that
 * produced it. A run whose gen no longer matches was cancelled or superseded,
 * so its late result is dropped instead of overwriting a newer one — the bug
 * you only see on a slow link, when an abandoned capture lands after the user
 * has already moved on.
 */
enum class CaptureStatus(val badge: String) {
    /** The header pill reads READY on Error too, because the app *is* ready to
     *  try again — the failure itself is spoken and shown on Home. */
    Idle("READY"),
    Analyzing("ANALYZING"),
    Result("RESULT"),
    Error("READY"),
}

data class AnalysisResult(val expression: String, val pct: Int)

data class EchoState(
    val status: CaptureStatus = CaptureStatus.Idle,
    val result: AnalysisResult? = null,
    val error: String? = null,
    val gen: Long = 0L,
) {
    fun captureStart(gen: Long) =
        copy(status = CaptureStatus.Analyzing, error = null, gen = gen)

    fun captureSuccess(gen: Long, result: AnalysisResult) =
        if (gen != this.gen) this
        else copy(status = CaptureStatus.Result, result = result, error = null)

    fun captureFailure(gen: Long, error: String) =
        if (gen != this.gen) this
        else copy(status = CaptureStatus.Error, error = error)

    fun captureCancel(gen: Long) =
        copy(status = CaptureStatus.Idle, error = null, gen = gen)
}

/** History row. `time` is epoch milliseconds so it survives JSON persistence. */
data class HistoryEntry(val expression: String, val pct: Int, val time: Long)

/** Live-awareness event. [kind] drives both the left rule colour and the haptic. */
enum class LiveKind { Arrive, Leave, Attn }

data class LiveEvent(
    val label: String,
    val kind: LiveKind,
    val text: String,
    val time: Long,
)
